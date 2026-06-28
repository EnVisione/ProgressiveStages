package com.enviouse.progressivestages.server.triggers;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.trigger.TriggerCondition;
import com.enviouse.progressivestages.common.trigger.TriggerConditionType;
import com.enviouse.progressivestages.common.trigger.TriggerMode;
import com.enviouse.progressivestages.common.trigger.TriggerRule;
import com.mojang.logging.LogUtils;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v2.3 per-stage trigger engine. Replaces the old global {@code triggers.toml} +
 * {@code MultiTriggerManager}. Each stage declares {@code [[triggers]]} rules; this engine
 * evaluates them and auto-grants the stage when any rule is satisfied.
 *
 * <p><b>Counters come from vanilla statistics.</b> Kill/mine/craft/pickup/use/distance/etc.
 * read {@code ServerPlayer.getStats()}, which Minecraft already tracks and persists per
 * player. That makes counter conditions automatically retroactive (a player who already did
 * the thing is credited the moment the trigger loads) and restart-proof — no bespoke counter
 * storage needed. Only "visited a dimension/biome" one-shots are persisted by us (vanilla
 * doesn't keep a monotonic "ever visited" flag), via {@link TriggerPersistence}.
 *
 * <p><b>Scope.</b> Progress is read from the individual player; the first team member to
 * satisfy a whole rule unlocks the stage for the entire team (grants propagate through the
 * normal team-sync path). This matches the per-UUID semantics of the previous system.
 *
 * <p><b>Evaluation cadence.</b> A light per-player poll runs every {@link #POLL_INTERVAL_TICKS}
 * ticks reading the current stats. Relevant events (kill, advancement earned, dimension change,
 * login) additionally schedule an immediate re-evaluation on the next tick so unlocks feel
 * instant; kills are deferred one tick so the kill statistic has been awarded first.
 */
public final class StageTriggerEvaluator {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PERSISTENCE_TYPE = "stagetrigger";

    /** How often (in ticks) the background poll re-checks a player's trigger progress. */
    public static final int POLL_INTERVAL_TICKS = 20;

    /** stageId -> its trigger rules. Only stages that declare at least one rule appear here. */
    private static final Map<StageId, List<TriggerRule>> RULES = new LinkedHashMap<>();
    private static volatile boolean active = false;

    private static final Map<UUID, Long> lastPoll = new ConcurrentHashMap<>();

    /** Distance movement keyword -> the custom statistic that tracks it (in centimetres). */
    private static final Map<String, ResourceLocation> DISTANCE_STATS = new LinkedHashMap<>();
    static {
        DISTANCE_STATS.put("walk", Stats.WALK_ONE_CM);
        DISTANCE_STATS.put("sprint", Stats.SPRINT_ONE_CM);
        DISTANCE_STATS.put("crouch", Stats.CROUCH_ONE_CM);
        DISTANCE_STATS.put("sneak", Stats.CROUCH_ONE_CM);
        DISTANCE_STATS.put("swim", Stats.SWIM_ONE_CM);
        DISTANCE_STATS.put("fall", Stats.FALL_ONE_CM);
        DISTANCE_STATS.put("climb", Stats.CLIMB_ONE_CM);
        DISTANCE_STATS.put("fly", Stats.FLY_ONE_CM);
        DISTANCE_STATS.put("dive", Stats.WALK_UNDER_WATER_ONE_CM);
        DISTANCE_STATS.put("walk_under_water", Stats.WALK_UNDER_WATER_ONE_CM);
        DISTANCE_STATS.put("walk_on_water", Stats.WALK_ON_WATER_ONE_CM);
        DISTANCE_STATS.put("minecart", Stats.MINECART_ONE_CM);
        DISTANCE_STATS.put("boat", Stats.BOAT_ONE_CM);
        DISTANCE_STATS.put("pig", Stats.PIG_ONE_CM);
        DISTANCE_STATS.put("horse", Stats.HORSE_ONE_CM);
        DISTANCE_STATS.put("strider", Stats.STRIDER_ONE_CM);
        DISTANCE_STATS.put("aviate", Stats.AVIATE_ONE_CM);
        DISTANCE_STATS.put("elytra", Stats.AVIATE_ONE_CM);
    }

    private StageTriggerEvaluator() {}

    // ============================ registry ============================

    /** Rebuild the rule registry from the loaded stage definitions. Called on load + reload. */
    public static void rebuild(Collection<StageDefinition> stages) {
        RULES.clear();
        for (StageDefinition def : stages) {
            if (def.hasTriggers()) {
                RULES.put(def.getId(), def.getTriggers());
            }
        }
        active = !RULES.isEmpty();
        if (active) {
            int ruleCount = RULES.values().stream().mapToInt(List::size).sum();
            LOGGER.info("[ProgressiveStages] Per-stage triggers active for {} stage(s), {} rule(s) total",
                RULES.size(), ruleCount);
        } else {
            LOGGER.debug("[ProgressiveStages] No per-stage triggers declared");
        }
    }

    public static boolean isActive() { return active; }
    public static Set<StageId> stagesWithTriggers() { return RULES.keySet(); }
    public static List<TriggerRule> rulesFor(StageId id) { return RULES.getOrDefault(id, List.of()); }

    // ============================ events ============================

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!active) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now = player.level().getGameTime();
        int interval = Math.max(1, StageConfig.getTriggerPollInterval());
        Long last = lastPoll.get(player.getUUID());
        if (last != null && now - last < interval) return;
        lastPoll.put(player.getUUID(), now);
        evaluatePlayer(player);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!active) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            evaluatePlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            lastPoll.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!active) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            scheduleEvaluate(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!active) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            scheduleEvaluate(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onEntityDeath(LivingDeathEvent event) {
        if (!active) return;
        ServerPlayer killer = resolveKiller(event);
        if (killer != null) {
            // Defer one tick: the ENTITY_KILLED statistic is awarded by die() AFTER this event.
            scheduleEvaluate(killer);
        }
    }

    private static void scheduleEvaluate(ServerPlayer player) {
        var server = player.server;
        if (server == null) {
            evaluatePlayer(player);
            return;
        }
        // Capture the UUID, not the player object: by next tick the player may have logged out,
        // so we re-resolve the live ServerPlayer (and skip if they're gone — a reconnect will be
        // credited retroactively from vanilla stats on the next login/poll).
        UUID uuid = player.getUUID();
        server.execute(() -> {
            ServerPlayer current = server.getPlayerList().getPlayer(uuid);
            if (current != null) evaluatePlayer(current);
        });
    }

    /** Mirror of the old boss-kill killer resolution: direct → source → recent-hurt-by. */
    private static ServerPlayer resolveKiller(LivingDeathEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof ServerPlayer p) return p;
        Entity source = event.getSource().getEntity();
        if (source instanceof ServerPlayer p) return p;
        LivingEntity victim = event.getEntity();
        if (victim.getLastHurtByMob() instanceof ServerPlayer p
                && victim.getLastHurtByMobTimestamp() > victim.tickCount - 100) {
            return p;
        }
        return null;
    }

    // ============================ evaluation ============================

    /** Evaluate every triggered stage the player's team doesn't own, granting on first satisfied rule. */
    public static void evaluatePlayer(ServerPlayer player) {
        if (!active || player == null) return;
        updateOneShots(player);
        for (Map.Entry<StageId, List<TriggerRule>> e : RULES.entrySet()) {
            StageId stageId = e.getKey();
            if (ProgressiveStagesAPI.hasStage(player, stageId)) continue;
            // v2.3: triggers RESPECT dependencies — a stage is not auto-granted until every
            // prerequisite is owned. (Counter progress keeps accruing via vanilla stats, so the
            // moment the last prerequisite is granted the next poll completes the unlock.) Packs
            // that want a trigger to fire freely simply omit the stage's `dependency`.
            if (!dependenciesSatisfied(player, stageId)) continue;
            for (TriggerRule rule : e.getValue()) {
                if (ruleSatisfied(player, stageId, rule)) {
                    if (ProgressiveStagesAPI.grantStage(player, stageId, StageCause.TRIGGER)) {
                        LOGGER.info("[ProgressiveStages] Trigger satisfied for {} — granted stage '{}'",
                            player.getName().getString(), stageId);
                    }
                    break;
                }
            }
        }
    }

    /** Persist "visited" for any one-shot dimension/biome condition the player currently matches. */
    private static void updateOneShots(ServerPlayer player) {
        for (Map.Entry<StageId, List<TriggerRule>> e : RULES.entrySet()) {
            StageId stageId = e.getKey();
            if (ProgressiveStagesAPI.hasStage(player, stageId)) continue;
            for (TriggerRule rule : e.getValue()) {
                for (TriggerCondition c : rule.conditions()) {
                    if (c.type().isPersistedOneShot() && matchesCurrentState(player, c)
                            && !oneShotMarked(player, stageId, c)) {
                        markOneShot(player, stageId, c);
                    }
                }
            }
        }
    }

    /** True if every declared dependency of the stage is already owned by the player's team. */
    private static boolean dependenciesSatisfied(ServerPlayer player, StageId stageId) {
        return com.enviouse.progressivestages.common.stage.StageManager.getInstance()
            .getMissingDependencies(player, stageId).isEmpty();
    }

    private static boolean ruleSatisfied(ServerPlayer player, StageId stageId, TriggerRule rule) {
        if (rule.mode() == TriggerMode.ANY_OF) {
            for (TriggerCondition c : rule.conditions()) {
                if (currentProgress(player, stageId, c) >= c.count()) return true;
            }
            return false;
        }
        for (TriggerCondition c : rule.conditions()) {
            if (currentProgress(player, stageId, c) < c.count()) return false;
        }
        return true;
    }

    /**
     * Current progress of one condition for a player (read-only; never mutates persisted state).
     * Counters return the raw statistic; one-shots/state return {@code count} when satisfied else 0.
     */
    public static long currentProgress(ServerPlayer player, StageId stageId, TriggerCondition c) {
        return switch (c.type()) {
            case KILL       -> sumEntityStat(player, c, Stats.ENTITY_KILLED::get);
            case MINE       -> sumBlockStat(player, c, Stats.BLOCK_MINED::get);
            case CRAFT      -> sumItemStat(player, c, Stats.ITEM_CRAFTED::get);
            case PICKUP     -> sumItemStat(player, c, Stats.ITEM_PICKED_UP::get);
            case USE        -> sumItemStat(player, c, Stats.ITEM_USED::get);
            case DROP       -> sumItemStat(player, c, Stats.ITEM_DROPPED::get);
            case BREAK_ITEM -> sumItemStat(player, c, Stats.ITEM_BROKEN::get);
            case DISTANCE   -> distanceBlocks(player, c.target());
            case STAT       -> customStat(player, resolve(c.targetBody()));
            case PLAY_TIME  -> customStat(player, Stats.PLAY_TIME) / 1200L; // ticks -> minutes
            case LEVEL      -> player.experienceLevel;
            case XP         -> player.totalExperience;
            case ADVANCEMENT -> advancementDone(player, c) ? c.count() : 0L;
            case DIMENSION  -> (oneShotMarked(player, stageId, c) || matchesCurrentState(player, c)) ? c.count() : 0L;
            case BIOME      -> (oneShotMarked(player, stageId, c) || matchesCurrentState(player, c)) ? c.count() : 0L;
            case HAS_ITEM   -> heldItemCount(player, c);
        };
    }

    // ---- counter helpers ----

    private interface ItemStat { net.minecraft.stats.Stat<Item> get(Item item); }
    private interface BlockStat { net.minecraft.stats.Stat<Block> get(Block block); }
    private interface EntityStat { net.minecraft.stats.Stat<EntityType<?>> get(EntityType<?> type); }

    private static long sumItemStat(ServerPlayer player, TriggerCondition c, ItemStat fn) {
        if (c.targetIsTag()) {
            ResourceLocation tagId = resolve(c.targetBody());
            if (tagId == null) return 0;
            TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
            long sum = 0;
            var setOpt = BuiltInRegistries.ITEM.getTag(tag);
            if (setOpt.isPresent()) {
                for (Holder<Item> h : setOpt.get()) sum += player.getStats().getValue(fn.get(h.value()));
            }
            return sum;
        }
        ResourceLocation id = resolve(c.targetBody());
        if (id == null) return 0;
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return item == null ? 0 : player.getStats().getValue(fn.get(item));
    }

    private static long sumBlockStat(ServerPlayer player, TriggerCondition c, BlockStat fn) {
        if (c.targetIsTag()) {
            ResourceLocation tagId = resolve(c.targetBody());
            if (tagId == null) return 0;
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, tagId);
            long sum = 0;
            var setOpt = BuiltInRegistries.BLOCK.getTag(tag);
            if (setOpt.isPresent()) {
                for (Holder<Block> h : setOpt.get()) sum += player.getStats().getValue(fn.get(h.value()));
            }
            return sum;
        }
        ResourceLocation id = resolve(c.targetBody());
        if (id == null) return 0;
        Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        return block == null ? 0 : player.getStats().getValue(fn.get(block));
    }

    private static long sumEntityStat(ServerPlayer player, TriggerCondition c, EntityStat fn) {
        if (c.targetIsTag()) {
            ResourceLocation tagId = resolve(c.targetBody());
            if (tagId == null) return 0;
            TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, tagId);
            long sum = 0;
            var setOpt = BuiltInRegistries.ENTITY_TYPE.getTag(tag);
            if (setOpt.isPresent()) {
                for (Holder<EntityType<?>> h : setOpt.get()) sum += player.getStats().getValue(fn.get(h.value()));
            }
            return sum;
        }
        ResourceLocation id = resolve(c.targetBody());
        if (id == null) return 0;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        return type == null ? 0 : player.getStats().getValue(fn.get(type));
    }

    /** Distance in BLOCKS for a movement keyword ("all" sums every tracked movement stat). */
    private static long distanceBlocks(ServerPlayer player, String movement) {
        String m = movement == null ? "all" : movement.trim().toLowerCase(Locale.ROOT);
        if (m.isEmpty() || m.equals("all") || m.equals("total") || m.equals("any")) {
            long cm = 0;
            for (ResourceLocation rl : new java.util.LinkedHashSet<>(DISTANCE_STATS.values())) {
                cm += customStat(player, rl);
            }
            return cm / 100L;
        }
        ResourceLocation rl = DISTANCE_STATS.get(m);
        if (rl == null) {
            // Allow a raw custom-stat id as a movement target too.
            rl = resolve(m);
        }
        return rl == null ? 0 : customStat(player, rl) / 100L;
    }

    private static long customStat(ServerPlayer player, ResourceLocation customStatId) {
        if (customStatId == null) return 0;
        return player.getStats().getValue(Stats.CUSTOM.get(customStatId));
    }

    // ---- one-shot / state helpers ----

    private static boolean advancementDone(ServerPlayer player, TriggerCondition c) {
        ResourceLocation id = resolve(c.targetBody());
        if (id == null || player.server == null) return false;
        AdvancementHolder holder = player.server.getAdvancements().get(id);
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static boolean matchesCurrentState(ServerPlayer player, TriggerCondition c) {
        if (c.type() == TriggerConditionType.DIMENSION) {
            ResourceLocation id = resolve(c.targetBody());
            return id != null && player.level().dimension().location().equals(id);
        }
        if (c.type() == TriggerConditionType.BIOME) {
            ResourceLocation id = resolve(c.targetBody());
            if (id == null) return false;
            Holder<net.minecraft.world.level.biome.Biome> biome = player.level().getBiome(player.blockPosition());
            if (c.targetIsTag()) {
                return biome.is(TagKey.create(Registries.BIOME, id));
            }
            return biome.unwrapKey().map(k -> k.location().equals(id)).orElse(false);
        }
        return false;
    }

    private static long heldItemCount(ServerPlayer player, TriggerCondition c) {
        ResourceLocation id = resolve(c.targetBody());
        if (id == null) return 0;
        TagKey<Item> tag = c.targetIsTag() ? TagKey.create(Registries.ITEM, id) : null;
        Item item = tag == null ? BuiltInRegistries.ITEM.getOptional(id).orElse(null) : null;
        if (tag == null && item == null) return 0;
        long count = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            boolean match = tag != null ? stack.is(tag) : stack.is(item);
            if (match) count += stack.getCount();
        }
        return count;
    }

    private static ResourceLocation resolve(String body) {
        return body == null || body.isEmpty() ? null : ResourceLocation.tryParse(body);
    }

    // ---- persistence ----

    private static String persistKey(StageId stageId, TriggerCondition c) {
        return stageId.toString() + "|" + c.canonicalKey();
    }

    private static boolean oneShotMarked(ServerPlayer player, StageId stageId, TriggerCondition c) {
        if (player.server == null) return false;
        return TriggerPersistence.get(player.server)
            .hasTriggered(PERSISTENCE_TYPE, persistKey(stageId, c), player.getUUID());
    }

    private static void markOneShot(ServerPlayer player, StageId stageId, TriggerCondition c) {
        if (player.server == null) return;
        TriggerPersistence.get(player.server)
            .markTriggered(PERSISTENCE_TYPE, persistKey(stageId, c), player.getUUID());
    }

    /** Admin reset: clear every persisted one-shot for a stage for one player. */
    public static void resetStageFor(ServerPlayer player, StageId stageId) {
        if (player.server == null) return;
        TriggerPersistence persistence = TriggerPersistence.get(player.server);
        for (TriggerRule rule : rulesFor(stageId)) {
            for (TriggerCondition c : rule.conditions()) {
                if (c.type().isPersistedOneShot()) {
                    persistence.clearTrigger(PERSISTENCE_TYPE, persistKey(stageId, c), player.getUUID());
                }
            }
        }
    }

    // ============================ progress snapshot (commands / GUI) ============================

    public record ConditionProgress(TriggerCondition condition, long current, long threshold, boolean satisfied) {}
    public record RuleProgress(TriggerMode mode, List<ConditionProgress> conditions, String description, boolean satisfied) {}

    /** Read-only progress breakdown of every rule on a stage for a player. */
    public static List<RuleProgress> describeProgress(ServerPlayer player, StageId stageId) {
        List<RuleProgress> out = new ArrayList<>();
        for (TriggerRule rule : rulesFor(stageId)) {
            List<ConditionProgress> cps = new ArrayList<>();
            boolean allSat = true;
            boolean anySat = false;
            for (TriggerCondition c : rule.conditions()) {
                long cur = currentProgress(player, stageId, c);
                boolean sat = cur >= c.count();
                allSat &= sat;
                anySat |= sat;
                cps.add(new ConditionProgress(c, cur, c.count(), sat));
            }
            boolean ruleSat = rule.mode() == TriggerMode.ANY_OF ? anySat : allSat;
            out.add(new RuleProgress(rule.mode(), cps, rule.description(), ruleSat));
        }
        return out;
    }
}
