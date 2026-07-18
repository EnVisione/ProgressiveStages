package com.enviouse.progressivestages.compat.kubejs;

import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.compat.ScriptHooks;
import com.enviouse.progressivestages.common.api.ProgressiveStagesRehaulAPI;
import com.enviouse.progressivestages.common.rehaul.catalog.CatalogQuery;
import com.enviouse.progressivestages.common.rehaul.extension.ExtensionArgument;
import com.enviouse.progressivestages.common.rehaul.extension.ExtensionKind;
import com.enviouse.progressivestages.common.rehaul.extension.ExtensionMetadata;
import com.enviouse.progressivestages.common.rehaul.extension.ExtensionMetadataRegistry;
import com.enviouse.progressivestages.common.rehaul.extension.MissingCallbackPolicy;
import com.enviouse.progressivestages.common.rehaul.extension.ScriptExtensionAdapters;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.server.triggers.StageTriggerEvaluator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * v2.5: the global {@code ProgressiveStages} object bound into KubeJS scripts. Gives packs deep,
 * server-authoritative control over stages:
 *
 * <pre>
 *   // react to engine grants/revokes (commands, triggers, quests, purchases, regression — all of them)
 *   ProgressiveStages.onGranted((player, stage) =&gt; player.tell('Unlocked ' + stage))
 *   ProgressiveStages.onRevoked((player, stage) =&gt; player.tell('Lost ' + stage))
 *
 *   // a fully custom trigger condition — reference it from a stage as: type = "script", id = "rich"
 *   ProgressiveStages.condition('rich', player =&gt; player.getMainHandItem().id == 'minecraft:diamond')
 *
 *   // imperative control from any KubeJS event
 *   ServerEvents.tick(e =&gt; e.server.players.forEach(p =&gt; {
 *     if (ProgressiveStages.percent(p, 'diamond_age') &gt;= 100) ProgressiveStages.grant(p, 'diamond_age')
 *   }))
 * </pre>
 */
public final class PSKubeBindings {

    // ---- lifecycle hooks & custom conditions ----

    public void onGranted(ScriptHooks.StageCallback cb) { ScriptHooks.onGranted(cb); }
    public void onRevoked(ScriptHooks.StageCallback cb) { ScriptHooks.onRevoked(cb); }
    public void onChanged(ScriptHooks.StageChangeCallback cb) { ScriptHooks.onChanged(cb); }
    public void onEvent(ScriptHooks.StructuredEventCallback cb) { ScriptHooks.onEvent(cb); }
    public void condition(String id, ScriptHooks.StagePredicate predicate) {
        ScriptHooks.registerCondition(id, predicate);
        legacyMetadata(id, ExtensionKind.CONDITION);
    }
    public void condition(String id, ScriptHooks.StagePredicate predicate, Map<?, ?> metadata) {
        ScriptHooks.registerCondition(id, predicate);
        registerMetadata(id, ExtensionKind.CONDITION, metadata);
    }
    public void progressCondition(String id, ScriptHooks.StageProgressProvider provider) {
        ScriptHooks.registerProgress(id, provider);
        legacyMetadata(id, ExtensionKind.VALUE);
    }
    public void progressCondition(String id, ScriptHooks.StageProgressProvider provider, Map<?, ?> metadata) {
        ScriptHooks.registerProgress(id, provider);
        registerMetadata(id, ExtensionKind.VALUE, metadata);
    }
    public void action(String id, ScriptHooks.ScriptActionCallback callback, Map<?, ?> metadata) {
        ResourceLocation key = extensionId(id);
        ScriptHooks.registerAction(key.toString(), callback);
        ScriptExtensionAdapters.action(key);
        registerMetadata(key.toString(), ExtensionKind.ACTION, metadata);
    }
    public void selector(String id, String prefix, ScriptHooks.ScriptSelectorCallback callback, Map<?, ?> metadata) {
        ResourceLocation key = extensionId(id);
        ScriptHooks.registerSelector(key.toString(), callback);
        ScriptExtensionAdapters.selector(key, prefix);
        registerMetadata(key.toString(), ExtensionKind.SELECTOR, metadata);
    }
    public void challengeMeasure(String id, ScriptHooks.ScriptMeasureCallback callback, Map<?, ?> metadata) {
        ResourceLocation key = extensionId(id);
        ScriptHooks.registerMeasure(key.toString(), callback);
        ScriptExtensionAdapters.measure(key);
        registerMetadata(key.toString(), ExtensionKind.CHALLENGE_MEASURE, metadata);
    }

    // ---- imperative stage control / queries ----

    public boolean has(Player player, String stage) {
        StageId id = parse(stage);
        return player instanceof ServerPlayer sp && id != null && StageManager.getInstance().hasStage(sp, id);
    }

    public boolean grant(Player player, String stage) {
        StageId id = parse(stage);
        if (!(player instanceof ServerPlayer sp) || id == null) return false;
        return ProgressiveStagesAPI.grantStage(sp, id, StageCause.SCRIPT);
    }

    public boolean revoke(Player player, String stage) {
        StageId id = parse(stage);
        if (!(player instanceof ServerPlayer sp) || id == null) return false;
        return ProgressiveStagesAPI.revokeStage(sp, id, StageCause.SCRIPT);
    }

    /** Administrative/scripted grant that intentionally ignores dependency requirements. */
    public boolean grantBypass(Player player, String stage) {
        StageId id = parse(stage);
        return player instanceof ServerPlayer sp && id != null
            && ProgressiveStagesAPI.grantStageBypass(sp, id, StageCause.SCRIPT);
    }

    public int grantMany(Player player, Collection<?> stages) {
        return player instanceof ServerPlayer sp
            ? ProgressiveStagesAPI.grantStages(sp, parseMany(stages), StageCause.SCRIPT) : 0;
    }

    public int revokeMany(Player player, Collection<?> stages) {
        return player instanceof ServerPlayer sp
            ? ProgressiveStagesAPI.revokeStages(sp, parseMany(stages), StageCause.SCRIPT) : 0;
    }

    public int grantAll(Player player) {
        if (!(player instanceof ServerPlayer sp)) return 0;
        int changed = 0;
        for (StageId id : ProgressiveStagesAPI.getAllStageIds()) {
            if (ProgressiveStagesAPI.grantStageBypass(sp, id, StageCause.SCRIPT)) changed++;
        }
        return changed;
    }

    public int revokeAll(Player player) {
        return player instanceof ServerPlayer sp
            ? ProgressiveStagesAPI.revokeStages(sp, new ArrayList<>(ProgressiveStagesAPI.getStages(sp)), StageCause.SCRIPT) : 0;
    }

    /** Toggle a stage and return the player's new ownership state. */
    public boolean toggle(Player player, String stage) {
        StageId id = parse(stage);
        if (!(player instanceof ServerPlayer sp) || id == null) return false;
        if (ProgressiveStagesAPI.hasStage(sp, id)) {
            ProgressiveStagesAPI.revokeStage(sp, id, StageCause.SCRIPT);
            return false;
        }
        ProgressiveStagesAPI.grantStage(sp, id, StageCause.SCRIPT);
        return ProgressiveStagesAPI.hasStage(sp, id);
    }

    public boolean exists(String stage) {
        StageId id = parse(stage);
        return id != null && ProgressiveStagesAPI.stageExists(id);
    }

    public boolean available(Player player, String stage) {
        StageId id = parse(stage);
        return player instanceof ServerPlayer sp && id != null && ProgressiveStagesAPI.isAvailable(sp, id);
    }

    public boolean hasAll(Player player, Collection<?> stages) {
        List<StageId> parsed = parseMany(stages);
        return player instanceof ServerPlayer sp && stages != null && parsed.size() == stages.size()
            && ProgressiveStagesAPI.hasAllStages(sp, parsed);
    }

    public boolean hasAny(Player player, Collection<?> stages) {
        List<StageId> parsed = parseMany(stages);
        return player instanceof ServerPlayer sp && stages != null && parsed.size() == stages.size()
            && ProgressiveStagesAPI.hasAnyStage(sp, parsed);
    }

    public List<String> dependencies(String stage) {
        StageId id = parse(stage);
        if (id == null) return List.of();
        return ProgressiveStagesAPI.getDefinition(id).stream()
            .flatMap(def -> def.getDependencies().stream()).map(StageId::toString).toList();
    }

    public List<String> missingDependencies(Player player, String stage) {
        StageId id = parse(stage);
        if (!(player instanceof ServerPlayer sp) || id == null) return List.of();
        return ProgressiveStagesAPI.getMissingDependencies(sp, id).stream().map(StageId::toString).toList();
    }

    public List<String> allDependencies(String stage) {
        StageId id = parse(stage);
        return id == null ? List.of() : strings(ProgressiveStagesAPI.getAllDependencies(id));
    }

    public List<String> dependents(String stage) {
        StageId id = parse(stage);
        return id == null ? List.of() : strings(ProgressiveStagesAPI.getDependents(id));
    }

    public List<String> allDependents(String stage) {
        StageId id = parse(stage);
        return id == null ? List.of() : strings(ProgressiveStagesAPI.getAllDependents(id));
    }

    public List<String> withTag(String tag) {
        return ProgressiveStagesAPI.getStagesWithTag(tag).stream().map(StageId::toString).toList();
    }

    public List<String> withCategory(String category) {
        return strings(ProgressiveStagesAPI.getStagesInCategory(category));
    }

    public List<String> categories() {
        return ProgressiveStagesAPI.getAllDefinitions().stream()
            .map(def -> def.getCategory().trim()).filter(s -> !s.isEmpty())
            .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public int grantTag(Player player, String tag) {
        if (!(player instanceof ServerPlayer sp)) return 0;
        int changed = 0;
        for (StageId id : ProgressiveStagesAPI.getStagesWithTag(tag)) {
            if (ProgressiveStagesAPI.grantStage(sp, id, StageCause.SCRIPT)) changed++;
        }
        return changed;
    }

    public int revokeTag(Player player, String tag) {
        if (!(player instanceof ServerPlayer sp)) return 0;
        int changed = 0;
        for (StageId id : ProgressiveStagesAPI.getStagesWithTag(tag)) {
            if (ProgressiveStagesAPI.revokeStage(sp, id, StageCause.SCRIPT)) changed++;
        }
        return changed;
    }

    public List<String> list(Player player) {
        List<String> out = new ArrayList<>();
        if (player instanceof ServerPlayer sp) {
            for (StageId id : StageManager.getInstance().getStages(sp)) out.add(id.toString());
        }
        return out;
    }

    /** Alias with a clearer name for scripts that also use {@link #all()}. */
    public List<String> owned(Player player) { return list(player); }

    public List<String> all() { return strings(ProgressiveStagesAPI.getAllStageIds()); }

    public List<String> locked(Player player) {
        return player instanceof ServerPlayer sp
            ? strings(ProgressiveStagesAPI.getLockedStages(sp)) : List.of();
    }

    public List<String> availableStages(Player player) {
        return player instanceof ServerPlayer sp
            ? strings(ProgressiveStagesAPI.getAvailableStages(sp)) : List.of();
    }

    /** Completion percent (0..100) of a stage's {@code [[triggers]]} for the player. */
    public int percent(Player player, String stage) {
        StageId id = parse(stage);
        if (!(player instanceof ServerPlayer sp) || id == null) return 0;
        return Math.round(StageTriggerEvaluator.stagePercent(sp, id) * 100f);
    }

    /** A script-friendly definition snapshot; missing IDs return an empty map. */
    public Map<String, Object> info(String stage) {
        StageId id = parse(stage);
        if (id == null) return Map.of();
        return ProgressiveStagesAPI.getDefinition(id).map(def -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", def.getId().toString());
            out.put("displayName", def.getDisplayName());
            out.put("description", def.getDescription());
            out.put("icon", def.getIcon().map(Object::toString).orElse(""));
            out.put("dependencies", strings(def.getDependencies()));
            out.put("dependencyMode", def.getDependencyMode().configName());
            out.put("dependencyCount", def.getDependencyCount());
            out.put("category", def.getCategory());
            out.put("tags", List.copyOf(def.getTags()));
            out.put("scope", def.getScope());
            out.put("hidden", def.isHidden());
            out.put("temporary", def.isTemporary());
            out.put("durationMillis", def.getDurationMillis());
            out.put("purchasable", def.isPurchasable());
            out.put("hasTriggers", def.hasTriggers());
            List<Map<String, Object>> conditionalRules = new ArrayList<>();
            for (var rule : def.getConditionalRules()) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("id", rule.id().toString());
                data.put("effect", rule.effect().name().toLowerCase(java.util.Locale.ROOT));
                data.put("activation", rule.activation().name().toLowerCase(java.util.Locale.ROOT));
                data.put("stageState", rule.stageState().name().toLowerCase(java.util.Locale.ROOT));
                data.put("priority", rule.priority());
                data.put("trigger", rule.triggerType().name().toLowerCase(java.util.Locale.ROOT));
                data.put("durationMillis", rule.durationMillis());
                data.put("targetTypes", rule.targets().types().stream()
                    .map(type -> type.name().toLowerCase(java.util.Locale.ROOT)).toList());
                conditionalRules.add(data);
            }
            out.put("conditionalRules", conditionalRules);
            return out;
        }).orElseGet(Map::of);
    }

    /** Full rule/condition progress snapshot, useful for custom KubeJS UIs and quest logic. */
    public Map<String, Object> progress(Player player, String stage) {
        StageId id = parse(stage);
        if (!(player instanceof ServerPlayer sp) || id == null) return Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stage", id.toString());
        out.put("percent", percent(player, stage));
        List<Map<String, Object>> rules = new ArrayList<>();
        for (var rule : ProgressiveStagesAPI.getTriggerProgress(sp, id)) {
            Map<String, Object> ruleData = new LinkedHashMap<>();
            ruleData.put("mode", rule.mode().name().toLowerCase(java.util.Locale.ROOT));
            ruleData.put("description", rule.description());
            ruleData.put("satisfied", rule.satisfied());
            List<Map<String, Object>> conditions = new ArrayList<>();
            for (var condition : rule.conditions()) {
                Map<String, Object> conditionData = new LinkedHashMap<>();
                conditionData.put("type", condition.condition().type().name().toLowerCase(java.util.Locale.ROOT));
                conditionData.put("target", condition.condition().target());
                conditionData.put("current", condition.current());
                conditionData.put("threshold", condition.threshold());
                conditionData.put("satisfied", condition.satisfied());
                conditions.add(conditionData);
            }
            ruleData.put("conditions", conditions);
            rules.add(ruleData);
        }
        out.put("rules", rules);
        return out;
    }

    public long counter(Player player, String counter) {
        return player instanceof ServerPlayer sp ? ProgressiveStagesAPI.getCounter(sp, counter) : 0L;
    }

    public long addCounter(Player player, String counter, long amount) {
        return player instanceof ServerPlayer sp ? ProgressiveStagesAPI.addCounter(sp, counter, amount) : 0L;
    }

    public long setCounter(Player player, String counter, long value) {
        return player instanceof ServerPlayer sp ? ProgressiveStagesAPI.setCounter(sp, counter, value) : 0L;
    }

    public void resetCounter(Player player, String counter) {
        if (player instanceof ServerPlayer sp) ProgressiveStagesAPI.resetCounter(sp, counter);
    }

    public boolean activateRule(Player player, String rule) {
        return player instanceof ServerPlayer sp
            && ProgressiveStagesAPI.activateConditionalRule(sp, rule, 0L);
    }

    public boolean activateRule(Player player, String rule, long seconds) {
        return player instanceof ServerPlayer sp && seconds > 0L
            && seconds <= Long.MAX_VALUE / 1_000L
            && ProgressiveStagesAPI.activateConditionalRule(sp, rule, seconds * 1_000L);
    }

    public boolean clearRule(Player player, String rule) {
        return player instanceof ServerPlayer sp
            && ProgressiveStagesAPI.clearConditionalRule(sp, rule);
    }

    public int clearRules(Player player) {
        return player instanceof ServerPlayer sp
            ? ProgressiveStagesAPI.clearConditionalRules(sp) : 0;
    }

    public Map<String, Long> activeRules(Player player) {
        if (!(player instanceof ServerPlayer sp)) return Map.of();
        Map<String, Long> out = new LinkedHashMap<>();
        ProgressiveStagesAPI.getActiveConditionalRules(sp).forEach((id, remainingMillis) ->
            out.put(id.toString(), (remainingMillis + 999L) / 1_000L));
        return out;
    }

    public List<String> ruleIds() {
        return ProgressiveStagesAPI.getConditionalRuleIds().stream()
            .map(Object::toString).sorted().toList();
    }

    public Map<String, Object> ruleInfo(String requested) {
        return ProgressiveStagesAPI.getConditionalRule(requested).map(rule -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", rule.id().toString());
            out.put("ownerStage", rule.ownerStage().toString());
            out.put("effect", rule.effect().name().toLowerCase(java.util.Locale.ROOT));
            out.put("activation", rule.activation().name().toLowerCase(java.util.Locale.ROOT));
            out.put("stageState", rule.stageState().name().toLowerCase(java.util.Locale.ROOT));
            out.put("priority", rule.priority());
            out.put("trigger", rule.triggerType().name().toLowerCase(java.util.Locale.ROOT));
            out.put("triggerEntities", rule.triggerEntities().stream().map(entry -> entry.raw()).toList());
            out.put("durationMillis", rule.durationMillis());
            out.put("refreshDuration", rule.refreshDuration());

            Map<String, List<String>> targets = new LinkedHashMap<>();
            Map<String, List<String>> exceptions = new LinkedHashMap<>();
            for (var type : rule.targets().types()) {
                String key = type.name().toLowerCase(java.util.Locale.ROOT);
                targets.put(key, rule.targets().included(type).stream().map(entry -> entry.raw()).toList());
                if (!rule.targets().excluded(type).isEmpty()) {
                    exceptions.put(key, rule.targets().excluded(type).stream().map(entry -> entry.raw()).toList());
                }
            }
            out.put("targets", targets);
            out.put("exceptions", exceptions);

            var context = rule.context();
            Map<String, Object> when = new LinkedHashMap<>();
            when.put("mode", context.mode().name().toLowerCase(java.util.Locale.ROOT));
            when.put("dimensions", context.dimensions().stream().map(entry -> entry.raw()).toList());
            when.put("structures", context.structures().stream().map(entry -> entry.raw()).toList());
            when.put("biomes", context.biomes().stream().map(entry -> entry.raw()).toList());
            if (context.minY() != null) when.put("minY", context.minY());
            if (context.maxY() != null) when.put("maxY", context.maxY());
            if (context.minHealth() != null) when.put("minHealth", context.minHealth());
            if (context.maxHealth() != null) when.put("maxHealth", context.maxHealth());
            when.put("stages", context.requiredStages().stream().map(Object::toString).toList());
            when.put("missingStages", context.missingStages().stream().map(Object::toString).toList());
            when.put("effects", context.effects().stream().map(Object::toString).toList());
            if (context.sneaking() != null) when.put("sneaking", context.sneaking());
            if (context.sprinting() != null) when.put("sprinting", context.sprinting());
            if (context.swimming() != null) when.put("swimming", context.swimming());
            if (context.riding() != null) when.put("riding", context.riding());
            if (context.onGround() != null) when.put("onGround", context.onGround());
            if (!context.scriptCondition().isEmpty()) when.put("script", context.scriptCondition());
            out.put("when", when);
            return out;
        }).orElseGet(Map::of);
    }

    /** Re-run all declarative trigger rules immediately after a script-side state change. */
    public void evaluate(Player player) {
        if (player instanceof ServerPlayer sp) ProgressiveStagesAPI.evaluateTriggers(sp);
    }

    /** Force a complete authoritative client cache refresh after unusual script-side changes. */
    public void sync(Player player) {
        if (player instanceof ServerPlayer sp) ProgressiveStagesAPI.syncPlayer(sp);
    }

    /** Open the vanilla-style stage map for a player. */
    public void openGui(Player player) {
        if (player instanceof ServerPlayer sp) {
            com.enviouse.progressivestages.common.network.NetworkHandler.sendStageGuiData(sp);
        }
    }

    public long compiledRevision() {
        return ProgressiveStagesRehaulAPI.compiledSnapshot().revision();
    }

    public long catalogRevision() {
        return ProgressiveStagesRehaulAPI.catalogSnapshot().revision();
    }

    public List<String> capabilities() {
        return ProgressiveStagesRehaulAPI.capabilities().stream().map(Object::toString).sorted().toList();
    }

    public boolean hasCapability(String capability) {
        ResourceLocation id = ResourceLocation.tryParse(capability);
        if (id == null && capability != null) id = ResourceLocation.fromNamespaceAndPath("progressivestages", capability);
        return id != null && ProgressiveStagesRehaulAPI.hasCapability(id);
    }

    public Map<String, Object> catalog(String catalog, String field, String mode, String text,
                                       int pageSize, String cursor, long expectedRevision) {
        ResourceLocation catalogId = ResourceLocation.tryParse(catalog);
        if (catalogId == null) return Map.of("error", "invalid_catalog");
        var page = ProgressiveStagesRehaulAPI.searchCatalog(new CatalogQuery(catalogId,
            field == null ? "kubejs" : field, mode == null ? "id" : mode,
            text == null ? "" : text, Map.of(), "relevance", Math.max(1, Math.min(100, pageSize)),
            cursor == null ? "" : cursor, expectedRevision));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("revision", page.revision());
        out.put("total", page.totalMatches());
        out.put("nextCursor", page.nextCursor());
        out.put("stale", page.staleRevision());
        out.put("entries", page.entries().stream().map(entry -> Map.of(
            "key", entry.key(), "label", entry.label(), "namespace", entry.namespace(),
            "source", entry.sourceType(), "tags", entry.tags().stream().map(Object::toString).toList(),
            "metadata", entry.metadata())).toList());
        return out;
    }

    public Map<String, Object> explain(Player player, String category, String action, String target) {
        if (!(player instanceof ServerPlayer serverPlayer)) return Map.of("error", "server_player_required");
        ResourceLocation targetId = ResourceLocation.tryParse(target);
        if (targetId == null) return Map.of("error", "invalid_target");
        return ProgressiveStagesRehaulAPI.decide(serverPlayer, category, action, targetId)
            .map(PSKubeBindings::traceMap).orElseGet(() -> Map.of("matched", false));
    }

    public List<Map<String, Object>> decisionHistory() {
        return ProgressiveStagesRehaulAPI.decisionHistory().stream().map(PSKubeBindings::traceMap).toList();
    }

    public List<Map<String, Object>> transitionHistory() {
        return ProgressiveStagesRehaulAPI.transitionHistory().stream().map(entry -> Map.<String, Object>of(
            "subject", entry.subject(), "rule", entry.rule().toString(), "stage", entry.stage().toString(),
            "direction", entry.direction().name().toLowerCase(java.util.Locale.ROOT),
            "timestamp", entry.timestamp(), "success", entry.committed(), "explanation", entry.explanation())).toList();
    }

    public List<Map<String, Object>> challenges(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return List.of();
        return ProgressiveStagesRehaulAPI.challengeSessions(serverPlayer).stream().map(session -> Map.<String, Object>of(
            "id", session.challenge().toString(), "status", session.status().name().toLowerCase(java.util.Locale.ROOT),
            "startedAt", session.startedAt(), "endedAt", session.endedAt(), "step", session.currentStep(),
            "attempts", session.attempts(), "budgets", session.budgetValues(), "explanation", session.explanation())).toList();
    }

    public Object variable(Player player, String variable) {
        ResourceLocation id = ResourceLocation.tryParse(variable);
        return player instanceof ServerPlayer serverPlayer && id != null
            ? ProgressiveStagesRehaulAPI.getVariable(serverPlayer, id) : null;
    }

    public Object setVariable(Player player, String variable, Object value) {
        ResourceLocation id = ResourceLocation.tryParse(variable);
        return player instanceof ServerPlayer serverPlayer && id != null
            ? ProgressiveStagesRehaulAPI.setVariable(serverPlayer, id, value) : null;
    }

    public double addVariable(Player player, String variable, double amount) {
        ResourceLocation id = ResourceLocation.tryParse(variable);
        return player instanceof ServerPlayer serverPlayer && id != null
            ? ProgressiveStagesRehaulAPI.addVariable(serverPlayer, id, amount) : 0;
    }

    public double evaluateFormula(String formula, Map<String, Object> values) {
        Map<String, Double> numeric = new LinkedHashMap<>();
        if (values != null) values.forEach((key, value) -> {
            if (value instanceof Number number) numeric.put(key, number.doubleValue());
        });
        return ProgressiveStagesRehaulAPI.evaluateFormula(formula, numeric);
    }

    public Map<String, Object> expandTemplate(String template, Map<String, Object> arguments) {
        ResourceLocation id = ResourceLocation.tryParse(template);
        return id == null ? Map.of("error", "invalid_template")
            : ProgressiveStagesRehaulAPI.expandTemplate(id, arguments == null ? Map.of() : arguments);
    }

    public Map<String, Object> extensionCatalog() {
        var snapshot = ExtensionMetadataRegistry.get().snapshot();
        return Map.of("revision", snapshot.revision(), "frozen", snapshot.frozen(), "registrations",
            snapshot.registrations().stream().map(PSKubeBindings::metadataMap).toList());
    }

    public List<Map<String, Object>> lifecycleProgress(Player player, String eventInterest) {
        return player instanceof ServerPlayer serverPlayer
            ? ProgressiveStagesRehaulAPI.lifecycleProgress(serverPlayer,
                eventInterest == null || eventInterest.isBlank() ? Set.of() : Set.of(eventInterest)) : List.of();
    }

    public boolean armLifecycle(Player player, String rule) {
        ResourceLocation id = ResourceLocation.tryParse(rule);
        if (!(player instanceof ServerPlayer serverPlayer) || id == null) return false;
        ProgressiveStagesRehaulAPI.armLifecycle(serverPlayer, id);
        return true;
    }

    public boolean resetLifecycle(Player player, String rule) {
        ResourceLocation id = ResourceLocation.tryParse(rule);
        if (!(player instanceof ServerPlayer serverPlayer) || id == null) return false;
        ProgressiveStagesRehaulAPI.resetLifecycle(serverPlayer, id);
        return true;
    }

    public boolean resetChallenge(Player player, String challenge) {
        ResourceLocation id = ResourceLocation.tryParse(challenge);
        return player instanceof ServerPlayer serverPlayer && id != null
            && ProgressiveStagesRehaulAPI.resetChallenge(serverPlayer, id);
    }

    public Map<String, Object> clientSnapshot() {
        var snapshot = ProgressiveStagesRehaulAPI.clientSnapshot();
        return Map.of("protocol", snapshot.manifest().protocolVersion(),
            "revision", snapshot.manifest().configurationRevision(), "checksum", snapshot.manifest().checksum(),
            "chunks", snapshot.manifest().chunks(), "compressedBytes", snapshot.manifest().compressedBytes(),
            "uncompressedBytes", snapshot.manifest().uncompressedBytes(), "capabilities", snapshot.manifest().capabilities());
    }

    private static StageId parse(String stage) {
        try { return StageId.parse(stage); } catch (Exception e) { return null; }
    }

    private static List<StageId> parseMany(Collection<?> stages) {
        if (stages == null) return List.of();
        List<StageId> out = new ArrayList<>();
        for (Object value : stages) {
            if (value == null) continue;
            StageId id = parse(String.valueOf(value));
            if (id != null) out.add(id);
        }
        return out;
    }

    private static List<String> strings(Collection<StageId> ids) {
        return ids.stream().map(StageId::toString).toList();
    }

    private static void legacyMetadata(String id, ExtensionKind kind) {
        ResourceLocation key = extensionId(id);
        if (ExtensionMetadataRegistry.get().find(kind, key).isEmpty()) {
            ExtensionMetadataRegistry.get().register(ExtensionMetadata.legacy(key, kind));
        }
    }

    private static void registerMetadata(String id, ExtensionKind kind, Map<?, ?> raw) {
        ResourceLocation key = extensionId(id);
        if (ExtensionMetadataRegistry.get().find(kind, key).isPresent()) return;
        Map<String, Object> metadata = normalize(raw);
        List<ExtensionArgument> arguments = new ArrayList<>();
        Object rawArguments = metadata.get("arguments");
        if (rawArguments instanceof Collection<?> collection) {
            for (Object value : collection) {
                Map<String, Object> argument = value instanceof Map<?, ?> map ? normalize(map) : Map.of();
                String name = String.valueOf(argument.getOrDefault("name", ""));
                if (name.isBlank()) continue;
                ResourceLocation catalog = ResourceLocation.tryParse(String.valueOf(argument.getOrDefault("catalog", "")));
                List<String> choices = argument.get("choices") instanceof Collection<?> values
                    ? values.stream().map(String::valueOf).toList() : List.of();
                arguments.add(new ExtensionArgument(name, String.valueOf(argument.getOrDefault("type", "string")),
                    Boolean.parseBoolean(String.valueOf(argument.getOrDefault("required", false))),
                    argument.get("default"), catalog, choices, String.valueOf(argument.getOrDefault("help", "")), argument));
            }
        }
        MissingCallbackPolicy missing;
        Object missingValue = metadata.containsKey("missingCallbackPolicy")
            ? metadata.get("missingCallbackPolicy") : metadata.getOrDefault("missingCallback", "reject");
        try { missing = MissingCallbackPolicy.valueOf(String.valueOf(missingValue).toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException error) { missing = MissingCallbackPolicy.REJECT; }
        Object interests = metadata.containsKey("eventInterests")
            ? metadata.get("eventInterests") : metadata.get("events");
        Set<ResourceLocation> capabilities = stringsSet(metadata.get("capabilities")).stream()
            .map(ResourceLocation::tryParse).filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<Map<String, Object>> examples = new ArrayList<>();
        if (metadata.get("examples") instanceof Collection<?> values) {
            for (Object value : values) if (value instanceof Map<?, ?> map) examples.add(normalize(map));
        }
        ExtensionMetadataRegistry.get().register(new ExtensionMetadata(key, kind,
            String.valueOf(metadata.getOrDefault("title", key.toString())),
            String.valueOf(metadata.getOrDefault("description", "")),
            String.valueOf(metadata.getOrDefault("icon", "")), arguments,
            stringsSet(metadata.get("scopes")), stringsSet(interests), capabilities, examples, missing, false));
    }

    private static Map<String, Object> traceMap(com.enviouse.progressivestages.common.rehaul.decision.DecisionTrace trace) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("matched", true);
        out.put("target", trace.target().toString());
        out.put("category", trace.category());
        out.put("action", trace.action());
        out.put("blocked", trace.blocked());
        out.put("effect", trace.winningEffect() == null ? "" : trace.winningEffect().name().toLowerCase(java.util.Locale.ROOT));
        out.put("winner", trace.winner().map(Object::toString).orElse(""));
        out.put("explanation", trace.explanation());
        out.put("candidates", trace.candidates().stream().map(candidate -> Map.of(
            "rule", candidate.ruleId().toString(), "effect", candidate.effect().name().toLowerCase(java.util.Locale.ROOT),
            "priority", candidate.priority(), "matched", candidate.conditionMatched(), "explanation", candidate.explanation())).toList());
        return out;
    }

    private static Map<String, Object> metadataMap(ExtensionMetadata metadata) {
        return Map.of("id", metadata.id().toString(), "kind", metadata.kind().name().toLowerCase(java.util.Locale.ROOT),
            "title", metadata.title(), "description", metadata.description(), "icon", metadata.icon(),
            "arguments", metadata.arguments(), "scopes", metadata.scopes(), "events", metadata.eventInterests(),
            "missingCallback", metadata.missingCallbackPolicy().name().toLowerCase(java.util.Locale.ROOT),
            "legacy", metadata.legacy());
    }

    private static Map<String, Object> normalize(Map<?, ?> map) {
        if (map == null) return Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((key, value) -> out.put(String.valueOf(key), value));
        return out;
    }

    private static Set<String> stringsSet(Object value) {
        if (!(value instanceof Collection<?> collection)) return Set.of();
        return collection.stream().map(String::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static ResourceLocation extensionId(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Extension id cannot be blank");
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.contains(":") ? ResourceLocation.parse(normalized)
            : ResourceLocation.fromNamespaceAndPath("kubejs", normalized);
    }
}
