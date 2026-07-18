package com.enviouse.progressivestages.common.rehaul.counter;

import com.enviouse.progressivestages.common.rehaul.condition.SubjectScope;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WindowCounterStoreTest {

    @Test
    void rollingWindowsPruneAndSnapshotsRestore() {
        WindowCounterStore store = new WindowCounterStore();
        CounterKey key = new CounterKey("player", SubjectScope.PLAYER,
            ResourceLocation.parse("test:damage"),
            new CounterWindow(WindowKind.ROLLING_DURATION, 1_000, "", ResetPolicy.NEVER, false));
        store.add(key, 4, 100);
        store.add(key, 6, 900);
        assertEquals(10, store.value(key, 1_000));
        CounterSnapshot snapshot = store.snapshot();
        assertEquals(6, store.value(key, 1_500));
        store.clear();
        store.restore(snapshot);
        assertEquals(10, store.value(key, 1_000));
    }
}
