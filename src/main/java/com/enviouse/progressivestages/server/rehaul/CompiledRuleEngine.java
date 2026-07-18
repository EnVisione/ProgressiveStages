package com.enviouse.progressivestages.server.rehaul;

import com.enviouse.progressivestages.common.rehaul.CompiledRule;
import com.enviouse.progressivestages.common.rehaul.CompiledSnapshot;
import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import com.enviouse.progressivestages.common.rehaul.RuleEffect;
import com.enviouse.progressivestages.common.rehaul.RuleLifetime;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CompiledRuleEngine {

    private final SelectorMatcherRegistry selectors;
    private final ConditionEvaluator conditions;
    private final TemporaryRuleEngine temporary;
    private volatile long revision;
    private volatile Map<String, List<CompiledRule>> byCategory = Map.of();
    private volatile Map<ResourceLocation, CompiledRule> byId = Map.of();
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
        byId = Map.copyOf(ids);
        revision = snapshot.revision();
        temporary.clear();
        history.clear();
    }

    public Optional<DecisionTrace> resolve(ServerPlayer player, String category, String action,
                                           ResourceLocation targetId, Holder<?> holder) {
        List<CompiledRule> rules = rules(category);
        if (player == null || targetId == null || rules.isEmpty()) return Optional.empty();
        SelectorTarget target = target(targetId, holder);
        ConditionContext context = MinecraftConditionContextFactory.create(player, RehaulRuntime.get(), Set.of());
        List<DecisionCandidate> candidates = new ArrayList<>();
        int order = 0;
        for (CompiledRule rule : rules) {
            if (!actionMatches(rule.action(), action)) continue;
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
        remember(trace);
        return Optional.of(trace);
    }

    public boolean hasRules(String category) {
        return !rules(category).isEmpty();
    }

    public long revision() { return revision; }

    public Optional<CompiledRule> findRule(ResourceLocation id) { return Optional.ofNullable(byId.get(id)); }

    public synchronized List<DecisionTrace> history() { return List.copyOf(history); }

    public synchronized void setHistoryCapacity(int capacity) {
        if (capacity < 1 || capacity > 100_000) throw new IllegalArgumentException("Decision history capacity is outside the allowed range");
        historyCapacity = capacity;
        while (history.size() > capacity) history.removeFirst();
    }

    private boolean active(ServerPlayer player, CompiledRule rule, ConditionContext context) {
        if (!stageStateMatches(player, rule)) return false;
        if (rule.lifetime() == RuleLifetime.PERMANENT) {
            return conditions.evaluate(rule.condition(), context).result().matched();
        }
        ActivationPolicy policy = new ActivationPolicy(rule.lifetime(),
            duration(rule.settings().getOrDefault("duration_millis", rule.settings().get("duration"))),
            duration(rule.settings().get("cooldown")), duration(rule.settings().get("debounce")),
            duration(rule.settings().get("grace")), duration(rule.settings().get("minimum_active")),
            duration(rule.settings().get("minimum_inactive")),
            bool(rule.settings().getOrDefault("refresh_duration", rule.settings().get("refresh")), true),
            bool(rule.settings().get("pause_offline"), false),
            String.valueOf(rule.settings().getOrDefault("session", "")));
        ConditionNode reset = rule.settings().get("reset_condition") instanceof ConditionNode condition ? condition : null;
        return temporary.evaluate(rule.id(), policy, rule.condition(), reset, context).active();
    }

    private static boolean stageStateMatches(ServerPlayer player, CompiledRule rule) {
        boolean owned = StageManager.getInstance().hasStage(player, rule.ownerStage());
        String configured = String.valueOf(rule.settings().getOrDefault("stage_state", "")).toLowerCase(Locale.ROOT);
        if (configured.equals("always") || configured.equals("any")) return true;
        if (configured.equals("owned") || configured.equals("has")) return owned;
        if (configured.equals("missing") || configured.equals("lacks")) return !owned;
        String source = rule.provenance().section();
        if (source.contains("temporary") || source.equals("rules")) return owned;
        return switch (rule.effect()) {
            case LOCK, DENY, EXCLUDE -> !owned;
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

    private static boolean actionMatches(String ruleAction, String requested) {
        if (requested == null || requested.isBlank() || ruleAction.equalsIgnoreCase(requested)) return true;
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
