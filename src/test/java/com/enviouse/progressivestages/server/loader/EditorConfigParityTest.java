package com.enviouse.progressivestages.server.loader;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import com.enviouse.progressivestages.common.lock.PrefixEntry;
import com.enviouse.progressivestages.common.rehaul.CompiledStage;
import com.enviouse.progressivestages.common.rehaul.LegacyStageCompiler;

final class EditorConfigParityTest {
    @Test
    void parsesTheEditorProducedBlockOverrideFixture() throws Exception {
        try (var fixture = getClass().getResourceAsStream("/block-overrides.toml")) {
            assertNotNull(fixture, "The editor produced fixture must be on the test classpath.");

            var parsed = StageFileParser.parseText(new String(fixture.readAllBytes(), StandardCharsets.UTF_8),
                "challenge4.toml", "test", false);
            assertTrue(parsed.isSuccess(), parsed.getErrorMessage());
            var overrides = parsed.getStageDefinition().getLocks().blockOverrides();
            assertEquals(2, overrides.size());
            assertEquals("blocks.overrides", overrides.get(0).sourceTable());
            assertEquals("blocks.overrides[0].target", overrides.get(0).sourceField());
            assertEquals("minecraft:diamond_ore", overrides.get(0).targets().getFirst().id().toString());
            assertEquals("minecraft:stone", overrides.get(0).displayAs().toString());
            assertEquals("minecraft:cobblestone", overrides.get(0).dropAs().toString());
            assertEquals(0, overrides.get(0).priority());

            assertEquals("ores.overrides", overrides.get(1).sourceTable());
            assertEquals("ores.overrides[0].targets", overrides.get(1).sourceField());
            assertEquals(2, overrides.get(1).targets().size());
            assertEquals(PrefixEntry.Kind.TAG, overrides.get(1).targets().get(0).kind());
            assertEquals("c:ores", overrides.get(1).targets().get(0).id().toString());
            assertEquals(PrefixEntry.Kind.MOD, overrides.get(1).targets().get(1).kind());
            assertEquals("immersiveengineering", overrides.get(1).targets().get(1).value());
            assertEquals(7, overrides.get(1).priority());
        }
    }

    @Test
    void rejectsTheIgnoredOresLockedCategoryWithAUsefulError() {
        var parsed = StageFileParser.parseText("[stage]\nid = \"challenge4\"\n[ores]\nlocked = [\"minecraft:diamond_ore\"]\n",
            "challenge4.toml", "test", false);
        assertFalse(parsed.isSuccess());
        assertTrue(parsed.getErrorMessage().contains("[ores].locked"));
        assertTrue(parsed.getErrorMessage().contains("[[blocks.overrides]]"));
    }

    @Test
    void compilesTheExactBlockOverrideBytesWrittenByTheEditorForm() throws Exception {
        try (var fixture = getClass().getResourceAsStream("/editor-generated-block-overrides.toml")) {
            assertNotNull(fixture, "The form-generated fixture must be on the test classpath.");
            String source = new String(fixture.readAllBytes(), StandardCharsets.UTF_8);
            var parsed = StageFileParser.parseText(source, "challenge4.toml", "test", false);
            assertTrue(parsed.isSuccess(), parsed.getErrorMessage());

            var legacy = LegacyStageCompiler.compile(parsed.getStageDefinition(), "test");
            var schema4 = Schema4StageCompiler.compile(parsed.getStageDefinition(), parsed.getSourceConfig(), "test", 0);
            assertEditorOverrideRows(legacy);
            assertEditorOverrideRows(schema4);
        }
    }

    private static void assertEditorOverrideRows(CompiledStage compiled) {
        var rows = compiled.compatibilityView().getLocks().blockOverrides();
        assertEquals(5, rows.size());
        assertEquals("minecraft:diamond_ore", rows.get(0).targets().getFirst().id().toString());
        assertEquals("minecraft:stone", rows.get(1).targets().getFirst().id().toString());
        assertEquals(PrefixEntry.Kind.TAG, rows.get(2).targets().getFirst().kind());
        assertEquals("minecraft:coal_ores", rows.get(2).targets().getFirst().id().toString());
        assertEquals(PrefixEntry.Kind.MOD, rows.get(3).targets().getFirst().kind());
        assertEquals("minecraft", rows.get(3).targets().getFirst().value());
        assertEquals(2, rows.get(4).targets().size());
        assertEquals("blocks.overrides", rows.get(4).sourceTable());
        assertEquals("minecraft:deepslate", rows.get(4).displayAs().toString());
        assertEquals("minecraft:raw_iron", rows.get(4).dropAs().toString());
        assertEquals(15, rows.get(4).priority());
    }

    @Test
    void reportsTheExactFieldForAnInvalidScalarTarget() {
        var parsed = StageFileParser.parseText("[stage]\nid = \"challenge4\"\n"
                + "[[blocks.overrides]]\ntarget = \"name:ore\"\n"
                + "display_as = \"minecraft:stone\"\ndrop_as = \"minecraft:cobblestone\"\n",
            "challenge4.toml", "test", false);
        assertFalse(parsed.isSuccess());
        assertTrue(parsed.getErrorMessage().contains("blocks.overrides[0].target must be"));
        assertFalse(parsed.getErrorMessage().contains("blocks.overrides[0].target]"));
    }

    @Test
    void rejectsMalformedBlockOverrideRowsAtTheirAuthoredField() {
        var cases = java.util.List.of(
            new InvalidOverride("non-string target", "target = 7", "blocks.overrides[0].target"),
            new InvalidOverride("empty target list", "targets = []", "blocks.overrides[0] needs"),
            new InvalidOverride("two target fields", "target = \"minecraft:stone\"\ntargets = [\"minecraft:stone\"]",
                "blocks.overrides[0] must use target or targets"),
            new InvalidOverride("non-string list member", "targets = [\"minecraft:stone\", 7]",
                "blocks.overrides[0].targets[1]"),
            new InvalidOverride("unsupported selector", "target = \"name:ore\"", "blocks.overrides[0].target"),
            new InvalidOverride("unknown display block", "target = \"minecraft:stone\"\ndisplay_as = \"missing:stone\"",
                "blocks.overrides[0].display_as"),
            new InvalidOverride("unknown drop item", "target = \"minecraft:stone\"\ndrop_as = \"missing:stone\"",
                "blocks.overrides[0].drop_as"),
            new InvalidOverride("priority overflow", "target = \"minecraft:stone\"\npriority = 2147483648",
                "blocks.overrides[0].priority")
        );
        for (var testCase : cases) {
            String source = "[stage]\nid = \"challenge4\"\n[[blocks.overrides]]\n"
                + testCase.row() + "\n";
            if (!testCase.row().contains("display_as")) source += "display_as = \"minecraft:stone\"\n";
            if (!testCase.row().contains("drop_as")) source += "drop_as = \"minecraft:cobblestone\"\n";
            var parsed = StageFileParser.parseText(source, "challenge4.toml", "test", false);
            assertFalse(parsed.isSuccess(), testCase.name());
            assertTrue(parsed.getErrorMessage().contains(testCase.field()),
                testCase.name() + " must point to " + testCase.field() + ". Actual error: " + parsed.getErrorMessage());
        }
    }

    private record InvalidOverride(String name, String row, String field) {}
}
