package com.enviouse.progressivestages.client;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StageGuideProgressOrderingTest {
    private static final ResourceLocation STAGE = ResourceLocation.parse("progressivestages:guide_test");

    @AfterEach
    void clear() {
        ClientTriggerProgress.clear();
        ClientStageCache.clear();
    }

    @Test
    void olderAndOutOfOrderProgressCannotReplaceCurrentGuideFacts() {
        ClientStageCache.setDefinitionSnapshotRevision(10L);
        ClientTriggerProgress.onDefinitionsAccepted(10L);
        assertTrue(ClientTriggerProgress.acceptSnapshot(10L, 2L, List.of(progress("Current"))));
        assertEquals("Current", ClientTriggerProgress.get(StageId.fromResourceLocation(STAGE))
            .acquisition().remaining());
        assertFalse(ClientTriggerProgress.acceptSnapshot(10L, 1L, List.of(progress("Old"))));
        assertFalse(ClientTriggerProgress.acceptSnapshot(10L, 2L, List.of(progress("Duplicate"))));
        assertEquals("Current", ClientTriggerProgress.get(StageId.fromResourceLocation(STAGE))
            .acquisition().remaining());

        ClientStageCache.setDefinitionSnapshotRevision(11L);
        ClientTriggerProgress.onDefinitionsAccepted(11L);
        assertFalse(ClientTriggerProgress.hasData());
        assertFalse(ClientTriggerProgress.acceptSnapshot(10L, 3L, List.of(progress("Stale definition"))));
        assertTrue(ClientTriggerProgress.acceptSnapshot(11L, 1L, List.of(progress("New"))));
        assertEquals("New", ClientTriggerProgress.get(StageId.fromResourceLocation(STAGE))
            .acquisition().remaining());
    }

    @Test
    void progressPayloadRoundTripsItsDefinitionRevisionAndSequence() {
        var expected = new NetworkHandler.StageGuiDataPayload(32L, 7L, List.of(progress("Need an ingot")));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            NetworkHandler.StageGuiDataPayload.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, NetworkHandler.StageGuiDataPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static NetworkHandler.StageProgress progress(String remaining) {
        return new NetworkHandler.StageProgress(STAGE, List.of(), List.of(), 0,
            NetworkHandler.CostInfo.NONE, new NetworkHandler.AcquisitionInfo(true, remaining, 0L),
            List.of(), List.of(), List.of(), List.of());
    }
}
