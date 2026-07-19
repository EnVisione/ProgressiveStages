package com.enviouse.progressivestages.server.loader;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyCompatibilityBaselineTest {

    private static final Path PROJECT = Path.of(System.getProperty("progressivestages.projectDir"));

    @Test
    void legacyGoldenSourcesRemainStableAndParseable() throws Exception {
        Map<String, String> expected = Map.of(
            "examples/reference/diamond_stage.toml",
            "cb09183e3d602484b2031f51c6b283c1791e52736c122083f1f980d21d804b7c",
            "examples/beginner_pack/stages/stone_age.toml",
            "36ad8de47bf1709d841f1def05e46ed3c82c9e00f3ea9cbac198874f2c6625d5",
            "examples/beginner_pack/stages/iron_age.toml",
            "398a160b66e546672691e9a8f63899416f20631b55d43cf5e1bbcc348e930b1c",
            "examples/beginner_pack/stages/diamond_age.toml",
            "4de5d55a469f4d5b13e5a49177aef855a2db0227f3eda324667d1d5ceac76d14"
        );

        for (Map.Entry<String, String> entry : expected.entrySet()) {
            Path source = PROJECT.resolve(entry.getKey());
            assertEquals(entry.getValue(), sha256(source), entry.getKey());
            StageFileParser.ParseResult parsed = StageFileParser.parseWithErrors(source);
            assertTrue(parsed.isSuccess(), parsed::getErrorMessage);
        }
    }

    @Test
    void generatedDiamondDefaultRemainsTheGoldenReference() throws Exception {
        assertEquals(DefaultStageTemplates.diamondAge(), Files.readString(
            PROJECT.resolve("examples/reference/diamond_stage.toml")));
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
    }
}
