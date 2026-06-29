package com.enviouse.progressivestages.server.loader;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.RevokeRule;
import com.enviouse.progressivestages.common.config.StageAttribute;
import com.enviouse.progressivestages.common.config.StageCost;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.config.UnlockEffects;
import com.enviouse.progressivestages.common.lock.CategoryLocks;
import com.enviouse.progressivestages.common.lock.EnforcementCategory;
import com.enviouse.progressivestages.common.lock.LockDefinition;
import com.enviouse.progressivestages.common.lock.PrefixEntry;
import com.enviouse.progressivestages.common.trigger.TriggerCondition;
import com.enviouse.progressivestages.common.trigger.TriggerConditionType;
import com.enviouse.progressivestages.common.trigger.TriggerMode;
import com.enviouse.progressivestages.common.trigger.TriggerRule;
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

        // v2.3: per-stage [[triggers]] auto-grant rules + [display] overrides.
        builder.triggers(parseTriggers(config));
        applyDisplaySection(config, builder);

        // v2.4: [stage] presentation/scope metadata + new sections.
        builder.hidden(readBool(stageSection, "hidden"));
        String color = stageSection.get("color");
        if (color != null) builder.color(color);
        String category = stageSection.get("category");
        if (category != null) builder.category(category);
        String scope = stageSection.get("scope");
        if (scope != null) builder.scope(scope);
        builder.durationMillis(parseDuration(stageSection.get("duration")));
        builder.attributes(parseAttributes(config));
        builder.revoke(parseRevoke(config));
        builder.cost(parseCost(config));
        builder.unlock(parseUnlock(config));
        builder.lockedAbilities(parseAbilities(config));

        return ParseResult.success(builder.build());
    }

    // -------------------- v2.4 sections --------------------

    /** Parse a real-time duration like {@code "30m"} / {@code "2h"} / {@code "1d"} / {@code "90s"} to millis (-1 = permanent). */
    private static long parseDuration(Object raw) {
        if (!(raw instanceof String s)) return -1L;
        s = s.trim().toLowerCase();
        if (s.isEmpty()) return -1L;
        long mult = 60_000L; // default unit = minutes
        char last = s.charAt(s.length() - 1);
        String num = s;
        if (!Character.isDigit(last)) {
            num = s.substring(0, s.length() - 1);
            mult = switch (last) {
                case 's' -> 1_000L;
                case 'm' -> 60_000L;
                case 'h' -> 3_600_000L;
                case 'd' -> 86_400_000L;
                default -> 60_000L;
            };
        }
        try {
            double v = Double.parseDouble(num.trim());
            return v <= 0 ? -1L : (long) (v * mult);
        } catch (NumberFormatException e) {
            LOGGER.warn("[ProgressiveStages] Invalid [stage].duration '{}' — ignoring", s);
            return -1L;
        }
    }

    private static List<StageAttribute> parseAttributes(Config config) {
        Object raw = config.get("attribute");
        if (raw == null) raw = config.get("attributes");
        List<StageAttribute> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) if (o instanceof Config c) { StageAttribute a = parseAttribute(c); if (a != null) out.add(a); }
        } else if (raw instanceof Config single) {
            StageAttribute a = parseAttribute(single);
            if (a != null) out.add(a);
        }
        return out;
    }

    private static StageAttribute parseAttribute(Config c) {
        String idStr = c.get("id");
        if (idStr == null) idStr = c.get("attribute");
        if (idStr == null) idStr = c.get("name");
        if (idStr == null || idStr.trim().isEmpty()) return null;
        ResourceLocation id = ResourceLocation.tryParse(idStr.trim());
        if (id == null) { LOGGER.warn("[ProgressiveStages] Invalid [attribute] id '{}'", idStr); return null; }
        double amount = readDouble(c, "amount", 0.0);
        return new StageAttribute(id, StageAttribute.parseOperation(c.get("operation")), amount);
    }

    private static RevokeRule parseRevoke(Config config) {
        Config sec = config.get("revoke");
        if (sec == null) return RevokeRule.NONE;
        boolean onDeath = readBool(sec, "on_death");
        long xpBelow = -1L;
        Object xb = sec.get("xp_below");
        if (xb instanceof Number n) xpBelow = n.longValue();
        boolean cascade = readBool(sec, "cascade");
        return new RevokeRule(onDeath, xpBelow, cascade);
    }

    private static StageCost parseCost(Config config) {
        Config sec = config.get("cost");
        if (sec == null) return null; // not purchasable
        int xpLevels = (int) readLong(sec, "xp_levels", 0L);
        boolean bypass = readBool(sec, "bypass_requirements");
        List<StageCost.ItemCost> items = new ArrayList<>();
        for (String raw : stringList(sec, "items")) {
            StageCost.ItemCost ic = parseItemCost(raw);
            if (ic != null) items.add(ic);
        }
        return new StageCost(Math.max(0, xpLevels), items, bypass);
    }

    /** Parse {@code "minecraft:diamond:5"} (id + count) or {@code "minecraft:diamond"} (count 1). */
    private static StageCost.ItemCost parseItemCost(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String s = raw.trim();
        int count = 1;
        int lastColon = s.lastIndexOf(':');
        if (lastColon > 0 && lastColon < s.length() - 1) {
            String tail = s.substring(lastColon + 1);
            if (tail.chars().allMatch(Character::isDigit)) {
                try { count = Math.max(1, Integer.parseInt(tail)); s = s.substring(0, lastColon); } catch (NumberFormatException ignored) {}
            }
        }
        ResourceLocation id = ResourceLocation.tryParse(s);
        return id == null ? null : new StageCost.ItemCost(id, count);
    }

    private static java.util.Set<String> parseAbilities(Config config) {
        Config sec = config.get("abilities");
        if (sec == null) return java.util.Set.of();
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (String s : stringList(sec, "locked")) {
            if (s != null && !s.trim().isEmpty()) out.add(s.trim().toLowerCase());
        }
        return out;
    }

    private static UnlockEffects parseUnlock(Config config) {
        Config sec = config.get("unlock");
        if (sec == null) return UnlockEffects.NONE;
        return new UnlockEffects(
            sec.getOrElse("toast", ""),
            sec.getOrElse("title", ""),
            sec.getOrElse("subtitle", ""),
            sec.getOrElse("sound", ""),
            sec.getOrElse("particle", ""),
            readBool(sec, "progress_nudges"),
            readBool(sec, "hud_bar"));
    }

    private static double readDouble(Config c, String key, double def) {
        Object v = c.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) { try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) {} }
        return def;
    }

    // -------------------- [[triggers]] (v2.3 per-stage auto-grant) --------------------

    /**
     * Parse the per-stage triggers. Accepts either an array-of-tables ({@code [[triggers]]} —
     * one entry per independent rule) or a single table ({@code [triggers]} — one rule). The
     * stage is auto-granted when ANY rule is satisfied; within a rule, conditions combine by
     * {@code mode}.
     */
    private static List<TriggerRule> parseTriggers(Config config) {
        Object raw = config.get("triggers");
        if (raw == null) return Collections.emptyList();

        List<TriggerRule> rules = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Config ruleCfg) {
                    TriggerRule r = parseTriggerRule(ruleCfg);
                    if (r != null) rules.add(r);
                } else {
                    LOGGER.warn("[ProgressiveStages] [[triggers]] entry is not a table — skipped");
                }
            }
        } else if (raw instanceof Config single) {
            TriggerRule r = parseTriggerRule(single);
            if (r != null) rules.add(r);
        } else {
            LOGGER.warn("[ProgressiveStages] [triggers] section has an unexpected shape — skipped");
        }
        return rules;
    }

    private static TriggerRule parseTriggerRule(Config c) {
        TriggerMode mode = TriggerMode.fromString(c.get("mode"));
        String description = c.get("description");

        List<TriggerCondition> conditions = new ArrayList<>();
        Object condRaw = c.get("conditions");
        if (condRaw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Config cc) {
                    TriggerCondition tc = parseCondition(cc);
                    if (tc != null) conditions.add(tc);
                }
            }
        } else if (condRaw instanceof Config cc) {
            TriggerCondition tc = parseCondition(cc);
            if (tc != null) conditions.add(tc);
        } else if (c.get("type") != null) {
            // Shorthand: a rule with no `conditions` list but a `type` IS a single condition.
            TriggerCondition tc = parseCondition(c);
            if (tc != null) conditions.add(tc);
        }

        if (conditions.isEmpty()) {
            LOGGER.warn("[ProgressiveStages] [[triggers]] rule has no valid conditions — skipped");
            return null;
        }
        return new TriggerRule(mode, conditions, description);
    }

    private static TriggerCondition parseCondition(Config c) {
        TriggerConditionType type = TriggerConditionType.fromString(c.get("type"));
        if (type == null) {
            LOGGER.warn("[ProgressiveStages] Unknown trigger condition type '{}' — skipped", String.valueOf(c.get("type")));
            return null;
        }
        long count = readLong(c, "count", 1L);
        String target = readTriggerTarget(c, type);
        if (target.isEmpty() && type.requiresTarget()) {
            LOGGER.warn("[ProgressiveStages] Trigger condition '{}' is missing its target — skipped",
                type.name().toLowerCase());
            return null;
        }
        if (target.isEmpty() && type == TriggerConditionType.DISTANCE) {
            target = "all"; // default movement kind
        }
        // v2.4: kill_with also names the held item.
        String with = "";
        if (type == TriggerConditionType.KILL_WITH) {
            Object w = c.get("with");
            if (w == null) w = c.get("held_item");
            if (w == null) w = c.get("item");
            if (w instanceof String s) with = s.trim();
            if (with.isEmpty()) {
                LOGGER.warn("[ProgressiveStages] kill_with condition is missing its 'with' item — skipped");
                return null;
            }
        }
        return new TriggerCondition(type, target, count, with);
    }

    /** Read the type-appropriate target key, with generous fallbacks. */
    private static String readTriggerTarget(Config c, TriggerConditionType type) {
        String[] keys = switch (type) {
            case KILL                                        -> new String[]{"entity", "mob", "target", "id"};
            case MINE                                        -> new String[]{"block", "target", "id"};
            case CRAFT, PICKUP, USE, DROP, BREAK_ITEM, HAS_ITEM -> new String[]{"item", "target", "id"};
            case DISTANCE                                    -> new String[]{"movement", "kind", "target"};
            case STAT                                        -> new String[]{"stat", "id", "target"};
            case ADVANCEMENT                                 -> new String[]{"advancement", "id", "target"};
            case DIMENSION                                   -> new String[]{"dimension", "id", "target"};
            case BIOME                                       -> new String[]{"biome", "id", "target"};
            case EFFECT                                      -> new String[]{"effect", "id", "target"};
            case WEATHER                                     -> new String[]{"weather", "id", "target"};
            case ENTER_STRUCTURE                             -> new String[]{"structure", "id", "target"};
            case KILL_WITH                                   -> new String[]{"entity", "mob", "target", "id"};
            // BREED/TAME accept an OPTIONAL species target (id or #tag); absent = count all.
            case TAME, BREED                                 -> new String[]{"entity", "animal", "target", "id"};
            case PLAY_TIME, LEVEL, XP, DAY_COUNT, WORLD_TIME -> new String[]{};
        };
        for (String k : keys) {
            Object v = c.get(k);
            if (v instanceof String s && !s.trim().isEmpty()) return s.trim();
        }
        return "";
    }

    private static long readLong(Config c, String key, long def) {
        Object v = c.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    // -------------------- [display] (v2.3 per-stage tooltip / unknown-item) --------------------

    /**
     * Parse the optional {@code [display]} section. Each key is a tri-state override: present →
     * use the stage value; absent → leave {@code null} so the global progressivestages.toml
     * default applies.
     */
    private static void applyDisplaySection(Config config, StageDefinition.Builder builder) {
        Config sec = config.get("display");
        if (sec == null) return;
        builder.displayAsUnknownItem(readOptionalBool(sec, "display_as_unknown_item"));
        builder.obscureIcon(readOptionalBool(sec, "obscure_icon"));
        builder.showTooltip(readOptionalBool(sec, "show_tooltip"));
        builder.showDescriptionOnTooltip(readOptionalBool(sec, "show_description_on_tooltip"));
    }

    /** Returns the boolean if explicitly present, else {@code null} (inherit global default). */
    private static Boolean readOptionalBool(Config c, String key) {
        Object v = c.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) {
            String t = s.trim().toLowerCase();
            if (t.equals("true"))  return Boolean.TRUE;
            if (t.equals("false")) return Boolean.FALSE;
        }
        return null;
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
        b.trades(       parseCategory(config, "trades"));
        b.professions(  parseCategory(config, "professions"));
        b.advancements( parseCategory(config, "advancements"));
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
        // v2.5: extra buffer (blocks) before the structure boundary repels the player. May be set
        // on [structures].rules.entry_padding or directly on [structures].entry_padding.
        int pad = (int) readLong(section, "entry_padding", 0L);
        if (rules != null) pad = Math.max(pad, (int) readLong(rules, "entry_padding", 0L));
        return new LockDefinition.StructureRules(lockedEntry, pbb, pbp, pex, dms, Math.max(0, pad));
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

        // v2.0.1: transitive crafting / automated crafting toggles
        Object bclI = section.get("block_crafting_with_locked_ingredients");
        if (bclI instanceof Boolean bool) b.blockCraftingWithLockedIngredients(bool);
        Object bAC = section.get("block_automated_crafting");
        if (bAC instanceof Boolean bool) b.blockAutomatedCrafting(bool);
        Object radius = section.get("crafter_check_radius");
        if (radius instanceof Number n) b.crafterCheckRadius(n.intValue());
        Object oreRadius = section.get("ore_spoof_radius");
        if (oreRadius instanceof Number n) b.oreSpoofRadius(n.intValue());

        // v2.3: per-stage enforcement category overrides — the SAME key names as the global
        // progressivestages.toml toggles (e.g. block_item_use, block_block_placement). Setting one
        // here overrides that behaviour for the resources THIS stage gates; omit to inherit global.
        for (EnforcementCategory cat : EnforcementCategory.values()) {
            Object v = section.get(cat.key());
            if (v instanceof Boolean bool) {
                b.enforcementOverride(cat, bool);
            } else if (v instanceof String s) {
                String t = s.trim().toLowerCase();
                if (t.equals("true")) b.enforcementOverride(cat, Boolean.TRUE);
                else if (t.equals("false")) b.enforcementOverride(cat, Boolean.FALSE);
            }
        }
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
