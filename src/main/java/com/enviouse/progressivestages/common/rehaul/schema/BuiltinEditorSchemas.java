package com.enviouse.progressivestages.common.rehaul.schema;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.enviouse.progressivestages.common.config.StageConfig;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

final class BuiltinEditorSchemas {

    private static final Set<String> PREFIXES = Set.of("all", "id", "mod", "tag", "name");

    private BuiltinEditorSchemas() {}

    static void populate(BiConsumer<ResourceLocation, EditorFieldSchema> sink) {
        add(sink, "stage.id", "stage.toml", "stage.id", "Stage ID", "The permanent namespaced identity.",
            SchemaValueType.RESOURCE_ID, null, true, null, Set.of(), List.of());
        add(sink, "stage.name", "stage.toml", "stage.display_name", "Name", "The player facing stage name.",
            SchemaValueType.STRING, "", true, null, Set.of(), List.of());
        add(sink, "stage.description", "stage.toml", "stage.description", "Description", "The stage explanation.",
            SchemaValueType.STRING, "", false, null, Set.of(), List.of());
        add(sink, "stage.icon", "stage.toml", "stage.icon", "Icon", "The item shown for this stage.",
            SchemaValueType.RESOURCE_ID, "minecraft:stone", false, catalog("items"), Set.of(), List.of());
        add(sink, "stage.dependencies", "stage.toml", "stage.dependencies", "Dependencies", "Stages required before this stage.",
            SchemaValueType.LIST, List.of(), false, catalog("stages"), Set.of(), List.of());
        add(sink, "stage.dependency_mode", "stage.toml", "stage.dependency_mode", "Dependency rule", "Require every selected stage, any selected stage, or a chosen minimum.",
            SchemaValueType.ENUM, "all", false, null, Set.of(), List.of("all", "any", "at_least"));
        add(sink, "stage.dependency_count", "stage.toml", "stage.dependency_count", "Dependency minimum", "The number of selected stages required when dependency rule is at least.",
            SchemaValueType.INTEGER, 1, false, null, Set.of(), List.of());
        add(sink, "stage.priority", "stage.toml", "stage.priority", "Priority", "The inherited decision priority.",
            SchemaValueType.INTEGER, 0, false, null, Set.of(), List.of());
        add(sink, "stage.scope", "stage.toml", "stage.scope", "Scope", "The ownership scope.",
            SchemaValueType.ENUM, "team", false, null, Set.of(), List.of("team", "server"));
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

        category(sink, "items", "items", List.of("use", "pickup", "inventory", "hotbar", "mouse_pickup", "drop"));
        category(sink, "blocks", "blocks", List.of("place", "break", "interact"));
        category(sink, "fluids", "fluids", List.of("pickup", "place", "flow", "submerge"));
        category(sink, "recipes", "recipes", List.of("craft", "automate", "display"));
        category(sink, "crops", "blocks", List.of("plant", "grow", "bonemeal", "harvest"));
        category(sink, "dimensions", "dimensions", List.of("enter", "portal", "teleport"));
        category(sink, "enchants", "enchantments", List.of("table", "anvil", "trade", "hold"));
        category(sink, "entities", "entities", List.of("presence", "attack", "interact", "mount"));
        category(sink, "interactions", "items", List.of("block_right_click", "item_on_block", "item_on_entity"));
        category(sink, "loot", "loot_tables", List.of("generate", "open", "drop"));
        category(sink, "mobs", "entities", List.of("spawn", "replace"));
        category(sink, "pets", "entities", List.of("tame", "breed", "command", "ride"));
        category(sink, "screens", "menus", List.of("open"));
        category(sink, "trades", "items", List.of("display", "purchase"));
        category(sink, "professions", "professions", List.of("trade"));
        category(sink, "advancements", "advancements", List.of("display", "toast"));
        category(sink, "structures", "structures", List.of("enter", "break", "place", "explode", "spawn"));
        category(sink, "regions", "dimensions", List.of("enter", "break", "place", "explode", "spawn"));
        category(sink, "curios", "curios_slots", List.of("equip", "retain"));
        category(sink, "ores", "blocks", List.of("display", "drop"));
        category(sink, "beacon", "effects", List.of("apply"));
        category(sink, "brewing", "potions", List.of("brew", "take"));
        category(sink, "abilities", "abilities", List.of("use"));

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
                                 String catalog, List<String> actions) {
        add(sink, "rules." + category + ".locked", "rules.toml", category + ".locked", title(category) + " locked",
            "One selector per line. These entries are denied until the stage is owned.",
            SchemaValueType.PREFIX, List.of(), false, catalog(catalog), PREFIXES, actions);
        add(sink, "rules." + category + ".allowed", "rules.toml", category + ".allowed", title(category) + " allowed",
            "One selector per line. These are explicit allows resolved by priority.",
            SchemaValueType.PREFIX, List.of(), false, catalog(catalog), PREFIXES, actions);
        add(sink, "rules." + category + ".priority", "rules.toml", category + ".priority", title(category) + " priority",
            "The category priority used when an entry has no explicit priority.",
            SchemaValueType.INTEGER, 0, false, null, Set.of(), List.of());
        add(sink, "rules." + category + ".priorities", "rules.toml", category + ".priorities", title(category) + " entry priorities",
            "An inline object that maps exact selector text to priority.",
            SchemaValueType.OBJECT, Map.of(), false, null, Set.of(), List.of());
        add(sink, "rules." + category + ".presentation", "rules.toml", category + ".presentation", title(category) + " viewer policy",
            "Per selector EMI and JEI show hide overlay and priority settings.",
            SchemaValueType.OBJECT, Map.of(), false, catalog(catalog), PREFIXES, List.of());
    }

    private static void add(BiConsumer<ResourceLocation, EditorFieldSchema> sink, String idPath,
                            String file, String path, String label, String help, SchemaValueType type,
                            Object defaultValue, boolean required, ResourceLocation catalog,
                            Set<String> prefixes, List<String> values) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("progressivestages", idPath.replace('.', '/'));
        List<String> enumValues = type == SchemaValueType.ENUM ? values : List.of();
        Map<String, Object> hints = type == SchemaValueType.PREFIX ? Map.of("actions", values) : Map.of();
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
            sink.accept(id, new EditorFieldSchema(id, "progressivestages.toml", path,
                title(entry.getKey()), help, type, defaultValue, false, null, Set.of(), List.of(),
                Set.of(), restart, Map.of("generated", true)));
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
