package com.enviouse.progressivestages.server.loader;

import com.enviouse.progressivestages.common.rehaul.RuleEffect;
import com.enviouse.progressivestages.common.rehaul.RuleLifetime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(1, compiled.progression().challenges().size());
        assertEquals(2, compiled.progression().challenges().getFirst().budgets().getFirst().maximum());
    }
}
