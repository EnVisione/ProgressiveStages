package com.enviouse.progressivestages.server.loader;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.lock.LockDefinition;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses stage definition files from TOML format
 */
public class StageFileParser {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TomlParser PARSER = new TomlParser();

    /**
     * Result of parsing a stage file
     */
    public static class ParseResult {
        private final StageDefinition stageDefinition;
        private final String errorMessage;
        private final boolean syntaxError;

        private ParseResult(StageDefinition def, String error, boolean syntax) {
            this.stageDefinition = def;
            this.errorMessage = error;
            this.syntaxError = syntax;
        }

        public static ParseResult success(StageDefinition def) {
            return new ParseResult(def, null, false);
        }

        public static ParseResult syntaxError(String message) {
            return new ParseResult(null, message, true);
        }

        public static ParseResult validationError(String message) {
            return new ParseResult(null, message, false);
        }

        public boolean isSuccess() { return stageDefinition != null; }
        public boolean isSyntaxError() { return syntaxError; }
        public StageDefinition getStageDefinition() { return stageDefinition; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * Parse a stage file with detailed error reporting
     */
    public static ParseResult parseWithErrors(Path filePath) {
        if (!Files.exists(filePath)) {
            return ParseResult.validationError("File does not exist");
        }

        Config config;
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            config = PARSER.parse(reader);
        } catch (IOException e) {
            return ParseResult.validationError("Failed to read file: " + e.getMessage());
        } catch (com.electronwill.nightconfig.core.io.ParsingException e) {
            // TOML syntax error
            String errorMsg = e.getMessage();
            if (errorMsg == null) errorMsg = e.getClass().getSimpleName();
            return ParseResult.syntaxError("TOML syntax error: " + errorMsg);
        } catch (Exception e) {
            return ParseResult.syntaxError("Parse error: " + e.getMessage());
        }

        return parseConfigWithErrors(config, filePath.getFileName().toString());
    }

    private static ParseResult parseConfigWithErrors(Config config, String fileName) {
        // Get stage section
        Config stageSection = config.get("stage");
        if (stageSection == null) {
            return ParseResult.validationError("Missing [stage] section");
        }

        // Parse required fields
        String id = stageSection.get("id");
        if (id == null || id.isEmpty()) {
            id = fileName.endsWith(".toml") ? fileName.substring(0, fileName.length() - 5) : fileName;
        }

        StageId stageId = StageId.of(id);

        // Parse optional fields
        String displayName = stageSection.getOrElse("display_name", id);
        String description = stageSection.getOrElse("description", "");
        int order = stageSection.getIntOrElse("order", 0);
        String icon = stageSection.get("icon");
        String unlockMessage = stageSection.get("unlock_message");

        // Parse locks section
        LockDefinition locks = parseLocks(config);

        // Build stage definition
        StageDefinition.Builder builder = StageDefinition.builder(stageId)
            .displayName(displayName)
            .description(description)
            .order(order)
            .locks(locks);

        if (icon != null && !icon.isEmpty()) {
            builder.icon(icon);
        }

        if (unlockMessage != null && !unlockMessage.isEmpty()) {
            builder.unlockMessage(unlockMessage);
        }

        return ParseResult.success(builder.build());
    }

    /**
     * Parse a stage file and return the StageDefinition
     */
    public static Optional<StageDefinition> parse(Path filePath) {
        try {
            if (!Files.exists(filePath)) {
                LOGGER.warn("Stage file does not exist: {}", filePath);
                return Optional.empty();
            }

            Config config;
            try (BufferedReader reader = Files.newBufferedReader(filePath)) {
                config = PARSER.parse(reader);
            }

            return parseConfig(config, filePath.getFileName().toString());

        } catch (IOException e) {
            LOGGER.error("Failed to read stage file: {}", filePath, e);
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.error("Failed to parse stage file: {}", filePath, e);
            return Optional.empty();
        }
    }

    private static Optional<StageDefinition> parseConfig(Config config, String fileName) {
        // Get stage section
        Config stageSection = config.get("stage");
        if (stageSection == null) {
            LOGGER.error("Stage file missing [stage] section: {}", fileName);
            return Optional.empty();
        }

        // Parse required fields
        String id = stageSection.get("id");
        if (id == null || id.isEmpty()) {
            // Use filename without .toml as ID
            id = fileName.endsWith(".toml") ? fileName.substring(0, fileName.length() - 5) : fileName;
        }

        StageId stageId = StageId.of(id);

        // Parse optional fields
        String displayName = stageSection.getOrElse("display_name", id);
        String description = stageSection.getOrElse("description", "");
        int order = stageSection.getIntOrElse("order", 0);
        String icon = stageSection.get("icon");
        String unlockMessage = stageSection.get("unlock_message");

        // Parse locks section
        LockDefinition locks = parseLocks(config);

        // Build stage definition
        StageDefinition.Builder builder = StageDefinition.builder(stageId)
            .displayName(displayName)
            .description(description)
            .order(order)
            .locks(locks);

        if (icon != null && !icon.isEmpty()) {
            builder.icon(icon);
        }

        if (unlockMessage != null && !unlockMessage.isEmpty()) {
            builder.unlockMessage(unlockMessage);
        }

        return Optional.of(builder.build());
    }

    private static LockDefinition parseLocks(Config config) {
        Config locksSection = config.get("locks");
        if (locksSection == null) {
            return LockDefinition.empty();
        }

        LockDefinition.Builder builder = LockDefinition.builder();

        // Parse simple lists
        builder.items(getStringList(locksSection, "items"));
        builder.itemTags(getStringList(locksSection, "item_tags"));
        builder.recipes(getStringList(locksSection, "recipes"));
        builder.recipeTags(getStringList(locksSection, "recipe_tags"));
        builder.blocks(getStringList(locksSection, "blocks"));
        builder.blockTags(getStringList(locksSection, "block_tags"));
        builder.dimensions(getStringList(locksSection, "dimensions"));
        builder.mods(getStringList(locksSection, "mods"));
        builder.names(getStringList(locksSection, "names"));

        // Parse interactions (array of tables)
        List<Config> interactionConfigs = locksSection.get("interactions");
        if (interactionConfigs != null) {
            List<LockDefinition.InteractionLock> interactions = new ArrayList<>();
            for (Config interactionConfig : interactionConfigs) {
                String type = interactionConfig.getOrElse("type", "item_on_block");
                String heldItem = interactionConfig.get("held_item");
                String targetBlock = interactionConfig.get("target_block");
                String description = interactionConfig.get("description");

                interactions.add(new LockDefinition.InteractionLock(type, heldItem, targetBlock, description));
            }
            builder.interactions(interactions);
        }

        return builder.build();
    }

    private static List<String> getStringList(Config config, String key) {
        List<String> result = config.get(key);
        return result != null ? result : new ArrayList<>();
    }
}
