package com.enviouse.progressivestages.server.structure;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureConditionSnapshotTest {

    private static final Path PROJECT = Path.of(System.getProperty("progressivestages.projectDir"));

    @Test
    void workerThreadUsesThePublishedSnapshotWithoutReadingLiveSessions() {
        var cached = new StructureSessionManager.ConditionSnapshot(List.of(), Map.of());
        AtomicBoolean liveRead = new AtomicBoolean();

        var selected = StructureSessionManager.selectConditionSnapshot(false, () -> {
            liveRead.set(true);
            return StructureSessionManager.ConditionSnapshot.empty();
        }, cached);

        assertSame(cached, selected);
        assertFalse(liveRead.get());
    }

    @Test
    void serverThreadPublishesLiveSessionState() {
        var cached = StructureSessionManager.ConditionSnapshot.empty();
        var live = new StructureSessionManager.ConditionSnapshot(List.of(), Map.of());
        AtomicBoolean liveRead = new AtomicBoolean();

        var selected = StructureSessionManager.selectConditionSnapshot(true, () -> {
            liveRead.set(true);
            return live;
        }, cached);

        assertSame(live, selected);
        assertTrue(liveRead.get());
    }

    @Test
    void publishedStructureTimesAreDefensiveAndImmutable() {
        ResourceLocation structure = ResourceLocation.withDefaultNamespace("stronghold");
        Map<ResourceLocation, Long> mutable = new HashMap<>();
        mutable.put(structure, 12L);

        var snapshot = new StructureSessionManager.ConditionSnapshot(List.of(), mutable);
        mutable.put(structure, 99L);

        assertEquals(12L, snapshot.activeStructureSeconds().get(structure));
        assertThrows(UnsupportedOperationException.class,
            () -> snapshot.activeStructureSeconds().put(structure, 20L));
    }

    @Test
    void conditionFactoryNeverReadsLiveStructureCollectionsDirectly() throws IOException {
        String factory = Files.readString(PROJECT.resolve(
            "src/main/java/com/enviouse/progressivestages/server/rehaul/MinecraftConditionContextFactory.java"));

        assertTrue(factory.contains("conditionSnapshot(player)"));
        assertFalse(factory.contains("activeSessions(player)"));
        assertFalse(factory.contains("activeStructureSeconds(player)"));
    }
}
