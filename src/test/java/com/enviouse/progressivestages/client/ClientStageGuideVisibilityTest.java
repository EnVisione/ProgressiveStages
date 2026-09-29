package com.enviouse.progressivestages.client;

import com.enviouse.progressivestages.common.api.StageId;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientStageGuideVisibilityTest {
    private static final StageId ENABLED = new StageId("test:guide_enabled");
    private static final StageId DISABLED = new StageId("test:guide_disabled");

    @AfterEach
    void clearClientCache() {
        ClientStageCache.clear();
    }

    @Test
    void globalSettingGatesEachStagesSavedGuideChoice() {
        ClientStageCache.setStageDefinitions(Map.of(
            ENABLED, definition(ENABLED, true),
            DISABLED, definition(DISABLED, false)));

        ClientStageCache.setStageGuideEnabled(false);
        assertFalse(ClientStageCache.isStageGuideVisible(ENABLED));
        assertFalse(ClientStageCache.isStageGuideVisible(DISABLED));

        ClientStageCache.setStageGuideEnabled(true);
        assertTrue(ClientStageCache.isStageGuideVisible(ENABLED));
        assertFalse(ClientStageCache.isStageGuideVisible(DISABLED));
        assertFalse(ClientStageCache.isStageGuideVisible(new StageId("test:missing")));
    }

    private static ClientStageCache.StageDefinitionData definition(StageId id, boolean showGuide) {
        return new ClientStageCache.StageDefinitionData(id, "Stage", List.of(), "all", 0,
            "", Optional.<ResourceLocation>empty(), false, false, true, true, false,
            false, "", "", "", 0, "deny", null, null, "task", "", "always", 0,
            "", "", "", "auto", showGuide);
    }
}
