package com.enviouse.progressivestages.server.loader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DefaultShowcaseStages {

    private static final String BACKGROUND = "minecraft:textures/gui/advancements/backgrounds/stone.png";

    private DefaultShowcaseStages() {}

    public static Map<String, String> files() {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        for (StageTemplate stage : stages()) {
            String folder = stage.id().substring(stage.id().indexOf(':') + 1);
            files.put(folder + "/stage.toml", identity(stage));
            files.put(folder + "/rules.toml", normalized(stage.rules()));
            files.put(folder + "/progression.toml", normalized(stage.progression()));
        }
        return java.util.Collections.unmodifiableMap(files);
    }

    public static int stageCount() {
        return stages().size();
    }

    public static List<String> stageIds() {
        return stages().stream().map(StageTemplate::id).toList();
    }

    private static List<StageTemplate> stages() {
        return List.of(
            stage("showcase:mage", "Mage", "Begin an arcane path and unlock magical tools.",
                "minecraft:amethyst_shard", List.of(), "all", 1, "Arcane", "#a970ff", "task",
                lock("items", "use", "minecraft:blaze_rod", 200),
                purchase(0, "minecraft:lapis_lazuli:16") + reward("minecraft:experience_bottle:4", 2)),
            stage("showcase:warrior", "Warrior", "Begin a martial path built around direct combat.",
                "minecraft:iron_sword", List.of(), "all", 1, "Martial", "#d65c4a", "task",
                lock("items", "use", "tag:minecraft:swords", 200),
                purchase(0, "minecraft:iron_ingot:16") + reward("minecraft:iron_sword:1", 0)),
            stage("showcase:paladin", "Paladin", "Begin a defensive path of shields and radiant gear.",
                "minecraft:shield", List.of(), "all", 1, "Radiant", "#f2c44d", "task",
                lock("items", "use", "minecraft:shield", 200),
                purchase(0, "minecraft:gold_ingot:12") + reward("minecraft:shield:1", 0)),
            stage("showcase:healer", "Healer", "Begin a support path focused on recovery and survival.",
                "minecraft:golden_apple", List.of(), "all", 1, "Support", "#ef78a7", "task",
                lock("items", "use", "minecraft:golden_apple", 200),
                purchase(0, "minecraft:glistering_melon_slice:16") + reward("minecraft:honey_bottle:4", 0)),
            stage("showcase:ranger", "Ranger", "Begin a mobile path of bows pets and exploration.",
                "minecraft:bow", List.of(), "all", 1, "Wild", "#69b86b", "task",
                lock("items", "use", "minecraft:bow", 200),
                purchase(0, "minecraft:arrow:32") + reward("minecraft:bow:1", 0)),
            stage("showcase:engineer", "Engineer", "Begin a technology path of redstone and machines.",
                "minecraft:redstone", List.of(), "all", 1, "Technology", "#e58b39", "task",
                lock("blocks", "interact", "minecraft:repeater", 200),
                purchase(0, "minecraft:redstone:32") + reward("minecraft:repeater:2", 0)),

            stage("showcase:wizard", "Wizard", "Refine Mage into a disciplined spellcasting path.",
                "minecraft:enchanted_book", List.of("showcase:mage"), "all", 1, "Arcane", "#9e70ff", "goal",
                itemAttribute("showcase:wizard/focus", "minecraft:blaze_rod", "minecraft:generic.attack_damage", 2.0, 260),
                purchase(8, "minecraft:lapis_lazuli:24", "minecraft:book:8")),
            stage("showcase:warlock", "Warlock", "Refine Mage through dangerous forbidden knowledge.",
                "minecraft:ender_eye", List.of("showcase:mage"), "all", 1, "Arcane", "#70428f", "goal",
                temporaryRule("showcase:warlock/nether_focus", "allow", "items", "use", "minecraft:ender_pearl", "dimension", "minecraft:the_nether", 450),
                grant("kill", "minecraft:evoker", 1) + reward("minecraft:ender_pearl:8", 3)),
            stage("showcase:knight", "Knight", "Refine Warrior into an armored protector.",
                "minecraft:iron_chestplate", List.of("showcase:warrior"), "all", 1, "Martial", "#b9bec6", "goal",
                itemEquipmentAttribute("showcase:knight/armor", "tag:minecraft:chest_armor", "minecraft:generic.armor", 2.0, 260),
                purchase(5, "minecraft:iron_ingot:32")),
            stage("showcase:berserker", "Berserker", "Refine Warrior into a relentless high damage fighter.",
                "minecraft:diamond_axe", List.of("showcase:warrior"), "all", 1, "Martial", "#b33c32", "goal",
                itemAttribute("showcase:berserker/axes", "tag:minecraft:axes", "minecraft:generic.attack_damage", 3.0, 270),
                grant("kill", "minecraft:ravager", 1) + effectReward("minecraft:strength", 120, 0)),
            stage("showcase:templar", "Templar", "Refine Paladin into a resilient guardian.",
                "minecraft:golden_helmet", List.of("showcase:paladin"), "all", 1, "Radiant", "#f0d06b", "goal",
                itemAttribute("showcase:templar/health", "minecraft:shield", "minecraft:generic.max_health", 4.0, 270),
                purchase(6, "minecraft:gold_ingot:24")),
            stage("showcase:crusader", "Crusader", "Refine Paladin into an aggressive radiant fighter.",
                "minecraft:golden_sword", List.of("showcase:paladin"), "all", 1, "Radiant", "#e39b36", "goal",
                lock("entities", "attack", "minecraft:villager", 350),
                grant("kill", "minecraft:pillager", 20) + reward("minecraft:golden_carrot:8", 2)),
            stage("showcase:cleric", "Cleric", "Refine Healer into a durable battlefield support class.",
                "minecraft:brewing_stand", List.of("showcase:healer"), "all", 1, "Support", "#ff9cc4", "goal",
                itemEffect("showcase:cleric/regeneration", "minecraft:golden_apple", "minecraft:regeneration", 0, 80, 280),
                purchase(8, "minecraft:emerald:16")),
            stage("showcase:alchemist", "Alchemist", "Refine Healer through brewing ingredients and utility.",
                "minecraft:potion", List.of("showcase:healer"), "all", 1, "Support", "#8bcf9d", "goal",
                lock("blocks", "interact", "minecraft:brewing_stand", 230),
                grant("craft", "minecraft:brewing_stand", 1) + reward("minecraft:blaze_powder:8", 2)),
            stage("showcase:beastmaster", "Beastmaster", "Refine Ranger through animal companions.",
                "minecraft:bone", List.of("showcase:ranger"), "all", 1, "Wild", "#8bb65d", "goal",
                lock("entities", "interact", "minecraft:wolf", 230),
                grant("tame", "minecraft:wolf", 3) + reward("minecraft:bone:16", 0)),
            stage("showcase:marksman", "Marksman", "Refine Ranger through precise ranged combat.",
                "minecraft:crossbow", List.of("showcase:ranger"), "all", 1, "Wild", "#4e9f68", "goal",
                itemAttribute("showcase:marksman/mobility", "minecraft:crossbow", "minecraft:generic.movement_speed", 0.03, 280),
                grant("kill_with_item", "minecraft:skeleton", 20) + purchase(0, "minecraft:arrow:64")),
            stage("showcase:mechanist", "Mechanist", "Refine Engineer through moving redstone machinery.",
                "minecraft:piston", List.of("showcase:engineer"), "all", 1, "Technology", "#d87937", "goal",
                lock("blocks", "interact", "minecraft:piston", 230),
                grant("craft", "minecraft:piston", 8) + reward("minecraft:sticky_piston:2", 1)),
            stage("showcase:miner", "Miner", "Refine Engineer through deep resource extraction.",
                "minecraft:iron_pickaxe", List.of("showcase:engineer"), "all", 1, "Technology", "#b99168", "goal",
                itemAttribute("showcase:miner/speed", "tag:minecraft:pickaxes", "minecraft:generic.movement_speed", 0.02, 250),
                grant("mine", "minecraft:iron_ore", 64) + reward("minecraft:torch:32", 1)),

            stage("showcase:archmage", "Archmage", "Master Wizard through dimensional arcane research.",
                "minecraft:end_crystal", List.of("showcase:wizard"), "all", 1, "Arcane mastery", "#c185ff", "challenge",
                lock("items", "use", "minecraft:end_crystal", 320),
                grant("kill", "minecraft:enderman", 32) + purchase(15, "minecraft:amethyst_shard:32")),
            stage("showcase:necromancer", "Necromancer", "Master Warlock through the souls of the Nether.",
                "minecraft:wither_skeleton_skull", List.of("showcase:warlock"), "all", 1, "Arcane mastery", "#63336f", "challenge",
                temporaryRule("showcase:necromancer/soul_safety", "allow", "items", "use", "minecraft:wither_skeleton_skull", "dimension", "minecraft:the_nether", 500),
                grant("kill", "minecraft:wither_skeleton", 25) + reward("minecraft:wither_skeleton_skull:1", 5)),
            stage("showcase:vanguard", "Vanguard", "Master Knight by surviving the strongest raids.",
                "minecraft:netherite_chestplate", List.of("showcase:knight"), "all", 1, "Martial mastery", "#aeb4bf", "challenge",
                itemEquipmentAttribute("showcase:vanguard/health", "tag:minecraft:chest_armor", "minecraft:generic.max_health", 6.0, 350),
                grant("kill", "minecraft:ravager", 3) + purchase(20, "minecraft:diamond:16")),
            stage("showcase:warlord", "Warlord", "Master Berserker through a limited boss challenge.",
                "minecraft:netherite_axe", List.of("showcase:berserker"), "all", 1, "Martial mastery", "#9b302b", "challenge",
                itemAttribute("showcase:warlord/power", "tag:minecraft:axes", "minecraft:generic.attack_damage", 5.0, 360),
                challenge("showcase:warlord/ravager_trial", "Ravager trial", "minecraft:ravager", 4, "8m")
                    + purchase(25, "minecraft:netherite_ingot:2")),
            stage("showcase:oracle", "Oracle", "Master Cleric through exploration and ancient knowledge.",
                "minecraft:echo_shard", List.of("showcase:cleric"), "all", 1, "Support mastery", "#e98db7", "challenge",
                itemEffect("showcase:oracle/vision", "minecraft:spyglass", "minecraft:night_vision", 0, 120, 340),
                grant("advancement", "minecraft:adventure/adventuring_time", 1) + reward("minecraft:experience_bottle:16", 8)),
            stage("showcase:artificer", "Artificer", "Master Mechanist through precise advanced components.",
                "minecraft:comparator", List.of("showcase:mechanist"), "all", 1, "Technology mastery", "#df873b", "challenge",
                temporaryRule("showcase:artificer/end_redstone", "allow", "blocks", "interact", "mod:minecraft", "dimension", "minecraft:the_end", 420),
                grant("craft", "minecraft:comparator", 8) + purchase(12, "minecraft:quartz:32")),
            stage("showcase:diamond_engineer", "Diamond Engineer", "Purchase this specialist class to double Fortune diamond drops only.",
                "minecraft:diamond_pickaxe", List.of("showcase:miner", "showcase:mechanist"), "all", 2, "Technology mastery", "#47d7d7", "challenge",
                diamondFortune(),
                purchase(0, "minecraft:diamond:32") + reward("minecraft:experience_bottle:8", 5)),

            stage("showcase:spellblade", "Spellblade", "Combine Wizard and Knight into a magical melee class.",
                "minecraft:diamond_sword", List.of("showcase:wizard", "showcase:knight"), "all", 2, "Hybrid", "#788fe8", "challenge",
                itemAttribute("showcase:spellblade/blade", "tag:minecraft:swords", "minecraft:generic.attack_damage", 4.0, 420),
                purchase(12, "minecraft:diamond_sword:1", "minecraft:lapis_lazuli:24")),
            stage("showcase:dark_paladin", "Dark Paladin", "Combine Warlock and Crusader into a Nether hybrid.",
                "minecraft:netherite_sword", List.of("showcase:warlock", "showcase:crusader"), "all", 2, "Hybrid", "#7f4c69", "challenge",
                temporaryRule("showcase:dark_paladin/nether_blades", "allow", "items", "use", "tag:minecraft:swords", "dimension", "minecraft:the_nether", 520),
                grant("kill", "minecraft:wither", 1) + reward("minecraft:nether_star:1", 10)),
            stage("showcase:battle_medic", "Battle Medic", "Combine Vanguard and Cleric into armored support.",
                "minecraft:enchanted_golden_apple", List.of("showcase:vanguard", "showcase:cleric"), "all", 2, "Hybrid", "#db7d8d", "challenge",
                itemEffect("showcase:battle_medic/recovery", "minecraft:shield", "minecraft:regeneration", 0, 100, 430),
                purchase(18, "minecraft:golden_apple:8", "minecraft:diamond:8")),
            stage("showcase:arcane_archer", "Arcane Archer", "Combine Wizard and Marksman into a mobile ranged class.",
                "minecraft:spectral_arrow", List.of("showcase:wizard", "showcase:marksman"), "all", 2, "Hybrid", "#6aa9c8", "challenge",
                abilities("elytra") + itemAttribute("showcase:arcane_archer/speed", "minecraft:bow", "minecraft:generic.movement_speed", 0.05, 430),
                purchase(12, "minecraft:spectral_arrow:32", "minecraft:lapis_lazuli:16")),
            stage("showcase:grandmaster", "Grandmaster", "Reach any three mastery or hybrid paths to prove broad progression.",
                "minecraft:nether_star", List.of("showcase:archmage", "showcase:necromancer", "showcase:warlord",
                    "showcase:oracle", "showcase:artificer", "showcase:diamond_engineer", "showcase:spellblade",
                    "showcase:dark_paladin", "showcase:battle_medic", "showcase:arcane_archer"),
                "at_least", 3, "Finale", "#f7bf43", "challenge",
                itemAttribute("showcase:grandmaster/health", "minecraft:nether_star", "minecraft:generic.max_health", 10.0, 800),
                grandmasterProgression())
        );
    }

    private static StageTemplate stage(String id, String name, String description, String icon,
                                       List<String> dependencies, String mode, int count,
                                       String category, String color, String frame,
                                       String rules, String progression) {
        return new StageTemplate(id, name, description, icon, dependencies, mode, count,
            category, color, frame, rules, progression);
    }

    private static String identity(StageTemplate stage) {
        return """
            [schema]
            version = 4

            [stage]
            id = %s
            display_name = %s
            description = %s
            icon = %s
            dependencies = %s
            dependency_mode = %s
            dependency_count = %d
            category = %s
            color = %s
            scope = "team"
            tags = ["showcase", "class"]

            [display]
            frame = %s
            reveal = %s
            background = %s
            """.formatted(quote(stage.id()), quote(stage.name()), quote(stage.description()), quote(stage.icon()),
                list(stage.dependencies()), quote(stage.mode()), stage.count(), quote(stage.category()),
                quote(stage.color()), quote(stage.frame()), quote(stage.dependencies().isEmpty() ? "always" : "dependencies"),
                quote(BACKGROUND));
    }

    private static String purchase(int xpLevels, String... items) {
        return """
            [cost]
            items = %s
            xp_levels = %d
            cooldown = "2s"
            refund_percent = 100
            bypass_requirements = false
            """.formatted(list(List.of(items)), xpLevels);
    }

    private static String reward(String item, int xpLevels) {
        return """

            [rewards]
            items = [%s]
            xp_levels = %d
            """.formatted(quote(item), xpLevels);
    }

    private static String effectReward(String effect, int seconds, int amplifier) {
        return """

            [rewards]
            effects = [%s]
            """.formatted(quote(effect + ":" + seconds + ":" + amplifier));
    }

    private static String grant(String type, String target, int count) {
        return """
            [[grants]]
            id = %s
            repeat = "once"
            scope = "player"
            condition = { type = %s, id = %s, count = %d }
            """.formatted(quote("showcase:grant_" + type + "_" + target.substring(target.indexOf(':') + 1) + "_" + count),
                quote(type), quote(target), count);
    }

    private static String lock(String category, String action, String selector, int priority) {
        return """
            [[rules]]
            id = %s
            effect = "lock"
            action = %s
            priority = %d
            targets.%s = [%s]
            """.formatted(quote("showcase:lock_" + category + "_" + Math.abs(selector.hashCode())),
                quote(action), priority, category, quote(selector));
    }

    private static String temporaryRule(String id, String effect, String category, String action,
                                        String selector, String condition, String target, int priority) {
        return """
            [[temporary_rules]]
            id = %s
            effect = %s
            lifetime = "live"
            action = %s
            priority = %d
            targets.%s = [%s]
            while = { type = %s, id = %s }
            """.formatted(quote(id), quote(effect), quote(action), priority, category, quote(selector),
                quote(condition), quote(target));
    }

    private static String itemAttribute(String id, String item, String attribute, double amount, int priority) {
        return itemAttribute(id, item, "either_hand", attribute, amount, priority);
    }

    private static String itemEquipmentAttribute(String id, String item, String attribute,
                                                 double amount, int priority) {
        return itemAttribute(id, item, "equipment", attribute, amount, priority);
    }

    private static String itemAttribute(String id, String item, String context, String attribute,
                                        double amount, int priority) {
        return """
            [[item_modifiers]]
            id = %s
            items = [%s]
            contexts = [%s]
            with_stages = [%s]
            priority = %d

            [[item_modifiers.attributes]]
            id = %s
            amount = %s
            operation = "add_value"
            """.formatted(quote(id), quote(item), quote(context),
                quote(id.substring(0, id.indexOf('/'))), priority, quote(attribute), Double.toString(amount));
    }

    private static String itemEffect(String id, String item, String effect, int amplifier,
                                     int duration, int priority) {
        return """
            [[item_modifiers]]
            id = %s
            items = [%s]
            while_holding = true
            with_stages = [%s]
            priority = %d

            [[item_modifiers.effects]]
            id = %s
            amplifier = %d
            duration_ticks = %d
            particles = false
            """.formatted(quote(id), quote(item), quote(id.substring(0, id.indexOf('/'))),
                priority, quote(effect), amplifier, duration);
    }

    private static String abilities(String... values) {
        return """
            [abilities]
            locked = %s

            """.formatted(list(List.of(values)));
    }

    private static String diamondFortune() {
        return """
            [[drop_modifiers]]
            id = "showcase:diamond_engineer/diamond_fortune"
            blocks = ["minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"]
            drops = ["minecraft:diamond"]
            tools = ["tag:minecraft:pickaxes"]
            required_enchantment = "minecraft:fortune"
            minimum_enchantment_level = 1
            multiply = 2.0
            priority = 600
            exclusive = true
            """;
    }

    private static String challenge(String id, String title, String boss, int hits, String timeout) {
        return """
            [[challenges]]
            id = %s
            title = %s
            start_when = { type = "boss_session", id = %s }
            success_when = { type = "kill", id = %s }
            max_hits = %d
            boss = %s
            timeout = %s
            retries = 3

            [challenges.hud]
            enabled = true
            placement = "top"
            color = "gold"
            icon = %s
            animation = "pulse"
            """.formatted(quote(id), quote(title), quote(boss), quote(boss), hits, quote(boss),
                quote(timeout), quote(boss.equals("minecraft:ravager") ? "minecraft:saddle" : "minecraft:nether_star"));
    }

    private static String grandmasterProgression() {
        return """
            [cost]
            items = ["minecraft:dragon_breath:16"]
            xp_levels = 30
            cooldown = "2s"
            refund_percent = 100
            bypass_requirements = false

            [rewards]
            items = ["minecraft:nether_star:1", "minecraft:experience_bottle:32"]
            xp_levels = 30

            [[variables]]
            id = "showcase:grandmaster/mastery_points"
            type = "counter"
            scope = "team"
            default = 0
            minimum = 0
            maximum = 100
            persistent = true
            sync_visible = true

            [formulas]
            mastery_score = "mastery_points * 10"

            [states]
            values = ["missing", "available", "owned", "completed"]
            ownership_states = ["owned", "completed"]
            initial = "missing"

            [states.transitions]
            missing = ["available"]
            available = ["owned"]
            owned = ["completed"]
            """;
    }

    private static String list(List<String> values) {
        return values.stream().map(DefaultShowcaseStages::quote)
            .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? "" : value.strip() + "\n";
    }

    private record StageTemplate(String id, String name, String description, String icon,
                                 List<String> dependencies, String mode, int count,
                                 String category, String color, String frame,
                                 String rules, String progression) {}
}
