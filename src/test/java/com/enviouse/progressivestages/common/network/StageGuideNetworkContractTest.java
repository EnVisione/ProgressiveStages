package com.enviouse.progressivestages.common.network;

import org.junit.jupiter.api.Test;
import com.enviouse.progressivestages.common.config.StageGuide;
import com.enviouse.progressivestages.client.ClientStageCache;
import com.enviouse.progressivestages.client.ClientTriggerProgress;
import org.junit.jupiter.api.AfterEach;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class StageGuideNetworkContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("progressivestages.projectDir"));

    @AfterEach
    void clearClientSnapshot() {
        NetworkHandler.clearClientDefinitionAssembly();
        ClientStageCache.clear();
        ClientTriggerProgress.clear();
    }

    @Test
    void incompleteAndSupersededDefinitionChunksNeverPublishPartialData() {
        var first = definitionEntry("first", "First guide", "", "");
        var second = definitionEntry("second", "Second guide", "", "");
        NetworkHandler.acceptStageDefinitionsClient(new NetworkHandler.StageDefinitionsSyncPayload(
            10L, 0, 2, true, List.of(first)));
        assertEquals(Long.MIN_VALUE, ClientStageCache.definitionSnapshotRevision());

        NetworkHandler.acceptStageDefinitionsClient(new NetworkHandler.StageDefinitionsSyncPayload(
            11L, 0, 2, true, List.of(second)));
        NetworkHandler.acceptStageDefinitionsClient(new NetworkHandler.StageDefinitionsSyncPayload(
            10L, 1, 2, true, List.of(second)));
        assertEquals(Long.MIN_VALUE, ClientStageCache.definitionSnapshotRevision());

        NetworkHandler.acceptStageDefinitionsClient(new NetworkHandler.StageDefinitionsSyncPayload(
            11L, 1, 2, true, List.of(first)));
        assertEquals(11L, ClientStageCache.definitionSnapshotRevision());
        assertTrue(ClientStageCache.isStageGuideVisible(
            com.enviouse.progressivestages.common.api.StageId.fromResourceLocation(second.stageId())));
        assertTrue(ClientStageCache.isStageGuideVisible(
            com.enviouse.progressivestages.common.api.StageId.fromResourceLocation(first.stageId())));
    }

    @Test
    void definitionRevisionsStayNonnegativeAndRejectSignedOrExhaustedOrigins() {
        AtomicLong revisions = new AtomicLong();
        assertEquals(1L, NetworkHandler.nextDefinitionRevision(revisions));
        assertEquals(2L, NetworkHandler.nextDefinitionRevision(revisions));

        AtomicLong negativeOrigin = new AtomicLong(Long.MIN_VALUE);
        assertThrows(IllegalStateException.class,
            () -> NetworkHandler.nextDefinitionRevision(negativeOrigin));
        assertEquals(Long.MIN_VALUE, negativeOrigin.get());

        AtomicLong exhausted = new AtomicLong(Long.MAX_VALUE);
        assertThrows(IllegalStateException.class,
            () -> NetworkHandler.nextDefinitionRevision(exhausted));
        assertEquals(Long.MAX_VALUE, exhausted.get());
    }

    @Test
    void incompleteDefinitionAssemblyExpiresAfterTwoHundredTicks() {
        var first = definitionEntry("first", "First guide", "", "");
        NetworkHandler.acceptStageDefinitionsClient(new NetworkHandler.StageDefinitionsSyncPayload(
            20L, 0, 2, true, List.of(first)));
        for (int tick = 0; tick <= 200; tick++) NetworkHandler.tickClientDefinitionAssemblies();
        NetworkHandler.acceptStageDefinitionsClient(new NetworkHandler.StageDefinitionsSyncPayload(
            20L, 1, 2, true, List.of(first)));
        assertEquals(Long.MIN_VALUE, ClientStageCache.definitionSnapshotRevision());
    }

    @Test
    void guideSyncUsesProtocolThreeAndAtomicChunks() throws IOException {
        String source = Files.readString(PROJECT.resolve(
            "src/main/java/com/enviouse/progressivestages/common/network/NetworkHandler.java"));
        assertTrue(source.contains("event.registrar(\"3\")"));
        assertTrue(source.contains("totalChunks"));
        assertTrue(source.contains("applyStageDefinitionSnapshot"));
        assertTrue(source.contains("MAX_TEXT_BYTES"));
        assertTrue(source.contains("DEFINITION_ASSEMBLY_EXPIRY_TICKS = 200L"));
        assertTrue(source.contains("tickClientDefinitionAssemblies"));
        assertTrue(source.contains("lastSeenTick"));
    }

    @Test
    void stageProgressCodecCarriesEvaluatorBackedGuideStatus() {
        var expected = new NetworkHandler.StageProgress(ResourceLocation.parse("progressivestages:miner"),
            List.of(), List.of(), 0, NetworkHandler.CostInfo.NONE,
            new NetworkHandler.AcquisitionInfo(true, "Need 2x Iron Ingots", 12_000L),
            List.of(), List.of(), List.of(), List.of());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            NetworkHandler.StageProgress.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, NetworkHandler.StageProgress.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void definitionPayloadRoundTripsThreeMaximumUnicodeGuideFieldsWithinMinecraftLimit() {
        String maximum = "😀".repeat(StageGuide.MAX_TEXT_CODE_POINTS);
        StageGuide guide = new StageGuide(maximum, maximum, maximum, StageGuide.Recommendation.AUTO, false);
        var definition = definitionEntry("maximum", guide.howToUnlock(), guide.nextSteps(), guide.whereToFind(), guide.enabled());
        List<List<NetworkHandler.StageDefinitionEntry>> chunks =
            NetworkHandler.splitDefinitionEntries(List.of(definition));
        assertEquals(1, chunks.size());

        var expected = new NetworkHandler.StageDefinitionsSyncPayload(10L, 0, 1, true, chunks.get(0));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            NetworkHandler.StageDefinitionsSyncPayload.STREAM_CODEC.encode(buffer, expected);
            assertTrue(buffer.readableBytes() > 24 * 1024,
                "the guide payload must exceed the unrelated player-snapshot chunk size");
            assertTrue(buffer.readableBytes() <= NetworkHandler.MAX_CUSTOM_PAYLOAD_BYTES);
            assertEquals(expected, NetworkHandler.StageDefinitionsSyncPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void definitionPayloadRejectsGuideTextOverTheConfiguredLimitOnEncode() {
        var definition = definitionEntry("too_long",
            "x".repeat(StageGuide.MAX_TEXT_CODE_POINTS + 1), "", "");
        var payload = new NetworkHandler.StageDefinitionsSyncPayload(1L, 0, 1, true, List.of(definition));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            assertThrows(IllegalArgumentException.class,
                () -> NetworkHandler.StageDefinitionsSyncPayload.STREAM_CODEC.encode(buffer, payload));
        } finally {
            buffer.release();
        }
    }

    @Test
    void definitionPayloadRejectsInvalidChunkHeaderBeforeReadingTheDefinitionList() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarLong(1L);
            buffer.writeVarInt(0);
            buffer.writeVarInt(0);
            buffer.writeBoolean(true);
            assertThrows(IllegalArgumentException.class,
                () -> NetworkHandler.StageDefinitionsSyncPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void definitionPayloadRejectsAnOversizedDeclaredListBeforeReadingEntries() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarLong(1L);
            buffer.writeVarInt(0);
            buffer.writeVarInt(1);
            buffer.writeBoolean(true);
            buffer.writeVarInt(NetworkHandler.MAX_STAGE_DEFINITION_ENTRIES_PER_CHUNK + 1);
            assertThrows(DecoderException.class,
                () -> NetworkHandler.StageDefinitionsSyncPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void definitionSplitterStartsANewChunkAtTheEntryCountBound() {
        List<NetworkHandler.StageDefinitionEntry> definitions = new ArrayList<>();
        for (int index = 0; index <= NetworkHandler.MAX_STAGE_DEFINITION_ENTRIES_PER_CHUNK; index++) {
            definitions.add(definitionEntry("small_" + index, "", "", ""));
        }

        List<List<NetworkHandler.StageDefinitionEntry>> chunks = NetworkHandler.splitDefinitionEntries(definitions);
        assertEquals(2, chunks.size());
        assertEquals(NetworkHandler.MAX_STAGE_DEFINITION_ENTRIES_PER_CHUNK, chunks.get(0).size());
        assertEquals(1, chunks.get(1).size());
    }

    @Test
    void definitionSplitterRejectsSnapshotsOverTheAggregateBound() {
        String maximum = "😀".repeat(StageGuide.MAX_TEXT_CODE_POINTS);
        List<NetworkHandler.StageDefinitionEntry> definitions = new ArrayList<>();
        for (int index = 0; index < 700; index++) {
            definitions.add(definitionEntry("bulk_" + index, maximum, maximum, maximum));
        }
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> NetworkHandler.splitDefinitionEntries(definitions));
        assertTrue(error.getMessage().contains("snapshot"));
    }

    @Test
    void triggerSummaryKeepsAnyAndAllRulesDistinctAndFailsClosedWithoutDetails() {
        var any = new NetworkHandler.RuleLine("any_of", "", false, List.of(
            new NetworkHandler.CondLine("Mine stone", 2, 10, false),
            new NetworkHandler.CondLine("Kill a zombie", 0, 1, false)));
        var all = new NetworkHandler.RuleLine("all_of", "", false, List.of(
            new NetworkHandler.CondLine("Collect logs", 1, 4, false),
            new NetworkHandler.CondLine("Craft a bench", 0, 1, false)));
        assertEquals("Complete one of these trigger routes: Mine stone (2/10) or Kill a zombie (0/1)",
            NetworkHandler.triggerBlocker(List.of(any)));
        assertEquals("Complete one of these trigger routes: Collect logs (1/4) and Craft a bench (0/1)",
            NetworkHandler.triggerBlocker(List.of(all)));
        assertNull(NetworkHandler.triggerBlocker(List.of(
            new NetworkHandler.RuleLine("unknown", "", false, List.of()))));
    }

    @Test
    void oversizedAcquisitionSummaryBecomesUnavailableInsteadOfBeingCutOff() {
        var info = NetworkHandler.AcquisitionInfo.of(
            List.of("x".repeat(com.enviouse.progressivestages.common.config.StageGuide.MAX_TEXT_CODE_POINTS + 1)),
            12_000L);
        assertFalse(info.available());
        assertEquals("", info.remaining());
        assertEquals(12_000L, info.cooldownRemainingMillis());
    }

    private static NetworkHandler.StageDefinitionEntry definitionEntry(String id, String howToUnlock,
                                                                         String nextSteps, String whereToFind) {
        return definitionEntry(id, howToUnlock, nextSteps, whereToFind, true);
    }

    private static NetworkHandler.StageDefinitionEntry definitionEntry(String id, String howToUnlock,
                                                                         String nextSteps, String whereToFind,
                                                                         boolean showGuide) {
        return new NetworkHandler.StageDefinitionEntry(ResourceLocation.parse("progressivestages:" + id),
            "Stage", List.of(), "all", 0, "", "", false, false, true, true, false,
            false, "", "", "", 0, "deny", false, 0, 0, "", "", "", 0,
            howToUnlock, nextSteps, whereToFind, "auto", showGuide);
    }
}
