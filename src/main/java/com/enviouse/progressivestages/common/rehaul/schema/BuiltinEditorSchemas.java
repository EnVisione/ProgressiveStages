package com.enviouse.progressivestages.common.rehaul.schema;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.config.LuckPermsStageOptions;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

final class BuiltinEditorSchemas {

    private static final Set<String> PREFIXES = Set.of("all", "id", "mod", "tag", "name");
    private static final Set<String> EXACT_ID = Set.of("id");

    private BuiltinEditorSchemas() {}

    static void populate(BiConsumer<ResourceLocation, EditorFieldSchema> sink) {
        add(sink, "stage.id", "stage.toml", "stage.id", "Stage ID", "The permanent namespaced identity.",
            SchemaValueType.RESOURCE_ID, null, true, null, Set.of(), List.of());
        add(sink, "stage.name", "stage.toml", "stage.display_name", "Stage name", "The name players see in the stage menu. Example: Miner.",
            SchemaValueType.STRING, "", true, null, Set.of(), List.of());
        add(sink, "stage.description", "stage.toml", "stage.description", "Description", "The stage explanation.",
            SchemaValueType.STRING, "", false, null, Set.of(), List.of());
        add(sink, "stage.icon", "stage.toml", "stage.icon", "Stage icon", "The item shown beside this stage. Example: minecraft:iron_pickaxe.",
            SchemaValueType.RESOURCE_ID, "minecraft:stone", false, catalog("items"), Set.of(), List.of());
        add(sink, "stage.dependencies", "stage.toml", "stage.dependencies", "Required stages", "Stages that must be complete before this stage can be earned.",
            SchemaValueType.LIST, List.of(), false, catalog("stages"), Set.of(), List.of());
        add(sink, "stage.dependency_mode", "stage.toml", "stage.dependency_mode", "Dependency rule", "Require every selected stage, any selected stage, or a chosen minimum.",
            SchemaValueType.ENUM, "all", false, null, Set.of(), List.of("all", "any", "at_least"));
        add(sink, "stage.dependency_count", "stage.toml", "stage.dependency_count", "Dependency minimum", "The number of selected stages required when dependency rule is at least.",
            SchemaValueType.INTEGER, 1, false, null, Set.of(), List.of());
        add(sink, "stage.priority", "stage.toml", "stage.priority", "Priority", "The inherited decision priority.",
            SchemaValueType.INTEGER, 0, false, null, Set.of(), List.of());
        add(sink, "stage.scope", "stage.toml", "stage.scope", "Scope", "The ownership scope.",
            SchemaValueType.ENUM, "team", false, null, Set.of(), List.of("team", "server"));
        add(sink, "stage.team_stage", "stage.toml", "stage.team_stage", "Team stage",
            "Optional override for team sharing. Leave unset to inherit the global team mode. Server stages cannot override this field.",
            SchemaValueType.BOOLEAN, null, false, null, Set.of(), List.of());
        add(sink, "stage.tags", "stage.toml", "stage.tags", "Tags", "Labels used for grouping and automation.",
            SchemaValueType.LIST, List.of(), false, null, Set.of(), List.of());
        add(sink, "stage.slot_group", "stage.toml", "stage.slot_group", "Slot group", "Related stages that share an ownership limit.",
            SchemaValueType.STRING, "", false, null, Set.of(), List.of());
        add(sink, "stage.slot_limit", "stage.toml", "stage.slot_limit", "Slot limit", "Maximum active stages in this group. Zero allows every stage to stack.",
            SchemaValueType.INTEGER, 0, false, null, Set.of(), List.of());
        add(sink, "stage.slot_policy", "stage.toml", "stage.slot_policy", "Full group behavior", "Block or replace stages when the group is full.",
            SchemaValueType.ENUM, "deny", false, null, Set.of(),
            List.of("deny", "replace_oldest", "replace_lowest_priority", "replace_all"));
        add(sink, "stage.category", "stage.toml", "stage.category", "Category", "The stage map and search category.",
            SchemaValueType.STRING, "", false, null, Set.of(), List.of());
        add(sink, "stage.color", "stage.toml", "stage.color", "Color", "The stage tint as a hex color or color code.",
            SchemaValueType.STRING, "", false, null, Set.of(), List.of());
        add(sink, "stage.hidden", "stage.toml", "stage.hidden", "Hidden", "Hide this stage from normal player views.",
            SchemaValueType.BOOLEAN, false, false, null, Set.of(), List.of());
        add(sink, "stage.duration", "stage.toml", "stage.duration", "Ownership duration", "Automatically expire ownership after this duration.",
            SchemaValueType.DURATION, "", false, null, Set.of(), List.of());
        add(sink, "permissions.luckperms.enabled", "stage.toml", "luckperms.enabled", "LuckPerms bridge",
            "Enable this stage's optional LuckPerms mappings.", SchemaValueType.BOOLEAN, true, false, null, Set.of(), List.of());
        add(sink, "permissions.luckperms.inbound_mode", "stage.toml", "luckperms.inbound_mode", "Inbound retention",
            "Keep or synchronize access granted by a LuckPerms row.", SchemaValueType.ENUM, "synchronized", false, null,
            Set.of(), List.of("synchronized", "permanent"));
        add(sink, "permissions.luckperms.inbound", "stage.toml", "luckperms.inbound", "Inbound rules",
            "LuckPerms groups, Boolean permissions, match mode and contexts that grant this stage.", SchemaValueType.OBJECT,
            List.of(), false, catalog("permissions"), Set.of(), List.of());
        add(sink, "permissions.luckperms.outbound", "stage.toml", "luckperms.outbound", "Outbound rules",
            "Transient existing groups or positive permissions contributed by this stage.", SchemaValueType.OBJECT,
            List.of(), false, catalog("permissions"), Set.of(), List.of());
        for (String direction : List.of("inbound", "outbound")) {
            add(sink, "permissions.luckperms." + direction + ".contexts", "stage.toml",
                "luckperms." + direction + "[].contexts", "Contexts",
                "All context keys must match. Any listed value may match within one key.",
                SchemaValueType.OBJECT, Map.of(), false, null, Set.of(), List.of());
        }
        add(sink, "permissions.command_permissions", "stage.toml", "command_permissions", "Command gates",
            "Literal command paths that require this stage while native permissions remain required.", SchemaValueType.OBJECT,
            List.of(), false, catalog("commands"), Set.of(), List.of());
        add(sink, "permissions.command_descendants", "stage.toml", "command_permissions[].descendants", "Gate descendants",
            "Require the stage for child literals and their arguments. Enabled when omitted.", SchemaValueType.BOOLEAN,
            true, false, null, Set.of(), List.of());
        add(sink, "display.background", "stage.toml", "display.background", "Background", "The stage map background texture.",
            SchemaValueType.RESOURCE_ID, "minecraft:textures/gui/advancements/backgrounds/stone.png", false,
            catalog("textures"), Set.of(), List.of());
        add(sink, "display.frame", "stage.toml", "display.frame", "Frame", "The vanilla stage frame.",
            SchemaValueType.ENUM, "task", false, null, Set.of(), List.of("task", "goal", "challenge"));
        add(sink, "display.reveal", "stage.toml", "display.reveal", "Reveal", "When players can see this stage.",
            SchemaValueType.ENUM, "dependencies", false, null, Set.of(),
            List.of("always", "dependencies", "unlocked"));
        add(sink, "display.x", "stage.toml", "display.x", "Map X", "Optional advancement map X coordinate.",
            SchemaValueType.INTEGER, 0, false, null, Set.of(), List.of());
        add(sink, "display.y", "stage.toml", "display.y", "Map Y", "Optional advancement map Y coordinate.",
            SchemaValueType.INTEGER, 0, false, null, Set.of(), List.of());
        add(sink, "display.order", "stage.toml", "display.sort_order", "Map order", "Stable automatic layout lane order.",
            SchemaValueType.INTEGER, 0, false, null, Set.of(), List.of());
        add(sink, "display.encrypt", "stage.toml", "display.encrypt_blocks", "Encrypt locked blocks", "Show locked blocks with a substitute texture.",
            SchemaValueType.BOOLEAN, false, false, null, Set.of(), List.of());
        add(sink, "display.encrypt_as", "stage.toml", "display.encrypt_as", "Encrypted block substitute", "The block shown while encryption is active.",
            SchemaValueType.RESOURCE_ID, "minecraft:stone", false, catalog("blocks"), Set.of(), List.of());

        category(sink, "items", "items", "use");
        category(sink, "blocks", "blocks", "interact");
        add(sink, "rules.blocks.overrides", "rules.toml", "blocks.overrides", "Block overrides",
            "Replace selected blocks and their drops until the stage is owned. Use block IDs, tags, or a mod namespace.",
            SchemaValueType.OBJECT, List.of(), false, null, Set.of(), List.of());
        category(sink, "fluids", "fluids", "interact");
        category(sink, "crops", "blocks", "grow");
        category(sink, "enchants", "enchantments", "apply");
        add(sink, "rules.enchants.max_levels", "rules.toml", "enchants.max_levels",
            "Enchantment level limits",
            "Exact enchantment IDs and maximum levels used until this stage is owned.",
            SchemaValueType.LIST, List.of(), false, catalog("enchantments"), Set.of(), List.of());
        add(sink, "rules.enchants.selection_weights", "rules.toml", "enchants.selection_weights",
            "Enchantment selection weights",
            "Exact enchantment IDs and roll weights used until this stage is owned. Zero prevents generation.",
            SchemaValueType.LIST, List.of(), false, catalog("enchantments"), Set.of(), List.of());
        category(sink, "entities", "entities", "presence");
        category(sink, "loot", "loot_tables", "generate");
        category(sink, "screens", "menus", "open");
        category(sink, "trades", "items", "trade");
        category(sink, "professions", "professions", "trade");
        category(sink, "advancements", "advancements", "display");
        category(sink, "beacon", "effects", "apply");
        category(sink, "brewing", "potions", "take");

        add(sink, "dimensions.locked", "rules.toml", "dimensions.locked", "Dimensions locked",
            "Exact dimension IDs. Mod, tag, name and wildcard selectors are not supported here.",
            SchemaValueType.PREFIX, List.of(), false, catalog("dimensions"), EXACT_ID, List.of("enter"));
        add(sink, "mobs.locked_spawns", "rules.toml", "mobs.locked_spawns", "Mob spawns locked",
            "Entity selectors whose spawning is stopped until this stage is owned.",
            SchemaValueType.PREFIX, List.of(), false, catalog("entities"), PREFIXES, List.of("spawn"));
        for (String action : List.of("taming", "breeding", "commanding")) {
            add(sink, "pets.locked_" + action, "rules.toml", "pets.locked_" + action,
                "Pet " + action + " locked", "Entity selectors for this pet action.",
                SchemaValueType.PREFIX, List.of(), false, catalog("entities"), PREFIXES,
                List.of(switch (action) { case "taming" -> "tame"; case "breeding" -> "breed"; default -> "command"; }));
        }
        add(sink, "curios.locked_slots", "rules.toml", "curios.locked_slots", "Curios slots locked",
            "Exact Curios slot names. This does not use item or block selectors.",
            SchemaValueType.LIST, List.of(), false, catalog("curios_slots"), Set.of(), List.of("equip"));
        add(sink, "abilities.locked", "rules.toml", "abilities.locked", "Abilities locked",
            "Exact ability names such as jump, sprint, swim, climb, or elytra.",
            SchemaValueType.LIST, List.of(), false, catalog("abilities"), Set.of(), List.of("use"));
        add(sink, "recipes.locked_items", "rules.toml", "recipes.locked_items", "Recipe outputs locked",
            "Exact output item IDs. Use locked_ids for recipe identifiers.",
            SchemaValueType.PREFIX, List.of(), false, catalog("items"), EXACT_ID, List.of("craft"));
        add(sink, "recipes.locked_ids", "rules.toml", "recipes.locked_ids", "Recipe IDs locked",
            "Exact recipe IDs. Use locked_items to lock every recipe with a selected output.",
            SchemaValueType.PREFIX, List.of(), false, catalog("recipes"), EXACT_ID, List.of("craft"));

        add(sink, "rules.temporary", "rules.toml", "temporary_rules", "Temporary rules",
            "Rules activated by any condition and lifetime.", SchemaValueType.OBJECT, List.of(), false,
            null, Set.of(), List.of());
        add(sink, "rules.modifiers", "rules.toml", "item_modifiers", "Item modifiers",
            "Contextual attributes effects and behavior.", SchemaValueType.OBJECT, List.of(), false,
            null, Set.of(), List.of());
        add(sink, "rules.drop_modifiers", "rules.toml", "drop_modifiers", "Drop modifiers",
            "Selector based block drops with tool enchantment stage condition priority and stacking controls.",
            SchemaValueType.OBJECT, List.of(), false, catalog("blocks"), PREFIXES, List.of("drop"));
        add(sink, "rules.generic", "rules.toml", "rules", "Advanced rules",
            "Permanent lock allow exclusion replacement and presentation entries.", SchemaValueType.OBJECT,
            List.of(), false, null, Set.of(), List.of());
        add(sink, "rules.unlocks", "rules.toml", "unlocks", "Global allows",
            "Higher priority allows that can defeat lower priority locks.", SchemaValueType.OBJECT,
            Map.of(), false, null, Set.of(), List.of());
        add(sink, "progression.grants", "progression.toml", "grants", "Grants",
            "Conditions and actions that grant the stage.", SchemaValueType.CONDITION, List.of(), false,
            catalog("conditions"), Set.of(), List.of());
        add(sink, "progression.revokes", "progression.toml", "revokes", "Revokes",
            "Conditions and actions that revoke the stage.", SchemaValueType.CONDITION, List.of(), false,
            catalog("conditions"), Set.of(), List.of());
        add(sink, "progression.rewards", "progression.toml", "rewards", "Rewards",
            "Actions run after a successful grant.", SchemaValueType.ACTION, List.of(), false,
            catalog("actions"), Set.of(), List.of());
        add(sink, "progression.cost", "progression.toml", "cost", "Cost",
            "The price cooldown and refund policy.", SchemaValueType.OBJECT, Map.of(), false,
            null, Set.of(), List.of());
        add(sink, "progression.challenges", "progression.toml", "challenges", "Challenges",
            "Session budgets sequences success and failure.", SchemaValueType.OBJECT, List.of(), false,
            catalog("challenge_measures"), Set.of(), List.of());
        add(sink, "progression.variables", "progression.toml", "variables", "Variables and currencies",
            "Typed bounded player team or server values.", SchemaValueType.OBJECT, List.of(), false,
            null, Set.of(), List.of());
        add(sink, "progression.formulas", "progression.toml", "formulas", "Formulas",
            "Safe arithmetic expressions that reference variables and other formulas.", SchemaValueType.OBJECT,
            Map.of(), false, null, Set.of(), List.of());
        add(sink, "progression.profiles", "progression.toml", "profiles", "Affinity profiles",
            "Proficiency levels that deny weaken strengthen or transform content behavior.", SchemaValueType.OBJECT,
            List.of(), false, null, Set.of(), List.of());
        add(sink, "progression.templates", "progression.toml", "templates", "Templates and bundles",
            "Reusable typed fragments with includes parameters and merge policies.", SchemaValueType.OBJECT,
            List.of(), false, null, Set.of(), List.of());
        add(sink, "progression.states", "progression.toml", "states", "Stage states",
            "Custom states ownership states and guarded transitions.", SchemaValueType.OBJECT,
            Map.of(), false, null, Set.of(), List.of());
        addSettings(sink, StageConfig.SPEC.getSpec(), "");
    }

    private static void category(BiConsumer<ResourceLocation, EditorFieldSchema> sink, String category,
                                 String catalog, String action) {
        add(sink, "rules." + category + ".locked", "rules.toml", category + ".locked", title(category) + " locked",
            "One selector per line. These entries are denied until the stage is owned.",
            SchemaValueType.PREFIX, List.of(), false, catalog(catalog), PREFIXES, List.of(action));
        add(sink, "rules." + category + ".always_unlocked", "rules.toml", category + ".always_unlocked",
            title(category) + " always unlocked", "Exact IDs excluded from this stage lock. Other selector types are not allowed.",
            SchemaValueType.PREFIX, List.of(), false, catalog(catalog), EXACT_ID, List.of(action));
    }

    private static void add(BiConsumer<ResourceLocation, EditorFieldSchema> sink, String idPath,
                            String file, String path, String label, String help, SchemaValueType type,
                            Object defaultValue, boolean required, ResourceLocation catalog,
                            Set<String> prefixes, List<String> values) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("progressivestages", idPath.replace('.', '/'));
        List<String> enumValues = type == SchemaValueType.ENUM ? values : List.of();
        Map<String, Object> hints = type == SchemaValueType.PREFIX ? Map.of("actions", values) : Map.of();
        if (path.equals("luckperms.inbound[].contexts") || path.equals("luckperms.outbound[].contexts")) {
            hints = Map.of("maxKeys", LuckPermsStageOptions.MAX_CONTEXT_KEYS,
                "maxValues", LuckPermsStageOptions.MAX_CONTEXT_VALUES,
                "maxCombinations", LuckPermsStageOptions.MAX_CONTEXT_COMBINATIONS);
        }
        sink.accept(id, new EditorFieldSchema(id, file, path, label, help, type, defaultValue,
            required, catalog, prefixes, enumValues, Set.of(), RestartRequirement.LIVE_APPLY, hints));
    }

    private static ResourceLocation catalog(String path) {
        return ResourceLocation.fromNamespaceAndPath("progressivestages", path);
    }

    private static void addSettings(BiConsumer<ResourceLocation, EditorFieldSchema> sink,
                                    UnmodifiableConfig config, String parent) {
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            String path = parent.isEmpty() ? entry.getKey() : parent + "." + entry.getKey();
            Object value = entry.getRawValue();
            if (value instanceof UnmodifiableConfig nested) {
                addSettings(sink, nested, path);
                continue;
            }
            if (!(value instanceof ModConfigSpec.ValueSpec spec)) continue;
            Object defaultValue = spec.getDefault();
            SchemaValueType type = settingType(defaultValue);
            String help = spec.getComment();
            if (help == null || help.isBlank()) help = "Configure " + path.replace('_', ' ') + ".";
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("progressivestages", "settings/" + path.replace('.', '/'));
            RestartRequirement restart = switch (spec.restartType()) {
                case NONE -> RestartRequirement.LIVE_APPLY;
                case WORLD -> RestartRequirement.SERVER_RESTART;
                case GAME -> RestartRequirement.CLIENT_RESTART;
            };
            Map<String, Object> hints = new LinkedHashMap<>();
            hints.put("generated", true);
            if (type == SchemaValueType.INTEGER || type == SchemaValueType.DECIMAL) {
                ModConfigSpec.Range<?> range = spec.getRange();
                if (range != null) {
                    hints.put("min", range.getMin());
                    hints.put("max", range.getMax());
                }
            }
            sink.accept(id, new EditorFieldSchema(id, "progressivestages.toml", path,
                title(entry.getKey()), help, type, defaultValue, false, null, Set.of(), List.of(),
                Set.of(), restart, hints));
        }
    }

    private static SchemaValueType settingType(Object value) {
        if (value instanceof Boolean) return SchemaValueType.BOOLEAN;
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return SchemaValueType.INTEGER;
        }
        if (value instanceof Number) return SchemaValueType.DECIMAL;
        if (value instanceof Iterable<?>) return SchemaValueType.LIST;
        return SchemaValueType.STRING;
    }

    private static String title(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).replace('_', ' ');
    }
}
