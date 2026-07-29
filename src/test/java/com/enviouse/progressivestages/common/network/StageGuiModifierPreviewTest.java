package com.enviouse.progressivestages.common.network;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StageGuiModifierPreviewTest {

    private static final Path PROJECT = Path.of(System.getProperty("progressivestages.projectDir"));

    @Test
    void modifierPreviewCarriesStructuredSelectorFields() {
        List<NetworkHandler.ModifierFieldLine> fields = new ArrayList<>();
        fields.add(new NetworkHandler.ModifierFieldLine(
            "Blocks", "", "block", "tag:c:ores/coal"));

        NetworkHandler.ModifierPreviewLine preview = new NetworkHandler.ModifierPreviewLine(
            "block_drop_bonus",
            ResourceLocation.fromNamespaceAndPath("showcase", "coal_engineer/coal_yield"),
            fields);
        fields.clear();

        assertEquals("block_drop_bonus", preview.kind());
        assertEquals("block", preview.fields().getFirst().registry());
        assertEquals("tag:c:ores/coal", preview.fields().getFirst().selector());
        assertThrows(UnsupportedOperationException.class, () -> preview.fields().clear());
    }

    @Test
    void structuredPreviewSchemaUsesTheCurrentNetworkVersion() throws IOException {
        String source = Files.readString(PROJECT.resolve(
            "src/main/java/com/enviouse/progressivestages/common/network/NetworkHandler.java"));

        assertEquals(1, occurrences(source, "event.registrar(\"2\")"));
    }

    private static int occurrences(String value, String target) {
        int count = 0;
        for (int index = value.indexOf(target); index >= 0; index = value.indexOf(target, index + target.length())) {
            count++;
        }
        return count;
    }
}
