package com.enviouse.progressivestages.common.api;

import com.enviouse.progressivestages.common.rehaul.CompiledSnapshot;
import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import com.enviouse.progressivestages.common.rehaul.SelectorSpec;
import com.enviouse.progressivestages.common.rehaul.action.ActionProvider;
import com.enviouse.progressivestages.common.rehaul.action.ActionRegistry;
import com.enviouse.progressivestages.common.rehaul.catalog.CatalogContributor;
import com.enviouse.progressivestages.common.rehaul.catalog.CatalogPage;
import com.enviouse.progressivestages.common.rehaul.catalog.CatalogQuery;
import com.enviouse.progressivestages.common.rehaul.catalog.CatalogSnapshot;
import com.enviouse.progressivestages.common.rehaul.catalog.EditorCatalogService;
import com.enviouse.progressivestages.common.rehaul.challenge.ChallengeMeasureProvider;
import com.enviouse.progressivestages.common.rehaul.challenge.ChallengeMeasureRegistry;
import com.enviouse.progressivestages.common.rehaul.challenge.ChallengeSessionView;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionCompiler;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionContext;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionProvider;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionRegistry;
import com.enviouse.progressivestages.common.rehaul.condition.ConditionTrace;
import com.enviouse.progressivestages.common.rehaul.decision.DecisionTrace;
import com.enviouse.progressivestages.common.rehaul.extension.ExtensionCatalogSnapshot;
import com.enviouse.progressivestages.common.rehaul.extension.ExtensionMetadata;
import com.enviouse.progressivestages.common.rehaul.extension.ExtensionMetadataRegistry;
import com.enviouse.progressivestages.common.rehaul.client.PreparedClientSnapshot;
import com.enviouse.progressivestages.common.rehaul.client.ClientSnapshotCodec;
import com.enviouse.progressivestages.common.rehaul.lifecycle.TransitionHistoryEntry;
import com.enviouse.progressivestages.common.rehaul.schema.EditorFieldSchema;
import com.enviouse.progressivestages.common.rehaul.schema.EditorSchemaRegistry;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorMatch;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorMatcher;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorMatcherRegistry;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorTarget;
import com.enviouse.progressivestages.server.loader.StageFileLoader;
import com.enviouse.progressivestages.server.rehaul.RehaulRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ProgressiveStagesRehaulAPI {
    public static final int SCHEMA_VERSION = 4;
    public static final int API_VERSION = 1;

    private ProgressiveStagesRehaulAPI() {}

    public static CompiledSnapshot compiledSnapshot() {
        return StageFileLoader.getInstance().getCompiledSnapshot();
    }

    public static CatalogSnapshot catalogSnapshot() {
        return EditorCatalogService.get().snapshot();
    }

    public static CatalogPage searchCatalog(CatalogQuery query) {
        return EditorCatalogService.get().search(query);
    }

    public static void registerCatalogContributor(CatalogContributor contributor) {
        EditorCatalogService.get().register(contributor);
    }

    public static void registerCondition(ConditionProvider provider) {
        ConditionRegistry.get().register(provider);
    }

    public static void registerAction(ActionProvider provider) {
        ActionRegistry.get().register(provider);
    }

    public static void registerSelector(SelectorMatcher matcher) {
        SelectorMatcherRegistry.get().register(matcher);
    }

    public static void registerChallengeMeasure(ChallengeMeasureProvider provider) {
        ChallengeMeasureRegistry.get().register(provider);
    }

    public static void registerEditorField(EditorFieldSchema field) {
        EditorSchemaRegistry.get().register(field);
    }

    public static void registerExtensionMetadata(ExtensionMetadata metadata) {
        ExtensionMetadataRegistry.get().register(metadata);
    }

    public static ExtensionCatalogSnapshot extensionCatalog() {
        return ExtensionMetadataRegistry.get().snapshot();
    }

    public static Optional<SelectorSpec> parseSelector(String input) {
        return SelectorMatcherRegistry.get().parse(input);
    }

    public static SelectorMatch matchSelector(SelectorSpec selector, SelectorTarget target) {
        return SelectorMatcherRegistry.get().match(selector, target);
    }

    public static ConditionNode compileCondition(Object source) {
        return new ConditionCompiler(ConditionRegistry.get()).compile(source);
    }

    public static ConditionTrace evaluateCondition(ConditionNode condition, ConditionContext context) {
        return RehaulRuntime.get().conditionEvaluator().evaluate(condition, context);
    }

    public static Optional<DecisionTrace> decide(ServerPlayer player, String category, String action,
                                                  ResourceLocation target) {
        return RehaulRuntime.get().rules().resolve(player, category, action, target, null);
    }

    public static List<DecisionTrace> decisionHistory() {
        return RehaulRuntime.get().rules().history();
    }

    public static List<TransitionHistoryEntry> transitionHistory() {
        return RehaulRuntime.get().transitionHistory().entries();
    }

    public static List<Map<String, Object>> lifecycleProgress(ServerPlayer player, Set<String> eventInterests) {
        return RehaulRuntime.get().lifecycleProgress(player, eventInterests);
    }

    public static void armLifecycle(ServerPlayer player, ResourceLocation rule) {
        RehaulRuntime.get().armLifecycle(player, rule);
    }

    public static void resetLifecycle(ServerPlayer player, ResourceLocation rule) {
        RehaulRuntime.get().resetLifecycle(player, rule);
    }

    public static PreparedClientSnapshot clientSnapshot() {
        return ClientSnapshotCodec.prepare(compiledSnapshot(), 0);
    }

    public static List<ChallengeSessionView> challengeSessions(ServerPlayer player) {
        return RehaulRuntime.get().challenges().sessions(player.getUUID().toString());
    }

    public static boolean resetChallenge(ServerPlayer player, ResourceLocation challenge) {
        return RehaulRuntime.get().challenges().reset(player.getUUID().toString(), challenge);
    }

    public static Object getVariable(ServerPlayer player, ResourceLocation variable) {
        return RehaulRuntime.get().variables().get(player.getUUID().toString(), variable);
    }

    public static Object setVariable(ServerPlayer player, ResourceLocation variable, Object value) {
        return RehaulRuntime.get().variables().set(player.getUUID().toString(), variable, value);
    }

    public static double addVariable(ServerPlayer player, ResourceLocation variable, double amount) {
        return RehaulRuntime.get().variables().add(player.getUUID().toString(), variable, amount);
    }

    public static double evaluateFormula(String formula, Map<String, Double> values) {
        return RehaulRuntime.get().formulas().evaluate(formula, values);
    }

    public static Map<String, Object> expandTemplate(ResourceLocation template, Map<String, Object> arguments) {
        return RehaulRuntime.get().templates().expand(template, arguments);
    }

    public static List<EditorFieldSchema> editorSchemas() {
        return EditorSchemaRegistry.get().all();
    }

    public static Map<ResourceLocation, ConditionProvider> conditionProviders() {
        return ConditionRegistry.get().providers();
    }

    public static Map<ResourceLocation, ActionProvider> actionProviders() {
        return ActionRegistry.get().providers();
    }

    public static Map<ResourceLocation, SelectorMatcher> selectorMatchers() {
        return SelectorMatcherRegistry.get().matchers();
    }

    public static Set<ResourceLocation> capabilities() {
        return Set.of(
            id("schema_4"), id("packages"), id("priority_resolver"), id("condition_registry"),
            id("action_registry"), id("selector_registry"), id("challenge_budgets"),
            id("contextual_modifiers"), id("variables"), id("stage_states"), id("catalogs"),
            id("extension_metadata"), id("decision_traces"));
    }

    public static boolean hasCapability(ResourceLocation capability) {
        return capabilities().contains(capability);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressivestages", path);
    }
}
