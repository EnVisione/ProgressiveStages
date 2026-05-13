package com.enviouse.progressivestages.server.triggers;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import com.mojang.logging.LogUtils;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runtime registry + dispatcher for {@code [[multi]]} requirements.
 *
 * <p>Each {@link MultiTrigger} is registered once at config-load time. When any of
 * the four single-trigger surfaces fires (item pickup, advancement earned, dimension
 * change, boss kill), this manager:
 *
 * <ol>
 *   <li>Looks up which multi-requirements reference that sub-trigger via the
 *       {@code byKey} index.</li>
 *   <li>Marks the sub-key as satisfied for the player in {@link TriggerPersistence}
 *       (persisted across restarts).</li>
 *   <li>Re-evaluates the requirement: {@code ALL_OF} grants when every sub-key is
 *       persisted; {@code ANY_OF} grants the moment one fires.</li>
 *   <li>Grants the stage via {@link ProgressiveStagesAPI} with cause
 *       {@link StageCause#MULTI_TRIGGER}.</li>
 * </ol>
 *
 * <p>On player login (or first server join) {@link #scanOnLogin(ServerPlayer)} walks
 * the inventory, advancements, and current dimension so a player who already had
 * those things gets retroactive credit. Boss kills are NOT scanned because there is
 * no record of past kills outside our own persistence.
 *
 * <p>This handler is subscribed independently of the existing single-trigger
 * handlers — a single pickup may fire both a single-trigger grant AND advance a
 * multi-trigger toward completion. Order does not matter: the stage check inside
 * each path short-circuits if the stage is already granted.
 */
public final class MultiTriggerManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PERSISTENCE_TYPE = "multi";

    private static final List<MultiTrigger> REQUIREMENTS = new ArrayList<>();

    /**
     * Index from canonical sub-key ({@code "item:minecraft:diamond"}) to every
     * requirement that references it. Built when requirements are registered;
     * used as the hot path during event dispatch.
     */
    private static final Map<String, List<MultiTrigger>> BY_KEY = new HashMap<>();

    private MultiTriggerManager() {}

    // -------------------- registry --------------------

    public static void register(MultiTrigger req) {
        if (req == null) return;
        REQUIREMENTS.add(req);
        for (MultiTrigger.SubTrigger sub : req.subTriggers()) {
            BY_KEY.computeIfAbsent(sub.canonicalKey(), k -> new ArrayList<>()).add(req);
        }
        LOGGER.debug("[ProgressiveStages] Registered multi-requirement {} -> {} ({} sub-triggers, {})",
            req.requirementId(), req.stageId(), req.subTriggers().size(), req.mode());
    }

    public static void clear() {
        REQUIREMENTS.clear();
        BY_KEY.clear();
    }

    public static List<MultiTrigger> getAll() {
        return Collections.unmodifiableList(REQUIREMENTS);
    }

    // -------------------- dispatch --------------------

    /**
     * Mark a sub-trigger as satisfied for a player and, if the parent requirement
     * is now complete, grant the stage. Idempotent — safe to call repeatedly for
     * the same {@code (player, type, key)}.
     */
    public static void notifyTriggered(ServerPlayer player, MultiTrigger.SubType type, ResourceLocation key) {
        if (REQUIREMENTS.isEmpty() || player == null || key == null) return;
        String subKey = type.prefix() + ":" + key.toString();
        List<MultiTrigger> reqs = BY_KEY.get(subKey);
        if (reqs == null || reqs.isEmpty()) return;

        TriggerPersistence persistence = TriggerPersistence.get(player.server);
        UUID playerId = player.getUUID();

        for (MultiTrigger req : reqs) {
            if (ProgressiveStagesAPI.hasStage(player, req.stageId())) continue;

            // Mark this sub-key satisfied for this player.
            String persistedKey = req.requirementId() + ":" + subKey;
            if (!persistence.hasTriggered(PERSISTENCE_TYPE, persistedKey, playerId)) {
                persistence.markTriggered(PERSISTENCE_TYPE, persistedKey, playerId);
                LOGGER.debug("[ProgressiveStages] Multi-trigger {} for {}: marked {} satisfied",
                    req.requirementId(), player.getName().getString(), subKey);
            }

            // Did this fire the requirement?
            if (isComplete(req, persistence, playerId)) {
                ProgressiveStagesAPI.grantStage(player, req.stageId(), StageCause.MULTI_TRIGGER);
                LOGGER.info("[ProgressiveStages] Multi-trigger {} ({} mode) completed for {} — granted stage '{}'",
                    req.requirementId(), req.mode(), player.getName().getString(), req.stageId());
            }
        }
    }

    /**
     * @return true if {@code req} is complete for {@code playerId} given the current
     * persisted sub-key set. ALL_OF requires every sub-key; ANY_OF requires one.
     */
    private static boolean isComplete(MultiTrigger req, TriggerPersistence persistence, UUID playerId) {
        if (req.mode() == MultiTrigger.Mode.ANY_OF) {
            for (MultiTrigger.SubTrigger sub : req.subTriggers()) {
                if (persistence.hasTriggered(PERSISTENCE_TYPE,
                        req.requirementId() + ":" + sub.canonicalKey(), playerId)) {
                    return true;
                }
            }
            return false;
        }
        // ALL_OF
        for (MultiTrigger.SubTrigger sub : req.subTriggers()) {
            if (!persistence.hasTriggered(PERSISTENCE_TYPE,
                    req.requirementId() + ":" + sub.canonicalKey(), playerId)) {
                return false;
            }
        }
        return true;
    }

    /** Number of sub-keys already satisfied for {@code req} for this player. Used by /stage list. */
    public static int countSatisfied(MultiTrigger req, ServerPlayer player) {
        if (player == null || player.server == null) return 0;
        TriggerPersistence persistence = TriggerPersistence.get(player.server);
        int n = 0;
        for (MultiTrigger.SubTrigger sub : req.subTriggers()) {
            if (persistence.hasTriggered(PERSISTENCE_TYPE,
                    req.requirementId() + ":" + sub.canonicalKey(), player.getUUID())) {
                n++;
            }
        }
        return n;
    }

    /** Reset every sub-key of a requirement for one player. Used by the admin reset command. */
    public static void resetForPlayer(ServerPlayer player, String requirementId) {
        if (player == null || requirementId == null) return;
        TriggerPersistence persistence = TriggerPersistence.get(player.server);
        for (MultiTrigger req : REQUIREMENTS) {
            if (!req.requirementId().equals(requirementId)) continue;
            for (MultiTrigger.SubTrigger sub : req.subTriggers()) {
                persistence.clearTrigger(PERSISTENCE_TYPE,
                    req.requirementId() + ":" + sub.canonicalKey(), player.getUUID());
            }
        }
    }

    // -------------------- login scan --------------------

    /**
     * Walk the player's current inventory, advancement progress, and current
     * dimension and fire {@link #notifyTriggered} for every match. Lets new
     * multi-requirements credit players for things they already had/done.
     *
     * <p>Boss kills cannot be retroactively detected — players need to kill them
     * again after the requirement is added.
     */
    public static void scanOnLogin(ServerPlayer player) {
        if (REQUIREMENTS.isEmpty()) return;

        // 1. Inventory — every item slot.
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null) notifyTriggered(player, MultiTrigger.SubType.ITEM, id);
        }

        // 2. Advancements — every completed advancement.
        PlayerAdvancements advancements = player.getAdvancements();
        if (advancements != null && player.server != null) {
            player.server.getAdvancements().getAllAdvancements().forEach(holder -> {
                if (advancements.getOrStartProgress(holder).isDone()) {
                    notifyTriggered(player, MultiTrigger.SubType.ADVANCEMENT, holder.id());
                }
            });
        }

        // 3. Current dimension — register as visited.
        ResourceLocation dim = player.level().dimension().location();
        notifyTriggered(player, MultiTrigger.SubType.DIMENSION, dim);
    }

    // -------------------- event listeners --------------------

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        ItemEntity itemEntity = event.getItemEntity();
        if (itemEntity == null || itemEntity.getItem().isEmpty()) return;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
        if (id != null) notifyTriggered(player, MultiTrigger.SubType.ITEM, id);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AdvancementHolder holder = event.getAdvancement();
        if (holder == null) return;
        notifyTriggered(player, MultiTrigger.SubType.ADVANCEMENT, holder.id());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ResourceLocation id = event.getTo().location();
        notifyTriggered(player, MultiTrigger.SubType.DIMENSION, id);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onEntityDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        ServerPlayer killer = resolveKiller(event);
        if (killer == null) return;
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
        if (entityId == null) return;
        notifyTriggered(killer, MultiTrigger.SubType.BOSS, entityId);
    }

    /** Mirror of {@code BossKillStageGrants#getKillingPlayer}: direct → projectile → recent-hurt-by. */
    private static ServerPlayer resolveKiller(LivingDeathEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof ServerPlayer p) return p;
        Entity source = event.getSource().getEntity();
        if (source instanceof ServerPlayer p) return p;
        LivingEntity victim = event.getEntity();
        if (victim.getLastHurtByMob() instanceof ServerPlayer p) {
            if (victim.getLastHurtByMobTimestamp() > victim.tickCount - 100) return p;
        }
        return null;
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            scanOnLogin(player);
        }
    }
}
