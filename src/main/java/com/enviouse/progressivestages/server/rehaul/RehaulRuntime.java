package com.enviouse.progressivestages.server.rehaul;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.common.rehaul.CompiledSnapshot;
import com.enviouse.progressivestages.common.rehaul.action.ActionContext;
import com.enviouse.progressivestages.common.rehaul.action.ActionExecutor;
import com.enviouse.progressivestages.common.rehaul.action.ActionRegistry;
import com.enviouse.progressivestages.common.rehaul.challenge.ChallengeEngine;
import com.enviouse.progressivestages.common.rehaul.challenge.ChallengeEvent;
import com.enviouse.progressivestages.common.rehaul.challenge.ChallengeMeasureRegistry;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionEvaluator;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionRegistry;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionStateStore;
import com.enviouse.progressivestages.common.rehaul.counter.CounterKey;
import com.enviouse.progressivestages.common.rehaul.counter.CounterWindow;
import com.enviouse.progressivestages.common.rehaul.counter.WindowCounterStore;
import com.enviouse.progressivestages.common.rehaul.lifecycle.CompiledLifecycleRule;
import com.enviouse.progressivestages.common.rehaul.lifecycle.LifecycleTransactionEngine;
import com.enviouse.progressivestages.common.rehaul.lifecycle.TemporaryRuleEngine;
import com.enviouse.progressivestages.common.rehaul.lifecycle.TransitionHistory;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorMatcherRegistry;
import com.enviouse.progressivestages.common.rehaul.state.StageStateEngine;
import com.enviouse.progressivestages.common.rehaul.profile.AffinityDecision;
import com.enviouse.progressivestages.common.rehaul.profile.AffinityProfile;
import com.enviouse.progressivestages.common.rehaul.profile.AffinityResolver;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorTarget;
import com.enviouse.progressivestages.common.rehaul.template.TemplateEngine;
import com.enviouse.progressivestages.common.rehaul.value.FormulaRegistry;
import com.enviouse.progressivestages.common.rehaul.value.VariableStore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RehaulRuntime {

    private static final RehaulRuntime INSTANCE = new RehaulRuntime();
    private static final List<String> METRICS = List.of("death", "respawn", "health_gained", "health_lost",
        "damage_taken", "raw_damage_taken", "damage_dealt", "raw_damage_dealt", "hits_taken", "hits_dealt");

    private final ConditionStateStore conditionStates = new ConditionStateStore();
    private final ConditionEvaluator conditions = new ConditionEvaluator(ConditionRegistry.get(), conditionStates);
    private final TemporaryRuleEngine temporary = new TemporaryRuleEngine(conditions);
    private final CompiledRuleEngine rules = new CompiledRuleEngine(SelectorMatcherRegistry.get(), conditions, temporary);
    private final WindowCounterStore counters = new WindowCounterStore();
    private final ActionExecutor actions = new ActionExecutor(ActionRegistry.get());
    private final TransitionHistory transitionHistory = new TransitionHistory(2_000);
    private final LifecycleTransactionEngine lifecycle = new LifecycleTransactionEngine(
        conditions, actions, transitionHistory, 256);
    private final ChallengeEngine challenges = new ChallengeEngine(conditions, actions, ChallengeMeasureRegistry.get());
    private final VariableStore variables = new VariableStore();
    private final StageStateEngine stageStates = new StageStateEngine();
    private final FormulaRegistry formulas = new FormulaRegistry();
    private final TemplateEngine templates = new TemplateEngine();
    private final AffinityResolver affinities = new AffinityResolver(SelectorMatcherRegistry.get());
    private final Map<UUID, Float> lastHealth = new ConcurrentHashMap<>();
    private final Map<String, Long> lastDamageAt = new ConcurrentHashMap<>();
    private final Map<String, Long> activeSessions = new ConcurrentHashMap<>();
    private volatile List<CompiledLifecycleRule> lifecycleRules = List.of();
    private volatile Map<StageId, Set<StageId>> dependencies = Map.of();
    private volatile List<AffinityProfile> profiles = List.of();
    private volatile CompiledSnapshot snapshot = CompiledSnapshot.EMPTY;
    private volatile MinecraftServer server;
    private volatile MinecraftServer persistenceServer;
    private volatile long lastPersistGameTime = Long.MIN_VALUE;

    private RehaulRuntime() {}

    public static RehaulRuntime get() { return INSTANCE; }

    public synchronized void rebuild(CompiledSnapshot next, MinecraftServer server) {
        this.snapshot = next;
        this.server = server;
        rules.rebuild(next);
        List<CompiledLifecycleRule> lifecycleValues = new ArrayList<>();
        List<com.enviouse.progressivestages.common.rehaul.challenge.CompiledChallenge> challengeValues = new ArrayList<>();
        List<com.enviouse.progressivestages.common.rehaul.value.VariableDefinition> variableValues = new ArrayList<>();
        List<com.enviouse.progressivestages.common.rehaul.state.StageStateDefinition> stateValues = new ArrayList<>();
        List<AffinityProfile> profileValues = new ArrayList<>();
        List<com.enviouse.progressivestages.common.rehaul.template.TemplateDefinition> templateValues = new ArrayList<>();
        Map<String, String> formulaValues = new LinkedHashMap<>();
        Map<StageId, Set<StageId>> dependencyValues = new LinkedHashMap<>();
        next.stages().values().forEach(stage -> {
            lifecycleValues.addAll(stage.progression().lifecycleRules());
            challengeValues.addAll(stage.progression().challenges());
            variableValues.addAll(stage.progression().variables());
            stateValues.addAll(stage.progression().states());
            profileValues.addAll(stage.progression().profiles());
            templateValues.addAll(stage.progression().templates());
            stage.progression().formulas().forEach((id, expression) -> {
                if (formulaValues.putIfAbsent(id, expression) != null) {
                    throw new IllegalArgumentException("Duplicate formula. " + id);
                }
            });
            dependencyValues.put(stage.id(), Set.copyOf(stage.compatibilityView().getDependencies()));
        });
        lifecycleRules = List.copyOf(lifecycleValues);
        dependencies = Map.copyOf(dependencyValues);
        challenges.rebuild(challengeValues);
        variables.rebuild(variableValues);
        stageStates.rebuild(stateValues);
        profiles = List.copyOf(profileValues);
        templates.rebuild(templateValues);
        formulas.rebuild(formulaValues);
        if (persistenceServer != server) {
            RehaulSavedData.get(server).restore(this);
            persistenceServer = server;
        }
    }

    public synchronized void reset() {
        persist();
        snapshot = CompiledSnapshot.EMPTY;
        server = null;
        lifecycleRules = List.of();
        dependencies = Map.of();
        profiles = List.of();
        counters.clear();
        conditionStates.clear();
        temporary.clear();
        rules.rebuild(CompiledSnapshot.EMPTY);
        challenges.rebuild(List.of());
        variables.rebuild(List.of());
        stageStates.rebuild(List.of());
        templates.rebuild(List.of());
        formulas.rebuild(Map.of());
        transitionHistory.clear();
        lastHealth.clear();
        lastDamageAt.clear();
        activeSessions.clear();
        persistenceServer = null;
        lastPersistGameTime = Long.MIN_VALUE;
    }

    public CompiledRuleEngine rules() { return rules; }
    public ChallengeEngine challenges() { return challenges; }
    public VariableStore variables() { return variables; }
    public StageStateEngine stageStates() { return stageStates; }
    public FormulaRegistry formulas() { return formulas; }
    public TemplateEngine templates() { return templates; }
    public ConditionEvaluator conditionEvaluator() { return conditions; }
    public TransitionHistory transitionHistory() { return transitionHistory; }
    public CompiledSnapshot snapshot() { return snapshot; }

    public java.util.Optional<AffinityDecision> affinity(ServerPlayer player, SelectorTarget target) {
        Map<String, Double> values = new LinkedHashMap<>(variables.numericValues(player.getUUID().toString()));
        MinecraftConditionContextFactory.create(player, this, Set.of()).values().forEach((key, value) -> {
            if (value instanceof Number number) values.putIfAbsent(key, number.doubleValue());
        });
        for (String id : formulas.formulas().keySet()) values.put(id, formulas.evaluate(id, values));
        for (StageId stage : StageManager.getInstance().getStages(player)) values.put(stage.toString(), 1D);
        return affinities.resolve(profiles, target, values);
    }

    ConditionStateStore conditionStates() { return conditionStates; }
    TemporaryRuleEngine temporary() { return temporary; }
    LifecycleTransactionEngine lifecycle() { return lifecycle; }
    WindowCounterStore counters() { return counters; }

    public synchronized void persist() {
        if (server != null) RehaulSavedData.get(server).capture(this);
    }

    public List<Map<String, Object>> lifecycleProgress(ServerPlayer player, Set<String> dirty) {
        var context = MinecraftConditionContextFactory.create(player, this, dirty);
        List<Map<String, Object>> result = new ArrayList<>();
        for (CompiledLifecycleRule rule : lifecycleRules) {
            var trace = conditions.evaluate(rule.condition(), context);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("rule", rule.id().toString());
            entry.put("stage", rule.targetStage().toString());
            entry.put("direction", rule.direction().name().toLowerCase(java.util.Locale.ROOT));
            entry.put("matched", trace.result().matched());
            entry.put("current", trace.result().current());
            entry.put("required", trace.result().required());
            entry.put("explanation", trace.result().explanation());
            result.add(Map.copyOf(entry));
        }
        return List.copyOf(result);
    }

    public void armLifecycle(ServerPlayer player, ResourceLocation rule) {
        lifecycle.arm(player.getUUID().toString(), rule);
        persist();
    }

    public void resetLifecycle(ServerPlayer player, ResourceLocation rule) {
        lifecycle.reset(player.getUUID().toString(), rule);
        persist();
    }

    Map<String, Object> metricValues(String subject) {
        Map<String, Object> values = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        for (String metric : METRICS) {
            double value = counters.value(key(subject, metric), now);
            values.put(metric, value);
        }
        return values;
    }

    long noDamageFor(String subject, long now) {
        return Math.max(0, now - lastDamageAt.getOrDefault(subject, now));
    }

    Map<String, Object> sessionValues(String subject, long now) {
        Map<String, Object> values = new LinkedHashMap<>();
        String prefix = subject + "|";
        activeSessions.entrySet().removeIf(entry -> entry.getValue() <= now);
        for (Map.Entry<String, Long> entry : activeSessions.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) continue;
            String key = entry.getKey().substring(prefix.length());
            values.put(key, 1D);
        }
        return values;
    }

    private void markCombatSession(ServerPlayer player, net.minecraft.world.entity.Entity opponent, long now) {
        if (opponent == null) return;
        ResourceLocation entity = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(opponent.getType());
        if (entity == null) return;
        String subject = player.getUUID().toString();
        long expiry = now + 15_000L;
        activeSessions.put(subject + "|combat_session." + entity, expiry);
        if (opponent instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
                || opponent instanceof net.minecraft.world.entity.boss.wither.WitherBoss) {
            activeSessions.put(subject + "|boss_session." + entity, expiry);
        }
    }

    private void evaluate(ServerPlayer player, Set<String> dirty) {
        if (snapshot.revision() == 0) return;
        var condition = MinecraftConditionContextFactory.create(player, this, dirty);
        MinecraftActionSubject subject = new MinecraftActionSubject(player, this);
        ActionContext action = new ActionContext(subject, condition.nowMillis(), condition.values(), Map.of());
        lifecycle.evaluate(lifecycleRules, condition, action, dependencies);
        for (ResourceLocation challenge : challenges.challengeIds()) {
            challenges.reconcile(challenge, condition, action);
        }
        com.enviouse.progressivestages.common.network.NetworkHandler.sendChallengeHud(player);
    }

    private void record(ServerPlayer player, String metric, double amount, long now,
                        Map<String, Object> properties) {
        String subject = player.getUUID().toString();
        counters.add(key(subject, metric), amount, now);
        if (metric.contains("damage") || metric.equals("hits_taken")) lastDamageAt.put(subject, now);
        MinecraftActionSubject actionSubject = new MinecraftActionSubject(player, this);
        var changed = challenges.record(new ChallengeEvent(subject, id(metric), amount, now, properties),
            new ActionContext(actionSubject, now, Map.of(), Map.of()));
        for (var session : changed) com.enviouse.progressivestages.common.compat.ScriptHooks.fireEvent(
            "challenge", Map.of("player", player, "challenge", session.challenge().toString(),
                "status", session.status().name().toLowerCase(java.util.Locale.ROOT),
                "budgets", session.budgetValues(), "explanation", session.explanation()));
        if (!changed.isEmpty()) com.enviouse.progressivestages.common.network.NetworkHandler.sendChallengeHud(player);
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        long now = System.currentTimeMillis();
        double rawAmount = event.getAmount();
        double amount = rawAmount;
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            amount = com.enviouse.progressivestages.server.enforcement.ContextualModifierApplier
                .transform(attacker, id("outgoing_damage"), amount);
        }
        if (event.getEntity() instanceof ServerPlayer victim) {
            amount = com.enviouse.progressivestages.server.enforcement.ContextualModifierApplier
                .transform(victim, id("incoming_damage"), amount);
        }
        event.setAmount((float) Math.max(0, amount));
        if (event.getEntity() instanceof ServerPlayer victim) {
            INSTANCE.markCombatSession(victim, event.getSource().getEntity(), now);
            INSTANCE.evaluate(victim, Set.of("combat_session", "boss_session"));
            Map<String, Object> properties = entityProperties(event.getSource().getEntity());
            INSTANCE.record(victim, "damage_taken", amount, now, properties);
            INSTANCE.record(victim, "raw_damage_taken", rawAmount, now, properties);
            if (amount > 0) INSTANCE.record(victim, "hits_taken", 1, now, properties);
            INSTANCE.evaluate(victim, Set.of("damage_taken", "hits_taken"));
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            INSTANCE.markCombatSession(attacker, event.getEntity(), now);
            INSTANCE.evaluate(attacker, Set.of("combat_session", "boss_session"));
            Map<String, Object> properties = entityProperties(event.getEntity());
            INSTANCE.record(attacker, "damage_dealt", amount, now, properties);
            INSTANCE.record(attacker, "raw_damage_dealt", rawAmount, now, properties);
            if (amount > 0) INSTANCE.record(attacker, "hits_dealt", 1, now, properties);
            INSTANCE.evaluate(attacker, Set.of("damage_dealt", "hits_dealt"));
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        long now = System.currentTimeMillis();
        if (event.getEntity() instanceof ServerPlayer player) {
            INSTANCE.record(player, "death", 1, now, Map.of("cause", event.getSource().getMsgId()));
            INSTANCE.evaluate(player, Set.of("death"));
        }
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            INSTANCE.markCombatSession(killer, event.getEntity(), now);
            killer.server.execute(() -> INSTANCE.evaluate(killer, Set.of("kill", "boss_session")));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now = System.currentTimeMillis();
        INSTANCE.record(player, "respawn", 1, now, Map.of());
        INSTANCE.lastHealth.put(player.getUUID(), player.getHealth());
        INSTANCE.evaluate(player, Set.of("respawn"));
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Float previous = INSTANCE.lastHealth.put(player.getUUID(), player.getHealth());
        if (previous != null && Float.compare(previous, player.getHealth()) != 0) {
            long now = System.currentTimeMillis();
            if (player.getHealth() > previous) INSTANCE.record(player, "health_gained", player.getHealth() - previous, now, Map.of());
            else INSTANCE.record(player, "health_lost", previous - player.getHealth(), now, Map.of());
        }
        if (player.level().getGameTime() % 10 == 0) {
            INSTANCE.evaluate(player, Set.of("tick"));
            com.enviouse.progressivestages.server.enforcement.ContextualModifierApplier.reconcile(player);
        }
        long gameTime = player.level().getGameTime();
        if (gameTime % 100 == 0 && INSTANCE.lastPersistGameTime != gameTime) {
            INSTANCE.lastPersistGameTime = gameTime;
            INSTANCE.persist();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        INSTANCE.lastHealth.remove(player.getUUID());
        com.enviouse.progressivestages.common.network.NetworkHandler.clearPlayerRuntimeState(player.getUUID());
        INSTANCE.persist();
    }

    private static CounterKey key(String subject, String metric) {
        return new CounterKey(subject,
            com.enviouse.progressivestages.common.rehaul.condition.SubjectScope.PLAYER,
            id(metric), CounterWindow.lifetime());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressivestages", path);
    }

    private static Map<String, Object> entityProperties(net.minecraft.world.entity.Entity entity) {
        if (entity == null) return Map.of();
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id == null ? Map.of() : Map.of("entity", id.toString());
    }
}
