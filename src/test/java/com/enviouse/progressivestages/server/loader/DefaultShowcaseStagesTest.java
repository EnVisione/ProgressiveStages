package com.enviouse.progressivestages.server.loader;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
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
    void generatesThirtyValidInterconnectedStagePackages() throws Exception {
        assertEquals(30, DefaultShowcaseStages.stageCount());
        assertEquals(90, DefaultShowcaseStages.files().size());
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
        assertEquals(30, discovery.packages().size());
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
        assertEquals(6, definitions.stream().filter(stage -> stage.getDependencies().isEmpty()).count());
        assertEquals(2, diamondEngineer.compatibilityView().getDependencies().size());
        StageDefinition grandmaster = definitions.stream()
            .filter(stage -> stage.getId().equals(StageId.parse("showcase:grandmaster"))).findFirst().orElseThrow();
        assertEquals(com.enviouse.progressivestages.common.stage.DependencyMode.AT_LEAST,
            grandmaster.getDependencyMode());
        assertEquals(3, grandmaster.getDependencyCount());
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
}
