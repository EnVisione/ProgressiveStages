package com.enviouse.progressivestages.server.triggers;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.stage.StageOrder;
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

    // v2.4: event-counted conditions. We only increment counters for combos some rule actually
    // watches, to keep StageCounterData bounded.
    /** Held-item ids referenced by any kill_with rule — we count a kill only when the held item matches. */
    private static final Set<String> watchedKillWithItems = ConcurrentHashMap.newKeySet();
    private static volatile boolean watchTame = false;
    /** v2.5: true if any breed rule targets a specific species/tag (drives per-species event counting). */
    private static volatile boolean watchBreedSpecies = false;
    /** v3.0: biome_time conditions — accumulated per poll while the player is in the biome. */
    private static final List<TriggerCondition> biomeTimeConds = new java.util.concurrent.CopyOnWriteArrayList<>();
    // v2.4 unlock juice
    private static final Set<StageId> hudBarStages = ConcurrentHashMap.newKeySet();
    private static final Set<StageId> nudgeStages = ConcurrentHashMap.newKeySet();
    private static final Map<String, Boolean> sentNudges = new ConcurrentHashMap<>(); // "uuid|stage|threshold"
    private static final Map<UUID, String> lastGoalSent = new ConcurrentHashMap<>();
    private static final int[] NUDGE_THRESHOLDS = {50, 75, 90};

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
        watchedKillWithItems.clear();
        watchTame = false;
        watchBreedSpecies = false;
        biomeTimeConds.clear();
        hudBarStages.clear();
        nudgeStages.clear();
        for (StageDefinition def : stages) {
            if (def.hasTriggers()) {
                RULES.put(def.getId(), def.getTriggers());
                for (TriggerRule rule : def.getTriggers()) {
                    for (TriggerCondition c : rule.conditions()) {
                        if (c.type() == TriggerConditionType.BIOME_TIME) {
                            biomeTimeConds.add(c);
                        }
                        if (c.type() == TriggerConditionType.KILL_WITH) {
                            // Count by the concrete victim killed with this item; tag victims are
                            // summed over their members at read time, so we only key on the item.
                            if (!c.with().isEmpty()) watchedKillWithItems.add(c.with());
                        } else if (c.type() == TriggerConditionType.TAME) {
                            watchTame = true;
                        } else if (c.type() == TriggerConditionType.BREED && !c.target().isEmpty()) {
                            watchBreedSpecies = true;
                        }
                    }
                }
                if (def.getUnlock().hudBar()) hudBarStages.add(def.getId());
                if (def.getUnlock().progressNudges()) nudgeStages.add(def.getId());
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

    /** v2.4: true if the stage has no trigger rules, or at least one of its rules is satisfied. */
    public static boolean triggersSatisfied(ServerPlayer player, StageId stageId) {
        List<TriggerRule> rules = RULES.get(stageId);
        if (rules == null || rules.isEmpty()) return true;
        for (TriggerRule rule : rules) if (ruleSatisfied(player, stageId, rule)) return true;
        return false;
    }

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
        accumulateBiomeTime(player, interval);
        evaluatePlayer(player);
        // v2.4 unlock juice (poll-driven, change-detected).
        if (!hudBarStages.isEmpty()) pushActiveGoal(player);
        if (!nudgeStages.isEmpty()) checkNudges(player);
    }

    // ---- v2.4 unlock juice helpers ----

    private static float fraction(ServerPlayer player, StageId stageId, TriggerCondition c) {
        if (c.count() <= 0) return 1f;
        return Math.min(1f, (float) currentProgress(player, stageId, c) / (float) c.count());
    }

    /** Best rule completion fraction for a stage (any_of = best condition, all_of = average). */
    private static float bestRulePercent(ServerPlayer player, StageId stageId) {
        float best = 0f;
        for (TriggerRule rule : rulesFor(stageId)) {
            float f;
            if (rule.mode() == TriggerMode.ANY_OF) {
                f = 0f;
                for (TriggerCondition c : rule.conditions()) f = Math.max(f, fraction(player, stageId, c));
            } else if (rule.conditions().isEmpty()) {
                f = 0f;
            } else {
                float s = 0f;
                for (TriggerCondition c : rule.conditions()) s += fraction(player, stageId, c);
                f = s / rule.conditions().size();
            }
            best = Math.max(best, f);
        }
        return Math.min(1f, best);
    }

    private static void pushActiveGoal(ServerPlayer player) {
        StageId best = null;
        float bestPct = -1f;
        for (StageId id : hudBarStages) {
            if (ProgressiveStagesAPI.hasStage(player, id) || !dependenciesSatisfied(player, id)) continue;
            float pct = bestRulePercent(player, id);
            if (pct > bestPct) { bestPct = pct; best = id; }
        }
        String stateKey;
        if (best != null) {
            stateKey = "1|" + best + "|" + Math.round(bestPct * 100);
            if (!stateKey.equals(lastGoalSent.get(player.getUUID()))) {
                String label = StageOrder.getInstance().getStageDefinition(best)
                    .map(StageDefinition::getDisplayName).orElse(best.getPath());
                com.enviouse.progressivestages.common.network.NetworkHandler.sendActiveGoal(player, label, bestPct, true);
                lastGoalSent.put(player.getUUID(), stateKey);
            }
        } else {
            stateKey = "0";
            if (!stateKey.equals(lastGoalSent.get(player.getUUID()))) {
                com.enviouse.progressivestages.common.network.NetworkHandler.sendActiveGoal(player, "", 0f, false);
                lastGoalSent.put(player.getUUID(), stateKey);
            }
        }
    }

    private static void checkNudges(ServerPlayer player) {
        for (StageId id : nudgeStages) {
            if (ProgressiveStagesAPI.hasStage(player, id) || !dependenciesSatisfied(player, id)) continue;
            int pctInt = Math.round(bestRulePercent(player, id) * 100f);
            for (int t : NUDGE_THRESHOLDS) {
                if (pctInt >= t) {
                    String key = player.getUUID() + "|" + id + "|" + t;
                    if (sentNudges.putIfAbsent(key, Boolean.TRUE) == null) {
                        String name = StageOrder.getInstance().getStageDefinition(id)
                            .map(StageDefinition::getDisplayName).orElse(id.getPath());
                        player.sendSystemMessage(com.enviouse.progressivestages.common.util.TextUtil
                            .parseColorCodes("&b✨ &7" + t + "% toward &f" + name));
                    }
                }
            }
        }
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
            UUID uuid = player.getUUID();
            lastPoll.remove(uuid);
            // Clear per-player HUD/nudge tracking so a fresh push happens on relog (and we don't leak
            // map entries for players who never come back).
            lastGoalSent.remove(uuid);
            String prefix = uuid + "|";
            sentNudges.keySet().removeIf(k -> k.startsWith(prefix));
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
        if (killer == null) return;

        // v2.4/v2.5: kill_with counting — count this kill when the held item is one a rule watches.
        // We store per-(victim,item) so both exact-id and #tag victims resolve at read time.
        if (!watchedKillWithItems.isEmpty() && killer.server != null) {
            ResourceLocation held = BuiltInRegistries.ITEM.getKey(killer.getMainHandItem().getItem());
            if (held != null && watchedKillWithItems.contains(held.toString())) {
                ResourceLocation victim = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
                if (victim != null) {
                    StageCounterData.get(killer.server).increment(killer.getUUID(),
                        "killwith:" + victim + "|" + held, 1);
                }
            }
        }

        // Defer one tick: the ENTITY_KILLED statistic is awarded by die() AFTER this event.
        scheduleEvaluate(killer);
    }

    /** v2.5: per-species breed counting for {@code breed} conditions that name a species/tag. */
    @SubscribeEvent
    public static void onBabySpawn(net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent event) {
        if (!active || !watchBreedSpecies) return;
        if (!(event.getCausedByPlayer() instanceof ServerPlayer player) || player.server == null) return;
        net.minecraft.world.entity.AgeableMob child = event.getChild();
        if (child == null) return;
        ResourceLocation childType = BuiltInRegistries.ENTITY_TYPE.getKey(child.getType());
        if (childType != null) {
            StageCounterData.get(player.server).increment(player.getUUID(), "breed:" + childType, 1);
        }
        scheduleEvaluate(player);
    }

    /** v2.4: tame counting for the {@code tame} condition. */
    @SubscribeEvent
    public static void onAnimalTame(net.neoforged.neoforge.event.entity.living.AnimalTameEvent event) {
        if (!active || !watchTame) return;
        if (!(event.getTamer() instanceof ServerPlayer player) || player.server == null) return;
        StageCounterData data = StageCounterData.get(player.server);
        data.increment(player.getUUID(), "tame", 1);
        ResourceLocation animal = BuiltInRegistries.ENTITY_TYPE.getKey(event.getAnimal().getType());
        if (animal != null) data.increment(player.getUUID(), "tame:" + animal, 1);
        scheduleEvaluate(player);
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
            // v2.4
            case EFFECT     -> hasEffect(player, c) ? c.count() : 0L;
            case BREED      -> breedProgress(player, c);
            case DAY_COUNT  -> player.level().getDayTime() / 24000L;
            case WORLD_TIME -> player.level().getDayTime() % 24000L;
            case WEATHER, ENTER_STRUCTURE ->
                (oneShotMarked(player, stageId, c) || matchesCurrentState(player, c)) ? c.count() : 0L;
            case TAME       -> counterValue(player, c.target().isEmpty() ? "tame" : "tame:" + c.targetBody());
            case KILL_WITH  -> killWithProgress(player, c);
            case SCRIPT     -> com.enviouse.progressivestages.common.compat.ScriptHooks
                                   .evalCondition(c.targetBody(), player) ? c.count() : 0L;
            // v3.0
            case REACH_Y    -> player.blockPosition().getY();
            case FISH       -> customStat(player, Stats.FISH_CAUGHT);
            case SLEEP      -> customStat(player, Stats.SLEEP_IN_BED);
            case RIDE       -> rideBlocks(player);
            case BIOME_TIME -> counterValue(player, "biometime:" + c.targetBody());
            case STAGE_HELD_FOR -> stageHeldSeconds(player, c);
        };
    }

    /** Per-poll biome-time accrual: +seconds (= poll interval / 20) for each biome_time the player is in. */
    private static void accumulateBiomeTime(ServerPlayer player, int intervalTicks) {
        if (biomeTimeConds.isEmpty() || player.server == null) return;
        long seconds = Math.max(1L, intervalTicks / 20L);
        for (TriggerCondition c : biomeTimeConds) {
            if (inBiome(player, c.targetBody(), c.targetIsTag())) {
                StageCounterData.get(player.server).increment(player.getUUID(),
                    "biometime:" + c.targetBody(), seconds);
            }
        }
    }

    /** Total blocks ridden on any vehicle (retroactive, from vanilla distance stats). */
    private static long rideBlocks(ServerPlayer player) {
        long cm = customStat(player, Stats.MINECART_ONE_CM) + customStat(player, Stats.BOAT_ONE_CM)
            + customStat(player, Stats.PIG_ONE_CM) + customStat(player, Stats.HORSE_ONE_CM)
            + customStat(player, Stats.STRIDER_ONE_CM);
        return cm / 100L;
    }

    /** Seconds since the player's team was granted the target stage (0 if not owned / no record). */
    private static long stageHeldSeconds(ServerPlayer player, TriggerCondition c) {
        if (player.server == null) return 0;
        StageId target;
        try { target = StageId.parse(c.targetBody()); } catch (Exception e) { return 0; }
        if (!ProgressiveStagesAPI.hasStage(player, target)) return 0;
        StageDefinition def = StageOrder.getInstance().getStageDefinition(target).orElse(null);
        UUID key = (def != null && def.isServerScope())
            ? com.enviouse.progressivestages.common.stage.StageManager.SERVER_TEAM
            : com.enviouse.progressivestages.common.team.TeamProvider.getInstance().getTeamId(player);
        long grant = StageRegressionData.get(player.server).getGrantTime(key, target);
        if (grant <= 0) return 0;
        return (System.currentTimeMillis() - grant) / 1000L;
    }

    /** True if the player is currently in the given biome id / {@code #tag}. */
    private static boolean inBiome(ServerPlayer player, String targetBody, boolean isTag) {
        ResourceLocation id = resolve(targetBody);
        if (id == null) return false;
        Holder<net.minecraft.world.level.biome.Biome> biome = player.level().getBiome(player.blockPosition());
        if (isTag) return biome.is(TagKey.create(Registries.BIOME, id));
        return biome.unwrapKey().map(k -> k.location().equals(id)).orElse(false);
    }

    /** v2.5: best completion fraction (0..1) for a stage — public accessor for KubeJS / external use. */
    public static float stagePercent(ServerPlayer player, StageId stageId) {
        return bestRulePercent(player, stageId);
    }

    private static long counterValue(ServerPlayer player, String counterKey) {
        if (player.server == null) return 0;
        return StageCounterData.get(player.server).get(player.getUUID(), counterKey);
    }

    /**
     * Breed progress. No target → the global vanilla {@code ANIMALS_BRED} stat (retroactive, all
     * species). Exact species → the per-species event counter. {@code #tag} → the sum of its
     * members' per-species counters. (Per-species counts are event-driven, so they only accrue
     * from when the trigger was loaded — not retroactive, matching tame/kill_with.)
     */
    private static long breedProgress(ServerPlayer player, TriggerCondition c) {
        if (c.target().isEmpty()) return customStat(player, Stats.ANIMALS_BRED);
        if (!c.targetIsTag()) return counterValue(player, "breed:" + c.targetBody());
        return sumEntityTagCounter(player, c.targetBody(), "breed:", "");
    }

    /**
     * kill_with progress. Exact victim → the per-(victim,item) counter; {@code #tag} victim → the
     * sum of that counter over every entity type in the tag, all sharing the same held item.
     */
    private static long killWithProgress(ServerPlayer player, TriggerCondition c) {
        String item = c.with();
        if (!c.targetIsTag()) return counterValue(player, "killwith:" + c.targetBody() + "|" + item);
        return sumEntityTagCounter(player, c.targetBody(), "killwith:", "|" + item);
    }

    /** Sum {@code prefix + <memberId> + suffix} counters across every entity type in an entity tag. */
    private static long sumEntityTagCounter(ServerPlayer player, String tagBody, String prefix, String suffix) {
        ResourceLocation tagId = resolve(tagBody);
        if (tagId == null) return 0;
        TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, tagId);
        long sum = 0;
        var setOpt = BuiltInRegistries.ENTITY_TYPE.getTag(tag);
        if (setOpt.isPresent()) {
            for (Holder<EntityType<?>> h : setOpt.get()) {
                ResourceLocation mid = BuiltInRegistries.ENTITY_TYPE.getKey(h.value());
                if (mid != null) sum += counterValue(player, prefix + mid + suffix);
            }
        }
        return sum;
    }

    private static boolean hasEffect(ServerPlayer player, TriggerCondition c) {
        ResourceLocation id = resolve(c.targetBody());
        if (id == null) return false;
        var holder = BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null);
        return holder != null && player.hasEffect(holder);
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
        if (c.type() == TriggerConditionType.WEATHER) {
            String w = c.targetBody().toLowerCase(Locale.ROOT);
            var level = player.level();
            return switch (w) {
                case "thunder", "thundering", "storm", "thunderstorm" -> level.isThundering();
                case "rain", "raining", "rainy" -> level.isRaining();
                case "clear", "sunny", "sun" -> !level.isRaining();
                default -> false;
            };
        }
        if (c.type() == TriggerConditionType.ENTER_STRUCTURE) {
            ResourceLocation id = resolve(c.targetBody());
            if (id == null || !(player.level() instanceof net.minecraft.server.level.ServerLevel sl)) return false;
            var key = net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, id);
            var holder = sl.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(key).orElse(null);
            if (holder == null) return false;
            return sl.structureManager().getStructureWithPieceAt(player.blockPosition(), holder.value()).isValid();
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
