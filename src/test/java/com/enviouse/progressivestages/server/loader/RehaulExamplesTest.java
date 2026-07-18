package com.enviouse.progressivestages.server.loader;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.rehaul.RuleEffect;
import com.enviouse.progressivestages.common.rehaul.RuleLifetime;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RehaulExamplesTest {
    private static final Path PROJECT = Path.of(System.getProperty("progressivestages.projectDir"));

    @Test
    void everyPublishedThreeFileExampleParsesAndCompiles() {
        var discovery = StagePackageDiscovery.discover(PROJECT.resolve("examples/rehaul"));
        assertTrue(discovery.errors().isEmpty(), String.join(". ", discovery.errors()));
        Map<StageId, com.enviouse.progressivestages.common.rehaul.CompiledStage> compiled = new LinkedHashMap<>();
        for (StagePackageSource source : discovery.packages()) {
            var parsed = StagePackageParser.parse(source);
            assertTrue(parsed.isSuccess(), source.root() + ". " + parsed.getErrorMessage());
            var stage = Schema4StageCompiler.compile(parsed.getStageDefinition(), parsed.getSourceConfig(),
                source.sourceId(), 0);
            compiled.put(stage.id(), stage);
        }

        assertEquals(8, compiled.size());
        var diamond = compiled.get(StageId.parse("examples:diamond"));
        assertTrue(diamond.rules().stream().anyMatch(rule -> rule.viewerPolicy().shared()
            == com.enviouse.progressivestages.common.rehaul.ViewerPolicy.Mode.SHOW));
        assertEquals(2, diamond.progression().lifecycleRules().size());
        assertEquals(1, diamond.progression().modifiers().size());
        var structure = compiled.get(StageId.parse("examples:structure_weapons"));
        assertTrue(structure.rules().stream().anyMatch(rule -> rule.effect() == RuleEffect.DENY
            && rule.lifetime() == RuleLifetime.SESSION));
        assertTrue(structure.rules().stream().anyMatch(rule -> rule.effect() == RuleEffect.EXCLUDE));
        assertTrue(structure.rules().stream().anyMatch(rule -> rule.effect() == RuleEffect.ALLOW));
        var trial = compiled.get(StageId.parse("examples:wither_trial"));
        assertEquals(2, trial.progression().challenges().getFirst().budgets().getFirst().maximum());
        assertEquals("minecraft:nether_star", trial.progression().challenges().getFirst().hud().icon());
        assertEquals("pulse", trial.progression().challenges().getFirst().hud().animation());
        var knight = compiled.get(StageId.parse("examples:knight"));
        assertEquals(1, knight.progression().profiles().size());
        assertEquals(2, knight.progression().modifiers().getFirst().transforms().size());
    }

    @Test
    void javaExtensionExampleUsesThePublicTypedRegistrationSurface() throws IOException {
        String source = Files.readString(PROJECT.resolve(
            "examples/rehaul/java/ExampleProgressiveStagesPlugin.java"));
        assertTrue(source.contains("ProgressiveStagesRehaulAPI.registerCondition"));
        assertTrue(source.contains("ProgressiveStagesRehaulAPI.registerExtensionMetadata"));
        assertTrue(source.contains("implements ConditionProvider"));
        assertTrue(source.contains("ExtensionArgument"));
    }

}
