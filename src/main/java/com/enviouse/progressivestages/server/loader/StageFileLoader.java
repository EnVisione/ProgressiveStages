package com.enviouse.progressivestages.server.loader;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.config.ConfigPaths;
import com.enviouse.progressivestages.common.lock.CategoryLocks;
import com.enviouse.progressivestages.common.lock.ConditionalRule;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.lock.PrefixEntry;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.common.tags.StageTagRegistry;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Loads stage definition files from the ProgressiveStages directory in config folder
 */
public class StageFileLoader {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<StageId, StageDefinition> loadedStages = new LinkedHashMap<>();
    /** v2.5: stages parsed from datapacks (data/&lt;ns&gt;/progressivestages/stages/*.toml). Config wins on id conflict. */
    private final Map<StageId, StageDefinition> datapackStages = new LinkedHashMap<>();
    private Path stagesDirectory;
    private MinecraftServer server;
    private boolean initialized = false;
    private List<String> lastReloadErrors = List.of();

    private static StageFileLoader INSTANCE;

    public static StageFileLoader getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new StageFileLoader();
        }
        return INSTANCE;
    }

    private StageFileLoader() {}

    /**
     * Initialize the loader and create default files if needed
     */
    public void initialize(MinecraftServer server) {
        // Integrated servers share one JVM. Always rebuild runtime registries so opening a second
        // world can never inherit definitions or locks from the previous world.
        clearRuntimeRegistries();
        this.server = server;
        ConfigPaths.prepareAndMigrate(LOGGER);
        stagesDirectory = ConfigPaths.stagesDirectory();

        // Create directory if it doesn't exist
        if (!Files.exists(stagesDirectory)) {
            try {
                Files.createDirectories(stagesDirectory);
                LOGGER.info("Created ProgressiveStages directory: {}", stagesDirectory);
            } catch (IOException e) {
                LOGGER.error("Failed to create ProgressiveStages directory", e);
            }
        }

        FileDiscovery discovery = discoverStageFiles();
        if (discovery.error() != null) {
            LOGGER.error("[ProgressiveStages] {}", discovery.error());
        } else if (discovery.files().isEmpty()) {
            LOGGER.info("No stage files found, generating defaults...");
            generateDefaultStageFiles();
        }

        LoadCandidate candidate = readCandidateStages();
        lastReloadErrors = List.copyOf(candidate.errors());
        for (String error : candidate.errors()) LOGGER.error("[ProgressiveStages] Stage load error: {}", error);
        if (candidate.errors().isEmpty()) {
            try {
                applyCandidate(candidate.stages());
            } catch (RuntimeException applicationFailure) {
                clearRuntimeRegistries();
                lastReloadErrors = List.of("Could not apply the initial stage snapshot. "
                    + applicationFailure.getMessage());
                LOGGER.error("[ProgressiveStages] Could not apply the initial stage snapshot", applicationFailure);
            }
        } else {
            LOGGER.error("[ProgressiveStages] No stage definitions were activated because the initial snapshot is invalid");
        }
        initialized = true;
    }

    private void clearRuntimeRegistries() {
        loadedStages.clear();
        LockRegistry.getInstance().clear();
        StageOrder.getInstance().clear();
        StageTagRegistry.clear();
        com.enviouse.progressivestages.server.triggers.StageTriggerEvaluator.resetRuntimeState();
        com.enviouse.progressivestages.server.enforcement.AbilityEnforcer.rebuild(java.util.List.of());
        com.enviouse.progressivestages.server.enforcement.ConditionalLockEngine.rebuild(java.util.List.of());
    }

    /** Release all world-specific state when a server stops. Event handlers remain registered once. */
    public void shutdown() {
        clearRuntimeRegistries();
        datapackStages.clear();
        stagesDirectory = null;
        server = null;
        initialized = false;
        lastReloadErrors = List.of();
    }

    /**
     * v2.5: receive the stages parsed from datapacks. If the loader is already initialized (config
     * stages loaded), trigger a full reload so the datapack set is merged in (config still wins on
     * id conflict). Called from the datapack reload listener.
     */
    public void setDatapackStages(Map<StageId, StageDefinition> stages) {
        Map<StageId, StageDefinition> previous = new LinkedHashMap<>(datapackStages);
        datapackStages.clear();
        if (stages != null) datapackStages.putAll(stages);
        LOGGER.info("[ProgressiveStages] Loaded {} datapack stage definition(s)", datapackStages.size());
        if (initialized) {
            if (reload()) {
                syncPlayersAfterReload();
            } else {
                datapackStages.clear();
                datapackStages.putAll(previous);
            }
        }
    }

    /**
     * Reload all stage files from disk
     */
    public boolean reload() {
        LoadCandidate candidate = readCandidateStages();
        if (!candidate.errors().isEmpty()) {
            lastReloadErrors = List.copyOf(candidate.errors());
            for (String error : candidate.errors()) LOGGER.error("[ProgressiveStages] Reload rejected: {}", error);
            LOGGER.error("[ProgressiveStages] Kept the previous stage snapshot because the candidate reload is invalid");
            return false;
        }

        com.enviouse.progressivestages.server.enforcement.OreSpoofManager.get().prepareForReload(server);
        Map<StageId, StageDefinition> previous = new LinkedHashMap<>(loadedStages);
        try {
            applyCandidate(candidate.stages());
        } catch (RuntimeException applicationFailure) {
            String error = "Could not apply the candidate stage snapshot. " + applicationFailure.getMessage();
            LOGGER.error("[ProgressiveStages] Reload rejected while applying the candidate snapshot", applicationFailure);
            try {
                applyCandidate(previous);
            } catch (RuntimeException rollbackFailure) {
                LOGGER.error("[ProgressiveStages] Failed to restore the previous stage snapshot", rollbackFailure);
                error += ". The previous snapshot could not be restored. " + rollbackFailure.getMessage();
            }
            lastReloadErrors = List.of(error);
            return false;
        }
        lastReloadErrors = List.of();
        LOGGER.info("Reloaded {} stages", loadedStages.size());
        return true;
    }

    private LoadCandidate readCandidateStages() {
        Map<StageId, StageDefinition> candidate = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        FileDiscovery discovery = discoverStageFiles();
        if (discovery.error() != null) errors.add(discovery.error());
        for (Path file : discovery.files()) {
            StageFileParser.ParseResult result = StageFileParser.parseWithErrors(file);
            if (!result.isSuccess()) {
                errors.add(relativeStagePath(file) + ". " + result.getErrorMessage());
                continue;
            }
            StageDefinition stage = result.getStageDefinition();
            StageDefinition existing = candidate.putIfAbsent(stage.getId(), stage);
            if (existing != null) {
                errors.add(relativeStagePath(file) + ". Duplicate stage ID. " + stage.getId());
            }
        }

        for (Map.Entry<StageId, StageDefinition> entry : datapackStages.entrySet()) {
            if (candidate.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                LOGGER.info("[ProgressiveStages] Datapack stage {} overridden by a config file", entry.getKey());
            }
        }

        errors.addAll(StageOrder.validateDefinitions(candidate.values()));
        return new LoadCandidate(Collections.unmodifiableMap(new LinkedHashMap<>(candidate)), List.copyOf(errors));
    }

    private void applyCandidate(Map<StageId, StageDefinition> candidate) {
        clearRuntimeRegistries();
        loadedStages.putAll(candidate);
        for (StageDefinition stage : loadedStages.values()) StageOrder.getInstance().registerStage(stage);
        registerLocksFromStages();
        com.enviouse.progressivestages.server.triggers.StageTriggerEvaluator.rebuild(loadedStages.values());
        com.enviouse.progressivestages.server.enforcement.AbilityEnforcer.rebuild(loadedStages.values());
        com.enviouse.progressivestages.server.enforcement.ConditionalLockEngine.rebuild(loadedStages.values());
        LOGGER.info("Loaded {} stage definitions", loadedStages.size());
    }

    private FileDiscovery discoverStageFiles() {
        if (stagesDirectory == null || !Files.isDirectory(stagesDirectory)) {
            return new FileDiscovery(List.of(), null);
        }
        try (Stream<Path> paths = Files.walk(stagesDirectory)) {
            return new FileDiscovery(paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".toml"))
                .filter(path -> !path.getFileName().toString().equalsIgnoreCase("triggers.toml"))
                .sorted(Comparator.comparing(this::relativeStagePath))
                .toList(), null);
        } catch (IOException e) {
            LOGGER.error("Failed to list stage files", e);
            return new FileDiscovery(List.of(), "Could not scan the stages directory. " + e.getMessage());
        }
    }

    private String relativeStagePath(Path file) {
        if (stagesDirectory == null) return file.toString();
        try {
            return stagesDirectory.relativize(file).toString();
        } catch (IllegalArgumentException ignored) {
            return file.toString();
        }
    }

    private record LoadCandidate(Map<StageId, StageDefinition> stages, List<String> errors) {}
    private record FileDiscovery(List<Path> files, String error) {}

    private void registerLocksFromStages() {
        LockRegistry registry = LockRegistry.getInstance();

        for (StageDefinition stage : loadedStages.values()) {
            registry.registerStage(stage);
        }

        StageTagRegistry.rebuildFromStages();

        LOGGER.debug("Registered locks from {} stages", loadedStages.size());
    }

    /**
     * Validation result for a single file
     */
    public static class FileValidationResult {
        public final String fileName;
        public final boolean success;
        public final boolean syntaxError;
        public final String errorMessage;
        public final List<String> invalidItems;

        public FileValidationResult(String fileName, boolean success, boolean syntaxError,
                                     String errorMessage, List<String> invalidItems) {
            this.fileName = fileName;
            this.success = success;
            this.syntaxError = syntaxError;
            this.errorMessage = errorMessage;
            this.invalidItems = invalidItems != null ? invalidItems : new ArrayList<>();
        }
    }

    public List<FileValidationResult> validateAllStages() {
        List<FileValidationResult> results = new ArrayList<>();

        if (stagesDirectory == null || !Files.exists(stagesDirectory)) {
            return results;
        }

        FileDiscovery discovery = discoverStageFiles();
        if (discovery.error() != null) {
            results.add(new FileValidationResult(stagesDirectory.toString(), false, false,
                discovery.error(), null));
            return results;
        }
        for (Path file : discovery.files()) results.add(validateStageFile(file));

        return results;
    }

    private FileValidationResult validateStageFile(Path file) {
        String fileName = relativeStagePath(file);

        StageFileParser.ParseResult parseResult = StageFileParser.parseWithErrors(file);

        if (!parseResult.isSuccess()) {
            return new FileValidationResult(
                fileName,
                false,
                parseResult.isSyntaxError(),
                parseResult.getErrorMessage(),
                null
            );
        }

        // Parse succeeded, now validate exact-ID entries across the 2.0 categories.
        // We only validate id: entries — mod/tag/name resolve at runtime and aren't
        // always present when loading (e.g. tags are datapack-driven).
        StageDefinition stage = parseResult.getStageDefinition();
        List<String> invalidItems = new ArrayList<>();

        var itemRegistry = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        var blockRegistry = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        var fluidRegistry = net.minecraft.core.registries.BuiltInRegistries.FLUID;
        var entityRegistry = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE;
        var locks = stage.getLocks();

        validateCategoryIds(locks.items(),    itemRegistry,   "Item",    invalidItems);
        validateCategoryIds(locks.blocks(),   blockRegistry,  "Block",   invalidItems);
        validateCategoryIds(locks.fluids(),   fluidRegistry,  "Fluid",   invalidItems);
        validateCategoryIds(locks.entities(), entityRegistry, "Entity",  invalidItems);
        validateCategoryIds(locks.crops(),    blockRegistry,  "Crop",    invalidItems);
        validateCategoryIds(locks.screens(),  blockRegistry,  "Screen",  invalidItems);
        validateCategoryIds(locks.loot(),     itemRegistry,   "Loot",    invalidItems);
        validateCategoryIds(locks.mobSpawns(),entityRegistry, "MobSpawn",invalidItems);
        validateCategoryIds(locks.petsTaming(),   entityRegistry, "PetTaming",   invalidItems);
        validateCategoryIds(locks.petsBreeding(), entityRegistry, "PetBreeding", invalidItems);
        validateCategoryIds(locks.recipeOutputs(), itemRegistry, "RecipeOutput", invalidItems);
        validateCategoryIds(locks.professions(),
            net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION, "Profession", invalidItems);
        validateCategoryIds(stage.getActiveLocks().items(), itemRegistry, "ActiveItem", invalidItems);

        // v2.5: dead trigger targets — a [[triggers]] condition whose (non-tag) subject id doesn't
        // resolve to a real entity/block/item/effect. Data-driven targets (advancement/dimension/
        // biome/structure/stat) and tags are skipped here since they aren't in the static registries.
        validateTriggerTargets(stage, invalidItems);
        validateConditionalTargets(stage, invalidItems);

        if (!invalidItems.isEmpty()) {
            return new FileValidationResult(
                fileName,
                false,
                false,
                "Contains " + invalidItems.size() + " invalid resource IDs",
                invalidItems
            );
        }

        return new FileValidationResult(fileName, true, false, null, null);
    }

    /**
     * v2.5: validate that each trigger condition's exact-id subject resolves to a real registry
     * entry. Only the four statically-resolvable kinds (entity / block / item / mob-effect) are
     * checked; tags ({@code #...}) and data-driven targets (advancement/dimension/biome/structure/
     * raw stat) are intentionally skipped because they aren't present in the built-in registries.
     */
    private static void validateTriggerTargets(StageDefinition stage,
                                               List<String> invalidItems) {
        if (!stage.hasTriggers()) return;
        var itemReg   = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        var blockReg  = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        var entityReg = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE;
        var effectReg = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT;
        for (var rule : stage.getTriggers()) {
            for (var c : rule.conditions()) {
                if (!c.targetBody().isEmpty() && !c.targetIsTag()) {
                    net.minecraft.core.Registry<?> reg = switch (c.type()) {
                        case KILL, KILL_WITH, TAME -> entityReg;
                        case MINE                  -> blockReg;
                        case CRAFT, PICKUP, USE, DROP, BREAK_ITEM, HAS_ITEM -> itemReg;
                        case EFFECT                -> effectReg;
                        default -> null;
                    };
                    if (reg != null) {
                        var id = net.minecraft.resources.ResourceLocation.tryParse(c.targetBody());
                        if (id == null || !reg.containsKey(id)) {
                            invalidItems.add("Trigger(" + c.type().name().toLowerCase(java.util.Locale.ROOT)
                                + "): unknown target " + c.targetBody());
                        }
                    }
                }
                // kill_with's held item is always an item id.
                if (c.type() == com.enviouse.progressivestages.common.trigger.TriggerConditionType.KILL_WITH
                        && !c.with().isEmpty() && !c.with().startsWith("#")) {
                    var id = net.minecraft.resources.ResourceLocation.tryParse(c.with());
                    if (id == null || !itemReg.containsKey(id)) {
                        invalidItems.add("Trigger(kill_with): unknown held item " + c.with());
                    }
                }
            }
        }
    }

    private static void validateConditionalTargets(StageDefinition stage,
                                                   List<String> invalidItems) {
        var itemReg = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        var blockReg = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        var fluidReg = net.minecraft.core.registries.BuiltInRegistries.FLUID;
        var entityReg = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE;
        var effectReg = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT;
        for (ConditionalRule rule : stage.getConditionalRules()) {
            for (ConditionalRule.TargetType type : rule.targets().types()) {
                net.minecraft.core.Registry<?> registry = switch (type) {
                    case ITEM -> itemReg;
                    case BLOCK -> blockReg;
                    case FLUID -> fluidReg;
                    case ENTITY -> entityReg;
                    default -> null;
                };
                if (registry == null) continue;
                validateConditionalEntries(rule, type, rule.targets().included(type), registry,
                    "target", invalidItems);
                validateConditionalEntries(rule, type, rule.targets().excluded(type), registry,
                    "exception", invalidItems);
            }
            validateConditionalEntries(rule, ConditionalRule.TargetType.ENTITY,
                rule.triggerEntities(), entityReg, "trigger entity", invalidItems);
            for (var effect : rule.context().effects()) {
                if (!effectReg.containsKey(effect)) {
                    invalidItems.add("ConditionalRule(" + rule.id() + ") effect: " + effect);
                }
            }
        }
    }

    private static void validateConditionalEntries(ConditionalRule rule,
                                                    ConditionalRule.TargetType type,
                                                    List<PrefixEntry> entries,
                                                    net.minecraft.core.Registry<?> registry,
                                                    String role,
                                                    List<String> invalidItems) {
        for (PrefixEntry entry : entries) {
            if (entry.kind() != PrefixEntry.Kind.ID || entry.id() == null) continue;
            if (!registry.containsKey(entry.id())) {
                invalidItems.add("ConditionalRule(" + rule.id() + ") " + role + " "
                    + type.name().toLowerCase(java.util.Locale.ROOT) + ": " + entry.raw());
            }
        }
    }

    /**
     * Check each {@code id:} entry in a category against a registry and append a
     * descriptive message to {@code invalidItems} for anything that isn't present.
     */
    private static void validateCategoryIds(CategoryLocks category,
                                            net.minecraft.core.Registry<?> registry,
                                            String label,
                                            List<String> invalidItems) {
        for (PrefixEntry entry : category.locked()) {
            if (entry.kind() != PrefixEntry.Kind.ID || entry.id() == null) continue;
            if (!registry.containsKey(entry.id())) {
                invalidItems.add(label + ": " + entry.raw());
            }
        }
    }

    public Path getStagesDirectory() {
        return stagesDirectory;
    }

    public int countStageFiles() {
        FileDiscovery discovery = discoverStageFiles();
        return discovery.error() == null ? discovery.files().size() : 0;
    }

    public Optional<StageDefinition> getStage(StageId id) {
        return Optional.ofNullable(loadedStages.get(id));
    }

    public Collection<StageDefinition> getAllStages() {
        return List.copyOf(loadedStages.values());
    }

    public Set<StageId> getAllStageIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(loadedStages.keySet()));
    }

    public List<String> getLastReloadErrors() {
        return lastReloadErrors;
    }

    public int syncPlayersAfterReload() {
        if (server == null) return 0;
        int count = 0;
        for (var player : server.getPlayerList().getPlayers()) {
            com.enviouse.progressivestages.common.api.ProgressiveStagesAPI.syncPlayer(player);
            com.enviouse.progressivestages.server.enforcement.StageAttributeApplier.reconcile(player);
            com.enviouse.progressivestages.server.enforcement.AdvancementHider.resyncIfNeeded(player);
            com.enviouse.progressivestages.common.stage.StageManager.getInstance().fireBulkChangedEvent(
                player, com.enviouse.progressivestages.common.api.StagesBulkChangedEvent.Reason.RELOAD);
            count++;
        }
        return count;
    }

    // ============================================================================
    // Default 2.0 stage file templates
    // ============================================================================

    private void generateDefaultStageFiles() {
        generateStoneAgeFile();
        generateIronAgeFile();
        generateDiamondAgeFile();
    }

    private void generateStoneAgeFile() {
        writeStageFile("stone_age.toml", DefaultStageTemplates.stoneAge());
    }

    private void generateIronAgeFile() {
        writeStageFile("iron_age.toml", DefaultStageTemplates.ironAge());
    }

    private void generateDiamondAgeFile() {
        writeStageFile("diamond_age.toml", DefaultStageTemplates.diamondAge());
    }
    private void writeStageFile(String fileName, String content) {
        Path filePath = stagesDirectory.resolve(fileName);
        try {
            Files.writeString(filePath, content);
            LOGGER.info("Generated default stage file: {}", fileName);
        } catch (IOException e) {
            LOGGER.error("Failed to write stage file: {}", fileName, e);
        }
    }
}
