package com.enviouse.progressivestages.server.loader;

import com.enviouse.progressivestages.common.rehaul.RuleEffect;
import com.enviouse.progressivestages.common.rehaul.RuleLifetime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Schema4StageCompilerTest {

    @Test
    void compilesPrioritiesTemporaryRulesLifecycleModifiersAndChallenges() {
        String stage = """
            [schema]
            version = 4

            [stage]
            id = "pack:trial"
            name = "Trial"
            priority = 25
            """;
        String rules = """
            [items]
            locked = ["mod:example", "id:example:sword|priority=600"]
            priority = 100

            [items.priorities]
            "mod:example" = 125

            [items.presentation."id:example:sword"]
            viewer = "show"
            priority = 700

            [unlocks]
            items = ["example:training_sword"]
            priority = 500

            [[temporary_rules]]
            id = "pack:end_tools"
            effect = "deny"
            lifetime = "duration"
            priority = 800
            duration = "30s"
            targets.items = ["tag:c:tools"]
            while = { type = "dimension", id = "minecraft:the_end" }

            [[item_modifiers]]
            id = "pack:mage_sword"
            items = ["tag:c:swords"]
            while_holding = true
            with_stages = ["pack:mage"]
            aggregation = "once"
            priority = 300

            [[item_modifiers.attributes]]
            id = "minecraft:generic.attack_damage"
            amount = -0.5
            operation = "add_multiplied_total"

            [[drop_modifiers]]
            id = "pack:diamond_fortune"
            blocks = ["minecraft:diamond_ore"]
            drops = ["minecraft:diamond"]
            tools = ["tag:minecraft:pickaxes"]
            required_enchantment = "minecraft:fortune"
            minimum_enchantment_level = 1
            multiply = 2.0
            priority = 650
            exclusive = true
            """;
        String progression = """
            [[grants]]
            id = "pack:grant_trial"
            repeat = "once"
            condition = { type = "death", count = 2 }

            [[revokes]]
            id = "pack:revoke_trial"
            repeat = "edge"
            condition = { type = "health_lost", amount = 40 }

            [[challenges]]
            id = "pack:wither_trial"
            title = "Wither trial"
            start_when = { type = "boss_session", id = "minecraft:wither" }
            success_when = { type = "kill", id = "minecraft:wither" }
            max_hits = 2
            boss = "minecraft:wither"
            """;

        StageFileParser.ParseResult parsed = StagePackageParser.parseContents("test", "stage.toml", stage,
            "rules.toml", rules, "progression.toml", progression);
        assertTrue(parsed.isSuccess(), parsed.getErrorMessage());
        var compiled = Schema4StageCompiler.compile(parsed.getStageDefinition(), parsed.getSourceConfig(), "test", 0);
        assertTrue(compiled.rules().stream().anyMatch(rule -> rule.effect() == RuleEffect.LOCK && rule.priority() == 125));
        assertTrue(compiled.rules().stream().anyMatch(rule -> rule.effect() == RuleEffect.LOCK && rule.priority() == 600));
        assertTrue(compiled.rules().stream().anyMatch(rule -> rule.effect() == RuleEffect.ALLOW && rule.priority() == 500));
        assertTrue(compiled.rules().stream().anyMatch(rule -> rule.effect() == RuleEffect.DENY
            && rule.lifetime() == RuleLifetime.DURATION && rule.priority() == 800));
        assertEquals(2, compiled.progression().lifecycleRules().size());
        assertEquals(1, compiled.progression().modifiers().size());
        assertEquals(1, compiled.progression().dropModifiers().size());
        assertEquals(2.0, compiled.progression().dropModifiers().getFirst().multiply());
        assertEquals(1, compiled.progression().challenges().size());
        assertEquals(2, compiled.progression().challenges().getFirst().budgets().getFirst().maximum());
    }

    @Test
    void keepsEntryAllowedStructureProtectionOutOfCompiledEntryRules() {
        var parsed = StagePackageParser.parseContents("test", "stage.toml",
            "[schema]\nversion=4\n[stage]\nid=\"pack:protection\"\npriority=12\n",
            "rules.toml", """
                [structures]
                locked_entry = ["minecraft:stronghold|priority=20"]
                [structures.rules]
                entry_allowed = true
                priority = -7
                prevent_block_place = true
                """, "progression.toml", "");
        assertTrue(parsed.isSuccess(), parsed.getErrorMessage());
        var definition = parsed.getStageDefinition();
        assertTrue(definition.getLocks().structures().entryAllowed());
        var compiled = Schema4StageCompiler.compile(definition, parsed.getSourceConfig(), "test", 0);
        assertTrue(compiled.rules().stream().noneMatch(rule -> rule.category().equals("structures")
            && rule.action().equals("enter")));
    }

    @Test
    void usesStructureGlobalPriorityWhenStagePriorityIsOmitted() {
        var parsed = StagePackageParser.parseContents("test", "stage.toml",
            "[schema]\nversion=4\n[stage]\nid=\"pack:protection\"\n",
            "rules.toml", """
                [structures]
                global_priority = -2
                locked_entry = ["minecraft:stronghold"]
                """, "progression.toml", "");
        assertTrue(parsed.isSuccess(), parsed.getErrorMessage());
        var compiled = Schema4StageCompiler.compile(parsed.getStageDefinition(), parsed.getSourceConfig(), "test", 0);
        assertTrue(compiled.rules().stream().anyMatch(rule -> rule.category().equals("structures")
            && rule.priority() == -2));
    }

    @Test
    void rejectsSpecializedCategoriesInGenericRulesAndExceptions() {
        var parsed = StagePackageParser.parseContents("test", "stage.toml",
            "[schema]\nversion=4\n[stage]\nid=\"pack:specialized\"\n", "rules.toml", """
                [[rules]]
                id = "pack:ore-rule"
                effect = "lock"
                action = "display"
                targets.ores = ["id:minecraft:diamond_ore"]

                """, "progression.toml", "");
        assertTrue(parsed.isSuccess(), parsed.getErrorMessage());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> Schema4StageCompiler.compile(parsed.getStageDefinition(), parsed.getSourceConfig(), "test", 0));
        assertTrue(error.getMessage().contains("rules.targets.ores"));
        assertTrue(error.getMessage().contains("[[blocks.overrides]]"));

        var region = StagePackageParser.parseContents("test", "stage.toml",
            "[schema]\nversion=4\n[stage]\nid=\"pack:region\"\n", "rules.toml", """
                [[rules]]
                id = "pack:region-rule"
                effect = "lock"
                action = "break"
                targets.region = ["id:minecraft:overworld"]
                """, "progression.toml", "");
        assertTrue(region.isSuccess(), region.getErrorMessage());
        IllegalArgumentException regionError = assertThrows(IllegalArgumentException.class,
            () -> Schema4StageCompiler.compile(region.getStageDefinition(), region.getSourceConfig(), "test", 0));
        assertTrue(regionError.getMessage().contains("rules.targets.region"));
        assertTrue(regionError.getMessage().contains("[[regions]]"));

        var exception = StagePackageParser.parseContents("test", "stage.toml",
            "[schema]\nversion=4\n[stage]\nid=\"pack:exception\"\n", "rules.toml", """
                [[rules]]
                id = "pack:block-rule"
                effect = "lock"
                targets.blocks = ["id:minecraft:diamond_block"]

                [[rules.exceptions]]
                effect = "exclude"
                targets.ore = ["id:minecraft:diamond_ore"]
                """, "progression.toml", "");
        assertTrue(exception.isSuccess(), exception.getErrorMessage());
        IllegalArgumentException exceptionError = assertThrows(IllegalArgumentException.class,
            () -> Schema4StageCompiler.compile(exception.getStageDefinition(), exception.getSourceConfig(), "test", 0));
        assertTrue(exceptionError.getMessage().contains("rules.exceptions.targets.ore"));
        assertTrue(exceptionError.getMessage().contains("[[blocks.overrides]]"));
    }

    @Test
    void rejectsActionsAndResultsThatHaveNoCoreRuntimeHandler() {
        var fluid = StagePackageParser.parseContents("test", "stage.toml",
            "[schema]\nversion=4\n[stage]\nid=\"pack:fluid\"\n", "rules.toml", """
                [[rules]]
                effect = "lock"
                action = "interact"
                targets.fluids = ["id:minecraft:water"]
                """, "progression.toml", "");
        assertTrue(fluid.isSuccess(), fluid.getErrorMessage());
        var fluidError = assertThrows(IllegalArgumentException.class,
            () -> Schema4StageCompiler.compile(fluid.getStageDefinition(), fluid.getSourceConfig(), "test", 0));
        assertTrue(fluidError.getMessage().contains("unsupported action 'interact'"));

        var pet = StagePackageParser.parseContents("test", "stage.toml",
            "[schema]\nversion=4\n[stage]\nid=\"pack:pet\"\n", "rules.toml", """
                [[rules]]
                effect = "lock"
                action = "ride"
                targets.pets = ["id:minecraft:wolf"]
                """, "progression.toml", "");
        assertTrue(pet.isSuccess(), pet.getErrorMessage());
        var petError = assertThrows(IllegalArgumentException.class,
            () -> Schema4StageCompiler.compile(pet.getStageDefinition(), pet.getSourceConfig(), "test", 0));
        assertTrue(petError.getMessage().contains("targets.entities"));

        var result = StagePackageParser.parseContents("test", "stage.toml",
            "[schema]\nversion=4\n[stage]\nid=\"pack:result\"\n", "rules.toml", """
                [[rules]]
                effect = "replace"
                action = "place"
                targets.blocks = ["id:minecraft:stone"]
                """, "progression.toml", "");
        assertTrue(result.isSuccess(), result.getErrorMessage());
        var resultError = assertThrows(IllegalArgumentException.class,
            () -> Schema4StageCompiler.compile(result.getStageDefinition(), result.getSourceConfig(), "test", 0));
        assertTrue(resultError.getMessage().contains("unsupported result"));
    }

    @Test
    void usesLiveFluidActionAsDefaultAndRequiresExactSelectorsForExactCategories() {
        var fluid = StagePackageParser.parseContents("test", "stage.toml",
            "[schema]\nversion=4\n[stage]\nid=\"pack:fluid\"\n", "rules.toml", """
                [[rules]]
                effect = "lock"
                targets.fluids = ["id:minecraft:water"]
                """, "progression.toml", "");
        assertTrue(fluid.isSuccess(), fluid.getErrorMessage());
        var compiled = Schema4StageCompiler.compile(fluid.getStageDefinition(), fluid.getSourceConfig(), "test", 0);
        assertTrue(compiled.rules().stream().anyMatch(rule -> rule.category().equals("fluids")
            && rule.action().equals("place")));

        var dimension = StagePackageParser.parseContents("test", "stage.toml",
            "[schema]\nversion=4\n[stage]\nid=\"pack:dimension\"\n", "rules.toml", """
                [[rules]]
                effect = "lock"
                action = "enter"
                targets.dimensions = ["mod:minecraft"]
                """, "progression.toml", "");
        assertTrue(dimension.isSuccess(), dimension.getErrorMessage());
        var selectorError = assertThrows(IllegalArgumentException.class,
            () -> Schema4StageCompiler.compile(dimension.getStageDefinition(), dimension.getSourceConfig(), "test", 0));
        assertTrue(selectorError.getMessage().contains("needs one exact ID"));
    }

    @Test
    void allowsAnyActionForCoreCategoriesAndLeavesUnknownCategoriesToIntegrations() {
        var parsed = StagePackageParser.parseContents("test", "stage.toml",
            "[schema]\nversion=4\n[stage]\nid=\"pack:extensions\"\n", "rules.toml", """
                [[rules]]
                effect = "lock"
                action = "access"
                targets.blocks = ["id:minecraft:stone"]

                [[rules]]
                effect = "replace"
                action = "replace"
                targets."addon:custom" = ["id:addon:thing"]
                """, "progression.toml", "");
        assertTrue(parsed.isSuccess(), parsed.getErrorMessage());
        var compiled = Schema4StageCompiler.compile(parsed.getStageDefinition(), parsed.getSourceConfig(), "test", 0);
        assertTrue(compiled.rules().stream().anyMatch(rule -> rule.category().equals("blocks") && rule.action().equals("access")));
        assertTrue(compiled.rules().stream().anyMatch(rule -> rule.category().equals("addon:custom")
            && rule.effect() == RuleEffect.REPLACE));
    }

    @Test
    void exceptionsReferenceEveryExpandedParentAndInheritOwnership() {
        var parsed = StagePackageParser.parseContents("test", "stage.toml",
            "[schema]\nversion=4\n[stage]\nid=\"pack:trial\"\n", "rules.toml", """
                [[rules]]
                id = "pack:food"
                effect = "lock"
                stage_state = "always"
                targets.items = ["all:*", "mod:minecraft"]
                [[rules.exceptions]]
                targets.items = ["id:minecraft:bread"]
                """, "progression.toml", "");
        assertTrue(parsed.isSuccess(), parsed.getErrorMessage());
        var rules = Schema4StageCompiler.compile(parsed.getStageDefinition(), parsed.getSourceConfig(), "test", 0).rules();
        var parents = rules.stream().filter(rule -> rule.effect() == RuleEffect.LOCK).map(rule -> rule.id()).collect(java.util.stream.Collectors.toSet());
        var exceptions = rules.stream().filter(rule -> rule.effect() == RuleEffect.EXCLUDE).toList();
        assertEquals(2, exceptions.size());
        assertEquals(parents, exceptions.stream().map(rule -> rule.parentRuleId()).collect(java.util.stream.Collectors.toSet()));
        assertTrue(exceptions.stream().allMatch(rule -> "always".equals(rule.settings().get("stage_state"))));
    }

}
