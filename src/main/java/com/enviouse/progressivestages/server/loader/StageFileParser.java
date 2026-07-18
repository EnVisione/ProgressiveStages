package com.enviouse.progressivestages.server.loader;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.RevokeRule;
import com.enviouse.progressivestages.common.config.StageAttribute;
import com.enviouse.progressivestages.common.config.StageCost;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.stage.DependencyMode;
import com.enviouse.progressivestages.common.config.StageRewards;
import com.enviouse.progressivestages.common.config.StageSlotPolicy;
import com.enviouse.progressivestages.common.config.UnlockEffects;
import com.enviouse.progressivestages.common.lock.CategoryLocks;
import com.enviouse.progressivestages.common.lock.ConditionalRule;
import com.enviouse.progressivestages.common.lock.ActiveLockDefinition;
import com.enviouse.progressivestages.common.api.structure.StructureLeaveOutcome;
import com.enviouse.progressivestages.common.lock.EnforcementCategory;
import com.enviouse.progressivestages.common.lock.LockDefinition;
import com.enviouse.progressivestages.common.lock.PrefixEntry;
import com.enviouse.progressivestages.common.rehaul.ConfigProvenance;
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
import java.util.Objects;

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
 * [[ores.overrides]]        target, display_as, drop_as
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
        try {
            return parseConfig(config, filePath.getFileName().toString(), filePath.toString(), false);
        } catch (RuntimeException e) {
            String message = e.getMessage();
            if (message == null || message.isBlank()) message = e.getClass().getSimpleName();
            return ParseResult.validationError("Invalid stage definition. " + message);
        }
    }

    /**
     * v2.5: parse a stage definition from an arbitrary stream (used for datapack-loaded stages, where
     * the TOML lives inside {@code data/<ns>/progressivestages/stages/*.toml} rather than a config file).
     */
    public static Optional<StageDefinition> parse(java.io.InputStream in, String fileName) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
            Config config = PARSER.parse(reader);
            ParseResult result = parseConfig(config, fileName, fileName, false);
            if (!result.isSuccess()) {
                LOGGER.warn("Failed to parse datapack stage {}: {}", fileName, result.getErrorMessage());
                return Optional.empty();
            }
            return Optional.of(result.getStageDefinition());
        } catch (Exception e) {
            LOGGER.warn("Failed to read datapack stage {}: {}", fileName, e.getMessage());
            return Optional.empty();
        }
    }

    public static ParseResult parseText(String content, String fileName, String sourceId, boolean packageSource) {
        Objects.requireNonNull(content, "content");
        try {
            Config config = PARSER.parse(content);
            return parseConfig(config, fileName, sourceId, packageSource);
        } catch (com.electronwill.nightconfig.core.io.ParsingException e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ParseResult.syntaxError("TOML syntax error: " + message);
        } catch (RuntimeException e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ParseResult.validationError("Invalid stage definition. " + message);
        }
    }

    public static ParseResult parseSources(List<SourcePart> sources, String sourceId) {
        if (sources == null || sources.isEmpty()) return ParseResult.validationError("A stage package has no sources");
        StringBuilder merged = new StringBuilder();
        for (SourcePart source : sources) {
            if (source == null) continue;
            merged.append('\n').append(source.content()).append('\n');
        }
        return parseText(merged.toString(), sources.getFirst().name(), sourceId, true);
    }

    public record SourcePart(String name, String content) {
        public SourcePart {
            name = Objects.requireNonNull(name, "name");
            content = Objects.requireNonNull(content, "content");
        }
    }

    static ParseResult parseConfig(Config config, String fileName, String sourceId, boolean packageSource) {
        Config stageSection = config.get("stage");
        if (stageSection == null) return ParseResult.validationError("Missing [stage] section");

        Config schemaSection = config.get("schema");
        int schemaVersion = schemaSection != null ? (int) readLong(schemaSection, "version", packageSource ? 4L : 3L)
            : (packageSource ? 4 : 3);
        if (schemaVersion < 1 || schemaVersion > 4) {
            throw new IllegalArgumentException("Unsupported schema version. " + schemaVersion);
        }

        String id = stageSection.get("id");
        if (id == null || id.isEmpty()) {
            id = fileName.endsWith(".toml") ? fileName.substring(0, fileName.length() - 5) : fileName;
        }
        StageId stageId = StageId.parse(id);

        String displayName = stageSection.get("display_name");
        if (displayName == null) displayName = stageSection.getOrElse("name", id);
        String description = stageSection.getOrElse("description", "");
        String icon = stageSection.get("icon");
        String unlockMessage = stageSection.get("unlock_message");
        List<StageId> dependencies = parseDependencies(stageSection);
        String dependencyModeRaw = stageSection.getOrElse("dependency_mode", "all");
        DependencyMode dependencyMode = DependencyMode.tryParse(dependencyModeRaw);
        if (dependencyMode == null) {
            throw new IllegalArgumentException("Invalid dependency mode. " + dependencyModeRaw);
        }
        int dependencyCount = (int) readLong(stageSection, "dependency_count", 1L);
        if (new java.util.LinkedHashSet<>(dependencies).size() != dependencies.size()) {
            throw new IllegalArgumentException("A stage cannot declare the same dependency more than once");
        }
        if (dependencyMode == DependencyMode.AT_LEAST && !dependencies.isEmpty()
                && (dependencyCount < 1 || dependencyCount > dependencies.size())) {
            throw new IllegalArgumentException("Dependency count is outside the dependency list size");
        }

        LockDefinition locks = parseLocks(config);
        if (locks.minecraftNamespace()) {
            LOGGER.debug("Stage {} declares minecraft=true (gates minecraft namespace)", stageId);
        }

        StageDefinition.Builder builder = StageDefinition.builder(stageId)
            .displayName(displayName)
            .description(description)
            .dependencies(dependencies)
            .dependencyPolicy(dependencyMode, dependencyCount)
            .locks(locks)
            .schemaVersion(schemaVersion)
            .priority((int) readLong(stageSection, "priority", 0L))
            .provenance(packageSource
                ? ConfigProvenance.packageField(sourceId, fileName, "stage", "")
                : ConfigProvenance.legacy(sourceId, fileName, "stage", ""));
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
        List<String> tags = new ArrayList<>();
        for (String t : stringList(stageSection, "tags")) {
            if (t != null && !t.isBlank()) tags.add(t.trim().toLowerCase(java.util.Locale.ROOT));
        }
        builder.tags(tags);
        String slotGroup = stageSection.getOrElse("slot_group", "");
        slotGroup = slotGroup == null ? "" : slotGroup.trim().toLowerCase(java.util.Locale.ROOT);
        if (!slotGroup.isEmpty() && !slotGroup.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("Stage slot group contains invalid characters. " + slotGroup);
        }
        int slotLimit = (int) readLong(stageSection, "slot_limit", 0L);
        if (slotLimit < 0 || slotLimit > 1024) {
            throw new IllegalArgumentException("Stage slot limit must be between zero and 1024");
        }
        if (slotGroup.isEmpty() && slotLimit > 0) {
            throw new IllegalArgumentException("Stage slot limit requires a slot group");
        }
        builder.slotGroup(slotGroup)
            .slotLimit(slotLimit)
            .slotPolicy(StageSlotPolicy.parse(stageSection.getOrElse("slot_policy", "deny")));
        String scope = stageSection.get("scope");
        if (scope != null) {
            String normalizedScope = scope.trim().toLowerCase(java.util.Locale.ROOT);
            if (!normalizedScope.equals("team") && !normalizedScope.equals("server")) {
                throw new IllegalArgumentException("Invalid stage scope. " + scope);
            }
            builder.scope(normalizedScope);
        }
        builder.durationMillis(parseDuration(stageSection.get("duration")));
        builder.attributes(parseAttributes(config));
        builder.revoke(parseRevoke(config));
        builder.cost(parseCost(config));
        builder.unlock(parseUnlock(config));
        builder.rewards(parseRewards(config));
        builder.lockedAbilities(parseAbilities(config));
        builder.conditionalRules(parseConditionalRules(config, stageId));
        builder.activeLocks(parseActiveLocks(config));

        return ParseResult.success(builder.build(), schemaVersion == 4 ? Config.copy(config) : null);
    }

    // -------------------- v2.4 sections --------------------

    /** Parse a real-time duration like {@code "30m"} / {@code "2h"} / {@code "1d"} / {@code "90s"} to millis (-1 = permanent). */
    private static long parseDuration(Object raw) {
        if (raw == null) return -1L;
        if (!(raw instanceof String s)) throw new IllegalArgumentException("A duration must be a string");
        s = s.trim().toLowerCase(java.util.Locale.ROOT);
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
                default -> throw new IllegalArgumentException("Invalid duration unit. " + s);
            };
        }
        try {
            double v = Double.parseDouble(num.trim());
            return v <= 0 ? -1L : (long) (v * mult);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid duration. " + s, e);
        }
    }

    private static List<StageAttribute> parseAttributes(Config config) {
        Object raw = config.get("attribute");
        if (raw == null) raw = config.get("attributes");
        List<StageAttribute> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Config c)) throw new IllegalArgumentException("An attribute entry must be a table");
                out.add(parseAttribute(c));
            }
        } else if (raw instanceof Config single) {
            out.add(parseAttribute(single));
        } else if (raw != null) {
            throw new IllegalArgumentException("The attribute section has an invalid shape");
        }
        return out;
    }

    private static StageAttribute parseAttribute(Config c) {
        String idStr = c.get("id");
        if (idStr == null) idStr = c.get("attribute");
        if (idStr == null) idStr = c.get("name");
        if (idStr == null || idStr.trim().isEmpty()) {
            throw new IllegalArgumentException("An attribute entry is missing its ID");
        }
        ResourceLocation id = ResourceLocation.tryParse(idStr.trim());
        if (id == null) throw new IllegalArgumentException("Invalid attribute ID. " + idStr);
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
            if (ic == null) throw new IllegalArgumentException("Invalid cost item. " + raw);
            items.add(ic);
        }
        // v3.0: optional purchase cooldown (seconds, or a friendly `cooldown = "5m"`) + revoke refund %.
        int cooldown = (int) readLong(sec, "cooldown_seconds", 0L);
        Object cd = sec.get("cooldown");
        if (cd instanceof String s && !s.isBlank()) {
            long millis = parseDuration(s);
            if (millis > 0) cooldown = (int) (millis / 1000L);
        }
        int refund = (int) readLong(sec, "refund_percent", 0L);
        return new StageCost(Math.max(0, xpLevels), items, bypass, Math.max(0, cooldown), refund);
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
            if (s != null && !s.trim().isEmpty()) out.add(s.trim().toLowerCase(java.util.Locale.ROOT));
        }
        return out;
    }

    private static List<ConditionalRule> parseConditionalRules(Config config, StageId ownerStage) {
        List<ConditionalRule> out = new ArrayList<>();
        java.util.Set<ResourceLocation> ids = new java.util.LinkedHashSet<>();
        parseConditionalRuleGroup(config, "temporary_locks", ownerStage,
            ConditionalRule.Effect.LOCK, ConditionalRule.Activation.LIVE, out, ids);
        parseConditionalRuleGroup(config, "temporary_unlocks", ownerStage,
            ConditionalRule.Effect.UNLOCK, ConditionalRule.Activation.LIVE, out, ids);
        parseConditionalRuleGroup(config, "triggered_locks", ownerStage,
            ConditionalRule.Effect.LOCK, ConditionalRule.Activation.TRIGGERED, out, ids);
        parseConditionalRuleGroup(config, "triggered_unlocks", ownerStage,
            ConditionalRule.Effect.UNLOCK, ConditionalRule.Activation.TRIGGERED, out, ids);
        parseConditionalRuleGroup(config, "conditional_rules", ownerStage, null, null, out, ids);
        return out;
    }

    private static void parseConditionalRuleGroup(Config config, String key, StageId ownerStage,
                                                   ConditionalRule.Effect fixedEffect,
                                                   ConditionalRule.Activation fixedActivation,
                                                   List<ConditionalRule> out,
                                                   java.util.Set<ResourceLocation> ids) {
        Object raw = config.get(key);
        if (raw == null) return;
        List<Config> entries = new ArrayList<>();
        if (raw instanceof Config entry) entries.add(entry);
        else if (raw instanceof List<?> list) {
            for (Object value : list) {
                if (!(value instanceof Config entry)) {
                    throw new IllegalArgumentException("A conditional rule must be a table");
                }
                entries.add(entry);
            }
        } else {
            throw new IllegalArgumentException("A conditional rule group must contain tables");
        }

        for (int index = 0; index < entries.size(); index++) {
            Config entry = entries.get(index);
            String rawId = entry.get("id");
            if (rawId == null || rawId.isBlank()) {
                throw new IllegalArgumentException("A conditional rule is missing its id");
            }
            ResourceLocation id = conditionalRuleId(ownerStage, rawId);
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate conditional rule id. " + id);

            ConditionalRule.Effect effect = fixedEffect != null ? fixedEffect
                : parseRuleEffect(entry.getOrElse("effect", "lock"));
            ConditionalRule.Activation activation = fixedActivation != null ? fixedActivation
                : parseRuleActivation(entry.getOrElse("activation", "live"));
            ConditionalRule.StageState stageState = parseRuleStageState(
                entry.getOrElse("stage_state", entry.getOrElse("active_when", "owned")));
            int priority = (int) readLong(entry, "priority", 100L);
            if (priority < -1_000_000 || priority > 1_000_000) {
                throw new IllegalArgumentException("Conditional rule priority is outside the supported range. " + id);
            }

            Config targetsConfig = entry.get("targets");
            if (targetsConfig == null) throw new IllegalArgumentException("Conditional rule has no targets. " + id);
            Config exceptConfig = entry.get("except");
            ConditionalRule.Targets targets = parseConditionalTargets(targetsConfig, exceptConfig, id);
            ConditionalRule.Context context = parseConditionalContext(entry.get("when"));

            ConditionalRule.TriggerType triggerType = ConditionalRule.TriggerType.MANUAL;
            List<PrefixEntry> triggerEntities = List.of();
            long durationMillis = -1L;
            boolean refresh = true;
            if (activation == ConditionalRule.Activation.TRIGGERED) {
                triggerType = parseRuleTrigger(entry.getOrElse("trigger", "manual"));
                triggerEntities = parsePrefixEntries(entry, "trigger_entities", true);
                Object duration = entry.get("duration");
                durationMillis = duration != null ? parseDuration(duration)
                    : Math.max(1L, readLong(entry, "duration_seconds", 10L)) * 1_000L;
                if (durationMillis <= 0L) durationMillis = 10_000L;
                Boolean refreshValue = readOptionalBool(entry, "refresh_duration");
                refresh = refreshValue == null || refreshValue;
            }

            out.add(new ConditionalRule(id, ownerStage, effect, activation, stageState, priority,
                context, targets, triggerType, triggerEntities, durationMillis, refresh));
        }
    }

    private static ResourceLocation conditionalRuleId(StageId ownerStage, String raw) {
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        ResourceLocation id = value.contains(":")
            ? ResourceLocation.tryParse(value)
            : ResourceLocation.tryParse(ownerStage.getNamespace() + ":" + ownerStage.getPath() + "/" + value);
        if (id == null) throw new IllegalArgumentException("Invalid conditional rule id. " + raw);
        return id;
    }

    private static ConditionalRule.Effect parseRuleEffect(String raw) {
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "lock", "deny", "block" -> ConditionalRule.Effect.LOCK;
            case "unlock", "allow", "permit" -> ConditionalRule.Effect.UNLOCK;
            default -> throw new IllegalArgumentException("Invalid conditional rule effect. " + raw);
        };
    }

    private static ConditionalRule.Activation parseRuleActivation(String raw) {
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "live", "temporary", "context" -> ConditionalRule.Activation.LIVE;
            case "triggered", "timed" -> ConditionalRule.Activation.TRIGGERED;
            default -> throw new IllegalArgumentException("Invalid conditional rule activation. " + raw);
        };
    }

    private static ConditionalRule.StageState parseRuleStageState(String raw) {
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "owned", "has" -> ConditionalRule.StageState.OWNED;
            case "missing", "lacks" -> ConditionalRule.StageState.MISSING;
            case "always", "any" -> ConditionalRule.StageState.ALWAYS;
            default -> throw new IllegalArgumentException("Invalid conditional rule stage state. " + raw);
        };
    }

    private static ConditionalRule.TriggerType parseRuleTrigger(String raw) {
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "manual", "api", "command" -> ConditionalRule.TriggerType.MANUAL;
            case "combat", "fight" -> ConditionalRule.TriggerType.COMBAT;
            case "attack", "dealt_damage" -> ConditionalRule.TriggerType.ATTACK;
            case "hurt", "take_damage", "damaged" -> ConditionalRule.TriggerType.HURT;
            case "kill", "killed" -> ConditionalRule.TriggerType.KILL;
            default -> throw new IllegalArgumentException("Invalid conditional rule trigger. " + raw);
        };
    }

    private static ConditionalRule.Targets parseConditionalTargets(Config included, Config excluded,
                                                                   ResourceLocation ruleId) {
        java.util.EnumMap<ConditionalRule.TargetType, List<PrefixEntry>> includeMap =
            new java.util.EnumMap<>(ConditionalRule.TargetType.class);
        java.util.EnumMap<ConditionalRule.TargetType, List<PrefixEntry>> excludeMap =
            new java.util.EnumMap<>(ConditionalRule.TargetType.class);
        for (ConditionalRule.TargetType type : ConditionalRule.TargetType.values()) {
            String key = targetKey(type);
            List<PrefixEntry> include = parsePrefixEntries(included, key, typeAllowsTags(type));
            if (!include.isEmpty()) includeMap.put(type, include);
            List<PrefixEntry> exclude = parsePrefixEntries(excluded, key, typeAllowsTags(type));
            if (!exclude.isEmpty()) excludeMap.put(type, exclude);
        }
        ConditionalRule.Targets targets = new ConditionalRule.Targets(includeMap, excludeMap);
        if (targets.isEmpty()) throw new IllegalArgumentException("Conditional rule has empty targets. " + ruleId);
        return targets;
    }

    private static String targetKey(ConditionalRule.TargetType type) {
        return switch (type) {
            case ITEM -> "items";
            case BLOCK -> "blocks";
            case FLUID -> "fluids";
            case ENTITY -> "entities";
            case RECIPE -> "recipes";
            case DIMENSION -> "dimensions";
            case STRUCTURE -> "structures";
            case ABILITY -> "abilities";
        };
    }

    private static boolean typeAllowsTags(ConditionalRule.TargetType type) {
        return type == ConditionalRule.TargetType.ITEM || type == ConditionalRule.TargetType.BLOCK
            || type == ConditionalRule.TargetType.FLUID || type == ConditionalRule.TargetType.ENTITY;
    }

    private static List<PrefixEntry> parsePrefixEntries(Config config, String key, boolean allowTags) {
        if (config == null) return List.of();
        List<PrefixEntry> out = new ArrayList<>();
        for (String raw : stringList(config, key)) {
            PrefixEntry entry = PrefixEntry.parse(raw);
            if (entry == null) throw new IllegalArgumentException("Invalid conditional selector. " + raw);
            if (!allowTags && entry.kind() == PrefixEntry.Kind.TAG) {
                throw new IllegalArgumentException("Tags are not supported for this conditional selector. " + raw);
            }
            out.add(entry);
        }
        return List.copyOf(out);
    }

    private static ConditionalRule.Context parseConditionalContext(Config when) {
        if (when == null) return ConditionalRule.Context.EMPTY;
        String modeValue = when.getOrElse("mode", "all_of");
        ConditionalRule.ContextMode mode = switch (modeValue.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "all", "all_of" -> ConditionalRule.ContextMode.ALL;
            case "any", "any_of" -> ConditionalRule.ContextMode.ANY;
            default -> throw new IllegalArgumentException("Invalid conditional context mode. " + modeValue);
        };
        List<PrefixEntry> dimensions = parsePrefixEntries(when, "dimensions", false);
        List<PrefixEntry> structures = parsePrefixEntries(when, "structures", false);
        List<PrefixEntry> biomes = parsePrefixEntries(when, "biomes", true);
        Integer minY = readOptionalInt(when, "min_y");
        Integer maxY = readOptionalInt(when, "max_y");
        if (minY != null && maxY != null && minY > maxY) {
            throw new IllegalArgumentException("Conditional min y cannot exceed max y");
        }
        Double minHealth = readOptionalDouble(when, "min_health", "health_above");
        Double maxHealth = readOptionalDouble(when, "max_health", "health_below");
        if (minHealth != null && maxHealth != null && minHealth > maxHealth) {
            throw new IllegalArgumentException("Conditional minimum health cannot exceed maximum health");
        }
        java.util.Set<StageId> requiredStages = parseStageIdSet(when, "stages");
        java.util.Set<StageId> missingStages = parseStageIdSet(when, "missing_stages");
        java.util.Set<ResourceLocation> effects = parseResourceSet(when, "effects");
        String script = when.getOrElse("script", "");
        return new ConditionalRule.Context(mode, dimensions, structures, biomes, minY, maxY,
            minHealth, maxHealth, requiredStages, missingStages, effects,
            readOptionalBool(when, "sneaking"), readOptionalBool(when, "sprinting"),
            readOptionalBool(when, "swimming"), readOptionalBool(when, "riding"),
            readOptionalBool(when, "on_ground"), script == null ? "" : script.trim());
    }

    private static Double readOptionalDouble(Config config, String... keys) {
        if (config == null) return null;
        for (String key : keys) {
            Object raw = config.get(key);
            if (raw instanceof Number number) return number.doubleValue();
            if (raw instanceof String value) {
                try { return Double.parseDouble(value.trim()); } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private static java.util.Set<StageId> parseStageIdSet(Config config, String key) {
        java.util.Set<StageId> out = new java.util.LinkedHashSet<>();
        for (String raw : stringList(config, key)) out.add(StageId.parse(raw));
        return java.util.Set.copyOf(out);
    }

    private static java.util.Set<ResourceLocation> parseResourceSet(Config config, String key) {
        java.util.Set<ResourceLocation> out = new java.util.LinkedHashSet<>();
        for (String raw : stringList(config, key)) {
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null) throw new IllegalArgumentException("Invalid conditional resource id. " + raw);
            out.add(id);
        }
        return java.util.Set.copyOf(out);
    }

    private static StageRewards parseRewards(Config config) {
        Config sec = config.get("rewards");
        if (sec == null) return StageRewards.NONE;
        List<StageCost.ItemCost> items = new ArrayList<>();
        for (String raw : stringList(sec, "items")) {
            StageCost.ItemCost ic = parseItemCost(raw);
            if (ic == null) throw new IllegalArgumentException("Invalid reward item. " + raw);
            items.add(ic);
        }
        List<StageRewards.EffectReward> effects = new ArrayList<>();
        for (String raw : stringList(sec, "effects")) {
            StageRewards.EffectReward er = parseEffectReward(raw);
            if (er == null) throw new IllegalArgumentException("Invalid reward effect. " + raw);
            effects.add(er);
        }
        List<String> commands = new ArrayList<>(stringList(sec, "commands"));
        Object single = sec.get("command");
        if (single instanceof String s && !s.isBlank()) commands.add(s.trim());
        String teleport = sec.getOrElse("teleport", "");
        int xpLevels = (int) readLong(sec, "xp_levels", 0L);
        int xpPoints = (int) readLong(sec, "xp_points", 0L);
        return new StageRewards(items, effects, commands, teleport == null ? "" : teleport.trim(),
            Math.max(0, xpLevels), Math.max(0, xpPoints));
    }

    /** Parse {@code "minecraft:strength:60:1"} → effect id + duration(seconds) + amplifier. */
    private static StageRewards.EffectReward parseEffectReward(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.trim().split(":");
        // Peel up to two trailing all-digit segments (duration, then amplifier) off the id.
        java.util.List<String> nums = new ArrayList<>();
        int end = parts.length;
        while (end > 1 && parts[end - 1].chars().allMatch(Character::isDigit) && nums.size() < 2) {
            nums.add(0, parts[end - 1]);
            end--;
        }
        ResourceLocation id = ResourceLocation.tryParse(String.join(":", java.util.Arrays.copyOfRange(parts, 0, end)));
        if (id == null) return null;
        int durationSec = 30, amp = 0;
        try {
            if (nums.size() == 1) durationSec = Integer.parseInt(nums.get(0));
            else if (nums.size() == 2) { durationSec = Integer.parseInt(nums.get(0)); amp = Integer.parseInt(nums.get(1)); }
        } catch (NumberFormatException ignored) {}
        return new StageRewards.EffectReward(id, Math.max(1, durationSec) * 20, Math.max(0, amp));
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
                    rules.add(parseTriggerRule(ruleCfg));
                } else {
                    throw new IllegalArgumentException("A triggers entry must be a table");
                }
            }
        } else if (raw instanceof Config single) {
            rules.add(parseTriggerRule(single));
        } else {
            throw new IllegalArgumentException("The triggers section has an invalid shape");
        }
        return rules;
    }

    private static TriggerRule parseTriggerRule(Config c) {
        Object rawMode = c.get("mode");
        TriggerMode mode = rawMode == null ? TriggerMode.ALL_OF
            : TriggerMode.tryParse(rawMode instanceof String value ? value : null);
        if (mode == null) throw new IllegalArgumentException("Invalid trigger mode. " + rawMode);
        String description = c.get("description");

        List<TriggerCondition> conditions = new ArrayList<>();
        Object condRaw = c.get("conditions");
        if (condRaw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Config cc) {
                    conditions.add(parseCondition(cc));
                } else {
                    throw new IllegalArgumentException("A trigger condition must be a table");
                }
            }
        } else if (condRaw instanceof Config cc) {
            conditions.add(parseCondition(cc));
        } else if (c.get("type") != null) {
            // Shorthand: a rule with no `conditions` list but a `type` IS a single condition.
            conditions.add(parseCondition(c));
        }

        if (conditions.isEmpty()) {
            throw new IllegalArgumentException("A triggers rule must contain at least one condition");
        }
        return new TriggerRule(mode, conditions, description);
    }

    private static TriggerCondition parseCondition(Config c) {
        Object rawType = c.get("type");
        TriggerConditionType type = TriggerConditionType.fromString(
            rawType instanceof String value ? value : null);
        if (type == null) {
            throw new IllegalArgumentException("Unknown trigger condition type. " + rawType);
        }
        long count = readLong(c, "count", 1L);
        // v3.0: time-based conditions accept a friendly `duration` (e.g. "3d") in place of raw seconds.
        if (type == TriggerConditionType.STAGE_HELD_FOR || type == TriggerConditionType.BIOME_TIME) {
            Object dur = c.get("duration");
            if (dur instanceof String ds && !ds.isBlank()) {
                long millis = parseDuration(ds);
                if (millis > 0) count = Math.max(1L, millis / 1000L);
            }
        }
        String target = readTriggerTarget(c, type);
        if (target.isEmpty() && type.requiresTarget()) {
            throw new IllegalArgumentException("Trigger condition is missing its target. "
                + type.name().toLowerCase(java.util.Locale.ROOT));
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
                throw new IllegalArgumentException("The kill with condition is missing its item");
            }
        }
        // v2.5: tame/breed/kill_with build counter keys from canonical registry ids on the write side
        // (BuiltInRegistries.getKey always namespaces). Canonicalize an exact-id (non-tag) target/with
        // here — "cow" -> "minecraft:cow" — so a namespaceless config value still matches at read time.
        target = canonicalizeSubject(type, target);
        if (type == TriggerConditionType.KILL_WITH) with = canonicalizeId(with);
        ResourceLocation provider = null;
        StageId requiredSessionStage = null;
        java.util.Set<StructureLeaveOutcome> outcomes = java.util.Set.of();
        if (type == TriggerConditionType.LEAVE_STRUCTURE) {
            Object providerRaw = c.get("provider");
            if (providerRaw instanceof String value && !value.isBlank()) {
                provider = ResourceLocation.tryParse(value.trim());
                if (provider == null) throw new IllegalArgumentException("Invalid structure provider id. " + value);
            }
            Object stageRaw = c.get("required_session_stage");
            if (stageRaw == null) stageRaw = c.get("session_stage");
            if (stageRaw instanceof String value && !value.isBlank()) {
                requiredSessionStage = StageId.parse(value);
            }
            java.util.Set<StructureLeaveOutcome> parsedOutcomes = new java.util.LinkedHashSet<>();
            for (String value : stringList(c, "outcomes")) {
                if (value.equalsIgnoreCase("any")) continue;
                try { parsedOutcomes.add(StructureLeaveOutcome.parse(value)); }
                catch (IllegalArgumentException error) {
                    throw new IllegalArgumentException("Invalid structure leave outcome. " + value);
                }
            }
            Object outcomeRaw = c.get("outcome");
            if (outcomeRaw instanceof String value && !value.isBlank()
                    && !value.equalsIgnoreCase("any")) {
                try { parsedOutcomes.add(StructureLeaveOutcome.parse(value)); }
                catch (IllegalArgumentException error) {
                    throw new IllegalArgumentException("Invalid structure leave outcome. " + value);
                }
            }
            outcomes = java.util.Set.copyOf(parsedOutcomes);
        }
        return new TriggerCondition(type, target, count, with, provider, requiredSessionStage, outcomes);
    }

    /** Canonicalize an exact-id subject for the event-counted condition types; tags/keywords untouched. */
    private static String canonicalizeSubject(TriggerConditionType type, String target) {
        if (target.isEmpty()) return target;
        if (type != TriggerConditionType.TAME && type != TriggerConditionType.BREED
                && type != TriggerConditionType.KILL_WITH) return target;
        if (target.startsWith("#") || target.startsWith("tag:")) return target; // tags resolve member-wise
        String body = target.startsWith("id:") ? target.substring(3) : target;
        ResourceLocation rl = ResourceLocation.tryParse(body);
        return rl != null ? rl.toString() : target;
    }

    /** Canonicalize a plain item id (kill_with's held item), leaving tags/unparseable values as-is. */
    private static String canonicalizeId(String s) {
        if (s == null || s.isEmpty() || s.startsWith("#")) return s;
        ResourceLocation rl = ResourceLocation.tryParse(s);
        return rl != null ? rl.toString() : s;
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
            case SCRIPT                                      -> new String[]{"id", "condition", "script", "target"};
            case BIOME_TIME                                  -> new String[]{"biome", "id", "target"};
            case STAGE_HELD_FOR                              -> new String[]{"stage", "id", "target"};
            case CUSTOM_COUNTER                              -> new String[]{"counter", "key", "id", "target"};
            case SCOREBOARD                                  -> new String[]{"objective", "scoreboard", "id", "target"};
            case SCRIPT_VALUE                                -> new String[]{"id", "provider", "script", "target"};
            case LEAVE_STRUCTURE                             -> new String[]{"structure", "id", "target"};
            case PLAY_TIME, LEVEL, XP, DAY_COUNT, WORLD_TIME,
                 REACH_Y, FISH, SLEEP, RIDE, HEALTH, FOOD,
                 STAGE_COUNT, ONLINE_TEAM_SIZE               -> new String[]{};
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
        Integer x = readOptionalInt(sec, "x", "ui_x", "layout_x");
        Integer y = readOptionalInt(sec, "y", "ui_y", "layout_y");
        if ((x == null) != (y == null)) {
            throw new IllegalArgumentException("Display coordinates must provide both x and y");
        }
        builder.uiPosition(x, y);
        String frame = stringValue(sec, "frame", "type");
        if (frame != null) builder.uiFrame(normalizeEnum(frame, java.util.Set.of("task", "goal", "challenge"), "task"));
        String background = stringValue(sec, "background", "background_texture");
        if (background != null) {
            if (ResourceLocation.tryParse(background.trim()) == null) {
                throw new IllegalArgumentException("Invalid display background. " + background);
            }
            builder.uiBackground(background.trim());
        }
        String reveal = stringValue(sec, "reveal", "reveal_policy");
        if (reveal != null) {
            String normalized = reveal.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
            if (normalized.equals("dependency") || normalized.equals("prerequisites") || normalized.equals("available")) {
                normalized = "dependencies";
            } else if (normalized.equals("owned")) {
                normalized = "unlocked";
            }
            builder.uiReveal(normalizeEnum(normalized, java.util.Set.of("always", "dependencies", "unlocked"), "always"));
        }
        Integer sortOrder = readOptionalInt(sec, "sort_order", "order");
        if (sortOrder != null) builder.uiSortOrder(sortOrder);
        // v3.0: encrypted-block visual — masquerade this stage's locked blocks until owned.
        builder.encryptBlocks(readBool(sec, "encrypt_blocks"));
        String encryptAs = sec.get("encrypt_as");
        if (encryptAs != null && !encryptAs.isBlank()) {
            if (ResourceLocation.tryParse(encryptAs.trim()) == null) {
                throw new IllegalArgumentException("Invalid encrypted block substitute. " + encryptAs);
            }
            builder.encryptAs(encryptAs.trim());
        }
    }

    /** Returns the boolean if explicitly present, else {@code null} (inherit global default). */
    private static Boolean readOptionalBool(Config c, String key) {
        Object v = c.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) {
            String t = s.trim().toLowerCase(java.util.Locale.ROOT);
            if (t.equals("true"))  return Boolean.TRUE;
            if (t.equals("false")) return Boolean.FALSE;
        }
        return null;
    }

    private static Integer readOptionalInt(Config c, String... keys) {
        for (String key : keys) {
            Object v = c.get(key);
            if (v instanceof Number n) return n.intValue();
            if (v instanceof String s) {
                try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private static String stringValue(Config c, String... keys) {
        for (String key : keys) {
            Object v = c.get(key);
            if (v instanceof String s && !s.isBlank()) return s;
        }
        return null;
    }

    private static String normalizeEnum(String value, java.util.Set<String> allowed, String fallback) {
        String normalized = value == null ? fallback : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException("Invalid value. " + value);
        return normalized;
    }

    // -------------------- [stage].dependency --------------------

    private static List<StageId> parseDependencies(Config stageSection) {
        List<StageId> out = new ArrayList<>();
        Object value = stageSection.get("dependency");
        if (value == null) value = stageSection.get("dependencies");
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
            throw new IllegalArgumentException("Invalid dependency ID. " + s, e);
        }
    }

    private static ActiveLockDefinition parseActiveLocks(Config config) {
        Config section = config.get("active_locks");
        if (section == null) return ActiveLockDefinition.EMPTY;
        for (var entry : section.entrySet()) {
            String key = entry.getKey();
            if (!java.util.Set.of("scope", "items", "enforcement").contains(key)) {
                throw new IllegalArgumentException("Unknown active lock category. " + key);
            }
        }
        String rawScope = section.getOrElse("scope", "structure_session");
        ActiveLockDefinition.Scope scope = switch (rawScope.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "structure_session" -> ActiveLockDefinition.Scope.STRUCTURE_SESSION;
            default -> throw new IllegalArgumentException("Unknown active lock scope. " + rawScope);
        };
        Config items = section.get("items");
        if (items != null) {
            for (var entry : items.entrySet()) {
                String key = entry.getKey();
                if (!java.util.Set.of("locked", "always_unlocked").contains(key)) {
                    throw new IllegalArgumentException("Unknown active item lock field. " + key);
                }
            }
        }
        CategoryLocks itemLocks = items == null ? CategoryLocks.EMPTY
            : parseCategoryLists(items, "locked", "always_unlocked");
        Config enforcement = section.get("enforcement");
        if (enforcement != null) {
            for (var entry : enforcement.entrySet()) {
                String key = entry.getKey();
                if (!key.equals("block_item_use")) {
                    throw new IllegalArgumentException("Unknown active lock enforcement field. " + key);
                }
            }
        }
        boolean blockItemUse = enforcement == null || enforcement.getOrElse("block_item_use", true);
        return new ActiveLockDefinition(scope, itemLocks, blockItemUse);
    }

    // -------------------- locks --------------------

    private static LockDefinition parseLocks(Config config) {
        LockDefinition.Builder b = LockDefinition.builder();

        b.items(        parseCategory(config, "items"));
        b.blocks(       parseCategory(config, "blocks"));
        b.fluids(       parseCategory(config, "fluids"));
        b.entities(     parseCategory(config, "entities"));
        b.enchants(     parseCategory(config, "enchants"));
        b.enchantCaps(  parseEnchantCaps(config));
        b.crops(        parseCategory(config, "crops"));
        b.screens(      parseCategory(config, "screens"));
        b.loot(         parseCategory(config, "loot"));
        b.trades(       parseCategory(config, "trades"));
        b.professions(  parseCategory(config, "professions"));
        b.advancements( parseCategory(config, "advancements"));
        b.beacon(       parseCategory(config, "beacon"));
        b.brewing(      parseCategory(config, "brewing"));
        b.mobSpawns(    parseCategoryField(config, "mobs", "locked_spawns"));

        // pets: two named lists in the same table
        Config petsSection = config.get("pets");
        b.petsTaming(petsSection == null ? CategoryLocks.EMPTY
            : parseCategoryLists(petsSection, "locked_taming", null));
        b.petsBreeding(petsSection == null ? CategoryLocks.EMPTY
            : parseCategoryLists(petsSection, "locked_breeding", null));
        b.petsCommanding(petsSection == null ? CategoryLocks.EMPTY
            : parseCategoryLists(petsSection, "locked_commanding", null));

        // [curios].locked_slots — slot identifiers (plain strings, not prefix entries)
        Config curiosSection = config.get("curios");
        if (curiosSection != null) {
            b.curioLockedSlots(stringList(curiosSection, "locked_slots"));
        }

        // recipes: two named lists
        Config recipesSection = config.get("recipes");
        b.recipeIds(recipesSection == null ? CategoryLocks.EMPTY
            : parseCategoryLists(recipesSection, "locked_ids", null));
        b.recipeOutputs(recipesSection == null ? CategoryLocks.EMPTY
            : parseCategoryLists(recipesSection, "locked_items", null));

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
            String body = s.startsWith("id:") ? s.substring(3) : s;
            ResourceLocation id = ResourceLocation.tryParse(body);
            if (id == null) throw new IllegalArgumentException("Invalid resource ID. " + s);
            out.add(id);
        }
        return out;
    }

    private static java.util.Set<String> parseModSet(Config sec, String field) {
        List<String> raw = stringList(sec, field);
        if (raw.isEmpty()) return java.util.Collections.emptySet();
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (String s : raw) {
            String body = s.startsWith("mod:") ? s.substring(4) : s;
            body = body.trim().toLowerCase(java.util.Locale.ROOT);
            if (body.isEmpty() || !body.matches("[a-z0-9_.-]+")) {
                throw new IllegalArgumentException("Invalid mod ID. " + s);
            }
            out.add(body);
        }
        return out;
    }

    /** v3.0: parse {@code [enchants].max_levels = ["minecraft:sharpness:3", ...]} → enchant level caps. */
    private static List<LockDefinition.EnchantCap> parseEnchantCaps(Config config) {
        Config sec = config.get("enchants");
        if (sec == null) return Collections.emptyList();
        List<LockDefinition.EnchantCap> out = new ArrayList<>();
        for (String raw : stringList(sec, "max_levels")) {
            if (raw == null) throw new IllegalArgumentException("Invalid enchantment level cap");
            int idx = raw.lastIndexOf(':');
            if (idx <= 0 || idx >= raw.length() - 1) {
                throw new IllegalArgumentException("Invalid enchantment level cap. " + raw);
            }
            String lvlPart = raw.substring(idx + 1);
            if (!lvlPart.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("Invalid enchantment level cap. " + raw);
            }
            ResourceLocation id = ResourceLocation.tryParse(raw.substring(0, idx));
            if (id == null) throw new IllegalArgumentException("Invalid enchantment ID. " + raw);
            try {
                out.add(new LockDefinition.EnchantCap(id, Math.max(0, Integer.parseInt(lvlPart))));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid enchantment level cap. " + raw, e);
            }
        }
        return out;
    }

    private static CategoryLocks parseCategory(Config config, String sectionName) {
        Config section = config.get(sectionName);
        if (section == null) return CategoryLocks.EMPTY;
        return parseCategoryLists(section, "locked", "always_unlocked");
    }

    /** Build a CategoryLocks from a single named field inside a section (no always_unlocked). */
    private static CategoryLocks parseCategoryField(Config config, String sectionName, String fieldName) {
        Config section = config.get(sectionName);
        if (section == null) return CategoryLocks.EMPTY;
        return parseCategoryLists(section, fieldName, null);
    }

    private static CategoryLocks parseCategoryLists(Config section, String lockedField,
                                                     String alwaysUnlockedField) {
        List<String> locked = stringList(section, lockedField);
        for (String raw : locked) {
            if (PrefixEntry.parse(raw) == null) {
                throw new IllegalArgumentException("Invalid lock entry. " + raw);
            }
        }
        List<String> alwaysUnlocked = alwaysUnlockedField == null
            ? List.of() : stringList(section, alwaysUnlockedField);
        for (String raw : alwaysUnlocked) {
            PrefixEntry entry = PrefixEntry.parse(raw);
            if (entry == null || entry.kind() != PrefixEntry.Kind.ID || entry.id() == null) {
                throw new IllegalArgumentException("Invalid always unlocked entry. " + raw);
            }
        }
        return CategoryLocks.builder().addLocked(locked).addAlwaysUnlocked(alwaysUnlocked).build();
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
                throw new IllegalArgumentException("Invalid dimension ID. " + raw);
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
            type = type.trim().toLowerCase(java.util.Locale.ROOT);
            if (!java.util.Set.of("item_on_block", "block_right_click", "item_on_entity").contains(type)) {
                throw new IllegalArgumentException("Invalid interaction type. " + type);
            }
            String heldItem = c.get("held_item");
            String description = c.get("description");
            String target = "item_on_entity".equals(type) ? c.get("target_entity") : c.get("target_block");
            if (target == null || target.isBlank()) {
                throw new IllegalArgumentException("An interaction entry is missing its target");
            }
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
                throw new IllegalArgumentException("Invalid mob replacement entry");
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
                throw new IllegalArgumentException("Invalid region entry");
            }
            ResourceLocation dim;
            try {
                dim = ResourceLocation.parse(dimRaw);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid region dimension. " + dimRaw, e);
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
        CategoryLocks lockedEntry = parseCategoryLists(section, "locked_entry", null);
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
                throw new IllegalArgumentException("Invalid ore override entry");
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
                String t = s.trim().toLowerCase(java.util.Locale.ROOT);
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
        private final Config sourceConfig;

        private ParseResult(StageDefinition def, String error, boolean syntax, Config sourceConfig) {
            this.stageDefinition = def;
            this.errorMessage = error;
            this.syntaxError = syntax;
            this.sourceConfig = sourceConfig;
        }

        public static ParseResult success(StageDefinition def)     { return new ParseResult(def, null, false, null); }
        public static ParseResult success(StageDefinition def, Config sourceConfig) { return new ParseResult(def, null, false, sourceConfig); }
        public static ParseResult syntaxError(String msg)          { return new ParseResult(null, msg, true, null); }
        public static ParseResult validationError(String msg)      { return new ParseResult(null, msg, false, null); }

        public boolean isSuccess()              { return stageDefinition != null; }
        public boolean isSyntaxError()          { return syntaxError; }
        public StageDefinition getStageDefinition() { return stageDefinition; }
        public String getErrorMessage()         { return errorMessage; }
        public Config getSourceConfig()         { return sourceConfig == null ? null : Config.copy(sourceConfig); }
    }
}
