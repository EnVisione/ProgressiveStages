package com.enviouse.progressivestages.server.loader;

import com.enviouse.progressivestages.common.lock.ConditionalRule;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.config.StageGuide;
import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import com.enviouse.progressivestages.common.rehaul.RuleLifetime;
import com.enviouse.progressivestages.common.api.structure.StructureLeaveOutcome;
import net.minecraft.resources.ResourceLocation;
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

    @Test
    void parsesChatRestrictionsAndPersonalGameModeGrant() {
        var result = StageFileParser.parseText("""
            [stage]
            id = "chat_access"
            team_stage = false
            [chat]
            lock_access = true
            mask_terms = ["Create", "Mechanical Press"]
            [gamemode]
            grant_in = ["creative", "spectator"]
            """, "chat.toml", "test", false);
        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertTrue(result.getStageDefinition().getChat().lockAccess());
        assertEquals(List.of("Create", "Mechanical Press"), result.getStageDefinition().getChat().maskTerms());
        assertTrue(result.getStageDefinition().getGameModeGrant().modes()
            .contains(net.minecraft.world.level.GameType.CREATIVE));
    }

    @Test
    void rejectsUnsafeOrSharedGameModeRulesAndBadChatTerms() {
        assertFalse(StageFileParser.parseText("""
            [stage]
            id = "shared_mode"
            [gamemode]
            grant_in = ["creative"]
            """, "bad.toml", "test", false).isSuccess());
        assertFalse(StageFileParser.parseText("""
            [stage]
            id = "bad_mode"
            team_stage = false
            [gamemode]
            grant_in = ["unknown"]
            """, "bad.toml", "test", false).isSuccess());
        assertFalse(StageFileParser.parseText("""
            [stage]
            id = "unset_mode"
            team_stage = false
            [gamemode]
            grant_in = ["not_set"]
            """, "bad.toml", "test", false).isSuccess());
        assertFalse(StageFileParser.parseText("""
            [stage]
            id = "bad_terms"
            [chat]
            mask_terms = ["Create", "create"]
            """, "bad.toml", "test", false).isSuccess());
    }

    @Test
    void parsesBoundedGuideMetadataForLegacyStages() {
        var result = StageFileParser.parseText("""
            [stage]
            id = "guide_test"
            [guide]
            how_to_unlock = "Complete the first quest."
            next_steps = "Visit the village."
            where_to_find = "Look beside the river."
            recommendation = "include"
            enabled = false
            """, "guide.toml", "test", false);

        assertTrue(result.isSuccess());
        assertEquals("Complete the first quest.", result.getStageDefinition().getGuide().howToUnlock());
        assertEquals(StageGuide.Recommendation.INCLUDE, result.getStageDefinition().getGuide().recommendation());
        assertFalse(result.getStageDefinition().getGuide().enabled());
    }

    @Test
    void stageGuideDefaultsToEnabledForExistingFilesAndRejectsWrongTypes() {
        var legacy = StageFileParser.parseText("[stage]\nid = \"legacy_guide\"\n[guide]\nhow_to_unlock = \"Find the key.\"\n",
            "guide.toml", "test", false);
        var wrongType = StageFileParser.parseText("[stage]\nid = \"bad_guide\"\n[guide]\nenabled = \"false\"\n",
            "guide.toml", "test", false);

        assertTrue(legacy.isSuccess());
        assertTrue(legacy.getStageDefinition().getGuide().enabled());
        assertFalse(wrongType.isSuccess());
    }

    @Test
    void rejectsInvalidGuideTextAndRecommendation() {
        var control = StageFileParser.parseText("[stage]\nid = \"guide_test\"\n[guide]\nhow_to_unlock = \"bad"
            + Character.toString((char) 1) + "text\"\n", "guide.toml", "test", false);
        var recommendation = StageFileParser.parseText("""
            [stage]
            id = "guide_test"
            [guide]
            recommendation = "sometimes"
            """, "guide.toml", "test", false);

        assertFalse(control.isSuccess());
        assertFalse(recommendation.isSuccess());
    }

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
        assertFalse(result.getStageDefinition().isPriorityAuthored());
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
    void preservesAnExplicitZeroStagePriority() throws IOException {
        Path file = write("zero_priority.toml", """
            [stage]
            id = "example:zero"
            priority = 0
            """);

        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(file);

        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals(0, result.getStageDefinition().getPriority());
        assertTrue(result.getStageDefinition().isPriorityAuthored());
    }

    @Test
    void parsesIndependentStructureProtectionAndPriority() throws IOException {
        Path file = write("structure_rules.toml", """
            [schema]
            version = 4

            [stage]
            id = "example:protection"
            priority = 12

            [structures]
            priority = 3
            global_priority = -2
            locked_entry = ["minecraft:stronghold|priority=20"]

            [structures.rules]
            entry_allowed = true
            priority = -7
            prevent_block_place = true
            """);

        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(file);

        assertTrue(result.isSuccess(), result.getErrorMessage());
        var rules = result.getStageDefinition().getLocks().structures();
        assertTrue(rules.entryAllowed());
        assertEquals(-7, rules.priority());
        assertEquals(3, rules.categoryPriority());
        assertEquals(-2, rules.globalPriority());
        assertTrue(rules.preventBlockPlace());
        assertTrue(result.getStageDefinition().isPriorityAuthored());
        assertEquals("minecraft:stronghold|priority=20", rules.lockedEntry().locked().getFirst().raw());
        assertEquals(20, rules.lockedEntry().locked().getFirst().explicitPriority());
    }

    @Test
    void preservesLegacyStructureSelectorsBeforeSchema4() throws IOException {
        Path file = write("legacy_structure_selector.toml", """
            [schema]
            version = 3

            [stage]
            id = "example:legacy_structure"

            [structures]
            locked_entry = ["mod:example", "name:stronghold"]
            """);

        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(file);

        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals(List.of("mod:example", "name:stronghold"),
            result.getStageDefinition().getLocks().structures().lockedEntry().locked().stream()
                .map(entry -> entry.raw()).toList());
    }

    @Test
    void rejectsNonBooleanStructureFlagsAndOutOfRangePriorities() throws IOException {
        Path wrongType = write("structure_wrong_type.toml", """
            [stage]
            id = "example:wrong"
            [structures]
            locked_entry = ["minecraft:stronghold"]
            [structures.rules]
            entry_allowed = "true"
            """);
        assertFalse(StageFileParser.parseWithErrors(wrongType).isSuccess());

        Path overflow = write("structure_overflow.toml", """
            [stage]
            id = "example:overflow"
            [structures]
            locked_entry = ["minecraft:stronghold"]
            [structures.rules]
            priority = 2147483648
        """);
        assertFalse(StageFileParser.parseWithErrors(overflow).isSuccess());

        Path inlineOverflow = write("structure_inline_overflow.toml", """
            [schema]
            version = 4

            [stage]
            id = "example:inline_overflow"
            [structures]
            locked_entry = ["minecraft:stronghold|priority=2147483648"]
            """);
        var inlineResult = StageFileParser.parseWithErrors(inlineOverflow);
        assertFalse(inlineResult.isSuccess());
        assertTrue(inlineResult.getErrorMessage().contains("priority"), inlineResult.getErrorMessage());
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
    void parsesEnchantmentSelectionWeights() throws IOException {
        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(write("enchant_weights.toml", """
            [stage]
            id = "enchant_weights"

            [enchants]
            selection_weights = ["minecraft:mending:0", "minecraft:fortune:10"]
            """));

        assertTrue(result.isSuccess(), result.getErrorMessage());
        var weights = result.getStageDefinition().getLocks().enchantSelectionWeights();
        assertEquals(2, weights.size());
        assertEquals("minecraft:mending", weights.getFirst().enchant().toString());
        assertEquals(0, weights.getFirst().weight());
        assertEquals("minecraft:fortune", weights.getLast().enchant().toString());
        assertEquals(10, weights.getLast().weight());
    }

    @Test
    void rejectsInvalidEnchantmentSelectionWeights() throws IOException {
        List<String> values = List.of(
            "minecraft:mending",
            "minecraft:mending:-1",
            "minecraft:mending:1025"
        );

        for (int index = 0; index < values.size(); index++) {
            StageFileParser.ParseResult result = StageFileParser.parseWithErrors(write(
                "bad_enchant_weight_" + index + ".toml", """
                    [stage]
                    id = "bad_enchant_weight"

                    [enchants]
                    selection_weights = ["%s"]
                    """.formatted(values.get(index))));
            assertFalse(result.isSuccess(), values.get(index));
            assertTrue(result.getErrorMessage().contains("selection weight"), result.getErrorMessage());
        }
    }

    @Test
    void rejectsDuplicateEnchantmentSelectionWeights() throws IOException {
        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(write("duplicate_enchant_weight.toml", """
            [stage]
            id = "duplicate_enchant_weight"

            [enchants]
            selection_weights = ["minecraft:mending:0", "minecraft:mending:4"]
            """));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Duplicate enchantment selection weight"),
            result.getErrorMessage());
    }

    @Test
    void parsesPerStageEnchantmentEnforcementOverride() throws IOException {
        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(write("enchant_override.toml", """
            [stage]
            id = "enchant_override"

            [enforcement]
            block_enchants = false
            """));

        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals(false, result.getStageDefinition().getLocks().enforcementOverrides()
            .get(com.enviouse.progressivestages.common.lock.EnforcementCategory.ENCHANTS));
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

    @Test
    void parsesInventoryInsertionInteractionsWithoutChangingLegacyInteractions() throws IOException {
        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(write("inventory_interaction.toml", """
            [stage]
            id = "inventory_interaction"

            [[interactions]]
            type = "item_on_block"
            held_item = "minecraft:stick"
            target_block = "minecraft:crafting_table"

            [[interactions]]
            type = "item_into_inventory"
            held_item = "tag:c:ores"
            target_kind = "block"
            target = "minecraft:chest"
            effect = "lock"
            priority = 250
            """));

        assertTrue(result.isSuccess(), result.getErrorMessage());
        var interactions = result.getStageDefinition().getLocks().interactions();
        assertEquals(2, interactions.size());
        assertEquals("item_on_block", interactions.getFirst().type());
        assertEquals("", interactions.getFirst().targetKind());
        var inventory = interactions.getLast();
        assertEquals("item_into_inventory", inventory.type());
        assertEquals("tag:c:ores", inventory.heldItem());
        assertEquals("block", inventory.targetKind());
        assertEquals("minecraft:chest", inventory.target());
        assertEquals("lock", inventory.effect());
        assertEquals(250, inventory.priority());

        LockRegistry registry = LockRegistry.getInstance();
        registry.clear();
        try {
            registry.registerStage(result.getStageDefinition());
            var compiled = registry.getAllInteractionLocksOfType("item_into_inventory");
            assertEquals(1, compiled.size());
            var entry = compiled.iterator().next();
            assertEquals("tag:c:ores", entry.heldItem);
            assertEquals("minecraft:chest", entry.targetBlock);
            assertEquals("block", entry.targetKind);
            assertEquals(250, entry.priority);
        } finally {
            registry.clear();
        }
    }

    @Test
    void registersModernInteractionSelectorsForTheLegacyQueryApi() throws IOException {
        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(write("modern_interaction.toml", """
            [stage]
            id = "modern_interaction"

            [[interactions]]
            type = "item_on_block"
            held_item = "all:*"
            target_block = "id:minecraft:crafting_table"
            """));

        assertTrue(result.isSuccess(), result.getErrorMessage());
        LockRegistry registry = LockRegistry.getInstance();
        registry.clear();
        try {
            registry.registerStage(result.getStageDefinition());
            var stages = registry.getRequiredStagesForInteraction(
                "item_on_block", "minecraft:stick", "minecraft:crafting_table");
            assertTrue(stages.contains(result.getStageDefinition().getId()));
        } finally {
            registry.clear();
        }
    }

    @Test
    void parsesInventoryInsertionActivationAndLifetimeMetadata() throws IOException {
        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(write("conditional_inventory_interaction.toml", """
            [stage]
            id = "conditional_inventory_interaction"

            [[interactions]]
            id = "example:ore_bin_window"
            type = "item_into_inventory"
            held_item = "id:minecraft:diamond"
            target_kind = "block"
            target = "id:minecraft:chest"
            effect = "lock"
            lifetime = "duration"
            duration = "30s"

            [interactions.while]
            type = "dimension"
            id = "minecraft:the_end"

            [interactions.reset_condition]
            type = "boolean"
            expected = true
            """));

        assertTrue(result.isSuccess(), result.getErrorMessage());
        var interaction = result.getStageDefinition().getLocks().interactions().getFirst();
        assertEquals(ResourceLocation.parse("example:ore_bin_window"), interaction.ruleId());
        assertEquals(RuleLifetime.DURATION, interaction.lifetime());
        assertTrue(interaction.condition() instanceof ConditionNode.Leaf);
        assertTrue(interaction.resetCondition() instanceof ConditionNode.Leaf);
        assertEquals("30s", interaction.activationSettings().get("duration"));

        LockRegistry registry = LockRegistry.getInstance();
        registry.clear();
        try {
            registry.registerStage(result.getStageDefinition());
            var entry = registry.getAllInteractionLocksOfType("item_into_inventory").iterator().next();
            assertEquals(ResourceLocation.parse("example:ore_bin_window"), entry.ruleId);
            assertEquals(RuleLifetime.DURATION, entry.lifetime);
            assertTrue(entry.condition instanceof ConditionNode.Leaf);
            assertTrue(entry.resetCondition instanceof ConditionNode.Leaf);
            assertEquals("30s", entry.activationSettings.get("duration"));
        } finally {
            registry.clear();
        }
    }

    @Test
    void supportsEverySelectorPrefixForInventoryInsertion() throws IOException {
        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(write("inventory_selectors.toml", """
            [stage]
            id = "inventory_selectors"

            [[interactions]]
            type = "item_into_inventory"
            held_item = "all:*"
            target_kind = "menu"
            target = "#example:workstations"

            [[interactions]]
            type = "item_into_inventory"
            held_item = "mod:minecraft"
            target_kind = "block"
            target = "name:selling"

            [[interactions]]
            type = "item_into_inventory"
            held_item = "tag:c:ores"
            target_kind = "inventory"
            target = "id:minecraft:player_inventory"
            """));

        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals(3, result.getStageDefinition().getLocks().interactions().size());
    }

    @Test
    void rejectsIncompleteInventoryInsertionInteractions() throws IOException {
        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(write("invalid_inventory_interaction.toml", """
            [stage]
            id = "invalid_inventory_interaction"

            [[interactions]]
            type = "item_into_inventory"
            held_item = "minecraft:iron_ore"
            target_kind = "missing"
            """));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("target_kind"), result.getErrorMessage());
    }

    @Test
    void rejectsInvalidInventoryInsertionEffectsAndPriorities() throws IOException {
        StageFileParser.ParseResult effect = StageFileParser.parseWithErrors(write("invalid_inventory_effect.toml", """
            [stage]
            id = "invalid_inventory_effect"

            [[interactions]]
            type = "item_into_inventory"
            held_item = "id:minecraft:iron_ore"
            target_kind = "block"
            target = "id:minecraft:chest"
            effect = "replace"
            """));
        assertFalse(effect.isSuccess());
        assertTrue(effect.getErrorMessage().contains("effect"), effect.getErrorMessage());

        StageFileParser.ParseResult priority = StageFileParser.parseWithErrors(write("invalid_inventory_priority.toml", """
            [stage]
            id = "invalid_inventory_priority"

            [[interactions]]
            type = "item_into_inventory"
            held_item = "id:minecraft:iron_ore"
            target_kind = "block"
            target = "id:minecraft:chest"
            priority = 1000001
            """));
        assertFalse(priority.isSuccess());
        assertTrue(priority.getErrorMessage().contains("priority"), priority.getErrorMessage());
    }

    @Test
    void rejectsAmbiguousGenericRecipeLocksWithoutReplacingTheCanonicalFields() throws IOException {
        StageFileParser.ParseResult result = StageFileParser.parseWithErrors(write("ambiguous_recipe_lock.toml", """
            [stage]
            id = "ambiguous_recipe_lock"

            [recipes]
            locked = ["id:minecraft:diamond_sword"]
            """));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("locked_items"), result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("locked_ids"), result.getErrorMessage());
    }

    private Path write(String name, String contents) throws IOException {
        Path file = temporaryDirectory.resolve(name);
        Files.writeString(file, contents);
        return file;
    }

    @org.junit.jupiter.api.Test
    void optionalTeamStagePreservesPresenceAndRejectsServerOverride() {
        String content = "[stage]\nid = \"progressivestages:chef\"\nteam_stage = false\n";
        var parsed = StageFileParser.parseText(content, "chef.toml", "test", false);
        org.junit.jupiter.api.Assertions.assertTrue(parsed.isSuccess());
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Optional.of(false),
            parsed.getStageDefinition().getTeamStage());
        org.junit.jupiter.api.Assertions.assertFalse(parsed.getStageDefinition().isScopePresent());
        org.junit.jupiter.api.Assertions.assertEquals(
            com.enviouse.progressivestages.common.stage.StageOwnershipOptions.Scope.TEAM,
            parsed.getStageDefinition().getOwnershipOptions().scope());

        String server = "[stage]\nid = \"progressivestages:server\"\nscope = \" server \"\nteam_stage = true\n";
        var rejected = StageFileParser.parseText(server, "server.toml", "test", false);
        org.junit.jupiter.api.Assertions.assertFalse(rejected.isSuccess());
    }

}
