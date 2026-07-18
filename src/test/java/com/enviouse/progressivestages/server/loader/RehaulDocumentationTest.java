package com.enviouse.progressivestages.server.loader;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RehaulDocumentationTest {
    private static final Path PROJECT = Path.of(System.getProperty("progressivestages.projectDir"));

    @Test
    void guideLinksEveryTestedExampleAndAllThirtyFeatures() throws Exception {
        String guide = Files.readString(PROJECT.resolve("REHAUL_GUIDE.md"));
        for (String example : new String[] {"starter", "diamond", "mage", "structure_weapons",
                "end_fight", "wither_trial", "kubejs"}) {
            assertTrue(guide.contains("examples/rehaul/" + example));
            assertTrue(Files.isRegularFile(PROJECT.resolve(Path.of("examples/rehaul", example, "stage.toml"))));
            assertTrue(Files.isRegularFile(PROJECT.resolve(Path.of("examples/rehaul", example, "rules.toml"))));
            assertTrue(Files.isRegularFile(PROJECT.resolve(Path.of("examples/rehaul", example, "progression.toml"))));
        }
        for (int feature = 1; feature <= 30; feature++) {
            assertTrue(guide.contains("\n" + feature + ". "), "Missing feature " + feature);
        }
        assertTrue(Files.readString(PROJECT.resolve("DOCUMENTATION.md")).contains("REHAUL_GUIDE.md"));
        assertTrue(Files.readString(PROJECT.resolve("README.md")).contains("REHAUL_GUIDE.md"));
        assertTrue(DefaultStageTemplates.diamondAge().contains("REHAUL_GUIDE.md"));
    }
}
