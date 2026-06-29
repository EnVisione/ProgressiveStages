package com.enviouse.progressivestages.server.loader;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.lock.CategoryLocks;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.lock.PrefixEntry;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.common.tags.StageTagRegistry;
import com.enviouse.progressivestages.common.util.Constants;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Loads stage definition files from the ProgressiveStages directory in config folder
 */
public class StageFileLoader {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<StageId, StageDefinition> loadedStages = new LinkedHashMap<>();
    /** v2.5: stages parsed from datapacks (data/&lt;ns&gt;/progressivestages/stages/*.toml). Config wins on id conflict. */
    private final Map<StageId, StageDefinition> datapackStages = new LinkedHashMap<>();
    private Path stagesDirectory;
    private boolean initialized = false;

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
        // Get the config folder path
        Path configFolder = FMLPaths.CONFIGDIR.get();
        stagesDirectory = configFolder.resolve(Constants.STAGE_FILES_DIRECTORY);

        // Create directory if it doesn't exist
        if (!Files.exists(stagesDirectory)) {
            try {
                Files.createDirectories(stagesDirectory);
                LOGGER.info("Created ProgressiveStages directory: {}", stagesDirectory);
            } catch (IOException e) {
                LOGGER.error("Failed to create ProgressiveStages directory", e);
            }
        }

        // Generate default stage files if none exist
        if (countStageFiles() == 0) {
            LOGGER.info("No stage files found, generating defaults...");
            generateDefaultStageFiles();
        }

        // Load all stage files
        loadAllStages();

        // Register with lock registry
        registerLocksFromStages();

        // v2.3: build the per-stage trigger registry from the loaded definitions
        com.enviouse.progressivestages.server.triggers.StageTriggerEvaluator.rebuild(loadedStages.values());
        com.enviouse.progressivestages.server.enforcement.AbilityEnforcer.rebuild(loadedStages.values());
        initialized = true;
    }

    /**
     * v2.5: receive the stages parsed from datapacks. If the loader is already initialized (config
     * stages loaded), trigger a full reload so the datapack set is merged in (config still wins on
     * id conflict). Called from the datapack reload listener.
     */
    public void setDatapackStages(Map<StageId, StageDefinition> stages) {
        datapackStages.clear();
        if (stages != null) datapackStages.putAll(stages);
        LOGGER.info("[ProgressiveStages] Loaded {} datapack stage definition(s)", datapackStages.size());
        if (initialized) reload();
    }

    /**
     * Reload all stage files from disk
     */
    public void reload() {
        loadedStages.clear();
        LockRegistry.getInstance().clear();
        StageOrder.getInstance().clear();
        StageTagRegistry.clear();

        loadAllStages();
        registerLocksFromStages();

        // v2.3: rebuild the per-stage trigger registry from the reloaded definitions
        com.enviouse.progressivestages.server.triggers.StageTriggerEvaluator.rebuild(loadedStages.values());
        com.enviouse.progressivestages.server.enforcement.AbilityEnforcer.rebuild(loadedStages.values());

        LOGGER.info("Reloaded {} stages", loadedStages.size());
    }

    /**
     * Load all .toml files from the stages directory
     */
    private void loadAllStages() {
        // Config files load first so they win on id conflict with datapack stages.
        if (stagesDirectory != null && Files.exists(stagesDirectory)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(stagesDirectory, "*.toml")) {
                for (Path file : stream) {
                    String fileName = file.getFileName().toString().toLowerCase();
                    if (fileName.equals("triggers.toml")) {
                        LOGGER.debug("Skipping triggers.toml - not a stage definition file");
                        continue;
                    }
                    loadStageFile(file);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to list stage files", e);
            }
        } else {
            LOGGER.warn("Stages directory not found");
        }

        // v2.5: merge datapack-provided stages for any id a config file didn't already define.
        for (Map.Entry<StageId, StageDefinition> e : datapackStages.entrySet()) {
            if (loadedStages.putIfAbsent(e.getKey(), e.getValue()) != null) {
                LOGGER.info("[ProgressiveStages] Datapack stage {} overridden by a config file", e.getKey());
            } else {
                LOGGER.debug("Loaded datapack stage: {}", e.getKey());
            }
        }

        for (StageDefinition stage : loadedStages.values()) {
            StageOrder.getInstance().registerStage(stage);
        }

        List<String> validationErrors = StageOrder.getInstance().validateDependencies();
        for (String error : validationErrors) {
            LOGGER.error("[ProgressiveStages] Dependency validation error: {}", error);
        }

        LOGGER.info("Loaded {} stage definitions", loadedStages.size());
    }

    private void loadStageFile(Path file) {
        Optional<StageDefinition> stageOpt = StageFileParser.parse(file);

        if (stageOpt.isPresent()) {
            StageDefinition stage = stageOpt.get();

            if (loadedStages.containsKey(stage.getId())) {
                LOGGER.warn("Duplicate stage ID: {} in file {}", stage.getId(), file);
                return;
            }

            loadedStages.put(stage.getId(), stage);
            LOGGER.debug("Loaded stage: {} with {} dependencies", stage.getId(), stage.getDependencies().size());
        } else {
            LOGGER.warn("Failed to parse stage file: {}", file);
        }
    }

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

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(stagesDirectory, "*.toml")) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString().toLowerCase();
                if (fileName.equals("triggers.toml")) {
                    continue;
                }
                results.add(validateStageFile(file));
            }
        } catch (IOException e) {
            LOGGER.error("Failed to list stage files for validation", e);
        }

        return results;
    }

    private FileValidationResult validateStageFile(Path file) {
        String fileName = file.getFileName().toString();

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

        // v2.5: dead trigger targets — a [[triggers]] condition whose (non-tag) subject id doesn't
        // resolve to a real entity/block/item/effect. Data-driven targets (advancement/dimension/
        // biome/structure/stat) and tags are skipped here since they aren't in the static registries.
        validateTriggerTargets(stage, invalidItems);

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
                            invalidItems.add("Trigger(" + c.type().name().toLowerCase()
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
        if (stagesDirectory == null || !Files.exists(stagesDirectory)) {
            return 0;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(stagesDirectory, "*.toml")) {
            int count = 0;
            for (Path file : stream) {
                String fileName = file.getFileName().toString().toLowerCase();
                if (!fileName.equals("triggers.toml")) {
                    count++;
                }
            }
            return count;
        } catch (IOException e) {
            return 0;
        }
    }

    public Optional<StageDefinition> getStage(StageId id) {
        return Optional.ofNullable(loadedStages.get(id));
    }

    public Collection<StageDefinition> getAllStages() {
        return Collections.unmodifiableCollection(loadedStages.values());
    }

    public Set<StageId> getAllStageIds() {
        return Collections.unmodifiableSet(loadedStages.keySet());
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
