package com.enviouse.progressivestages.server.loader;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.config.StageSlotPolicy;
import com.enviouse.progressivestages.common.rehaul.ConditionNode;
import com.enviouse.progressivestages.common.stage.StageOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultShowcaseStagesTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesFiftyValidInterconnectedStagePackages() throws Exception {
        assertEquals(50, DefaultShowcaseStages.stageCount());
        assertEquals(150, DefaultShowcaseStages.files().size());
        assertFalse(DefaultShowcaseStages.stageIds().contains("stone_age"));
        assertFalse(DefaultShowcaseStages.stageIds().contains("iron_age"));
        assertFalse(DefaultShowcaseStages.stageIds().contains("diamond_age"));
        for (var entry : DefaultShowcaseStages.files().entrySet()) {
            Path file = temporaryDirectory.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, entry.getValue());
        }

        var discovery = StagePackageDiscovery.discover(temporaryDirectory);
        assertTrue(discovery.errors().isEmpty(), () -> String.join("\n", discovery.errors()));
        assertEquals(50, discovery.packages().size());
        List<StageDefinition> definitions = new ArrayList<>();
        Map<StageId, com.enviouse.progressivestages.common.rehaul.CompiledStage> compiledStages = new LinkedHashMap<>();
        com.enviouse.progressivestages.common.rehaul.CompiledStage diamondEngineer = null;
        for (StagePackageSource source : discovery.packages()) {
            StageFileParser.ParseResult parsed = StagePackageParser.parse(source);
            assertTrue(parsed.isSuccess(), source.root() + ". " + parsed.getErrorMessage());
            definitions.add(parsed.getStageDefinition());
            var compiled = Schema4StageCompiler.compile(parsed.getStageDefinition(), parsed.getSourceConfig(),
                source.sourceId(), 0);
            compiledStages.put(compiled.id(), compiled);
            if (compiled.id().equals(StageId.parse("showcase:diamond_engineer"))) diamondEngineer = compiled;
        }
        assertTrue(StageOrder.validateDefinitions(definitions).isEmpty(),
            () -> String.join("\n", StageOrder.validateDefinitions(definitions)));
        assertNotNull(diamondEngineer);
        assertEquals(4, definitions.stream().filter(stage -> stage.getDependencies().isEmpty()).count());
        assertEquals(3, definitions.stream().filter(stage -> stage.getSlotGroup().equals("beginner_paths")).count());
        assertTrue(definitions.stream().filter(stage -> stage.getSlotGroup().equals("beginner_paths"))
            .allMatch(stage -> stage.getSlotLimit() == 2 && stage.getSlotPolicy() == StageSlotPolicy.DENY));
        assertEquals(6, definitions.stream().filter(stage -> stage.getSlotGroup().equals("engineering_tiers")).count());
        assertTrue(definitions.stream().filter(stage -> stage.getSlotGroup().equals("engineering_tiers"))
            .allMatch(stage -> stage.getSlotLimit() == 0));
        assertEquals(4, definitions.stream().filter(stage -> stage.getSlotGroup().equals("mining_modes")).count());
        assertTrue(definitions.stream().filter(stage -> stage.getSlotGroup().equals("mining_modes"))
            .allMatch(stage -> stage.getSlotLimit() == 1
                && stage.getSlotPolicy() == StageSlotPolicy.REPLACE_OLDEST));
        assertEquals(List.of(StageId.parse("showcase:iron_engineer")),
            diamondEngineer.compatibilityView().getDependencies());
        StageDefinition grandmaster = definitions.stream()
            .filter(stage -> stage.getId().equals(StageId.parse("showcase:grandmaster"))).findFirst().orElseThrow();
        assertEquals(com.enviouse.progressivestages.common.stage.DependencyMode.AT_LEAST,
            grandmaster.getDependencyMode());
        assertEquals(3, grandmaster.getDependencyCount());
        assertEquals(20, grandmaster.getDependencies().size());
        assertEquals(5, definitions.stream().filter(StageDefinition::isTemporary).count());
        assertEquals(15, definitions.stream().filter(StageDefinition::isPurchasable).count());
        StageDefinition mage = stage(definitions, "showcase:mage");
        StageDefinition warrior = stage(definitions, "showcase:warrior");
        StageDefinition ranger = stage(definitions, "showcase:ranger");
        assertTrue(mage.isPurchasable());
        assertTrue(warrior.isPurchasable());
        assertFalse(ranger.isPurchasable());
        assertFalse(stage(definitions, "showcase:necromancer").isHidden());
        assertEquals("unlocked", stage(definitions, "showcase:necromancer").getUiReveal());
        assertEquals("unlocked", stage(definitions, "showcase:shadow_scout").getUiReveal());
        assertEquals("unlocked", stage(definitions, "showcase:quantum_engineer").getUiReveal());
        assertTrue(stage(definitions, "showcase:end_resolve").isHidden());
        assertTrue(compiledStages.get(StageId.parse("showcase:ranger")).progression().lifecycleRules().size() >= 1);
        assertTrue(compiledStages.values().stream()
            .filter(stage -> !stage.progression().lifecycleRules().isEmpty()).count() >= 35);
        ConditionNode marksmanCondition = compiledStages.get(StageId.parse("showcase:marksman"))
            .progression().lifecycleRules().getFirst().condition();
        assertTrue(marksmanCondition instanceof ConditionNode.Leaf);
        ConditionNode.Leaf marksmanLeaf = (ConditionNode.Leaf) marksmanCondition;
        assertEquals("progressivestages:kill_with_item", marksmanLeaf.providerId().toString());
        assertEquals("minecraft:skeleton", marksmanLeaf.arguments().get("id"));
        assertEquals("minecraft:bow", marksmanLeaf.arguments().get("with"));
        assertEquals(32, diamondEngineer.compatibilityView().getCost().items().getFirst().count());
        assertEquals("minecraft:diamond", diamondEngineer.compatibilityView().getCost().items().getFirst().item().toString());
        assertEquals(1, diamondEngineer.progression().dropModifiers().size());
        assertEquals(2.0, diamondEngineer.progression().dropModifiers().getFirst().multiply());
        assertEquals("minecraft:fortune",
            diamondEngineer.progression().dropModifiers().getFirst().requiredEnchantment().toString());
        Set<net.minecraft.resources.ResourceLocation> ids = new HashSet<>();
        compiledStages.values().forEach(stage -> {
            stage.rules().forEach(rule -> assertTrue(ids.add(rule.id()), "Duplicate rule id. " + rule.id()));
            stage.progression().lifecycleRules().forEach(rule ->
                assertTrue(ids.add(rule.id()), "Duplicate lifecycle id. " + rule.id()));
            stage.progression().modifiers().forEach(rule ->
                assertTrue(ids.add(rule.id()), "Duplicate modifier id. " + rule.id()));
            stage.progression().dropModifiers().forEach(rule ->
                assertTrue(ids.add(rule.id()), "Duplicate drop modifier id. " + rule.id()));
            stage.progression().challenges().forEach(rule ->
                assertTrue(ids.add(rule.id()), "Duplicate challenge id. " + rule.id()));
        });
    }

    private static StageDefinition stage(List<StageDefinition> definitions, String id) {
        StageId stageId = StageId.parse(id);
        return definitions.stream().filter(stage -> stage.getId().equals(stageId)).findFirst().orElseThrow();
    }
}
