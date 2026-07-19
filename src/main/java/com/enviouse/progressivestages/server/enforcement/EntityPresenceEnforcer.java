package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.EnforcementCategory;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.network.NetworkHandler;
import com.enviouse.progressivestages.common.rehaul.RuleEffect;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.server.rehaul.RehaulRuntime;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * resolves whether an entity may be present for one player.
 * denied entities are concealed and harmless for that player.
 * spawns are cancelled only when every relevant player is denied the entity.
 */
public final class EntityPresenceEnforcer {

    private record GateDecision(boolean blocked, int priority) {}

    private static final Map<UUID, Set<ResourceLocation>> LAST_CLIENT_STATE = new ConcurrentHashMap<>();

    private EntityPresenceEnforcer() {}

    public static boolean isPresenceDenied(ServerPlayer player, EntityType<?> entityType) {
        if (player == null || entityType == null) return false;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return false;

        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        if (id == null) return false;
        Holder<EntityType<?>> holder = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entityType);
        LockRegistry registry = LockRegistry.getInstance();

        boolean staticEntityBlock = StageConfig.isBlockEntityAttack()
            && registry.getRequiredStagesForEntity(entityType).stream()
                .anyMatch(stage -> !StageManager.getInstance().hasStage(player, stage));
        GateDecision entity = decision(player, "entities", "presence", id, holder, staticEntityBlock,
            EnforcementCategory.ENTITY_ATTACK);

        GateDecision spawn = StageConfig.isBlockMobSpawns()
            ? decision(player, "mobs", "spawn", id, holder,
                registry.isEntitySpawnBlockedFor(player, entityType), null)
            : new GateDecision(false, Integer.MIN_VALUE);

        return choose(entity, spawn).blocked();
    }

    public static boolean shouldCancelSpawn(Mob mob, ServerLevelAccessor level,
                                            double x, double y, double z) {
        if (mob == null || !(level.getLevel() instanceof ServerLevel serverLevel)) return false;
        if (!StageConfig.isBlockMobSpawns()) return false;
        List<ServerPlayer> relevant = relevantPlayers(serverLevel, x, z);
        if (relevant.isEmpty()) return false;
        List<Boolean> denied = new ArrayList<>(relevant.size());
        for (ServerPlayer player : relevant) denied.add(isPresenceDenied(player, mob.getType()));
        return allRelevantPlayersDenied(denied);
    }

    static boolean allRelevantPlayersDenied(List<Boolean> denied) {
        return denied != null && !denied.isEmpty() && denied.stream().allMatch(Boolean.TRUE::equals);
    }

    static int simulationDistanceBlocks(int chunks) {
        return Math.max(1, chunks) * 16;
    }

    static boolean withinSimulationDistance(int playerChunkX, int playerChunkZ,
                                            int spawnChunkX, int spawnChunkZ, int chunks) {
        int distance = Math.max(1, chunks);
        return Math.abs(playerChunkX - spawnChunkX) <= distance
            && Math.abs(playerChunkZ - spawnChunkZ) <= distance;
    }

    public static void tick(ServerPlayer player) {
        if (player == null || (player.level().getGameTime() + player.getId()) % 10 != 0) return;
        syncClientState(player, false);
    }

    public static void syncClientState(ServerPlayer player, boolean force) {
        if (player == null || player.connection == null) return;
        Set<ResourceLocation> concealed = concealedTypeIds(player);
        Set<ResourceLocation> previous = LAST_CLIENT_STATE.put(player.getUUID(), concealed);
        if (force || !concealed.equals(previous)) {
            refreshEntityTracking(player);
            NetworkHandler.sendEntityVisibility(player, concealed);
        }
        pacifyNearbyMobs(player, concealed);
    }

    public static void clearPlayer(UUID playerId) {
        if (playerId != null) LAST_CLIENT_STATE.remove(playerId);
    }

    public static void resetRuntimeState() {
        LAST_CLIENT_STATE.clear();
    }

    public static void preventTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof ServerPlayer player)) return;
        if (isPresenceDenied(player, event.getEntity().getType())) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    public static boolean blocksDamage(ServerPlayer player, DamageSource source) {
        return player != null && source != null && source.getEntity() instanceof Mob attacker
            && isPresenceDenied(player, attacker.getType());
    }

    public static boolean shouldConcealTracking(ServerPlayer player, Entity entity) {
        return player != null && entity != null && entity != player
            && isPresenceDenied(player, entity.getType());
    }

    private static List<ServerPlayer> relevantPlayers(ServerLevel level, double x, double z) {
        int chunks = level.getServer().getPlayerList().getSimulationDistance();
        int spawnChunkX = ((int) Math.floor(x)) >> 4;
        int spawnChunkZ = ((int) Math.floor(z)) >> 4;
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            if (withinSimulationDistance(player.chunkPosition().x, player.chunkPosition().z,
                    spawnChunkX, spawnChunkZ, chunks)) {
                players.add(player);
            }
        }
        return players;
    }

    private static Set<ResourceLocation> concealedTypeIds(ServerPlayer player) {
        LinkedHashSet<ResourceLocation> output = new LinkedHashSet<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (!isPresenceDenied(player, type)) continue;
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id != null) output.add(id);
        }
        return Set.copyOf(output);
    }

    private static void refreshEntityTracking(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        var tracked = ((ChunkMapTrackingAccess) level.getChunkSource().chunkMap)
            .progressivestages$getEntityMap().values();
        for (Object value : tracked) {
            if (value instanceof TrackedEntityBridge bridge) {
                bridge.progressivestages$refreshPlayer(player);
            }
        }
    }

    private static void pacifyNearbyMobs(ServerPlayer player, Set<ResourceLocation> concealed) {
        if (concealed.isEmpty() || !(player.level() instanceof ServerLevel level)) return;
        int radius = simulationDistanceBlocks(level.getServer().getPlayerList().getSimulationDistance());
        AABB area = player.getBoundingBox().inflate(radius, radius, radius);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area, value -> value.getTarget() == player)) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
            if (id == null || !concealed.contains(id)) continue;
            mob.setTarget(null);
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
        }
    }

    private static GateDecision decision(ServerPlayer player, String category, String action,
                                         ResourceLocation id, Holder<?> holder, boolean staticBlocked,
                                         EnforcementCategory enforcementCategory) {
        var engine = RehaulRuntime.get().rules();
        var trace = engine.resolveUntracked(player, category, action, id, holder).orElse(null);
        if (trace == null || trace.winningEffect() == null) return new GateDecision(staticBlocked, 0);
        RuleEffect effect = trace.winningEffect();
        if (effect != RuleEffect.LOCK && effect != RuleEffect.DENY
                && effect != RuleEffect.ALLOW && effect != RuleEffect.UNLOCK) {
            return new GateDecision(staticBlocked, 0);
        }
        var winner = trace.winner().flatMap(engine::findRule).orElse(null);
        if (winner == null) return new GateDecision(staticBlocked, 0);
        if (enforcementCategory != null && !LockRegistry.getInstance()
                .isCategoryEnforced(winner.ownerStage(), enforcementCategory)) {
            return new GateDecision(false, Integer.MIN_VALUE);
        }
        int priority = winner.priority();
        return new GateDecision(trace.blocked(), priority);
    }

    private static GateDecision choose(GateDecision first, GateDecision second) {
        if (first.priority() > second.priority()) return first;
        if (second.priority() > first.priority()) return second;
        return new GateDecision(first.blocked() || second.blocked(), first.priority());
    }
}
