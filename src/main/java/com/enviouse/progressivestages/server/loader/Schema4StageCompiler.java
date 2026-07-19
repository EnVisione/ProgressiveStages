package com.enviouse.progressivestages.server.loader;

import com.electronwill.nightconfig.core.Config;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.rehaul.CompiledProgression;
import com.enviouse.progressivestages.common.rehaul.CompiledRule;
import com.enviouse.progressivestages.common.rehaul.CompiledStage;
import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import com.enviouse.progressivestages.common.rehaul.ConfigProvenance;
import com.enviouse.progressivestages.common.rehaul.LegacyStageCompiler;
import com.enviouse.progressivestages.common.rehaul.RuleEffect;
import com.enviouse.progressivestages.common.rehaul.RuleLifetime;
import com.enviouse.progressivestages.common.rehaul.SelectorSpec;
import com.enviouse.progressivestages.common.rehaul.ViewerPolicy;
import com.enviouse.progressivestages.common.rehaul.action.ActionChain;
import com.enviouse.progressivestages.common.rehaul.action.CompiledAction;
import com.enviouse.progressivestages.common.rehaul.action.FailurePolicy;
import com.enviouse.progressivestages.common.rehaul.challenge.BudgetMode;
import com.enviouse.progressivestages.common.rehaul.challenge.ChallengeBudget;
import com.enviouse.progressivestages.common.rehaul.challenge.ChallengeHud;
import com.enviouse.progressivestages.common.rehaul.challenge.ChallengeStep;
import com.enviouse.progressivestages.common.rehaul.challenge.CompiledChallenge;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionCompiler;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionRegistry;
import com.enviouse.progressivestages.common.rehaul.condition.SubjectScope;
import com.enviouse.progressivestages.common.rehaul.decision.PriorityCascade;
import com.enviouse.progressivestages.common.rehaul.decision.PrioritySource;
import com.enviouse.progressivestages.common.rehaul.decision.ResolvedPriority;
import com.enviouse.progressivestages.common.rehaul.lifecycle.CompiledLifecycleRule;
import com.enviouse.progressivestages.common.rehaul.lifecycle.LifecycleDirection;
import com.enviouse.progressivestages.common.rehaul.lifecycle.RepeatMode;
import com.enviouse.progressivestages.common.rehaul.modifier.AggregationMode;
import com.enviouse.progressivestages.common.rehaul.modifier.AttributeChange;
import com.enviouse.progressivestages.common.rehaul.modifier.AttributeOperation;
import com.enviouse.progressivestages.common.rehaul.modifier.CompiledModifier;
import com.enviouse.progressivestages.common.rehaul.modifier.CompiledDropModifier;
import com.enviouse.progressivestages.common.rehaul.modifier.EffectChange;
import com.enviouse.progressivestages.common.rehaul.modifier.ItemContext;
import com.enviouse.progressivestages.common.rehaul.modifier.NumericTransform;
import com.enviouse.progressivestages.common.rehaul.modifier.StackingPolicy;
import com.enviouse.progressivestages.common.rehaul.profile.AffinityEffect;
import com.enviouse.progressivestages.common.rehaul.profile.AffinityProfile;
import com.enviouse.progressivestages.common.rehaul.profile.ProficiencyLevel;
import com.enviouse.progressivestages.common.rehaul.state.StageStateDefinition;
import com.enviouse.progressivestages.common.rehaul.template.MergePolicy;
import com.enviouse.progressivestages.common.rehaul.template.ParameterType;
import com.enviouse.progressivestages.common.rehaul.template.TemplateDefinition;
import com.enviouse.progressivestages.common.rehaul.template.TemplateParameter;
import com.enviouse.progressivestages.common.rehaul.value.VariableDefinition;
import com.enviouse.progressivestages.common.rehaul.value.VariableType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Schema4StageCompiler {

    private static final ConditionCompiler CONDITIONS = new ConditionCompiler(ConditionRegistry.get());

    private Schema4StageCompiler() {}

    public static CompiledStage compile(StageDefinition stage, Config source, String sourceId, int globalPriority) {
        CompiledStage legacy = LegacyStageCompiler.compile(stage, sourceId);
        List<CompiledRule> rules = new ArrayList<>();
        for (CompiledRule rule : legacy.rules()) rules.add(applySimpleMetadata(rule, source, stage, globalPriority));
        addUnlocks(rules, source, stage, globalPriority);
        addGenericRules(rules, source, stage, globalPriority, "rules", RuleLifetime.PERMANENT);
        addGenericRules(rules, source, stage, globalPriority, "temporary_rules", RuleLifetime.LIVE);

        CompiledProgression base = legacy.progression();
        List<CompiledLifecycleRule> lifecycle = new ArrayList<>(base.lifecycleRules());
        addLifecycle(lifecycle, source, stage, "grants", LifecycleDirection.GRANT);
        addLifecycle(lifecycle, source, stage, "revokes", LifecycleDirection.REVOKE);
        List<CompiledModifier> modifiers = parseModifiers(source, stage);
        List<CompiledDropModifier> dropModifiers = parseDropModifiers(source, stage);
        List<CompiledChallenge> challenges = parseChallenges(source, stage);
        List<AffinityProfile> profiles = parseProfiles(source);
        List<VariableDefinition> variables = parseVariables(source);
        Map<String, String> formulas = parseFormulas(source);
        List<TemplateDefinition> templates = parseTemplates(source);
        List<StageStateDefinition> states = parseStates(source, stage);
        CompiledProgression progression = new CompiledProgression(lifecycle, modifiers, dropModifiers, challenges,
            profiles, variables, formulas, templates, states, extensionFields(source));
        return new CompiledStage(stage.getId(), stage.getDisplayName(), stage.getDescription(),
            stage.getPriority(), 4, sourceId, rules, progression, legacy.metadata(), stage,
            stage.getProvenance());
    }

    private static CompiledRule applySimpleMetadata(CompiledRule rule, Config source,
                                                    StageDefinition stage, int globalPriority) {
        Config category = category(source, rule.category());
        Integer entry = rule.selector().explicitPriority();
        if (entry == null) entry = lookupPriority(category, rule.selector().raw());
        Integer categoryPriority = integer(category, "priority");
        ResolvedPriority priority = PriorityCascade.resolve(entry, null, categoryPriority,
            stage.getPriority(), globalPriority);
        ViewerPolicy viewer = viewerPolicy(category, rule.selector().raw());
        Map<String, Object> settings = new LinkedHashMap<>(rule.settings());
        settings.put("priority_source", priority.source().name().toLowerCase(Locale.ROOT));
        return new CompiledRule(rule.id(), rule.ownerStage(), rule.category(), rule.action(),
            rule.effect(), rule.selector(), priority.value(), rule.lifetime(), rule.condition(),
            rule.parentRuleId(), viewer, settings, rule.provenance());
    }

    private static void addUnlocks(List<CompiledRule> rules, Config source, StageDefinition stage,
                                   int globalPriority) {
        Config unlocks = source.get("unlocks");
        if (unlocks == null) return;
        int index = 0;
        for (String category : List.of("items", "blocks", "fluids", "entities", "dimensions",
                "recipes", "structures", "abilities")) {
            for (String raw : strings(unlocks.getRaw(category))) {
                SelectorSpec selector = SelectorSpec.parse(raw).orElseThrow(() ->
                    new IllegalArgumentException("Invalid unlock selector. " + raw));
                Integer entry = selector.explicitPriority();
                if (entry == null) entry = lookupPriority(unlocks, raw);
                ResolvedPriority priority = PriorityCascade.resolve(entry, integer(unlocks, "priority"),
                    null, stage.getPriority(), globalPriority);
                ResourceLocation id = childId(stage.getId(), "unlocks/" + category + "/" + index++);
                rules.add(new CompiledRule(id, stage.getId(), category, defaultAction(category),
                    RuleEffect.ALLOW, selector, priority.value(), RuleLifetime.PERMANENT,
                    new ConditionNode.Constant(true), null, viewerPolicy(unlocks, raw),
                    Map.of("priority_source", priority.source().name().toLowerCase(Locale.ROOT)),
                    provenance(stage, "unlocks", raw)));
            }
        }
        for (String namespace : strings(unlocks.getRaw("mods"))) {
            String raw = namespace.startsWith("mod:") ? namespace : "mod:" + namespace;
            SelectorSpec selector = SelectorSpec.parse(raw).orElseThrow();
            for (String category : List.of("items", "blocks", "fluids", "entities")) {
                ResourceLocation id = childId(stage.getId(), "unlocks/" + category + "/" + index++);
                rules.add(new CompiledRule(id, stage.getId(), category, defaultAction(category),
                    RuleEffect.ALLOW, selector, stage.getPriority(), RuleLifetime.PERMANENT,
                    new ConditionNode.Constant(true), null, ViewerPolicy.INHERIT, Map.of(),
                    provenance(stage, "unlocks", "mods")));
            }
        }
    }

    private static void addGenericRules(List<CompiledRule> output, Config source, StageDefinition stage,
                                        int globalPriority, String key, RuleLifetime defaultLifetime) {
        List<Map<String, Object>> entries = maps(source.getRaw(key));
        for (int ruleIndex = 0; ruleIndex < entries.size(); ruleIndex++) {
            Map<String, Object> entry = entries.get(ruleIndex);
            ResourceLocation baseId = optionalId(entry.get("id"), childId(stage.getId(), key + "/" + ruleIndex));
            RuleEffect effect = effect(entry.get("effect"), RuleEffect.LOCK);
            RuleLifetime lifetime = enumValue(RuleLifetime.class, entry.get("lifetime"), defaultLifetime);
            Integer rulePriority = integer(entry.get("priority"));
            ConditionNode condition = condition(entry);
            ResourceLocation parent = entry.get("parent") == null ? null : ResourceLocation.parse(String.valueOf(entry.get("parent")));
            Map<String, Object> targets = map(entry.get("targets"));
            if (targets.isEmpty() && entry.get("selector") != null) {
                targets = Map.of(String.valueOf(entry.getOrDefault("category", "items")), List.of(entry.get("selector")));
            }
            int selectorIndex = 0;
            for (Map.Entry<String, Object> target : targets.entrySet()) {
                String category = singularCategory(target.getKey());
                for (String raw : strings(target.getValue())) {
                    SelectorSpec selector = SelectorSpec.parse(raw).orElseThrow(() ->
                        new IllegalArgumentException("Invalid rule selector. " + raw));
                    ResolvedPriority priority = PriorityCascade.resolve(selector.explicitPriority(), rulePriority,
                        null, stage.getPriority(), globalPriority);
                    ResourceLocation id = selectorIndex == 0 && totalTargetCount(targets) == 1
                        ? baseId : childId(baseId, "target/" + selectorIndex);
                    output.add(new CompiledRule(id, stage.getId(), category,
                        String.valueOf(entry.getOrDefault("action", defaultAction(category))), effect,
                        selector, priority.value(), lifetime, condition, parent,
                        viewerPolicy(entry), settings(entry), provenance(stage, key, Integer.toString(ruleIndex))));
                    selectorIndex++;
                }
            }
            for (Map<String, Object> exception : maps(entry.get("exceptions"))) {
                RuleEffect exceptionEffect = effect(exception.get("effect"), RuleEffect.EXCLUDE);
                Map<String, Object> exceptionTargets = map(exception.get("targets"));
                for (Map.Entry<String, Object> target : exceptionTargets.entrySet()) {
                    String category = singularCategory(target.getKey());
                    for (String raw : strings(target.getValue())) {
                        SelectorSpec selector = SelectorSpec.parse(raw).orElseThrow();
                        Integer exceptionPriority = integer(exception.get("priority"));
                        ResolvedPriority priority = PriorityCascade.resolve(selector.explicitPriority(),
                            exceptionPriority != null ? exceptionPriority : rulePriority, null,
                            stage.getPriority(), globalPriority);
                        ResourceLocation parentRule = exceptionEffect == RuleEffect.EXCLUDE
                            ? optionalId(exception.get("parent"), baseId) : null;
                        output.add(new CompiledRule(childId(baseId, "exception/" + selectorIndex++),
                            stage.getId(), category, String.valueOf(entry.getOrDefault("action", defaultAction(category))),
                            exceptionEffect, selector, priority.value(), lifetime, condition, parentRule,
                            viewerPolicy(exception), settings(exception),
                            provenance(stage, key + ".exceptions", raw)));
                    }
                }
            }
        }
    }

    private static void addLifecycle(List<CompiledLifecycleRule> output, Config source,
                                     StageDefinition stage, String key, LifecycleDirection direction) {
        List<Map<String, Object>> entries = maps(source.getRaw(key));
        for (int index = 0; index < entries.size(); index++) {
            Map<String, Object> entry = entries.get(index);
            ResourceLocation id = optionalId(entry.get("id"), childId(stage.getId(), key + "/" + index));
            StageId target = entry.get("stage") == null ? stage.getId() : StageId.parse(String.valueOf(entry.get("stage")));
            output.add(new CompiledLifecycleRule(id, target, direction, condition(entry),
                integer(entry.get("priority"), stage.getPriority()),
                enumValue(SubjectScope.class, entry.get("scope"), SubjectScope.PLAYER),
                enumValue(RepeatMode.class, entry.get("repeat"), RepeatMode.ONCE),
                bool(entry.get("evaluate_while_owned"), false), bool(entry.get("evaluate_while_missing"), false),
                duration(entry.get("cooldown")), duration(entry.get("debounce")), duration(entry.get("grace")),
                String.valueOf(entry.getOrDefault("state", "")), actions(entry.get("actions"), id),
                actions(entry.get("failure_actions"), childId(id, "failure")), provenance(stage, key, Integer.toString(index))));
        }
    }

    private static List<CompiledModifier> parseModifiers(Config source, StageDefinition stage) {
        List<Map<String, Object>> entries = maps(source.getRaw("item_modifiers"));
        List<CompiledModifier> output = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            Map<String, Object> entry = entries.get(index);
            ResourceLocation id = optionalId(entry.get("id"), childId(stage.getId(), "modifier/" + index));
            List<SelectorSpec> items = strings(entry.get("items")).stream()
                .map(value -> SelectorSpec.parse(value).orElseThrow()).toList();
            Set<ItemContext> contexts = new LinkedHashSet<>();
            for (String value : strings(entry.get("contexts"))) contexts.add(enumValue(ItemContext.class, value, null));
            contextFlag(entry, contexts, "while_holding", ItemContext.EITHER_HAND);
            contextFlag(entry, contexts, "while_in_main_hand", ItemContext.MAIN_HAND);
            contextFlag(entry, contexts, "while_in_off_hand", ItemContext.OFF_HAND);
            contextFlag(entry, contexts, "while_in_hotbar", ItemContext.HOTBAR);
            contextFlag(entry, contexts, "while_selected", ItemContext.SELECTED_HOTBAR);
            contextFlag(entry, contexts, "while_in_inventory", ItemContext.INVENTORY);
            contextFlag(entry, contexts, "while_wearing", ItemContext.EQUIPMENT);
            contextFlag(entry, contexts, "while_in_curios", ItemContext.CURIOS);
            contextFlag(entry, contexts, "while_using", ItemContext.USE);
            contextFlag(entry, contexts, "while_attacking", ItemContext.ATTACK);
            List<AttributeChange> attributes = maps(entry.get("attributes")).stream().map(Schema4StageCompiler::attribute).toList();
            List<EffectChange> effects = maps(entry.get("effects")).stream().map(Schema4StageCompiler::effectChange).toList();
            List<NumericTransform> transforms = maps(entry.get("transforms")).stream().map(Schema4StageCompiler::transform).toList();
            output.add(new CompiledModifier(id, stage.getId(), items, contexts,
                stageIds(entry.get("with_stages")), stageIds(entry.get("without_stages")), condition(entry),
                enumValue(AggregationMode.class, entry.get("aggregation"), AggregationMode.ONCE),
                integer(entry.get("cap"), 1), integer(entry.get("priority"), stage.getPriority()),
                attributes, effects, transforms, provenance(stage, "item_modifiers", Integer.toString(index))));
        }
        for (int index = 0; index < stage.getAttributes().size(); index++) {
            var attribute = stage.getAttributes().get(index);
            AttributeOperation operation = switch (attribute.operation()) {
                case ADD_VALUE -> AttributeOperation.ADD_VALUE;
                case ADD_MULTIPLIED_BASE -> AttributeOperation.ADD_MULTIPLIED_BASE;
                case ADD_MULTIPLIED_TOTAL -> AttributeOperation.ADD_MULTIPLIED_TOTAL;
            };
            output.add(new CompiledModifier(childId(stage.getId(), "legacy_attribute/" + index),
                stage.getId(), List.of(), Set.of(), Set.of(stage.getId()), Set.of(),
                new ConditionNode.Constant(true), AggregationMode.ONCE, 1, stage.getPriority(),
                List.of(new AttributeChange(attribute.attribute(), attribute.amount(), operation,
                    StackingPolicy.ADD, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)),
                List.of(), List.of(), provenance(stage, "attribute", Integer.toString(index))));
        }
        return List.copyOf(output);
    }

    private static List<CompiledDropModifier> parseDropModifiers(Config source, StageDefinition stage) {
        List<Map<String, Object>> entries = maps(source.getRaw("drop_modifiers"));
        List<CompiledDropModifier> output = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            Map<String, Object> entry = entries.get(index);
            ResourceLocation id = optionalId(entry.get("id"), childId(stage.getId(), "drop_modifier/" + index));
            List<SelectorSpec> blocks = selectorList(entry, "blocks", "source_blocks");
            List<SelectorSpec> drops = selectorList(entry, "drops", "output_items", "items");
            List<SelectorSpec> tools = selectorList(entry, "tools", "tool_items");
            Set<StageId> required = entry.containsKey("with_stages")
                ? stageIds(entry.get("with_stages")) : Set.of(stage.getId());
            ResourceLocation enchantment = entry.get("required_enchantment") == null
                ? null : namespacedId(entry.get("required_enchantment"));
            output.add(new CompiledDropModifier(id, stage.getId(), blocks, drops, tools,
                required, stageIds(entry.get("without_stages")), condition(entry), enchantment,
                integer(entry.get("minimum_enchantment_level"), enchantment == null ? 0 : 1),
                decimal(entry.get("add"), 0), decimal(entry.get("multiply"), 1),
                integer(entry.get("minimum"), 0), integer(entry.get("maximum"), Integer.MAX_VALUE),
                integer(entry.get("priority"), stage.getPriority()), bool(entry.get("exclusive"), false),
                provenance(stage, "drop_modifiers", Integer.toString(index))));
        }
        return List.copyOf(output);
    }

    private static List<SelectorSpec> selectorList(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            if (!source.containsKey(key)) continue;
            return strings(source.get(key)).stream().map(value -> SelectorSpec.parse(value)
                .orElseThrow(() -> new IllegalArgumentException("Invalid selector. " + value))).toList();
        }
        return List.of();
    }

    private static List<CompiledChallenge> parseChallenges(Config source, StageDefinition stage) {
        List<CompiledChallenge> output = new ArrayList<>();
        List<Map<String, Object>> entries = maps(source.getRaw("challenges"));
        for (int index = 0; index < entries.size(); index++) {
            Map<String, Object> entry = entries.get(index);
            ResourceLocation id = optionalId(entry.get("id"), childId(stage.getId(), "challenge/" + index));
            List<ChallengeBudget> budgets = new ArrayList<>();
            int budgetIndex = 0;
            for (Map<String, Object> budget : maps(entry.get("budgets"))) {
                ResourceLocation budgetId = optionalId(budget.get("id"), childId(id, "budget/" + budgetIndex++));
                ResourceLocation measure = namespacedId(budget.getOrDefault("measure", "hits_taken"));
                BudgetMode mode = enumValue(BudgetMode.class, budget.get("mode"), BudgetMode.MAXIMUM);
                double minimum = decimal(budget.get("minimum"), mode == BudgetMode.MINIMUM ? 1 : 0);
                double maximum = decimal(budget.get("maximum"), Double.MAX_VALUE);
                budgets.add(new ChallengeBudget(budgetId, measure, mode, minimum, maximum,
                    decimal(budget.get("regeneration_per_second"), 0), String.valueOf(budget.getOrDefault("shared_pool", "")),
                    map(budget.get("filters"))));
            }
            if (entry.get("max_hits") != null) {
                budgets.add(new ChallengeBudget(childId(id, "hits"), namespacedId("hits_taken"),
                    BudgetMode.MAXIMUM, 0, decimal(entry.get("max_hits"), 0), 0, "",
                    entry.get("boss") == null ? Map.of() : Map.of("entity", entry.get("boss"))));
            }
            List<ChallengeStep> steps = new ArrayList<>();
            int stepIndex = 0;
            for (Map<String, Object> step : maps(entry.get("steps"))) {
                steps.add(new ChallengeStep(optionalId(step.get("id"), childId(id, "step/" + stepIndex++)),
                    CONDITIONS.compile(step.getOrDefault("condition", step.get("when"))),
                    duration(step.get("timeout")), bool(step.get("reset_on_failure"), false)));
            }
            Map<String, Object> hud = map(entry.get("hud"));
            ChallengeHud challengeHud = new ChallengeHud(bool(hud.get("enabled"), true),
                String.valueOf(hud.getOrDefault("placement", "top")), decimal(hud.get("scale"), 1),
                String.valueOf(hud.getOrDefault("color", "white")), String.valueOf(hud.getOrDefault("icon", "")),
                String.valueOf(hud.getOrDefault("animation", "none")), bool(hud.get("compact"), false),
                bool(hud.get("hide_when_inactive"), true), bool(hud.get("values_secret"), false));
            output.add(new CompiledChallenge(id, String.valueOf(entry.getOrDefault("title", id.getPath())),
                enumValue(SubjectScope.class, entry.get("scope"), SubjectScope.PLAYER),
                compileCondition(entry.get("start_when"), true), compileCondition(entry.get("success_when"), false),
                compileCondition(entry.get("end_when"), false), budgets, steps, duration(entry.get("timeout")),
                integer(entry.get("retries"), 0), actions(entry.get("success_actions"), childId(id, "success")),
                actions(entry.get("failure_actions"), childId(id, "failure")), challengeHud,
                provenance(stage, "challenges", Integer.toString(index))));
        }
        return List.copyOf(output);
    }

    private static List<AffinityProfile> parseProfiles(Config source) {
        List<AffinityProfile> output = new ArrayList<>();
        for (Map<String, Object> entry : maps(source.getRaw("profiles"))) {
            ResourceLocation id = namespacedId(entry.get("id"));
            List<SelectorSpec> selectors = strings(entry.getOrDefault("content", entry.get("items"))).stream()
                .map(value -> SelectorSpec.parse(value).orElseThrow()).toList();
            List<ProficiencyLevel> levels = maps(entry.get("levels")).stream().map(level ->
                new ProficiencyLevel(String.valueOf(level.get("id")), decimal(level.get("minimum"), 0),
                    enumValue(AffinityEffect.class, level.get("effect"), AffinityEffect.WEAKEN),
                    maps(level.get("transforms")).stream().map(Schema4StageCompiler::transform).toList(),
                    integer(level.get("priority"), 0))).toList();
            output.add(new AffinityProfile(id, selectors, String.valueOf(entry.getOrDefault("proficiency", "")), levels));
        }
        return List.copyOf(output);
    }

    private static List<VariableDefinition> parseVariables(Config source) {
        List<VariableDefinition> output = new ArrayList<>();
        for (Map<String, Object> entry : maps(source.getRaw("variables"))) {
            VariableType type = enumValue(VariableType.class, entry.get("type"), VariableType.DECIMAL);
            Object defaultValue = entry.getOrDefault("default", type == VariableType.STRING ? "" : 0);
            output.add(new VariableDefinition(namespacedId(entry.get("id")), type,
                enumValue(SubjectScope.class, entry.get("scope"), SubjectScope.PLAYER), defaultValue,
                decimal(entry.get("minimum"), Double.NEGATIVE_INFINITY),
                decimal(entry.get("maximum"), Double.POSITIVE_INFINITY),
                bool(entry.get("persistent"), true), bool(entry.get("sync_visible"), false),
                Set.copyOf(strings(entry.get("mutation_permissions"))),
                String.valueOf(entry.getOrDefault("reset", "never"))));
        }
        return List.copyOf(output);
    }

    private static List<TemplateDefinition> parseTemplates(Config source) {
        List<TemplateDefinition> output = new ArrayList<>();
        for (Map<String, Object> entry : maps(source.getRaw("templates"))) {
            Map<String, TemplateParameter> parameters = new LinkedHashMap<>();
            map(entry.get("parameters")).forEach((name, raw) -> {
                Map<String, Object> parameter = map(raw);
                parameters.put(name, new TemplateParameter(name,
                    enumValue(ParameterType.class, parameter.get("type"), ParameterType.STRING),
                    bool(parameter.get("required"), false), parameter.get("default")));
            });
            output.add(new TemplateDefinition(namespacedId(entry.get("id")),
                strings(entry.get("includes")).stream().map(Schema4StageCompiler::namespacedId).toList(),
                parameters, map(entry.get("fragment")),
                enumValue(MergePolicy.class, entry.get("merge"), MergePolicy.DEEP_MERGE)));
        }
        return List.copyOf(output);
    }

    private static Map<String, String> parseFormulas(Config source) {
        Object raw = source.getRaw("formulas");
        if (raw == null) return Map.of();
        Map<String, String> output = new LinkedHashMap<>();
        if (raw instanceof Config config) {
            config.entrySet().forEach(entry -> output.put(entry.getKey(), String.valueOf((Object) entry.getValue())));
        } else {
            for (Map<String, Object> entry : maps(raw)) {
                String id = String.valueOf(entry.getOrDefault("id", ""));
                String expression = String.valueOf(entry.getOrDefault("expression", entry.getOrDefault("formula", "")));
                if (id.isBlank() || expression.isBlank()) throw new IllegalArgumentException("A formula requires id and expression");
                if (output.putIfAbsent(id, expression) != null) throw new IllegalArgumentException("Duplicate formula. " + id);
            }
        }
        return Map.copyOf(output);
    }

    private static List<StageStateDefinition> parseStates(Config source, StageDefinition stage) {
        Config section = source.get("states");
        if (section == null) return List.of();
        Set<String> states = new LinkedHashSet<>(strings(section.getRaw("values")));
        if (states.isEmpty()) states.addAll(List.of("unavailable", "available", "active", "suspended",
            "completed", "failed", "expired", "missing", "owned"));
        Set<String> ownership = new LinkedHashSet<>(strings(section.getRaw("ownership_states")));
        if (ownership.isEmpty()) ownership.addAll(List.of("owned", "completed"));
        Map<String, Set<String>> transitions = new LinkedHashMap<>();
        Config transitionSection = section.get("transitions");
        if (transitionSection != null) transitionSection.entrySet().forEach(entry ->
            transitions.put(entry.getKey(), Set.copyOf(strings(entry.getValue()))));
        return List.of(new StageStateDefinition(stage.getId(), states, ownership,
            String.valueOf(section.getOrElse("initial", "missing")), transitions));
    }

    private static AttributeChange attribute(Map<String, Object> entry) {
        return new AttributeChange(namespacedId(entry.getOrDefault("id", entry.get("attribute"))),
            decimal(entry.get("amount"), 0), enumValue(AttributeOperation.class, entry.get("operation"), AttributeOperation.ADD_VALUE),
            enumValue(StackingPolicy.class, entry.get("stacking"), StackingPolicy.ADD),
            decimal(entry.get("minimum"), Double.NEGATIVE_INFINITY),
            decimal(entry.get("maximum"), Double.POSITIVE_INFINITY));
    }

    private static EffectChange effectChange(Map<String, Object> entry) {
        return new EffectChange(namespacedId(entry.getOrDefault("id", entry.get("effect"))),
            integer(entry.get("amplifier"), 0), integer(entry.get("duration_ticks"), 40),
            bool(entry.get("particles"), true), bool(entry.get("icon"), true),
            String.valueOf(entry.getOrDefault("refresh", "refresh")));
    }

    private static NumericTransform transform(Map<String, Object> entry) {
        return new NumericTransform(namespacedId(entry.getOrDefault("type", entry.get("id"))),
            decimal(entry.get("add"), 0), decimal(entry.get("multiply"), 1),
            decimal(entry.get("minimum"), Double.NEGATIVE_INFINITY),
            decimal(entry.get("maximum"), Double.POSITIVE_INFINITY));
    }

    private static ActionChain actions(Object raw, ResourceLocation owner) {
        List<Map<String, Object>> entries = maps(raw);
        List<CompiledAction> actions = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            Map<String, Object> entry = entries.get(index);
            ResourceLocation provider = namespacedId(entry.getOrDefault("type", entry.get("action")));
            Map<String, Object> arguments = new LinkedHashMap<>(entry);
            arguments.keySet().removeAll(Set.of("id", "type", "action", "failure", "retries", "compensation_required"));
            actions.add(new CompiledAction(optionalId(entry.get("id"), childId(owner, Integer.toString(index))),
                provider, arguments, enumValue(FailurePolicy.class, entry.get("failure"), FailurePolicy.ROLLBACK),
                integer(entry.get("retries"), 0), bool(entry.get("compensation_required"), false)));
        }
        return new ActionChain(actions, true);
    }

    private static ConditionNode condition(Map<String, Object> entry) {
        Object raw = entry.get("while");
        if (raw == null) raw = entry.get("when");
        if (raw == null) raw = entry.get("condition");
        if (raw == null) raw = entry.get("conditions");
        return compileCondition(raw, true);
    }

    private static ConditionNode compileCondition(Object raw, boolean fallback) {
        if (raw == null) return new ConditionNode.Constant(fallback);
        if (raw instanceof List<?> list) {
            List<ConditionNode> children = list.stream().map(CONDITIONS::compile).toList();
            return children.size() == 1 ? children.getFirst() : new ConditionNode.All(children);
        }
        return CONDITIONS.compile(raw);
    }

    private static ViewerPolicy viewerPolicy(Config category, String selector) {
        if (category == null) return ViewerPolicy.INHERIT;
        Config presentation = category.get("presentation");
        if (presentation == null) return ViewerPolicy.INHERIT;
        Object value = presentation.getRaw(stripPriority(selector));
        return value instanceof Config config ? viewerPolicy(toMap(config)) : ViewerPolicy.INHERIT;
    }

    private static ViewerPolicy viewerPolicy(Map<String, Object> entry) {
        Map<String, Object> presentation = map(entry.get("presentation"));
        if (presentation.isEmpty()) presentation = entry;
        ViewerPolicy.Mode shared = enumValue(ViewerPolicy.Mode.class,
            presentation.getOrDefault("viewer", presentation.get("shared")), ViewerPolicy.Mode.INHERIT);
        return new ViewerPolicy(shared,
            enumValue(ViewerPolicy.Mode.class, presentation.get("emi"), ViewerPolicy.Mode.INHERIT),
            enumValue(ViewerPolicy.Mode.class, presentation.get("jei"), ViewerPolicy.Mode.INHERIT),
            bool(presentation.get("ingredient_visible"), true), bool(presentation.get("recipe_visible"), true),
            bool(presentation.get("output_visible"), true), bool(presentation.get("tooltip_visible"), true),
            bool(presentation.getOrDefault("show_locked_overlay", presentation.get("locked_overlay")), true));
    }

    private static Map<String, Object> settings(Map<String, Object> entry) {
        Map<String, Object> copy = new LinkedHashMap<>(entry);
        copy.keySet().removeAll(Set.of("id", "effect", "lifetime", "priority", "while", "when",
            "condition", "conditions", "targets", "exceptions", "selector", "category", "action", "parent"));
        return Map.copyOf(copy);
    }

    private static Config category(Config source, String category) {
        String[] parts = category.split("\\.");
        Config current = source.get(parts[0]);
        for (int index = 1; current != null && index < parts.length; index++) current = current.get(parts[index]);
        return current;
    }

    private static Integer lookupPriority(Config section, String selector) {
        if (section == null) return null;
        Config priorities = section.get("priorities");
        if (priorities == null) return null;
        Object raw = priorities.getRaw(stripPriority(selector));
        return integer(raw);
    }

    private static String stripPriority(String selector) {
        int marker = selector.lastIndexOf("|priority=");
        return marker > 0 ? selector.substring(0, marker).trim() : selector;
    }

    private static String singularCategory(String category) {
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "item" -> "items";
            case "block" -> "blocks";
            case "fluid" -> "fluids";
            case "entity" -> "entities";
            case "recipe" -> "recipes";
            case "dimension" -> "dimensions";
            case "structure" -> "structures";
            case "ability" -> "abilities";
            default -> category.toLowerCase(Locale.ROOT);
        };
    }

    private static String defaultAction(String category) {
        return switch (singularCategory(category)) {
            case "items" -> "use";
            case "blocks" -> "interact";
            case "fluids" -> "interact";
            case "entities" -> "presence";
            case "recipes" -> "craft";
            case "dimensions", "structures" -> "enter";
            case "abilities" -> "perform";
            default -> "access";
        };
    }

    private static int totalTargetCount(Map<String, Object> targets) {
        return targets.values().stream().mapToInt(value -> strings(value).size()).sum();
    }

    private static Set<StageId> stageIds(Object raw) {
        return strings(raw).stream().map(StageId::parse).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static void contextFlag(Map<String, Object> entry, Set<ItemContext> contexts,
                                    String key, ItemContext context) {
        if (bool(entry.get(key), false)) contexts.add(context);
    }

    private static RuleEffect effect(Object value, RuleEffect fallback) {
        if (value == null) return fallback;
        return switch (String.valueOf(value).trim().toLowerCase(Locale.ROOT)) {
            case "lock", "block" -> RuleEffect.LOCK;
            case "deny" -> RuleEffect.DENY;
            case "unlock" -> RuleEffect.UNLOCK;
            case "allow", "permit" -> RuleEffect.ALLOW;
            case "exclude" -> RuleEffect.EXCLUDE;
            case "modify" -> RuleEffect.MODIFY;
            case "replace" -> RuleEffect.REPLACE;
            case "present", "presentation" -> RuleEffect.PRESENT;
            default -> throw new IllegalArgumentException("Invalid rule effect. " + value);
        };
    }

    private static Map<String, Object> extensionFields(Config source) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        for (var entry : source.entrySet()) if (entry.getKey().contains(":")) extensions.put(entry.getKey(), convert(entry.getValue()));
        return Map.copyOf(extensions);
    }

    private static ConfigProvenance provenance(StageDefinition stage, String section, String field) {
        ConfigProvenance root = stage.getProvenance();
        return root == null ? ConfigProvenance.packageField(stage.getId().toString(), "unknown", section, field)
            : root.child(section, field);
    }

    private static ResourceLocation childId(StageId stage, String path) {
        return ResourceLocation.fromNamespaceAndPath(stage.getNamespace(), stage.getPath() + "/" + sanitize(path));
    }

    private static ResourceLocation childId(ResourceLocation parent, String path) {
        return ResourceLocation.fromNamespaceAndPath(parent.getNamespace(), parent.getPath() + "/" + sanitize(path));
    }

    private static String sanitize(String value) {
        return value.toLowerCase(Locale.ROOT).replace(' ', '_').replace('.', '/');
    }

    private static ResourceLocation optionalId(Object raw, ResourceLocation fallback) {
        return raw == null || String.valueOf(raw).isBlank() ? fallback : namespacedId(raw);
    }

    private static ResourceLocation namespacedId(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) throw new IllegalArgumentException("A namespaced ID is required");
        String value = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        return value.contains(":") ? ResourceLocation.parse(value)
            : ResourceLocation.fromNamespaceAndPath("progressivestages", value);
    }

    private static Integer integer(Config section, String key) {
        return section == null ? null : integer(section.getRaw(key));
    }

    private static Integer integer(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(String.valueOf(value));
    }

    private static int integer(Object value, int fallback) {
        Integer parsed = integer(value);
        return parsed == null ? fallback : parsed;
    }

    private static double decimal(Object value, double fallback) {
        if (value == null) return fallback;
        if (value instanceof Number number) return number.doubleValue();
        return Double.parseDouble(String.valueOf(value));
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value == null) return fallback;
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private static long duration(Object value) {
        if (value == null) return 0;
        if (value instanceof Number number) return Math.max(0, number.longValue());
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        long multiplier = 1;
        if (text.endsWith("ms")) text = text.substring(0, text.length() - 2);
        else if (text.endsWith("s")) { multiplier = 1_000; text = text.substring(0, text.length() - 1); }
        else if (text.endsWith("m")) { multiplier = 60_000; text = text.substring(0, text.length() - 1); }
        else if (text.endsWith("h")) { multiplier = 3_600_000; text = text.substring(0, text.length() - 1); }
        else if (text.endsWith("d")) { multiplier = 86_400_000; text = text.substring(0, text.length() - 1); }
        return Math.multiplyExact(Long.parseLong(text.trim()), multiplier);
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, Object value, T fallback) {
        if (value == null) return fallback;
        String normalized = String.valueOf(value).trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return Enum.valueOf(type, normalized);
    }

    private static List<String> strings(Object raw) {
        if (raw == null) return List.of();
        if (raw instanceof Collection<?> values) return values.stream().map(String::valueOf).toList();
        return List.of(String.valueOf(raw));
    }

    private static List<Map<String, Object>> maps(Object raw) {
        if (raw == null) return List.of();
        if (raw instanceof Config config) return List.of(toMap(config));
        if (raw instanceof Map<?, ?> map) return List.of(map(map));
        if (raw instanceof Collection<?> values) {
            List<Map<String, Object>> output = new ArrayList<>();
            for (Object value : values) {
                if (value instanceof Config config) output.add(toMap(config));
                else if (value instanceof Map<?, ?> map) output.add(map(map));
                else throw new IllegalArgumentException("A configuration entry must be a table");
            }
            return List.copyOf(output);
        }
        throw new IllegalArgumentException("A configuration section must contain tables");
    }

    private static Map<String, Object> map(Object raw) {
        if (raw == null) return Map.of();
        if (raw instanceof Config config) return toMap(config);
        if (raw instanceof Map<?, ?> source) {
            Map<String, Object> output = new LinkedHashMap<>();
            source.forEach((key, value) -> output.put(String.valueOf(key), convert(value)));
            return Map.copyOf(output);
        }
        throw new IllegalArgumentException("A configuration value must be a table");
    }

    private static Map<String, Object> toMap(Config config) {
        Map<String, Object> output = new LinkedHashMap<>();
        config.entrySet().forEach(entry -> output.put(entry.getKey(), convert(entry.getValue())));
        return Map.copyOf(output);
    }

    private static Object convert(Object value) {
        if (value instanceof Config config) return toMap(config);
        if (value instanceof Collection<?> collection) return collection.stream().map(Schema4StageCompiler::convert).toList();
        return value;
    }
}
