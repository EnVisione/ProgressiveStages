package com.enviouse.progressivestages.client;

import com.enviouse.progressivestages.common.api.StageId;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientLockCacheTest {

    @AfterEach
    void clearMultiLocks() {
        ClientLockCache.setItemMultiLocks(Map.of());
    }

    @Test
    void synchronizedStageSetsAreDefensiveCopies() {
        ResourceLocation item = ResourceLocation.parse("minecraft:diamond");
        StageId stage = StageId.parse("diamond_age");
        Set<StageId> sourceStages = new HashSet<>(Set.of(stage));
        Map<ResourceLocation, Set<StageId>> source = new HashMap<>();
        source.put(item, sourceStages);

        ClientLockCache.setItemMultiLocks(source);
        sourceStages.clear();

        Set<StageId> cached = ClientLockCache.getRequiredStagesForItem(item);
        assertEquals(Set.of(stage), cached);
        assertThrows(UnsupportedOperationException.class, () -> cached.add(StageId.parse("other")));
    }
}
