package com.enviouse.progressivestages.server.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorApplyServiceTest {
    @TempDir
    Path root;

    @Test
    void distinguishesRevisionDriftFromLiveFileChanges() throws Exception {
        Path stage = root.resolve("stages/wizard_warlock/stage.toml");
        Files.createDirectories(stage.getParent());
        Files.writeString(root.resolve("progressivestages.toml"), "[general]\n");
        Files.writeString(stage, "[stage]\nid = \"wizard:warlock\"\n");
        Map<String, String> base = Map.of(
            "progressivestages.toml", "[general]\n",
            "stages/wizard_warlock/stage.toml", "[stage]\nid = \"wizard:warlock\"\n");
        EditorApplyService service = new EditorApplyService(root);

        assertTrue(service.liveFilesMatch(base));

        Files.writeString(stage, "[stage]\nid = \"wizard:wizard\"\n");
        assertFalse(service.liveFilesMatch(base));
    }
}
