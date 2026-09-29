package com.enviouse.progressivestages.server.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorDraftTest {
    @Test
    void returnsSerializableFieldDiagnosticsForThePackageIdentityFile() {
        String identity = "[schema]\nversion = 4\n[stage]\nid = \"test:chef\"\n[luckperms]\nenabled = \"false\"\n";
        Map<String, String> files = Map.of("stages/chef/stage.toml", identity,
            "stages/chef/rules.toml", "# Rules remain editable.\n");
        DraftValidation validation = EditorDraftValidator.validate(files, 17);
        assertTrue(!validation.valid());
        assertEquals(17, validation.validatedRevision());
        assertEquals("stages/chef/stage.toml", validation.diagnostics().getFirst().file());
        assertEquals("luckperms.enabled", validation.diagnostics().getFirst().field());
        var json = com.google.gson.JsonParser.parseString(new com.google.gson.Gson().toJson(validation)).getAsJsonObject();
        assertEquals("invalid_type", json.getAsJsonArray("diagnostics").get(0).getAsJsonObject().get("code").getAsString());
        assertEquals(identity, files.get("stages/chef/stage.toml"));
        assertTrue(!json.toString().contains("progressivestages-editor-validation-"));
    }

    @Test
    void invalidBlockOverrideDraftsCannotChangeAcceptedFilesOrRevision(@TempDir Path temporary) throws IOException {
        String stagePath = "stages/block_override_validation/stage.toml";
        String rulesPath = "stages/block_override_validation/rules.toml";
        String identity = "[schema]\nversion = 4\n[stage]\nid = \"test:block_override_validation\"\n";
        String acceptedRules = """
            [[blocks.overrides]]
            target = "id:minecraft:diamond_ore"
            display_as = "minecraft:stone"
            drop_as = "minecraft:cobblestone"
            """;
        Map<String, String> acceptedFiles = new LinkedHashMap<>();
        acceptedFiles.put(stagePath, identity);
        acceptedFiles.put(rulesPath, acceptedRules);

        Path configRoot = temporary.resolve("config");
        for (var file : acceptedFiles.entrySet()) {
            Path target = configRoot.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue());
        }

        UUID owner = UUID.randomUUID();
        long acceptedRevision = 41;
        EditorDraft draft = new EditorDraft(UUID.randomUUID(), owner, acceptedRevision, 0, acceptedFiles);
        EditorApplyService applyService = new EditorApplyService(configRoot);
        var runtime = com.enviouse.progressivestages.server.rehaul.RehaulRuntime.get();
        long mappingRevision = runtime.snapshot().revision();
        List<String> invalidRules = List.of(
            acceptedRules.replace("target = \"id:minecraft:diamond_ore\"", "target = 42"),
            "[[blocks.overrides]]\ntargets = []\ndisplay_as = \"minecraft:stone\"\ndrop_as = \"minecraft:cobblestone\"\n",
            acceptedRules.replace("target = \"id:minecraft:diamond_ore\"", "target = \"id:minecraft:diamond_ore\"\ntargets = [\"id:minecraft:iron_ore\"]"),
            acceptedRules.replace("minecraft:stone", "missing:block"),
            acceptedRules.replace("minecraft:cobblestone", "missing:item"),
            acceptedRules.replace("drop_as = \"minecraft:cobblestone\"", "drop_as = \"minecraft:cobblestone\"\npriority = 2147483648"),
            acceptedRules.replace("id:minecraft:diamond_ore", "wildcard:minecraft:*"),
            "[ores]\nlocked = [\"id:minecraft:diamond_ore\"]\n",
            """
                [[rules]]
                effect = "lock"
                action = "display"
                targets.ores = ["id:minecraft:diamond_ore"]
                """,
            """
                [[rules]]
                effect = "lock"
                action = "break"
                targets.regions = ["id:minecraft:overworld"]
                """,
            """
                [[rules]]
                effect = "lock"
                targets.blocks = ["id:minecraft:diamond_block"]
                [[rules.exceptions]]
                effect = "exclude"
                targets.ore = ["id:minecraft:diamond_ore"]
                """
        );

        for (String invalid : invalidRules) {
            long draftRevision = draft.revision();
            draft.mutate(owner, draftRevision, rulesPath, invalid);
            long rejectedRevision = draft.revision();
            EditorApplyResult result = applyService.apply(null, owner, draft, acceptedRevision, true);

            assertFalse(result.success(), result.explanation());
            assertEquals("validation_failed", result.code());
            assertFalse(result.validation().valid());
            assertEquals(acceptedRevision, result.configurationRevision());
            assertEquals(rejectedRevision, result.validation().validatedRevision());
            assertEquals(acceptedFiles, draft.baseFiles());
            assertEquals(invalid, draft.files().get(rulesPath));
            assertEquals(identity, Files.readString(configRoot.resolve(stagePath)));
            assertEquals(acceptedRules, Files.readString(configRoot.resolve(rulesPath)));
            assertFalse(Files.exists(configRoot.resolve(".editor-backups")));
            assertEquals(mappingRevision, runtime.snapshot().revision());
        }

        long draftRevision = draft.revision();
        draft.mutate(owner, draftRevision, rulesPath, acceptedRules);
        EditorApplyResult recovered = applyService.apply(null, owner, draft, acceptedRevision, true);
        assertTrue(recovered.success(), recovered.explanation());
        assertEquals("no_changes", recovered.code());
        assertEquals(acceptedRevision, recovered.configurationRevision());
        assertEquals(acceptedFiles, draft.files());
        assertEquals(mappingRevision, runtime.snapshot().revision());
    }

    @Test
    void serializesMutationsAndSupportsSemanticUndoRedo() {
        UUID owner = UUID.randomUUID();
        EditorDraft draft = new EditorDraft(UUID.randomUUID(), owner, 5, 8,
            Map.of("stages/test/stage.toml", "old"));
        assertEquals(1, draft.mutate(owner, 0, "stages/test/stage.toml", "new"));
        assertEquals("new", draft.files().get("stages/test/stage.toml"));
        assertEquals(DraftDiffEntry.ChangeType.MODIFIED, draft.diff().getFirst().change());
        assertEquals(2, draft.undo(owner, 1));
        assertEquals("old", draft.files().get("stages/test/stage.toml"));
        assertEquals(3, draft.redo(owner, 2));
        assertEquals("new", draft.files().get("stages/test/stage.toml"));
        assertThrows(EditorDraft.DraftConflictException.class,
            () -> draft.mutate(owner, 1, "stages/test/rules.toml", "rules"));
        assertThrows(SecurityException.class,
            () -> draft.mutate(UUID.randomUUID(), 3, "stages/test/rules.toml", "rules"));
    }

    @Test
    void rejectsEscapingAndNonTomlPaths() {
        UUID owner = UUID.randomUUID();
        EditorDraft draft = new EditorDraft(UUID.randomUUID(), owner, 0, 0, Map.of());
        assertThrows(IllegalArgumentException.class, () -> draft.mutate(owner, 0, "../secret.toml", "bad"));
        assertThrows(IllegalArgumentException.class, () -> draft.mutate(owner, 0, "stages/test/script.js", "bad"));
    }

    @Test
    void appliedDraftBecomesTheNewCleanBase() {
        UUID owner = UUID.randomUUID();
        EditorDraft draft = new EditorDraft(UUID.randomUUID(), owner, 5, 8,
            Map.of("stages/test/stage.toml", "old"));
        draft.mutate(owner, 0, "stages/test/stage.toml", "new");

        draft.acceptApplied(6);

        assertEquals(6, draft.baseConfigurationRevision());
        assertEquals("new", draft.baseFiles().get("stages/test/stage.toml"));
        assertTrue(draft.diff().isEmpty());
        assertTrue(!draft.canUndo());
    }

    @Test
    void migrationChangesTheDraftWithoutCreatingAnUndoStep() {
        UUID owner = UUID.randomUUID();
        EditorDraft draft = new EditorDraft(UUID.randomUUID(), owner, 5, 8,
            Map.of("stages/test/rules.toml", "legacy"));

        assertTrue(draft.migrate(owner, "stages/test/rules.toml", "canonical"));

        assertEquals(1, draft.revision());
        assertEquals("canonical", draft.files().get("stages/test/rules.toml"));
        assertEquals(DraftDiffEntry.ChangeType.MODIFIED, draft.diff().getFirst().change());
        assertTrue(!draft.canUndo());
    }

    @Test
    void sessionNormalizesRecognizableLegacyRecipeRulesBeforeValidation() {
        UUID owner = UUID.randomUUID();
        String legacyRules = """
            [[rules]]
            id = "showcase:aquatic_blessing/rule_mtjmf0xs"
            effect = "lock"
            priority = 100
            action = "craft"
            targets.recipes = ["minecraft:diamond_sword"]
            """;
        EditorDraft draft = new EditorDraft(UUID.randomUUID(), owner, 5, 8, Map.of(
            "stages/showcase_aquatic_blessing/stage.toml", "[schema]\nversion = 4\n[stage]\nid = \"showcase:aquatic_blessing\"\n",
            "stages/showcase_aquatic_blessing/rules.toml", legacyRules));

        assertTrue(EditorSessionService.normalizeLegacyRecipeRules(owner, draft));
        assertTrue(EditorDraftValidator.validate(draft.files(), draft.revision()).valid());
        assertTrue(draft.files().get("stages/showcase_aquatic_blessing/rules.toml")
            .contains("locked_items = [\"minecraft:diamond_sword\"]"));
    }

    @Test
    void validatesACompleteThreeFileDraft() {
        Map<String, String> files = Map.of(
            "stages/test/stage.toml", "[schema]\nversion = 4\n[stage]\nid = \"test:editor\"\ndisplay_name = \"Editor\"\n",
            "stages/test/rules.toml", "[items]\nlocked = [\"minecraft:diamond\"]\n",
            "stages/test/progression.toml", "# Progression may be empty.\n");
        DraftValidation validation = EditorDraftValidator.validate(files, 3);
        assertTrue(validation.valid(), String.join(". ", validation.errors()));
        assertEquals(1, validation.stages());
    }

    @Test
    void rejectsTheLegacyGenericRecipeRuleBeforeAnyDraftCanApply() {
        Map<String, String> files = Map.of(
            "stages/test/stage.toml", "[schema]\nversion = 4\n[stage]\nid = \"test:editor\"\ndisplay_name = \"Editor\"\n",
            "stages/test/rules.toml", "[[rules]]\naction = \"craft\"\ntargets.recipes = [\"minecraft:diamond_sword\"]\n",
            "stages/test/progression.toml", "# Progression may be empty.\n");

        DraftValidation validation = EditorDraftValidator.validate(files, 3);

        assertTrue(!validation.valid());
        assertTrue(validation.errors().stream().anyMatch(error -> error.contains("[recipes].locked_items")),
            String.join(". ", validation.errors()));
    }

    @Test
    void rejectsTheAmbiguousRecipesLockedAliasBeforeAnyDraftCanApply() {
        Map<String, String> files = Map.of(
            "stages/test/stage.toml", "[schema]\nversion = 4\n[stage]\nid = \"test:editor\"\ndisplay_name = \"Editor\"\n",
            "stages/test/rules.toml", "[recipes]\nlocked = [\"minecraft:diamond_sword\"]\n",
            "stages/test/progression.toml", "# Progression may be empty.\n");

        DraftValidation validation = EditorDraftValidator.validate(files, 3);

        assertTrue(!validation.valid());
        assertTrue(validation.errors().stream().anyMatch(error -> error.contains("[recipes].locked is ambiguous")),
            String.join(". ", validation.errors()));
    }

    @Test
    void rejectsAnExactInventoryTargetThatTheEditorCannotResolve() {
        Map<String, String> files = Map.of(
            "stages/test/stage.toml", "[schema]\nversion = 4\n[stage]\nid = \"test:editor\"\ndisplay_name = \"Editor\"\n",
            "stages/test/rules.toml", """
                [[interactions]]
                type = "item_into_inventory"
                held_item = "id:minecraft:diamond"
                target_kind = "inventory"
                target = "id:test:missing_inventory"
                effect = "lock"
                priority = 100
                """,
            "stages/test/progression.toml", "# Progression may be empty.\n");

        DraftValidation validation = EditorDraftValidator.validate(files, 3);

        assertTrue(!validation.valid());
        assertTrue(validation.errors().stream().anyMatch(error -> error.contains("registered inventory target")),
            String.join(". ", validation.errors()));
    }
}
