package com.enviouse.progressivestages.server.loader;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.lock.CategoryLocks;
import com.enviouse.progressivestages.common.lock.LockDefinition;
import com.enviouse.progressivestages.common.lock.PrefixEntry;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Parses 2.0 stage definition files.
 *
 * <p>New-in-2.0 TOML shape (unified prefix system):
 * <pre>
 * [stage] id/display_name/description/icon/unlock_message/dependency
 *
 * [items]         locked, always_unlocked
 * [blocks]        locked, always_unlocked
 * [fluids]        locked, always_unlocked
 * [entities]      locked, always_unlocked
 * [enchants]      locked
 * [crops]         locked, always_unlocked
 * [screens]       locked
 * [loot]          locked
 * [pets]          locked_taming, locked_breeding
 * [mobs]          locked_spawns
 *   [[mobs.replacements]]   target, replace_with
 * [recipes]       locked_ids, locked_items
 * [dimensions]    locked
 * [[interactions]] type, held_item, target_block | target_entity, description
 * [[regions]]     dimension, pos1, pos2, prevent_entry, ...
 * [structures]    locked_entry
 *   [structures.rules]      prevent_block_break, ... disable_mob_spawning
 * [[ores.overrides]]        target, display_as, drop_as   (parsed, enforcement deferred)
 *
 * [enforcement]   allowed_use, allowed_pickup, allowed_hotbar,
 *                 allowed_mouse_pickup, allowed_inventory
 * </pre>
 */
public final class StageFileParser {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TomlParser PARSER = new TomlParser();

    private StageFileParser() {}

    // -------------------- public entry points --------------------

    public static Optional<StageDefinition> parse(Path filePath) {
        ParseResult result = parseWithErrors(filePath);
        if (!result.isSuccess()) {
            LOGGER.warn("Failed to parse stage file {}: {}", filePath, result.getErrorMessage());
            return Optional.empty();
        }
        return Optional.of(result.getStageDefinition());
    }

    public static ParseResult parseWithErrors(Path filePath) {
        if (!Files.exists(filePath)) return ParseResult.validationError("File does not exist");

        Config config;
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            config = PARSER.parse(reader);
        } catch (IOException e) {
            return ParseResult.validationError("Failed to read file: " + e.getMessage());
        } catch (com.electronwill.nightconfig.core.io.ParsingException e) {
            String msg = e.getMessage();
            if (msg == null) msg = e.getClass().getSimpleName();
            return ParseResult.syntaxError("TOML syntax error: " + msg);
        } catch (Exception e) {
            return ParseResult.syntaxError("Parse error: " + e.getMessage());
        }
        return parseConfig(config, filePath.getFileName().toString());
    }

    private static ParseResult parseConfig(Config config, String fileName) {
        Config stageSection = config.get("stage");
        if (stageSection == null) return ParseResult.validationError("Missing [stage] section");

        String id = stageSection.get("id");
        if (id == null || id.isEmpty()) {
            id = fileName.endsWith(".toml") ? fileName.substring(0, fileName.length() - 5) : fileName;
        }
        StageId stageId = StageId.of(id);

        String displayName = stageSection.getOrElse("display_name", id);
        String description = stageSection.getOrElse("description", "");
        String icon = stageSection.get("icon");
        String unlockMessage = stageSection.get("unlock_message");
        List<StageId> dependencies = parseDependencies(stageSection);

        LockDefinition locks = parseLocks(config);
        if (locks.minecraftNamespace()) {
            LOGGER.debug("Stage {} declares minecraft=true (gates minecraft namespace)", stageId);
        }

        StageDefinition.Builder builder = StageDefinition.builder(stageId)
            .displayName(displayName)
            .description(description)
            .dependencies(dependencies)
            .locks(locks);
        if (icon != null && !icon.isEmpty()) builder.icon(icon);
        if (unlockMessage != null && !unlockMessage.isEmpty()) builder.unlockMessage(unlockMessage);

        return ParseResult.success(builder.build());
    }

    // -------------------- [stage].dependency --------------------

    private static List<StageId> parseDependencies(Config stageSection) {
        List<StageId> out = new ArrayList<>();
        Object value = stageSection.get("dependency");
        if (value == null) return out;

        if (value instanceof String s) {
            addDependency(out, s);
        } else if (value instanceof List<?> list) {
            for (Object o : list) if (o instanceof String s) addDependency(out, s);
        }
        return out;
    }

    private static void addDependency(List<StageId> out, String s) {
        if (s.isEmpty()) return;
        try {
            out.add(StageId.parse(s));
        } catch (Exception e) {
            LOGGER.warn("Invalid dependency ID: {}", s);
        }
    }

    // -------------------- locks --------------------

    private static LockDefinition parseLocks(Config config) {
        LockDefinition.Builder b = LockDefinition.builder();

        b.items(        parseCategory(config, "items"));
        b.blocks(       parseCategory(config, "blocks"));
        b.fluids(       parseCategory(config, "fluids"));
        b.entities(     parseCategory(config, "entities"));
        b.enchants(     parseCategory(config, "enchants"));
        b.crops(        parseCategory(config, "crops"));
        b.screens(      parseCategory(config, "screens"));
        b.loot(         parseCategory(config, "loot"));
        b.mobSpawns(    parseCategoryField(config, "mobs", "locked_spawns"));

        // pets: two named lists in the same table
        Config petsSection = config.get("pets");
        b.petsTaming(petsSection == null ? CategoryLocks.EMPTY
            : CategoryLocks.builder().addLocked(stringList(petsSection, "locked_taming")).build());
        b.petsBreeding(petsSection == null ? CategoryLocks.EMPTY
            : CategoryLocks.builder().addLocked(stringList(petsSection, "locked_breeding")).build());
        b.petsCommanding(petsSection == null ? CategoryLocks.EMPTY
            : CategoryLocks.builder().addLocked(stringList(petsSection, "locked_commanding")).build());

        // [curios].locked_slots — slot identifiers (plain strings, not prefix entries)
        Config curiosSection = config.get("curios");
        if (curiosSection != null) {
            b.curioLockedSlots(stringList(curiosSection, "locked_slots"));
        }

        // recipes: two named lists
        Config recipesSection = config.get("recipes");
        b.recipeIds(recipesSection == null ? CategoryLocks.EMPTY
            : CategoryLocks.builder().addLocked(stringList(recipesSection, "locked_ids")).build());
        b.recipeOutputs(recipesSection == null ? CategoryLocks.EMPTY
            : CategoryLocks.builder().addLocked(stringList(recipesSection, "locked_items")).build());

        b.lockedDimensions(parseLockedDimensions(config));
        b.interactions(parseInteractions(config));
        b.mobReplacements(parseMobReplacements(config));
        b.regions(parseRegions(config));
        b.structures(parseStructures(config));
        b.oreOverrides(parseOreOverrides(config));

        parseEnforcement(config, b);

        // v2.0: minecraft=true shorthand. Root-level OR [locks].minecraft.
        boolean rootMinecraft = readBool(config, "minecraft");
        Config locksTable = config.get("locks");
        boolean locksMinecraft = readBool(locksTable, "minecraft");
        if (rootMinecraft || locksMinecraft) {
            b.minecraftNamespace(true);
        }

        // v2.0: [unlocks] table — per-stage carve-outs (see Phase C).
        b.unlocks(parseUnlocks(config));

        return b.build();
    }

    /** Read a boolean from a Config, returning false if section is null or missing. */
    private static boolean readBool(Config c, String key) {
        if (c == null) return false;
        Object v = c.get(key);
        return v instanceof Boolean && (Boolean) v;
    }

    /** Parse the optional [unlocks] table, mirror shape of locks: items, mods, fluids, dimensions, entities. */
    private static LockDefinition.UnlockGateLists parseUnlocks(Config config) {
        Config sec = config.get("unlocks");
        if (sec == null) return LockDefinition.UnlockGateLists.EMPTY;
        java.util.Set<ResourceLocation> items = parseRlSet(sec, "items");
        java.util.Set<String> mods = parseModSet(sec, "mods");
        java.util.Set<ResourceLocation> fluids = parseRlSet(sec, "fluids");
        java.util.Set<ResourceLocation> dims = parseRlSet(sec, "dimensions");
        java.util.Set<ResourceLocation> ents = parseRlSet(sec, "entities");
        return new LockDefinition.UnlockGateLists(items, mods, fluids, dims, ents);
    }

    private static java.util.Set<ResourceLocation> parseRlSet(Config sec, String field) {
        List<String> raw = stringList(sec, field);
        if (raw.isEmpty()) return java.util.Collections.emptySet();
        java.util.Set<ResourceLocation> out = new java.util.LinkedHashSet<>();
        for (String s : raw) {
            try {
                // Accept "id:..." prefix (skip) or bare "ns:path"
                String body = s.startsWith("id:") ? s.substring(3) : s;
                out.add(ResourceLocation.parse(body));
            } catch (Exception ignored) {}
        }
        return out;
    }

    private static java.util.Set<String> parseModSet(Config sec, String field) {
        List<String> raw = stringList(sec, field);
        if (raw.isEmpty()) return java.util.Collections.emptySet();
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (String s : raw) {
            String body = s.startsWith("mod:") ? s.substring(4) : s;
            if (!body.isEmpty()) out.add(body.toLowerCase());
        }
        return out;
    }

    private static CategoryLocks parseCategory(Config config, String sectionName) {
        Config section = config.get(sectionName);
        if (section == null) return CategoryLocks.EMPTY;
        return CategoryLocks.builder()
            .addLocked(stringList(section, "locked"))
            .addAlwaysUnlocked(stringList(section, "always_unlocked"))
            .build();
    }

    /** Build a CategoryLocks from a single named field inside a section (no always_unlocked). */
    private static CategoryLocks parseCategoryField(Config config, String sectionName, String fieldName) {
        Config section = config.get(sectionName);
        if (section == null) return CategoryLocks.EMPTY;
        return CategoryLocks.builder().addLocked(stringList(section, fieldName)).build();
    }

    private static List<ResourceLocation> parseLockedDimensions(Config config) {
        Config section = config.get("dimensions");
        if (section == null) return Collections.emptyList();
        List<ResourceLocation> out = new ArrayList<>();
        for (String raw : stringList(section, "locked")) {
            PrefixEntry entry = PrefixEntry.parse(raw);
            if (entry != null && entry.kind() == PrefixEntry.Kind.ID && entry.id() != null) {
                out.add(entry.id());
            } else {
                LOGGER.warn("Invalid dimension ID (must be exact, no mod:/tag:/name:): {}", raw);
            }
        }
        return out;
    }

    private static List<LockDefinition.InteractionLock> parseInteractions(Config config) {
        List<Config> entries = config.get("interactions");
        if (entries == null) return Collections.emptyList();
        List<LockDefinition.InteractionLock> out = new ArrayList<>();
        for (Config c : entries) {
            String type = c.getOrElse("type", "item_on_block");
            String heldItem = c.get("held_item");
            String description = c.get("description");
            String target = "item_on_entity".equals(type) ? c.get("target_entity") : c.get("target_block");
            out.add(new LockDefinition.InteractionLock(type, heldItem, target, description));
        }
        return out;
    }

    private static List<LockDefinition.MobReplacement> parseMobReplacements(Config config) {
        Config mobsSection = config.get("mobs");
        if (mobsSection == null) return Collections.emptyList();
        List<Config> entries = mobsSection.get("replacements");
        if (entries == null) return Collections.emptyList();

        List<LockDefinition.MobReplacement> out = new ArrayList<>();
        for (Config c : entries) {
            String targetRaw = c.get("target");
            String replaceRaw = c.get("replace_with");
            PrefixEntry target = PrefixEntry.parse(targetRaw);
            PrefixEntry replace = PrefixEntry.parse(replaceRaw);
            if (target == null || replace == null || replace.kind() != PrefixEntry.Kind.ID || replace.id() == null) {
                LOGGER.warn("Invalid [[mobs.replacements]] entry — target={}, replace_with={}", targetRaw, replaceRaw);
                continue;
            }
            out.add(new LockDefinition.MobReplacement(target, replace.id()));
        }
        return out;
    }

    private static List<LockDefinition.RegionLock> parseRegions(Config config) {
        List<Config> entries = config.get("regions");
        if (entries == null) return Collections.emptyList();

        List<LockDefinition.RegionLock> out = new ArrayList<>();
        for (Config c : entries) {
            String dimRaw = c.get("dimension");
            int[] pos1 = parsePos(c, "pos1");
            int[] pos2 = parsePos(c, "pos2");
            if (dimRaw == null || pos1 == null || pos2 == null) {
                LOGGER.warn("Invalid [[regions]] entry (missing dimension/pos1/pos2)");
                continue;
            }
            ResourceLocation dim;
            try {
                dim = ResourceLocation.parse(dimRaw);
            } catch (Exception e) {
                LOGGER.warn("Invalid [[regions]] dimension: {}", dimRaw);
                continue;
            }
            out.add(new LockDefinition.RegionLock(
                dim, pos1, pos2,
                c.getOrElse("prevent_entry", false),
                c.getOrElse("prevent_block_break", false),
                c.getOrElse("prevent_block_place", false),
                c.getOrElse("prevent_explosions", false),
                c.getOrElse("disable_mob_spawning", false)
            ));
        }
        return out;
    }

    private static int[] parsePos(Config c, String key) {
        List<?> raw = c.get(key);
        if (raw == null || raw.size() != 3) return null;
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            Object o = raw.get(i);
            if (!(o instanceof Number n)) return null;
            out[i] = n.intValue();
        }
        return out;
    }

    private static LockDefinition.StructureRules parseStructures(Config config) {
        Config section = config.get("structures");
        if (section == null) return LockDefinition.StructureRules.EMPTY;
        CategoryLocks lockedEntry = CategoryLocks.builder()
            .addLocked(stringList(section, "locked_entry"))
            .build();
        Config rules = section.get("rules");
        boolean pbb = rules != null && rules.getOrElse("prevent_block_break", false);
        boolean pbp = rules != null && rules.getOrElse("prevent_block_place", false);
        boolean pex = rules != null && rules.getOrElse("prevent_explosions", false);
        boolean dms = rules != null && rules.getOrElse("disable_mob_spawning", false);
        return new LockDefinition.StructureRules(lockedEntry, pbb, pbp, pex, dms);
    }

    private static List<LockDefinition.OreOverride> parseOreOverrides(Config config) {
        Config oresSection = config.get("ores");
        if (oresSection == null) return Collections.emptyList();
        List<Config> entries = oresSection.get("overrides");
        if (entries == null) return Collections.emptyList();

        List<LockDefinition.OreOverride> out = new ArrayList<>();
        for (Config c : entries) {
            ResourceLocation target = parseExactId(c.get("target"));
            ResourceLocation display = parseExactId(c.get("display_as"));
            ResourceLocation drop = parseExactId(c.get("drop_as"));
            if (target == null || display == null || drop == null) {
                LOGGER.warn("Invalid [[ores.overrides]] entry — target/display_as/drop_as must all be exact IDs");
                continue;
            }
            out.add(new LockDefinition.OreOverride(target, display, drop));
        }
        return out;
    }

    private static ResourceLocation parseExactId(String raw) {
        PrefixEntry e = PrefixEntry.parse(raw);
        return (e != null && e.kind() == PrefixEntry.Kind.ID) ? e.id() : null;
    }

    private static void parseEnforcement(Config config, LockDefinition.Builder b) {
        Config section = config.get("enforcement");
        if (section == null) return;
        b.allowedUse(stringList(section, "allowed_use"));
        b.allowedPickup(stringList(section, "allowed_pickup"));
        b.allowedHotbar(stringList(section, "allowed_hotbar"));
        b.allowedMousePickup(stringList(section, "allowed_mouse_pickup"));
        b.allowedInventory(stringList(section, "allowed_inventory"));
    }

    // -------------------- helpers --------------------

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Config config, String key) {
        Object v = config.get(key);
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) if (o instanceof String s) out.add(s);
            return out;
        }
        return Collections.emptyList();
    }

    // -------------------- ParseResult --------------------

    public static final class ParseResult {
        private final StageDefinition stageDefinition;
        private final String errorMessage;
        private final boolean syntaxError;

        private ParseResult(StageDefinition def, String error, boolean syntax) {
            this.stageDefinition = def;
            this.errorMessage = error;
            this.syntaxError = syntax;
        }

        public static ParseResult success(StageDefinition def)     { return new ParseResult(def, null, false); }
        public static ParseResult syntaxError(String msg)          { return new ParseResult(null, msg, true); }
        public static ParseResult validationError(String msg)      { return new ParseResult(null, msg, false); }

        public boolean isSuccess()              { return stageDefinition != null; }
        public boolean isSyntaxError()          { return syntaxError; }
        public StageDefinition getStageDefinition() { return stageDefinition; }
        public String getErrorMessage()         { return errorMessage; }
    }
}
