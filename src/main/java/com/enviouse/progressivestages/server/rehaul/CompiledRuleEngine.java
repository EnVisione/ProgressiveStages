package com.enviouse.progressivestages.server.rehaul;

import com.enviouse.progressivestages.common.rehaul.CompiledRule;
import com.enviouse.progressivestages.common.rehaul.CompiledSnapshot;
import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import com.enviouse.progressivestages.common.rehaul.RuleEffect;
import com.enviouse.progressivestages.common.rehaul.RuleLifetime;
import com.enviouse.progressivestages.common.rehaul.SelectorSpec;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionContext;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionEvaluator;
import com.enviouse.progressivestages.common.rehaul.decision.DecisionCandidate;
import com.enviouse.progressivestages.common.rehaul.decision.DecisionResolver;
import com.enviouse.progressivestages.common.rehaul.decision.DecisionTrace;
import com.enviouse.progressivestages.common.rehaul.decision.PrioritySource;
import com.enviouse.progressivestages.common.rehaul.decision.ResolvedPriority;
import com.enviouse.progressivestages.common.rehaul.decision.TiePolicy;
import com.enviouse.progressivestages.common.rehaul.lifecycle.ActivationPolicy;
import com.enviouse.progressivestages.common.rehaul.lifecycle.TemporaryRuleEngine;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorMatch;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorMatcherRegistry;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorTarget;
import com.enviouse.progressivestages.common.stage.StageManager;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public final class CompiledRuleEngine {

    public enum RecipeViewer {
        EMI,
        JEI
    }

    private final SelectorMatcherRegistry selectors;
    private final ConditionEvaluator conditions;
    private final TemporaryRuleEngine temporary;
    private volatile long revision;
    private volatile Map<String, List<CompiledRule>> byCategory = Map.of();
    private volatile Map<ResourceLocation, CompiledRule> byId = Map.of();
    private volatile ViewerSnapshot viewerSnapshot = new ViewerSnapshot(new ViewerRuleIndex(Map.of()), Map.of());
    private final ArrayDeque<DecisionTrace> history = new ArrayDeque<>();
    private int historyCapacity = 1_000;

    CompiledRuleEngine(SelectorMatcherRegistry selectors, ConditionEvaluator conditions,
                       TemporaryRuleEngine temporary) {
        this.selectors = selectors;
        this.conditions = conditions;
        this.temporary = temporary;
    }

    public synchronized void rebuild(CompiledSnapshot snapshot) {
        Map<String, List<CompiledRule>> next = new LinkedHashMap<>();
        Map<ResourceLocation, CompiledRule> ids = new LinkedHashMap<>();
        snapshot.stages().values().forEach(stage -> stage.rules().forEach(rule -> {
            next.computeIfAbsent(normalizeCategory(rule.category()), ignored -> new ArrayList<>()).add(rule);
            ids.put(rule.id(), rule);
        }));
        next.replaceAll((key, value) -> List.copyOf(value));
        byCategory = Map.copyOf(next);
        Map<ResourceLocation, CompiledRule> nextById = Map.copyOf(ids);
        ViewerRuleIndex nextViewerIndex;
        List<CompiledRule> itemRules = rules("items");
        if (itemRules.isEmpty()) {
            nextViewerIndex = new ViewerRuleIndex(Map.of());
        } else {
            List<SelectorTarget> items = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (id != null) items.add(target(id, BuiltInRegistries.ITEM.wrapAsHolder(item)));
            }
            nextViewerIndex = buildViewerRuleIndex(items, itemRules, selectors::match);
        }
        byId = nextById;
        viewerSnapshot = new ViewerSnapshot(nextViewerIndex, nextById);
        revision = snapshot.revision();
        temporary.clear();
        history.clear();
    }

    public Optional<DecisionTrace> resolve(ServerPlayer player, String category, String action,
                                           ResourceLocation targetId, Holder<?> holder) {
        return resolve(player, category, action, targetId, holder, true);
    }

    public Optional<DecisionTrace> resolveUntracked(ServerPlayer player, String category, String action,
                                                    ResourceLocation targetId, Holder<?> holder) {
        return resolve(player, category, action, targetId, holder, false);
    }

    public Optional<DecisionTrace> resolveUntracked(ServerPlayer player, String category, String action,
                                                    ResourceLocation targetId, Holder<?> holder,
                                                    ConditionContext context) {
        return resolve(player, category, action, targetId, holder, false, context);
    }

    private Optional<DecisionTrace> resolve(ServerPlayer player, String category, String action,
                                            ResourceLocation targetId, Holder<?> holder,
                                            boolean retainHistory) {
        return resolve(player, category, action, targetId, holder, retainHistory, null);
    }

    private Optional<DecisionTrace> resolve(ServerPlayer player, String category, String action,
                                            ResourceLocation targetId, Holder<?> holder,
                                            boolean retainHistory, ConditionContext providedContext) {
        List<CompiledRule> rules = rules(category);
        if (player == null || targetId == null || rules.isEmpty()) return Optional.empty();
        SelectorTarget target = target(targetId, holder);
        ConditionContext context = providedContext == null
            ? MinecraftConditionContextFactory.create(player, RehaulRuntime.get(), Set.of())
            : providedContext;
        List<DecisionCandidate> candidates = new ArrayList<>();
        int order = 0;
        for (CompiledRule rule : rules) {
            if (!actionMatches(rule.category(), rule.action(), action)) continue;
            if (legacyContext(rule.condition())) continue;
            SelectorMatch selectorMatch = selectors.match(rule.selector(), target);
            if (!selectorMatch.matched()) continue;
            boolean conditionMatched = active(player, rule, context);
            PrioritySource source = parsePrioritySource(rule.settings().get("priority_source"));
            candidates.add(new DecisionCandidate(rule, selectorMatch, conditionMatched, order++,
                new ResolvedPriority(rule.priority(), source)));
        }
        if (candidates.isEmpty()) return Optional.empty();
        DecisionTrace trace = DecisionResolver.resolve(targetId, category, action, candidates, TiePolicy.SAFE);
        if (retainHistory) remember(trace);
        return Optional.of(trace);
    }

    public boolean hasRules(String category) {
        return !rules(category).isEmpty();
    }

    public long revision() { return revision; }

    public Optional<CompiledRule> findRule(ResourceLocation id) { return Optional.ofNullable(byId.get(id)); }

    public boolean isActivationActive(ResourceLocation ruleId, RuleLifetime lifetime, ConditionNode condition,
                                      ConditionNode resetCondition, Map<String, Object> settings,
                                      ConditionContext context) {
        if (ruleId == null || context == null) return false;
        RuleLifetime resolvedLifetime = lifetime != null ? lifetime : RuleLifetime.PERMANENT;
        ConditionNode resolvedCondition = condition != null ? condition : new ConditionNode.Constant(true);
        Map<String, Object> resolvedSettings = settings != null ? settings : Map.of();
        if (resolvedLifetime == RuleLifetime.PERMANENT) {
            return conditions.evaluate(resolvedCondition, context).result().matched();
        }
        ActivationPolicy policy = activationPolicy(resolvedLifetime, resolvedSettings);
        return temporary.evaluate(ruleId, policy, resolvedCondition, resetCondition, context).active();
    }

    public Map<ResourceLocation, Set<StageId>> resolveViewerItemLocks(ServerPlayer player, RecipeViewer viewer) {
        if (viewer == null) return Map.of();
        ViewerItemLocks locks = resolveViewerItemLocks(player);
        return viewer == RecipeViewer.EMI ? locks.emi() : locks.jei();
    }

    public ViewerItemLocks resolveViewerItemLocks(ServerPlayer player) {
        ViewerSnapshot snapshot = viewerSnapshot;
        if (player == null || snapshot.index().targets().isEmpty()) return ViewerItemLocks.EMPTY;
        ConditionContext context = MinecraftConditionContextFactory.create(player, RehaulRuntime.get(), Set.of());
        return resolveIndexedViewerItemLocks(snapshot.index(), snapshot.rulesById(),
            rule -> active(player, rule, context));
    }

    public record ViewerItemLocks(Map<ResourceLocation, Set<StageId>> emi,
                                  Map<ResourceLocation, Set<StageId>> jei) {
        private static final ViewerItemLocks EMPTY = new ViewerItemLocks(Map.of(), Map.of());
    }

    record MatchedViewerRule(CompiledRule rule, SelectorMatch match, int order, ResolvedPriority priority) {}

    record ViewerRuleIndex(Map<ResourceLocation, Map<String, List<MatchedViewerRule>>> targets) {}

    private record ViewerSnapshot(ViewerRuleIndex index, Map<ResourceLocation, CompiledRule> rulesById) {}

    static ViewerRuleIndex buildViewerRuleIndex(Iterable<SelectorTarget> targets, List<CompiledRule> rules,
                                               BiFunction<SelectorSpec, SelectorTarget, SelectorMatch> matcher) {
        Map<ResourceLocation, Map<String, List<MatchedViewerRule>>> indexed = new LinkedHashMap<>();
        for (SelectorTarget target : targets) {
            Map<String, List<MatchedViewerRule>> byAction = new LinkedHashMap<>();
            int order = 0;
            for (CompiledRule rule : rules) {
                if (legacyContext(rule.condition())) continue;
                SelectorMatch match = matcher.apply(rule.selector(), target);
                if (!match.matched()) continue;
                PrioritySource source = parsePrioritySource(rule.settings().get("priority_source"));
                byAction.computeIfAbsent(rule.action(), ignored -> new ArrayList<>()).add(
                    new MatchedViewerRule(rule, match, order++, new ResolvedPriority(rule.priority(), source)));
            }
            if (byAction.isEmpty()) continue;
            byAction.replaceAll((action, matches) -> List.copyOf(matches));
            indexed.put(target.id(), Collections.unmodifiableMap(byAction));
        }
        return new ViewerRuleIndex(Collections.unmodifiableMap(indexed));
    }

    static ViewerItemLocks resolveIndexedViewerItemLocks(ViewerRuleIndex index,
                                                         Map<ResourceLocation, CompiledRule> rulesById,
                                                         Predicate<CompiledRule> isActive) {
        Map<CompiledRule, Boolean> active = new IdentityHashMap<>();
        Map<ResourceLocation, Set<StageId>> emi = new LinkedHashMap<>();
        Map<ResourceLocation, Set<StageId>> jei = new LinkedHashMap<>();
        for (var item : index.targets().entrySet()) {
            Set<StageId> emiStages = new LinkedHashSet<>();
            Set<StageId> jeiStages = new LinkedHashSet<>();
            for (var action : item.getValue().entrySet()) {
                List<DecisionCandidate> candidates = new ArrayList<>(action.getValue().size());
                for (MatchedViewerRule match : action.getValue()) {
                    candidates.add(new DecisionCandidate(match.rule(), match.match(),
                        active.computeIfAbsent(match.rule(), isActive::test), match.order(), match.priority()));
                }
                DecisionTrace trace = DecisionResolver.resolve(item.getKey(), "items", action.getKey(),
                    candidates, TiePolicy.SAFE);
                if (trace.winningEffect() != RuleEffect.LOCK || trace.winner().isEmpty()) continue;
                CompiledRule winner = rulesById.get(trace.winner().get());
                if (winner == null || !isMissingStageLock(winner) || winner.lifetime() != RuleLifetime.PERMANENT) continue;
                if (winner.viewerPolicy().hidesInEmi()) emiStages.add(winner.ownerStage());
                if (winner.viewerPolicy().hidesInJei()) jeiStages.add(winner.ownerStage());
            }
            if (!emiStages.isEmpty()) emi.put(item.getKey(), Set.copyOf(emiStages));
            if (!jeiStages.isEmpty()) jei.put(item.getKey(), Set.copyOf(jeiStages));
        }
        return new ViewerItemLocks(Map.copyOf(emi), Map.copyOf(jei));
    }

    public synchronized List<DecisionTrace> history() { return List.copyOf(history); }

    private static boolean isMissingStageLock(CompiledRule rule) {
        String state = String.valueOf(rule.settings().getOrDefault("stage_state", "")).toLowerCase(Locale.ROOT);
        if (state.equals("missing") || state.equals("lacks")) return true;
        if (!state.isEmpty()) return false;
        return !rule.provenance().section().contains("temporary");
    }

    public synchronized void setHistoryCapacity(int capacity) {
        if (capacity < 1 || capacity > 100_000) throw new IllegalArgumentException("Decision history capacity is outside the allowed range");
        historyCapacity = capacity;
        while (history.size() > capacity) history.removeFirst();
    }

    private boolean active(ServerPlayer player, CompiledRule rule, ConditionContext context) {
        if (!stageStateMatches(player, rule)) return false;
        ConditionNode reset = rule.settings().get("reset_condition") instanceof ConditionNode condition ? condition : null;
        return isActivationActive(rule.id(), rule.lifetime(), rule.condition(), reset, rule.settings(), context);
    }

    private static ActivationPolicy activationPolicy(RuleLifetime lifetime, Map<String, Object> settings) {
        return new ActivationPolicy(lifetime,
            duration(settings.getOrDefault("duration_millis", settings.get("duration"))),
            duration(settings.get("cooldown")), duration(settings.get("debounce")),
            duration(settings.get("grace")), duration(settings.get("minimum_active")),
            duration(settings.get("minimum_inactive")),
            bool(settings.getOrDefault("refresh_duration", settings.get("refresh")), true),
            bool(settings.get("pause_offline"), false),
            String.valueOf(settings.getOrDefault("session", "")));
    }

    private static boolean stageStateMatches(ServerPlayer player, CompiledRule rule) {
        boolean owned = StageManager.getInstance().hasStage(player, rule.ownerStage());
        String configured = String.valueOf(rule.settings().getOrDefault("stage_state", "")).toLowerCase(Locale.ROOT);
        return stageStateMatches(owned, configured, rule.provenance().section(), rule.effect());
    }

    static boolean stageStateMatches(boolean owned, String configured, String source, RuleEffect effect) {
        if (configured.equals("always") || configured.equals("any")) return true;
        if (configured.equals("owned") || configured.equals("has")) return owned;
        if (configured.equals("missing") || configured.equals("lacks")) return !owned;
        if (source.contains("temporary")) return owned;
        return switch (effect) {
            case LOCK, EXCLUDE -> !owned;
            case DENY -> owned;
            case UNLOCK, ALLOW, MODIFY, REPLACE, PRESENT -> owned;
        };
    }

    private List<CompiledRule> rules(String category) {
        String normalized = normalizeCategory(category);
        List<CompiledRule> direct = byCategory.getOrDefault(normalized, List.of());
        if (!direct.isEmpty()) return direct;
        return byCategory.entrySet().stream().filter(entry -> entry.getKey().startsWith(normalized + "."))
            .flatMap(entry -> entry.getValue().stream()).toList();
    }

    private static SelectorTarget target(ResourceLocation id, Holder<?> holder) {
        Set<ResourceLocation> tags = new LinkedHashSet<>();
        if (holder != null) holder.tags().forEach(tag -> tags.add(tag.location()));
        return new SelectorTarget(id, null, tags, Map.of());
    }

    private synchronized void remember(DecisionTrace trace) {
        history.addLast(trace);
        while (history.size() > historyCapacity) history.removeFirst();
    }

    private static boolean actionMatches(String category, String ruleAction, String requested) {
        if (requested == null || requested.isBlank() || ruleAction.equalsIgnoreCase(requested)) return true;
        if (category.equals("dimensions") && ruleAction.equalsIgnoreCase("enter")
                && (requested.equalsIgnoreCase("portal") || requested.equalsIgnoreCase("teleport"))) return true;
        if (category.equals("abilities") && ruleAction.equalsIgnoreCase("use") && requested.equalsIgnoreCase("perform")) return true;
        return ruleAction.equalsIgnoreCase("access") || ruleAction.equalsIgnoreCase("interact")
            && requested.toLowerCase(Locale.ROOT).contains("interact");
    }

    private static boolean legacyContext(ConditionNode node) {
        return node instanceof ConditionNode.Leaf leaf
            && leaf.providerId().equals(ResourceLocation.fromNamespaceAndPath("progressivestages", "legacy_context"));
    }

    private static PrioritySource parsePrioritySource(Object raw) {
        if (raw == null) return PrioritySource.RULE;
        try { return PrioritySource.valueOf(String.valueOf(raw).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return PrioritySource.RULE; }
    }

    private static String normalizeCategory(String category) {
        String value = category == null ? "" : category.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "item" -> "items";
            case "block" -> "blocks";
            case "fluid" -> "fluids";
            case "entity" -> "entities";
            case "recipe" -> "recipes";
            case "dimension" -> "dimensions";
            case "structure" -> "structures";
            case "ability" -> "abilities";
            default -> value;
        };
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value == null) return fallback;
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private static long duration(Object value) {
        if (value == null) return 0;
        if (value instanceof Number number) return Math.max(0, number.longValue());
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) return 0;
        long multiplier = 1;
        if (text.endsWith("ms")) text = text.substring(0, text.length() - 2);
        else if (text.endsWith("s")) { multiplier = 1_000; text = text.substring(0, text.length() - 1); }
        else if (text.endsWith("m")) { multiplier = 60_000; text = text.substring(0, text.length() - 1); }
        else if (text.endsWith("h")) { multiplier = 3_600_000; text = text.substring(0, text.length() - 1); }
        return Math.multiplyExact(Long.parseLong(text.trim()), multiplier);
    }
}
