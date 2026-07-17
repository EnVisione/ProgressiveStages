package com.enviouse.progressivestages.server.loader;

import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.stage.StageOrder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeginnerExamplePackTest {

    @Test
    void everyBeginnerStageParsesAndTheGraphValidates() throws IOException {
        Path stages = Path.of(System.getProperty("progressivestages.projectDir"),
            "examples", "beginner_pack", "stages");
        List<Path> files;
        try (var paths = Files.list(stages)) {
            files = paths.filter(path -> path.getFileName().toString().endsWith(".toml"))
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        }

        assertEquals(3, files.size());
        List<StageDefinition> definitions = new ArrayList<>();
        for (Path file : files) {
            StageFileParser.ParseResult result = StageFileParser.parseWithErrors(file);
            assertTrue(result.isSuccess(), file + ". " + result.getErrorMessage());
            definitions.add(result.getStageDefinition());
        }

        assertTrue(StageOrder.validateDefinitions(definitions).isEmpty(),
            () -> String.join("\n", StageOrder.validateDefinitions(definitions)));
    }

    @Test
    void generatedDiamondStageLinksTheGitHubGuides() {
        String diamond = DefaultStageTemplates.diamondAge();
        assertTrue(diamond.contains("https://github.com/EnVisione/ProgressiveStages/blob/master/GETTING_STARTED.md"));
        assertTrue(diamond.contains("https://github.com/EnVisione/ProgressiveStages/blob/master/DOCUMENTATION.md"));
    }
}
