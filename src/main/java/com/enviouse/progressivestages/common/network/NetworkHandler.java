package com.enviouse.progressivestages.common.network;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.common.util.Constants;
import com.enviouse.progressivestages.client.ClientStageCache;
import com.enviouse.progressivestages.client.ClientLockCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
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
@SuppressWarnings("removal")
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

        // Stage definitions sync packet (v1.3 - includes dependencies)
        registrar.playToClient(
            StageDefinitionsSyncPayload.TYPE,
            StageDefinitionsSyncPayload.STREAM_CODEC,
            new DirectionalPayloadHandler<>(
                NetworkHandler::handleStageDefinitionsSyncClient,
                (payload, context) -> {}
            )
        );

        // Creative bypass packet (notifies client to suppress lock UI)
        registrar.playToClient(
            CreativeBypassPayload.TYPE,
            CreativeBypassPayload.STREAM_CODEC,
            new DirectionalPayloadHandler<>(
                NetworkHandler::handleCreativeBypassClient,
                (payload, context) -> {}
            )
        );

        // Reveal-policy packet (mirrors reveal_stage_names_only_to_operators flag)
        registrar.playToClient(
            RevealPolicyPayload.TYPE,
            RevealPolicyPayload.STREAM_CODEC,
            new DirectionalPayloadHandler<>(
                NetworkHandler::handleRevealPolicyClient,
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
        // Also push reveal-policy so client knows whether to hide stage names
        sendRevealPolicy(player);
    }

    /**
     * Send a stage update (grant/revoke) to a player
     */
    public static void sendStageUpdate(ServerPlayer player, StageId stageId, boolean granted) {
        PacketDistributor.sendToPlayer(player, new StageUpdatePayload(stageId.getResourceLocation(), granted));
    }

    /**
     * Send lock registry sync to a player.
     * Sends ALL resolved item locks including name patterns, tags, and mod locks.
     * Also sends recipe locks (by recipe ID) and recipe-item locks (by output item ID).
     */
    public static void sendLockSync(ServerPlayer player) {
        LockRegistry registry = LockRegistry.getInstance();

        // v2.0: ship every (itemId, stageId) gating pair so the client can dedupe into a multi-stage map.
        // For items, walk every Item in the registry and emit ALL gating stages.
        List<LockEntry> itemLocks = new ArrayList<>();
        for (Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            ResourceLocation iid = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            if (iid == null) continue;
            java.util.Set<com.enviouse.progressivestages.common.api.StageId> gating = registry.getRequiredStages(item);
            if (gating.isEmpty()) continue;
            for (com.enviouse.progressivestages.common.api.StageId s : gating) {
                itemLocks.add(new LockEntry(iid, s.getResourceLocation()));
            }
        }

        // Recipes: only single-stage entries are tracked (recipeIdCat doesn't have a multi-stage public method by id),
        // but ship via the same emission. Multi-stage recipes will work because LockRegistry.recipeIdCat already
        // stores a list, so each entry is emitted as its own LockEntry row.
        List<LockEntry> recipeLocks = new ArrayList<>();
        for (var entry : registry.getAllRecipeLocks().entrySet()) {
            // Single-stage map view; for true multi-stage recipe locks, use getRequiredStagesForRecipe per id.
            java.util.Set<com.enviouse.progressivestages.common.api.StageId> stages = registry.getRequiredStagesForRecipe(entry.getKey());
            if (stages.isEmpty()) {
                recipeLocks.add(new LockEntry(entry.getKey(), entry.getValue().getResourceLocation()));
            } else {
                for (com.enviouse.progressivestages.common.api.StageId s : stages) {
                    recipeLocks.add(new LockEntry(entry.getKey(), s.getResourceLocation()));
                }
            }
        }

        List<LockEntry> recipeItemLocks = new ArrayList<>();
        for (Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            ResourceLocation iid = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            if (iid == null) continue;
            java.util.Set<com.enviouse.progressivestages.common.api.StageId> gating = registry.getRequiredStagesForRecipeByOutput(item);
            if (gating.isEmpty()) continue;
            for (com.enviouse.progressivestages.common.api.StageId s : gating) {
                recipeItemLocks.add(new LockEntry(iid, s.getResourceLocation()));
            }
        }

        PacketDistributor.sendToPlayer(player, new LockSyncPayload(itemLocks, recipeLocks, recipeItemLocks));
    }

    /**
     * Send stage definitions to a player (v1.3).
     * Includes dependencies for UI display and validation.
     */
    public static void sendStageDefinitionsSync(ServerPlayer player) {
        List<StageDefinitionEntry> definitions = new ArrayList<>();

        for (StageId stageId : StageOrder.getInstance().getAllStageIds()) {
            StageOrder.getInstance().getStageDefinition(stageId).ifPresent(def -> {
                List<ResourceLocation> deps = def.getDependencies().stream()
                    .map(StageId::getResourceLocation)
                    .toList();
                definitions.add(new StageDefinitionEntry(
                    stageId.getResourceLocation(),
                    def.getDisplayName(),
                    deps
                ));
            });
        }

        PacketDistributor.sendToPlayer(player, new StageDefinitionsSyncPayload(definitions));
    }

    /**
     * Send creative bypass state to a player.
     * When enabled, client suppresses all lock UI (icons, tooltips, EMI hiding).
     */
    public static void sendCreativeBypass(ServerPlayer player, boolean bypassing) {
        PacketDistributor.sendToPlayer(player, new CreativeBypassPayload(bypassing));
    }

    /**
     * Send the reveal-policy flag (server's reveal_stage_names_only_to_operators config) to a player.
     * Client uses this with its own permission level to decide whether to show stage names.
     */
    public static void sendRevealPolicy(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new RevealPolicyPayload(
            com.enviouse.progressivestages.common.config.StageConfig.isRevealStageNamesOnlyToOperators()
        ));
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
            // v2.0: dedupe LockEntry rows into both a single-stage map (first-seen wins, for back-compat)
            // and a multi-stage map for true multi-stage gating.
            Map<ResourceLocation, StageId> itemLocks = new HashMap<>();
            Map<ResourceLocation, java.util.Set<StageId>> itemMulti = new HashMap<>();
            for (LockEntry entry : payload.itemLocks()) {
                StageId sid = StageId.fromResourceLocation(entry.stageId());
                itemLocks.putIfAbsent(entry.itemId(), sid);
                itemMulti.computeIfAbsent(entry.itemId(), k -> new java.util.LinkedHashSet<>()).add(sid);
            }
            ClientLockCache.setItemLocks(itemLocks);
            ClientLockCache.setItemMultiLocks(itemMulti);

            Map<ResourceLocation, StageId> recipeLocks = new HashMap<>();
            Map<ResourceLocation, java.util.Set<StageId>> recipeMulti = new HashMap<>();
            for (LockEntry entry : payload.recipeLocks()) {
                StageId sid = StageId.fromResourceLocation(entry.stageId());
                recipeLocks.putIfAbsent(entry.itemId(), sid);
                recipeMulti.computeIfAbsent(entry.itemId(), k -> new java.util.LinkedHashSet<>()).add(sid);
            }
            ClientLockCache.setRecipeLocks(recipeLocks);
            ClientLockCache.setRecipeMultiLocks(recipeMulti);

            Map<ResourceLocation, StageId> recipeItemLocks = new HashMap<>();
            Map<ResourceLocation, java.util.Set<StageId>> recipeItemMulti = new HashMap<>();
            for (LockEntry entry : payload.recipeItemLocks()) {
                StageId sid = StageId.fromResourceLocation(entry.stageId());
                recipeItemLocks.putIfAbsent(entry.itemId(), sid);
                recipeItemMulti.computeIfAbsent(entry.itemId(), k -> new java.util.LinkedHashSet<>()).add(sid);
            }
            ClientLockCache.setRecipeItemLocks(recipeItemLocks);
            ClientLockCache.setRecipeItemMultiLocks(recipeItemMulti);
        });
    }

    private static void handleStageDefinitionsSyncClient(StageDefinitionsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Map<StageId, ClientStageCache.StageDefinitionData> definitions = new HashMap<>();
            for (StageDefinitionEntry entry : payload.definitions()) {
                StageId stageId = StageId.fromResourceLocation(entry.stageId());
                List<StageId> deps = entry.dependencies().stream()
                    .map(StageId::fromResourceLocation)
                    .toList();
                definitions.put(stageId, new ClientStageCache.StageDefinitionData(
                    stageId,
                    entry.displayName(),
                    deps
                ));
            }
            ClientStageCache.setStageDefinitions(definitions);
        });
    }

    private static void handleCreativeBypassClient(CreativeBypassPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientLockCache.setCreativeBypass(payload.bypassing());
        });
    }

    private static void handleRevealPolicyClient(RevealPolicyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientStageCache.setHideStageNamesFromNonOps(payload.hide());
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
     * Lock registry sync payload.
     * Includes item locks, recipe locks (by recipe ID), and recipe-item locks (by output item ID).
     */
    public record LockSyncPayload(List<LockEntry> itemLocks, List<LockEntry> recipeLocks, List<LockEntry> recipeItemLocks) implements CustomPacketPayload {
        public static final Type<LockSyncPayload> TYPE = new Type<>(Constants.LOCK_SYNC_PACKET);

        public static final StreamCodec<FriendlyByteBuf, LockSyncPayload> STREAM_CODEC = StreamCodec.composite(
            LockEntry.STREAM_CODEC.apply(ByteBufCodecs.list()),
            LockSyncPayload::itemLocks,
            LockEntry.STREAM_CODEC.apply(ByteBufCodecs.list()),
            LockSyncPayload::recipeLocks,
            LockEntry.STREAM_CODEC.apply(ByteBufCodecs.list()),
            LockSyncPayload::recipeItemLocks,
            LockSyncPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Stage definition entry for network serialization (v1.3)
     */
    public record StageDefinitionEntry(ResourceLocation stageId, String displayName, List<ResourceLocation> dependencies) {
        public static final StreamCodec<FriendlyByteBuf, StageDefinitionEntry> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            StageDefinitionEntry::stageId,
            ByteBufCodecs.STRING_UTF8,
            StageDefinitionEntry::displayName,
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()),
            StageDefinitionEntry::dependencies,
            StageDefinitionEntry::new
        );
    }

    /**
     * Stage definitions sync payload (v1.3)
     * Sends all stage definitions with dependencies to client.
     */
    public record StageDefinitionsSyncPayload(List<StageDefinitionEntry> definitions) implements CustomPacketPayload {
        public static final Type<StageDefinitionsSyncPayload> TYPE = new Type<>(Constants.STAGE_DEFINITIONS_SYNC_PACKET);

        public static final StreamCodec<FriendlyByteBuf, StageDefinitionsSyncPayload> STREAM_CODEC = StreamCodec.composite(
            StageDefinitionEntry.STREAM_CODEC.apply(ByteBufCodecs.list()),
            StageDefinitionsSyncPayload::definitions,
            StageDefinitionsSyncPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Creative bypass state payload.
     * Tells client to suppress or restore lock UI rendering.
     */
    public record CreativeBypassPayload(boolean bypassing) implements CustomPacketPayload {
        public static final Type<CreativeBypassPayload> TYPE = new Type<>(Constants.CREATIVE_BYPASS_PACKET);

        public static final StreamCodec<FriendlyByteBuf, CreativeBypassPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            CreativeBypassPayload::bypassing,
            CreativeBypassPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Reveal-policy payload. Mirrors reveal_stage_names_only_to_operators to the client.
     */
    public record RevealPolicyPayload(boolean hide) implements CustomPacketPayload {
        public static final Type<RevealPolicyPayload> TYPE = new Type<>(Constants.REVEAL_POLICY_PACKET);

        public static final StreamCodec<FriendlyByteBuf, RevealPolicyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            RevealPolicyPayload::hide,
            RevealPolicyPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
