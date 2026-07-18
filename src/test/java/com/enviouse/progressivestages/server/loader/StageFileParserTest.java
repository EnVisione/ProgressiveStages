package com.enviouse.progressivestages.server.loader;

import com.enviouse.progressivestages.common.lock.ConditionalRule;
import com.enviouse.progressivestages.common.api.structure.StructureLeaveOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageFileParserTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsNamespacedStageIdentifiers() throws IOException {
        Path file = write("namespaced.toml", """
            [stage]
            id = "example:root"
            display_name = "Root"
            """);

        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(file);

        assertTrue(result.isSuccess());
        assertEquals("example:root", result.getStageDefinition().getId().toString());
    }

    @Test
    void malformedIdentifiersBecomeValidationErrors() throws IOException {
        Path file = write("invalid.toml", """
            [stage]
            id = "invalid stage"
            """);

        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(file);

        assertFalse(result.isSuccess());
        assertFalse(result.isSyntaxError());
    }

    @Test
    void invalidTriggerTypesRejectTheDefinition() throws IOException {
        Path file = write("trigger.toml", """
            [stage]
            id = "trigger_test"

            [[triggers]]
            type = "missing_provider"
            target = "anything"
            """);

        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(file);

        assertFalse(result.isSuccess());
        assertFalse(result.isSyntaxError());
        assertTrue(result.getErrorMessage().contains("Unknown trigger condition type"), result.getErrorMessage());
    }

    @Test
    void generatedStageTemplatesRemainValid() throws IOException {
        String[] names = {"stone.toml", "iron.toml", "diamond.toml"};
        String[] templates = {
            DefaultStageTemplates.stoneAge(),
            DefaultStageTemplates.ironAge(),
            DefaultStageTemplates.diamondAge()
        };

        for (int index = 0; index < names.length; index++) {
            StageFileParser.ParseResult result = StageFileParser.parseWithErrors(
                write(names[index], templates[index]));
            assertTrue(result.isSuccess(), result.getErrorMessage());
        }
    }

    @Test
    void invalidCostEntriesRejectTheDefinition() throws IOException {
        Path file = write("cost.toml", """
            [stage]
            id = "cost_test"

            [cost]
            items = ["not a resource"]
            """);

        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(file);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Invalid cost item"), result.getErrorMessage());
    }

    @Test
    void customProgressionMapBackgroundIsParsed() throws IOException {
        Path file = write("background.toml", """
            [stage]
            id = "background_test"

            [display]
            background = "mypack:gui/progression"
            """);

        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(file);

        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals("mypack:gui/progression", result.getStageDefinition().getUiBackground());
    }

    @Test
    void parsesLiveAndTriggeredPriorityRules() throws IOException {
        Path file = write("conditional.toml", """
            [stage]
            id = "end_fight"

            [[temporary_locks]]
            id = "end_limits"
            priority = 200
            stage_state = "owned"

            [temporary_locks.when]
            dimensions = ["minecraft:the_end"]

            [temporary_locks.targets]
            items = ["minecraft:diamond_pickaxe"]
            abilities = ["jump", "elytra"]

            [[temporary_unlocks]]
            id = "stronghold_access"
            priority = 100

            [temporary_unlocks.targets]
            structures = ["minecraft:stronghold"]

            [[triggered_locks]]
            id = "dragon_combat"
            trigger = "combat"
            trigger_entities = ["minecraft:ender_dragon"]
            duration = "15s"
            refresh_duration = false

            [triggered_locks.targets]
            items = ["tag:minecraft:tools"]

            [triggered_locks.except]
            items = ["minecraft:iron_sword"]

            [[conditional_rules]]
            id = "manual_bow_permission"
            effect = "unlock"
            activation = "triggered"
            trigger = "manual"
            duration_seconds = 30

            [conditional_rules.targets]
            items = ["minecraft:bow"]
            """);

        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(file);

        assertTrue(result.isSuccess(), result.getErrorMessage());
        List<ConditionalRule> rules = result.getStageDefinition().getConditionalRules();
        assertEquals(4, rules.size());

        ConditionalRule endLimits = rules.get(0);
        assertEquals("progressivestages:end_fight/end_limits", endLimits.id().toString());
        assertEquals(ConditionalRule.Effect.LOCK, endLimits.effect());
        assertEquals(ConditionalRule.Activation.LIVE, endLimits.activation());
        assertEquals(200, endLimits.priority());
        assertEquals("minecraft:the_end", endLimits.context().dimensions().getFirst().raw());
        assertTrue(endLimits.targets().has(ConditionalRule.TargetType.ITEM));
        assertTrue(endLimits.targets().has(ConditionalRule.TargetType.ABILITY));

        ConditionalRule stronghold = rules.get(1);
        assertEquals(ConditionalRule.Effect.UNLOCK, stronghold.effect());
        assertEquals("minecraft:stronghold",
            stronghold.targets().included(ConditionalRule.TargetType.STRUCTURE).getFirst().raw());

        ConditionalRule combat = rules.get(2);
        assertEquals(ConditionalRule.TriggerType.COMBAT, combat.triggerType());
        assertEquals(15_000L, combat.durationMillis());
        assertFalse(combat.refreshDuration());
        assertEquals("minecraft:iron_sword",
            combat.targets().excluded(ConditionalRule.TargetType.ITEM).getFirst().raw());

        ConditionalRule manual = rules.get(3);
        assertEquals(ConditionalRule.Effect.UNLOCK, manual.effect());
        assertEquals(ConditionalRule.Activation.TRIGGERED, manual.activation());
        assertEquals(30_000L, manual.durationMillis());
    }

    @Test
    void rejectsConditionalRulesWithoutTargets() throws IOException {
        Path file = write("conditional_no_targets.toml", """
            [stage]
            id = "broken"

            [[temporary_locks]]
            id = "empty"
            """);

        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(file);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("no targets"), result.getErrorMessage());
    }

    @Test
    void rejectsDuplicateConditionalRuleIdentifiersInOneStage() throws IOException {
        Path file = write("conditional_duplicate.toml", """
            [stage]
            id = "broken"

            [[temporary_locks]]
            id = "same"
            [temporary_locks.targets]
            items = ["minecraft:bow"]

            [[temporary_unlocks]]
            id = "same"
            [temporary_unlocks.targets]
            items = ["minecraft:bow"]
            """);

        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(file);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Duplicate conditional rule id"), result.getErrorMessage());
    }

    @Test
    void parsesStructureSessionActiveLocksAndLeaveTriggers() throws IOException {
        Path file = write("structure_session.toml", """
            [stage]
            id = "dungeon_active"

            [active_locks]
            scope = "structure_session"

            [active_locks.items]
            locked = ["id:minecraft:bow", "tag:minecraft:pickaxes"]
            always_unlocked = ["id:minecraft:wooden_pickaxe"]

            [active_locks.enforcement]
            block_item_use = true

            [[triggers]]
            type = "leave_structure"
            structure = "#minecraft:stronghold"
            provider = "questtokens:assignments"
            required_session_stage = "dungeon_active"
            outcomes = ["completed", "recovery"]
            """);

        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(file);

        assertTrue(result.isSuccess(), result.getErrorMessage());
        var stage = result.getStageDefinition();
        assertFalse(stage.getActiveLocks().isEmpty());
        assertEquals(2, stage.getActiveLocks().items().locked().size());
        var leave = stage.getTriggers().getFirst().conditions().getFirst();
        assertEquals("questtokens:assignments", leave.provider().toString());
        assertEquals("progressivestages:dungeon_active", leave.requiredSessionStage().toString());
        assertEquals(java.util.Set.of(StructureLeaveOutcome.COMPLETED, StructureLeaveOutcome.RECOVERY),
            leave.outcomes());
    }

    @Test
    void rejectsUnknownActiveLockScopesAndLeaveOutcomes() throws IOException {
        StageFileParser.ParseResult scope = StageFileParser.parseWithErrors(write("bad_scope.toml", """
            [stage]
            id = "bad_scope"
            [active_locks]
            scope = "everywhere_forever"
            """));
        StageFileParser.ParseResult outcome = StageFileParser.parseWithErrors(write("bad_outcome.toml", """
            [stage]
            id = "bad_outcome"
            [[triggers]]
            type = "leave_structure"
            structure = "minecraft:stronghold"
            outcome = "victoriousish"
            """));

        assertFalse(scope.isSuccess());
        assertTrue(scope.getErrorMessage().contains("Unknown active lock scope"));
        assertFalse(outcome.isSuccess());
        assertTrue(outcome.getErrorMessage().contains("Invalid structure leave outcome"));
    }

    @Test
    void rejectsUnsupportedActiveLockCategories() throws IOException {
        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(write("bad_category.toml", """
            [stage]
            id = "bad_category"
            [active_locks]
            scope = "structure_session"
            [active_locks.blocks]
            locked = ["minecraft:stone"]
            """));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Unknown active lock category"));
    }

    @Test
    void parsesConfigurableStageSlotsAndReplacementPolicy() throws IOException {
        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(write("slots.toml", """
            [stage]
            id = "slot_test"
            slot_group = "beginner_classes"
            slot_limit = 2
            slot_policy = "replace_oldest"
            """));

        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals("beginner_classes", result.getStageDefinition().getSlotGroup());
        assertEquals(2, result.getStageDefinition().getSlotLimit());
        assertEquals(com.enviouse.progressivestages.common.config.StageSlotPolicy.REPLACE_OLDEST,
            result.getStageDefinition().getSlotPolicy());
    }

    private Path write(String name, String contents) throws IOException {
        Path file = temporaryDirectory.resolve(name);
        Files.writeString(file, contents);
        return file;
    }
}
