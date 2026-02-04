package com.enviouse.progressivestages.common.network;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.util.Constants;
import com.enviouse.progressivestages.client.ClientStageCache;
import com.enviouse.progressivestages.client.ClientLockCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.*;

/**
 * Handles network packet registration and sending
 */
@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class NetworkHandler {

    @SubscribeEvent
    public static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // Stage sync packet (full snapshot)
        registrar.playToClient(
            StageSyncPayload.TYPE,
            StageSyncPayload.STREAM_CODEC,
            new DirectionalPayloadHandler<>(
                NetworkHandler::handleStageSyncClient,
                (payload, context) -> {} // Server doesn't handle this
            )
        );

        // Stage update packet (delta)
        registrar.playToClient(
            StageUpdatePayload.TYPE,
            StageUpdatePayload.STREAM_CODEC,
            new DirectionalPayloadHandler<>(
                NetworkHandler::handleStageUpdateClient,
                (payload, context) -> {}
            )
        );

        // Lock registry sync packet
        registrar.playToClient(
            LockSyncPayload.TYPE,
            LockSyncPayload.STREAM_CODEC,
            new DirectionalPayloadHandler<>(
                NetworkHandler::handleLockSyncClient,
                (payload, context) -> {}
            )
        );
    }

    /**
     * Send a full stage sync to a player
     */
    public static void sendStageSync(ServerPlayer player, Set<StageId> stages) {
        List<ResourceLocation> stageList = stages.stream()
            .map(StageId::getResourceLocation)
            .toList();

        PacketDistributor.sendToPlayer(player, new StageSyncPayload(stageList));
    }

    /**
     * Send a stage update (grant/revoke) to a player
     */
    public static void sendStageUpdate(ServerPlayer player, StageId stageId, boolean granted) {
        PacketDistributor.sendToPlayer(player, new StageUpdatePayload(stageId.getResourceLocation(), granted));
    }

    /**
     * Send lock registry sync to a player
     */
    public static void sendLockSync(ServerPlayer player) {
        LockRegistry registry = LockRegistry.getInstance();

        // Convert item locks to a list format for network
        List<LockEntry> itemLocks = new ArrayList<>();
        for (var entry : registry.getAllItemLocks().entrySet()) {
            itemLocks.add(new LockEntry(entry.getKey(), entry.getValue().getResourceLocation()));
        }

        PacketDistributor.sendToPlayer(player, new LockSyncPayload(itemLocks));
    }

    // ============ Client Handlers ============

    private static void handleStageSyncClient(StageSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Set<StageId> stages = new HashSet<>();
            for (ResourceLocation rl : payload.stages()) {
                stages.add(StageId.fromResourceLocation(rl));
            }
            ClientStageCache.setStages(stages);
        });
    }

    private static void handleStageUpdateClient(StageUpdatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            StageId stageId = StageId.fromResourceLocation(payload.stageId());
            if (payload.granted()) {
                ClientStageCache.addStage(stageId);
            } else {
                ClientStageCache.removeStage(stageId);
            }
        });
    }

    private static void handleLockSyncClient(LockSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Map<ResourceLocation, StageId> itemLocks = new HashMap<>();
            for (LockEntry entry : payload.itemLocks()) {
                itemLocks.put(entry.itemId(), StageId.fromResourceLocation(entry.stageId()));
            }
            ClientLockCache.setItemLocks(itemLocks);
        });
    }

    // ============ Payload Definitions ============

    /**
     * Lock entry for network serialization
     */
    public record LockEntry(ResourceLocation itemId, ResourceLocation stageId) {
        public static final StreamCodec<FriendlyByteBuf, LockEntry> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            LockEntry::itemId,
            ResourceLocation.STREAM_CODEC,
            LockEntry::stageId,
            LockEntry::new
        );
    }

    /**
     * Full stage sync payload
     */
    public record StageSyncPayload(List<ResourceLocation> stages) implements CustomPacketPayload {
        public static final Type<StageSyncPayload> TYPE = new Type<>(Constants.STAGE_SYNC_PACKET);

        public static final StreamCodec<FriendlyByteBuf, StageSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()),
            StageSyncPayload::stages,
            StageSyncPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Stage update (delta) payload
     */
    public record StageUpdatePayload(ResourceLocation stageId, boolean granted) implements CustomPacketPayload {
        public static final Type<StageUpdatePayload> TYPE = new Type<>(Constants.STAGE_UPDATE_PACKET);

        public static final StreamCodec<FriendlyByteBuf, StageUpdatePayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            StageUpdatePayload::stageId,
            ByteBufCodecs.BOOL,
            StageUpdatePayload::granted,
            StageUpdatePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Lock registry sync payload
     */
    public record LockSyncPayload(List<LockEntry> itemLocks) implements CustomPacketPayload {
        public static final Type<LockSyncPayload> TYPE = new Type<>(Constants.LOCK_SYNC_PACKET);

        public static final StreamCodec<FriendlyByteBuf, LockSyncPayload> STREAM_CODEC = StreamCodec.composite(
            LockEntry.STREAM_CODEC.apply(ByteBufCodecs.list()),
            LockSyncPayload::itemLocks,
            LockSyncPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
