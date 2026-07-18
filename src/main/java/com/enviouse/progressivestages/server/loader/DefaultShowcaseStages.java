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
            slottedStage("showcase:mage", "Mage", "A beginner path for enchanting magic brewing and arcane control.", "minecraft:amethyst_shard", List.of(), "all", 1, "Core paths", "#a970ff", "task", "beginner_paths", 2, "deny", lock("enchants", "table", "minecraft:infinity", 210), purchase(0, "minecraft:lapis_lazuli:16") + reward("minecraft:experience_bottle:4", 2)),
            slottedStage("showcase:warrior", "Warrior", "A beginner path for armor direct combat and defensive abilities.", "minecraft:iron_sword", List.of(), "all", 1, "Core paths", "#d65c4a", "task", "beginner_paths", 2, "deny", lockWithException("showcase:warrior/swords", "items", "use", "tag:minecraft:swords", "minecraft:wooden_sword", 211), purchase(0, "minecraft:iron_ingot:16") + reward("minecraft:iron_sword:1", 0)),
            slottedStage("showcase:ranger", "Ranger", "A beginner path for ranged combat pets travel and survival.", "minecraft:bow", List.of(), "all", 1, "Core paths", "#69b86b", "task", "beginner_paths", 2, "deny", lock("items", "use", "minecraft:bow", 212), purchase(0, "minecraft:arrow:32") + reward("minecraft:bow:1", 0)),

            stage("showcase:wizard", "Wizard", "Evolve Mage through disciplined enchanting and spell tools.", "minecraft:enchanted_book", List.of("showcase:mage"), "all", 1, "Core paths", "#9e70ff", "goal", lock("blocks", "interact", "minecraft:enchanting_table", 260) + lock("recipes", "craft", "minecraft:enchanting_table", 261), purchase(8, "minecraft:lapis_lazuli:24", "minecraft:book:8")),
            stage("showcase:warlock", "Warlock", "Evolve Mage through dangerous Nether knowledge.", "minecraft:ender_eye", List.of("showcase:mage"), "all", 1, "Core paths", "#70428f", "goal", temporaryRule("showcase:warlock/nether_focus", "allow", "items", "use", "minecraft:ender_pearl", "dimension", "minecraft:the_nether", 450), grant("kill", "minecraft:evoker", 1) + reward("minecraft:ender_pearl:8", 3)),
            stage("showcase:cleric", "Cleric", "Evolve Mage into healing brewing and team support.", "minecraft:brewing_stand", List.of("showcase:mage"), "all", 1, "Core paths", "#ef78a7", "goal", lock("brewing", "brew", "minecraft:strong_healing", 261), purchase(8, "minecraft:emerald:16")),
            stage("showcase:knight", "Knight", "Evolve Warrior into an armored protector.", "minecraft:iron_chestplate", List.of("showcase:warrior"), "all", 1, "Core paths", "#b9bec6", "goal", itemEquipmentAttribute("showcase:knight/armor", "tag:minecraft:chest_armor", "minecraft:generic.armor", 2.0, 262) + lock("curios", "equip", "minecraft:body", 263), purchase(5, "minecraft:iron_ingot:32")),
            stage("showcase:berserker", "Berserker", "Evolve Warrior into a relentless axe fighter.", "minecraft:diamond_axe", List.of("showcase:warrior"), "all", 1, "Core paths", "#b33c32", "goal", itemAttribute("showcase:berserker/axes", "tag:minecraft:axes", "minecraft:generic.attack_damage", 3.0, 263), grant("kill", "minecraft:ravager", 1) + effectReward("minecraft:strength", 120, 0)),
            stage("showcase:paladin", "Paladin", "Evolve Warrior into shield defense and radiant utility.", "minecraft:shield", List.of("showcase:warrior"), "all", 1, "Core paths", "#f2c44d", "goal", lock("beacon", "apply", "minecraft:haste", 264), purchase(6, "minecraft:gold_ingot:24")),
            stage("showcase:marksman", "Marksman", "Evolve Ranger through precise ranged combat.", "minecraft:crossbow", List.of("showcase:ranger"), "all", 1, "Core paths", "#4e9f68", "goal", itemAttribute("showcase:marksman/mobility", "minecraft:crossbow", "minecraft:generic.movement_speed", 0.03, 265), grant("kill_with_item", "minecraft:skeleton", 20) + purchase(0, "minecraft:arrow:64")),
            stage("showcase:beastmaster", "Beastmaster", "Evolve Ranger through animal companions.", "minecraft:bone", List.of("showcase:ranger"), "all", 1, "Core paths", "#8bb65d", "goal", lock("pets", "tame", "minecraft:wolf", 266), grant("tame", "minecraft:wolf", 3) + reward("minecraft:bone:16", 0)),
            stage("showcase:scout", "Scout", "Evolve Ranger through navigation screens and exploration.", "minecraft:spyglass", List.of("showcase:ranger"), "all", 1, "Core paths", "#76a982", "goal", lock("screens", "open", "minecraft:cartography_table", 267), grant("advancement", "minecraft:adventure/root", 1) + reward("minecraft:compass:1", 1)),

            stage("showcase:archmage", "Archmage", "Master Wizard through End research and hidden knowledge.", "minecraft:end_crystal", List.of("showcase:wizard"), "all", 1, "Core paths", "#c185ff", "challenge", lock("advancements", "display", "minecraft:end/dragon_egg", 320), grant("kill", "minecraft:enderman", 32) + purchase(15, "minecraft:amethyst_shard:32")),
            stage("showcase:necromancer", "Necromancer", "Master Warlock through controlled undead spawning.", "minecraft:wither_skeleton_skull", List.of("showcase:warlock"), "all", 1, "Core paths", "#63336f", "challenge", lock("mobs", "spawn", "minecraft:wither_skeleton", 321), grant("kill", "minecraft:wither_skeleton", 25) + reward("minecraft:wither_skeleton_skull:1", 5)),
            stage("showcase:oracle", "Oracle", "Master Cleric through trades professions and ancient knowledge.", "minecraft:echo_shard", List.of("showcase:cleric"), "all", 1, "Core paths", "#e98db7", "challenge", lock("trades", "purchase", "minecraft:enchanted_book", 322), grant("advancement", "minecraft:adventure/adventuring_time", 1) + reward("minecraft:experience_bottle:16", 8)),
            stage("showcase:vanguard", "Vanguard", "Master Knight with equipment health and raid survival.", "minecraft:netherite_chestplate", List.of("showcase:knight"), "all", 1, "Core paths", "#aeb4bf", "challenge", itemEquipmentAttribute("showcase:vanguard/health", "tag:minecraft:chest_armor", "minecraft:generic.max_health", 6.0, 323), grant("kill", "minecraft:ravager", 3) + purchase(20, "minecraft:diamond:16")),
            stage("showcase:warlord", "Warlord", "Master Berserker through a timed limited boss trial.", "minecraft:netherite_axe", List.of("showcase:berserker"), "all", 1, "Core paths", "#9b302b", "challenge", itemAttribute("showcase:warlord/power", "tag:minecraft:axes", "minecraft:generic.attack_damage", 5.0, 324), challenge("showcase:warlord/ravager_trial", "Ravager trial", "minecraft:ravager", 4, "8m") + purchase(25, "minecraft:netherite_ingot:2")),
            stage("showcase:templar", "Templar", "Master Paladin through ability and shield control.", "minecraft:golden_helmet", List.of("showcase:paladin"), "all", 1, "Core paths", "#f0d06b", "challenge", abilities("climb") + itemAttribute("showcase:templar/health", "minecraft:shield", "minecraft:generic.max_health", 4.0, 325), purchase(10, "minecraft:golden_apple:4")),
            stage("showcase:deadeye", "Deadeye", "Master Marksman through item on entity interactions.", "minecraft:spectral_arrow", List.of("showcase:marksman"), "all", 1, "Core paths", "#438d58", "challenge", lock("interactions", "item_on_entity", "minecraft:spectral_arrow", 326), grant("kill_with_item", "minecraft:stray", 24) + reward("minecraft:spectral_arrow:32", 4)),
            stage("showcase:wildspeaker", "Wildspeaker", "Master Beastmaster through crops wildlife and growth.", "minecraft:goat_horn", List.of("showcase:beastmaster"), "all", 1, "Core paths", "#70954c", "challenge", lock("crops", "grow", "minecraft:sweet_berry_bush", 327), grant("tame", "minecraft:cat", 5) + reward("minecraft:golden_carrot:12", 3)),
            stage("showcase:pathfinder", "Pathfinder", "Master Scout through structures regions and travel.", "minecraft:filled_map", List.of("showcase:scout"), "all", 1, "Core paths", "#5f9673", "challenge", lock("structures", "enter", "minecraft:ancient_city", 328) + lock("regions", "enter", "showcase:ancient_city_ring", 329), grant("enter_structure", "minecraft:ancient_city", 1) + reward("minecraft:echo_shard:4", 6)),

            stage("showcase:spellblade", "Spellblade", "Merge Wizard and Knight into magical melee combat.", "minecraft:diamond_sword", List.of("showcase:wizard", "showcase:knight"), "all", 2, "Hybrid paths", "#788fe8", "challenge", itemAttribute("showcase:spellblade/blade", "tag:minecraft:swords", "minecraft:generic.attack_damage", 4.0, 420), purchase(12, "minecraft:diamond_sword:1", "minecraft:lapis_lazuli:24")),
            stage("showcase:dark_paladin", "Dark Paladin", "Merge Warlock and Paladin into a Nether guardian.", "minecraft:netherite_sword", List.of("showcase:warlock", "showcase:paladin"), "all", 2, "Hybrid paths", "#7f4c69", "challenge", temporaryRule("showcase:dark_paladin/nether_blades", "allow", "items", "use", "tag:minecraft:swords", "dimension", "minecraft:the_nether", 520), grant("kill", "minecraft:wither", 1) + reward("minecraft:nether_star:1", 10)),
            stage("showcase:battle_medic", "Battle Medic", "Merge Vanguard and Cleric into armored support.", "minecraft:enchanted_golden_apple", List.of("showcase:vanguard", "showcase:cleric"), "all", 2, "Hybrid paths", "#db7d8d", "challenge", itemEffect("showcase:battle_medic/recovery", "minecraft:shield", "minecraft:regeneration", 0, 100, 430), purchase(18, "minecraft:golden_apple:8", "minecraft:diamond:8")),
            stage("showcase:arcane_archer", "Arcane Archer", "Merge Wizard and Marksman into mobile ranged magic.", "minecraft:tipped_arrow", List.of("showcase:wizard", "showcase:marksman"), "all", 2, "Hybrid paths", "#6aa9c8", "challenge", abilities("elytra") + itemAttribute("showcase:arcane_archer/speed", "minecraft:bow", "minecraft:generic.movement_speed", 0.05, 431), purchase(12, "minecraft:spectral_arrow:32", "minecraft:lapis_lazuli:16")),
            stage("showcase:monster_hunter", "Monster Hunter", "Merge Berserker and Beastmaster for dangerous hunts.", "minecraft:crossbow", List.of("showcase:berserker", "showcase:beastmaster"), "all", 2, "Hybrid paths", "#9b6445", "challenge", lock("entities", "attack", "minecraft:warden", 432), grant("kill", "minecraft:elder_guardian", 2) + reward("minecraft:prismarine_shard:32", 8)),
            stage("showcase:holy_ranger", "Holy Ranger", "Merge Templar and Scout into radiant exploration.", "minecraft:golden_carrot", List.of("showcase:templar", "showcase:scout"), "all", 2, "Hybrid paths", "#d8bd5f", "challenge", itemEffect("showcase:holy_ranger/vision", "minecraft:spyglass", "minecraft:night_vision", 0, 120, 433), purchase(14, "minecraft:gold_ingot:16", "minecraft:compass:2")),
            stage("showcase:shadow_scout", "Shadow Scout", "Merge Warlock and Scout for hidden dimension travel.", "minecraft:ender_pearl", List.of("showcase:warlock", "showcase:scout"), "all", 2, "Hybrid paths", "#635779", "challenge", lock("dimensions", "enter", "minecraft:the_end", 434), grant("kill", "minecraft:shulker", 8) + reward("minecraft:shulker_shell:4", 6)),
            stage("showcase:nature_mage", "Nature Mage", "Merge Cleric and Wildspeaker for growth support.", "minecraft:flowering_azalea", List.of("showcase:cleric", "showcase:wildspeaker"), "all", 2, "Hybrid paths", "#8ebc79", "challenge", lock("loot", "generate", "minecraft:chests/jungle_temple", 435), purchase(10, "minecraft:emerald:12", "minecraft:bone_meal:32")),
            stage("showcase:siege_master", "Siege Master", "Merge Warlord and Deadeye into ranged destruction.", "minecraft:tnt", List.of("showcase:warlord", "showcase:deadeye"), "all", 2, "Hybrid paths", "#b64c38", "challenge", lock("blocks", "place", "minecraft:tnt", 436), grant("craft", "minecraft:tnt", 16) + reward("minecraft:firework_rocket:32", 5)),

            slottedStage("showcase:coal_engineer", "Coal Engineer", "Begin a separate purchasable mining buff chain. Engineering tiers stack by default.", "minecraft:coal", List.of(), "all", 1, "Engineering", "#555555", "task", "engineering_tiers", 0, "deny", oreBonus("showcase:coal_engineer/coal_yield", "tag:c:ores/coal", "minecraft:coal", 1.25, 510, false), purchase(0, "minecraft:coal:32")),
            slottedStage("showcase:iron_engineer", "Iron Engineer", "Keep Coal Engineer active and add an iron yield bonus.", "minecraft:iron_ingot", List.of("showcase:coal_engineer"), "all", 1, "Engineering", "#b9b9b9", "goal", "engineering_tiers", 0, "deny", oreBonus("showcase:iron_engineer/iron_yield", "tag:c:ores/iron", "minecraft:raw_iron", 1.5, 520, false), purchase(4, "minecraft:iron_ingot:32")),
            slottedStage("showcase:diamond_engineer", "Diamond Engineer", "Keep earlier engineering buffs and double Fortune diamond drops only.", "minecraft:diamond_pickaxe", List.of("showcase:iron_engineer"), "all", 1, "Engineering", "#47d7d7", "challenge", "engineering_tiers", 0, "deny", diamondFortune(), purchase(0, "minecraft:diamond:32") + reward("minecraft:experience_bottle:8", 5)),
            slottedStage("showcase:netherite_engineer", "Netherite Engineer", "Add ancient debris output while every earlier engineering tier remains active.", "minecraft:netherite_pickaxe", List.of("showcase:diamond_engineer"), "all", 1, "Engineering", "#5d4d57", "challenge", "engineering_tiers", 0, "deny", oreBonus("showcase:netherite_engineer/debris_yield", "minecraft:ancient_debris", "minecraft:ancient_debris", 2.0, 620, true), purchase(20, "minecraft:netherite_ingot:4")),
            slottedStage("showcase:redstone_engineer", "Redstone Engineer", "Branch from Iron Engineer into machines and redstone output.", "minecraft:redstone", List.of("showcase:iron_engineer"), "all", 1, "Engineering", "#d64232", "goal", "engineering_tiers", 0, "deny", oreBonus("showcase:redstone_engineer/redstone_yield", "tag:c:ores/redstone", "minecraft:redstone", 1.5, 530, false), grant("craft", "minecraft:comparator", 8) + purchase(8, "minecraft:redstone:64")),
            slottedStage("showcase:quantum_engineer", "Quantum Engineer", "Merge Diamond and Redstone engineering into a high priority all ore specialist.", "minecraft:beacon", List.of("showcase:diamond_engineer", "showcase:redstone_engineer"), "all", 2, "Engineering", "#55d9c9", "challenge", "engineering_tiers", 0, "deny", oreBonus("showcase:quantum_engineer/ore_yield", "tag:c:ores", "tag:c:raw_materials", 1.25, 700, false), purchase(25, "minecraft:nether_star:1", "minecraft:diamond:16") + grant("kubejs", "showcase:quantum_calibrated", 1)),

            slottedStage("showcase:fortune_mode", "Fortune Mode", "A mutually exclusive mining mode that favors raw output.", "minecraft:enchanted_book", List.of("showcase:coal_engineer"), "all", 1, "Mining modes", "#e0b84c", "goal", "mining_modes", 1, "replace_oldest", oreBonus("showcase:fortune_mode/lapis_yield", "tag:c:ores/lapis", "minecraft:lapis_lazuli", 2.0, 750, true), purchase(4, "minecraft:lapis_lazuli:24")),
            slottedStage("showcase:silk_mode", "Silk Mode", "Buying this mode replaces the currently active mining mode.", "minecraft:glass", List.of("showcase:coal_engineer"), "all", 1, "Mining modes", "#d5f1ed", "goal", "mining_modes", 1, "replace_oldest", itemAttribute("showcase:silk_mode/control", "tag:minecraft:pickaxes", "minecraft:generic.movement_speed", 0.01, 751), purchase(4, "minecraft:glass:32")),
            slottedStage("showcase:excavation_mode", "Excavation Mode", "A one slot mode demonstrating tool attributes and replacement.", "minecraft:diamond_shovel", List.of("showcase:iron_engineer"), "all", 1, "Mining modes", "#be956a", "goal", "mining_modes", 1, "replace_oldest", itemAttribute("showcase:excavation_mode/speed", "tag:minecraft:shovels", "minecraft:generic.movement_speed", 0.04, 752), purchase(6, "minecraft:iron_shovel:4")),
            slottedStage("showcase:precision_mode", "Precision Mode", "A one slot mode whose exclusive rule stops lower priority bonuses.", "minecraft:spyglass", List.of("showcase:iron_engineer"), "all", 1, "Mining modes", "#8fb9c5", "goal", "mining_modes", 1, "replace_oldest", oreBonus("showcase:precision_mode/quartz_yield", "tag:c:ores/quartz", "minecraft:quartz", 1.1, 900, true), purchase(6, "minecraft:quartz:32")),

            temporaryStage("showcase:battle_fury", "Battle Fury", "A ten minute strength purchase that disappears on death or expiry.", "minecraft:blaze_powder", List.of("showcase:warrior"), "Temporary power", "#d54b37", "10m", itemAttribute("showcase:battle_fury/power", "tag:minecraft:swords", "minecraft:generic.attack_damage", 2.0, 810), purchase(2, "minecraft:blaze_powder:8") + revokeOnDeath()),
            temporaryStage("showcase:miners_focus", "Miners Focus", "A fifteen minute mining movement buff with its own cost.", "minecraft:golden_pickaxe", List.of("showcase:coal_engineer"), "Temporary power", "#d7ad3b", "15m", itemAttribute("showcase:miners_focus/speed", "tag:minecraft:pickaxes", "minecraft:generic.movement_speed", 0.06, 811), purchase(3, "minecraft:coal:16", "minecraft:redstone:16")),
            temporaryStage("showcase:aquatic_blessing", "Aquatic Blessing", "A temporary swim ability and water breathing effect.", "minecraft:heart_of_the_sea", List.of("showcase:ranger"), "Temporary power", "#4a9ac4", "20m", abilities("swim") + itemEffect("showcase:aquatic_blessing/breath", "minecraft:heart_of_the_sea", "minecraft:water_breathing", 0, 120, 812), purchase(5, "minecraft:prismarine_crystals:12")),
            temporaryStage("showcase:village_hero", "Village Hero", "A temporary trade focused reward granted by raid progress.", "minecraft:emerald", List.of("showcase:cleric"), "Temporary power", "#67ad58", "30m", lock("professions", "trade", "minecraft:weaponsmith", 813), grant("kill", "minecraft:vindicator", 12) + effectReward("minecraft:hero_of_the_village", 1800, 0)),
            temporaryStage("showcase:end_resolve", "End Resolve", "A temporary End session power with movement restrictions and rewards.", "minecraft:dragon_breath", List.of("showcase:archmage"), "Temporary power", "#8d67b5", "12m", temporaryRule("showcase:end_resolve/end_elytra", "allow", "abilities", "use", "elytra", "dimension", "minecraft:the_end", 850), grant("kill", "minecraft:ender_dragon", 1) + reward("minecraft:dragon_breath:8", 12)),

            stage("showcase:stronghold_key", "Stronghold Key", "Showcase structure entry chest and block protection rules.", "minecraft:ender_eye", List.of("showcase:pathfinder"), "all", 1, "World rules", "#8c8c6a", "challenge", lock("structures", "enter", "minecraft:stronghold", 910), purchase(10, "minecraft:ender_eye:12")),
            stage("showcase:nether_license", "Nether License", "Showcase dimension travel fluid and submersion gates.", "minecraft:flint_and_steel", List.of("showcase:warlock"), "all", 1, "World rules", "#a64d35", "challenge", lock("fluids", "submerge", "minecraft:lava", 911) + lock("dimensions", "portal", "minecraft:the_nether", 912), grant("advancement", "minecraft:nether/root", 1)),
            stage("showcase:wither_protocol", "Wither Protocol", "Showcase boss challenge loot and triggered world access.", "minecraft:nether_star", List.of("showcase:necromancer", "showcase:warlord"), "all", 2, "World rules", "#554858", "challenge", lock("loot", "drop", "minecraft:entities/wither", 913), challenge("showcase:wither_protocol/trial", "Wither protocol", "minecraft:wither", 6, "10m") + grant("kill", "minecraft:wither", 2)),
            stage("showcase:end_protocol", "End Protocol", "Showcase final dimension ability and temporary priority rules.", "minecraft:dragon_head", List.of("showcase:stronghold_key", "showcase:archmage"), "all", 2, "World rules", "#79649c", "challenge", abilities("jump") + temporaryRule("showcase:end_protocol/end_pickaxe", "allow", "items", "use", "minecraft:diamond_pickaxe", "dimension", "minecraft:the_end", 950), purchase(20, "minecraft:end_crystal:4")),

            stage("showcase:grandmaster", "Grandmaster", "Own any three mastery hybrid engineering or world paths to complete the showcase.", "minecraft:nether_star", List.of("showcase:archmage", "showcase:necromancer", "showcase:oracle", "showcase:vanguard", "showcase:warlord", "showcase:templar", "showcase:deadeye", "showcase:wildspeaker", "showcase:pathfinder", "showcase:spellblade", "showcase:dark_paladin", "showcase:battle_medic", "showcase:arcane_archer", "showcase:monster_hunter", "showcase:holy_ranger", "showcase:shadow_scout", "showcase:nature_mage", "showcase:siege_master", "showcase:quantum_engineer", "showcase:end_protocol"), "at_least", 3, "Finale", "#f7bf43", "challenge", itemAttribute("showcase:grandmaster/health", "minecraft:nether_star", "minecraft:generic.max_health", 10.0, 1000), grandmasterProgression())
        );
    }

    private static StageTemplate stage(String id, String name, String description, String icon,
                                       List<String> dependencies, String mode, int count,
                                       String category, String color, String frame,
                                       String rules, String progression) {
        return new StageTemplate(id, name, description, icon, dependencies, mode, count,
            category, color, frame, "", 0, "deny", "", rules, progression);
    }

    private static StageTemplate slottedStage(String id, String name, String description, String icon,
                                              List<String> dependencies, String mode, int count,
                                              String category, String color, String frame,
                                              String slotGroup, int slotLimit, String slotPolicy,
                                              String rules, String progression) {
        return new StageTemplate(id, name, description, icon, dependencies, mode, count,
            category, color, frame, slotGroup, slotLimit, slotPolicy, "", rules, progression);
    }

    private static StageTemplate temporaryStage(String id, String name, String description, String icon,
                                                List<String> dependencies, String category, String color,
                                                String duration, String rules, String progression) {
        return new StageTemplate(id, name, description, icon, dependencies, "all", 1,
            category, color, "challenge", "", 0, "deny", duration, rules, progression);
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
            slot_group = %s
            slot_limit = %d
            slot_policy = %s
            duration = %s
            scope = "team"
            tags = %s

            [display]
            frame = %s
            reveal = %s
            background = %s
            """.formatted(quote(stage.id()), quote(stage.name()), quote(stage.description()), quote(stage.icon()),
                list(stage.dependencies()), quote(stage.mode()), stage.count(), quote(stage.category()),
                quote(stage.color()), quote(stage.slotGroup()), stage.slotLimit(), quote(stage.slotPolicy()),
                quote(stage.duration()), list(stageTags(stage)), quote(stage.frame()), quote(stage.dependencies().isEmpty() ? "always" : "dependencies"),
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

    private static String lockWithException(String id, String category, String action, String selector,
                                            String exception, int priority) {
        return """
            [[rules]]
            id = %s
            effect = "lock"
            action = %s
            priority = %d
            viewer = "hide"
            targets.%s = [%s]

            [[rules.exceptions]]
            effect = "exclude"
            priority = %d
            targets.%s = [%s]
            """.formatted(quote(id), quote(action), priority, category, quote(selector),
                priority + 100, category, quote(exception));
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

    private static String oreBonus(String id, String block, String drop, double multiply,
                                   int priority, boolean exclusive) {
        return """
            [[drop_modifiers]]
            id = %s
            blocks = [%s]
            drops = [%s]
            tools = ["tag:minecraft:pickaxes"]
            multiply = %s
            priority = %d
            exclusive = %s
            """.formatted(quote(id), quote(block), quote(drop), Double.toString(multiply),
                priority, Boolean.toString(exclusive));
    }

    private static String revokeOnDeath() {
        return """

            [revoke]
            on_death = true
            revoke_cascade = false
            """;
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

    private static List<String> stageTags(StageTemplate stage) {
        String kind = switch (stage.category()) {
            case "Engineering", "Mining modes" -> "specialist";
            case "Temporary power" -> "temporary";
            case "World rules" -> "world";
            default -> "class";
        };
        String category = stage.category().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
        return List.of("showcase", kind, category);
    }

    private record StageTemplate(String id, String name, String description, String icon,
                                 List<String> dependencies, String mode, int count,
                                 String category, String color, String frame, String slotGroup,
                                 int slotLimit, String slotPolicy, String duration,
                                 String rules, String progression) {}
}
