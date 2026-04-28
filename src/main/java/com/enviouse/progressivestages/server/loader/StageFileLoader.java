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
    private Path stagesDirectory;

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

        LOGGER.info("Reloaded {} stages", loadedStages.size());
    }

    /**
     * Load all .toml files from the stages directory
     */
    private void loadAllStages() {
        if (stagesDirectory == null || !Files.exists(stagesDirectory)) {
            LOGGER.warn("Stages directory not found");
            return;
        }

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
