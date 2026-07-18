package com.enviouse.progressivestages.common.network;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageManager;
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

        // v2.0.1: Ore-spoof delta packet (server batches per-section spoofs)
        registrar.playToClient(
            OreSpoofPayload.TYPE,
            OreSpoofPayload.STREAM_CODEC,
            new DirectionalPayloadHandler<>(
                NetworkHandler::handleOreSpoofClient,
                (payload, context) -> {}
            )
        );

        // v2.3: stage-tree GUI data (server -> client; arrival opens the screen)
        registrar.playToClient(
            StageGuiDataPayload.TYPE,
            StageGuiDataPayload.STREAM_CODEC,
            new DirectionalPayloadHandler<>(
                NetworkHandler::handleStageGuiDataClient,
                (payload, context) -> {}
            )
        );

        // v2.3: stage-tree GUI request (client -> server; e.g. keybind press)
        registrar.playToServer(
            RequestStageGuiPayload.TYPE,
            RequestStageGuiPayload.STREAM_CODEC,
            NetworkHandler::handleRequestStageGuiServer
        );

        // v2.4: skill-tree purchase request (client -> server)
        registrar.playToServer(
            RequestPurchasePayload.TYPE,
            RequestPurchasePayload.STREAM_CODEC,
            NetworkHandler::handlePurchaseServer
        );

        // v2.4: unlock toast + active-goal HUD bar (server -> client)
        registrar.playToClient(UnlockToastPayload.TYPE, UnlockToastPayload.STREAM_CODEC,
            new DirectionalPayloadHandler<>(NetworkHandler::handleUnlockToastClient, (p, c) -> {}));
        registrar.playToClient(ActiveGoalPayload.TYPE, ActiveGoalPayload.STREAM_CODEC,
            new DirectionalPayloadHandler<>(NetworkHandler::handleActiveGoalClient, (p, c) -> {}));
        registrar.playToClient(ChallengeHudPayload.TYPE, ChallengeHudPayload.STREAM_CODEC,
            new DirectionalPayloadHandler<>(NetworkHandler::handleChallengeHudClient, (p, c) -> {}));
        registrar.playToClient(ClientSnapshotManifestPayload.TYPE, ClientSnapshotManifestPayload.STREAM_CODEC,
            new DirectionalPayloadHandler<>(NetworkHandler::handleClientSnapshotManifest, (p, c) -> {}));
        registrar.playToClient(ClientSnapshotChunkPayload.TYPE, ClientSnapshotChunkPayload.STREAM_CODEC,
            new DirectionalPayloadHandler<>(NetworkHandler::handleClientSnapshotChunk, (p, c) -> {}));
        registrar.playToServer(ClientSnapshotAckPayload.TYPE, ClientSnapshotAckPayload.STREAM_CODEC,
            NetworkHandler::handleClientSnapshotAck);
        registrar.playToServer(ClientSnapshotRequestPayload.TYPE, ClientSnapshotRequestPayload.STREAM_CODEC,
            NetworkHandler::handleClientSnapshotRequest);
        registrar.playToClient(EditorOpenPayload.TYPE, EditorOpenPayload.STREAM_CODEC,
            new DirectionalPayloadHandler<>(NetworkHandler::handleEditorOpen, (p, c) -> {}));
        registrar.playToServer(EditorRequestPayload.TYPE, EditorRequestPayload.STREAM_CODEC,
            NetworkHandler::handleEditorRequest);
        registrar.playToClient(EditorResponsePayload.TYPE, EditorResponsePayload.STREAM_CODEC,
            new DirectionalPayloadHandler<>(NetworkHandler::handleEditorResponse, (p, c) -> {}));
    }

    public static void sendUnlockToast(ServerPlayer player, String title, String subtitle, String iconItem) {
        PacketDistributor.sendToPlayer(player, new UnlockToastPayload(title, subtitle, iconItem));
    }

    public static void sendActiveGoal(ServerPlayer player, String label, float percent, boolean show) {
        PacketDistributor.sendToPlayer(player, new ActiveGoalPayload(label, percent, show));
    }

    private static void handleUnlockToastClient(UnlockToastPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.enviouse.progressivestages.client.ClientUnlockJuice
            .showToast(payload.title(), payload.subtitle(), payload.iconItem()));
    }

    private static void handleActiveGoalClient(ActiveGoalPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.enviouse.progressivestages.client.ClientUnlockJuice
            .setActiveGoal(payload.label(), payload.percent(), payload.show()));
    }

    private static void handleChallengeHudClient(ChallengeHudPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.enviouse.progressivestages.client.ClientChallengeHud.setEntries(
            payload.entries().stream().map(entry -> new com.enviouse.progressivestages.client.ClientChallengeHud.Entry(
                entry.id(), entry.title(), entry.status(), entry.currentStep(), entry.totalSteps(), entry.attempts(),
                entry.startedAt(), entry.timeoutMillis(), entry.budgets(), entry.session(), entry.successCriteria(),
                entry.explanation(), entry.placement(), entry.scale(), entry.color(), entry.icon(), entry.animation(),
                entry.compact())).toList()));
    }

    public static void sendChallengeHud(ServerPlayer player) {
        var runtime = com.enviouse.progressivestages.server.rehaul.RehaulRuntime.get();
        List<ChallengeHudLine> lines = new ArrayList<>();
        for (var session : runtime.challenges().sessions(player.getUUID().toString())) {
            var definition = runtime.challenges().definition(session.challenge()).orElse(null);
            if (definition == null || !definition.hud().enabled()) continue;
            if (definition.hud().hideWhenInactive()
                    && session.status() != com.enviouse.progressivestages.common.rehaul.challenge.ChallengeStatus.ACTIVE) continue;
            List<String> budgets = definition.hud().valuesSecret() ? List.of("Progress is hidden")
                : session.budgetValues().entrySet().stream().map(entry ->
                    entry.getKey() + " " + Math.round(entry.getValue() * 100D) / 100D).toList();
            lines.add(new ChallengeHudLine(session.challenge(), definition.title(),
                session.status().name().toLowerCase(java.util.Locale.ROOT), session.currentStep(),
                definition.steps().size(), session.attempts(), session.startedAt(), definition.timeoutMillis(),
                budgets, definition.hud().valuesSecret() ? "" : conditionSummary(definition.startCondition()),
                definition.hud().valuesSecret() ? "Success criteria hidden" : conditionSummary(definition.successCondition()),
                session.explanation(), definition.hud().placement(), definition.hud().scale(),
                definition.hud().color(), definition.hud().icon(), definition.hud().animation(),
                definition.hud().compact()));
        }
        ChallengeHudPayload payload = new ChallengeHudPayload(List.copyOf(lines));
        int fingerprint = payload.hashCode();
        Integer previous = challengeHudFingerprints.put(player.getUUID(), fingerprint);
        if (!Objects.equals(previous, fingerprint)) PacketDistributor.sendToPlayer(player, payload);
    }

    private static String conditionSummary(com.enviouse.progressivestages.common.rehaul.ConditionNode node) {
        if (node instanceof com.enviouse.progressivestages.common.rehaul.ConditionNode.Constant constant) {
            return constant.value() ? "Always" : "Never";
        }
        if (node instanceof com.enviouse.progressivestages.common.rehaul.ConditionNode.Leaf leaf) {
            Object target = leaf.arguments().getOrDefault("id", leaf.arguments().get("target"));
            return leaf.providerId().getPath() + (target == null ? "" : ". " + target);
        }
        if (node instanceof com.enviouse.progressivestages.common.rehaul.ConditionNode.All all) {
            return "All of " + all.children().size() + " conditions";
        }
        if (node instanceof com.enviouse.progressivestages.common.rehaul.ConditionNode.Any any) {
            return "Any of " + any.children().size() + " conditions";
        }
        if (node instanceof com.enviouse.progressivestages.common.rehaul.ConditionNode.Sequence sequence) {
            return "Sequence of " + sequence.children().size() + " conditions";
        }
        if (node instanceof com.enviouse.progressivestages.common.rehaul.ConditionNode.Reference reference) {
            return "Condition " + reference.id();
        }
        return node.getClass().getSimpleName();
    }

    /**
     * v2.3: gather the player's live per-stage trigger progress and push it to the client,
     * which opens the stage-tree GUI on arrival.
     */
    public static void sendStageGuiData(ServerPlayer player) {
        // Build the per-stage "unlocks" preview once by scanning the item registry a single time
        // and bucketing each item under every stage that gates it (cheap, on-demand).
        final int SAMPLE_CAP = 90;
        Map<StageId, List<ResourceLocation>> unlockSample = new HashMap<>();
        Map<StageId, Integer> unlockTotal = new HashMap<>();
        LockRegistry reg = LockRegistry.getInstance();
        for (Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            ResourceLocation iid = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            if (iid == null) continue;
            for (StageId s : reg.getRequiredStages(item)) {
                unlockTotal.merge(s, 1, Integer::sum);
                List<ResourceLocation> list = unlockSample.computeIfAbsent(s, k -> new ArrayList<>());
                if (list.size() < SAMPLE_CAP) list.add(iid);
            }
        }

        List<StageProgress> out = new ArrayList<>();
        for (StageId stageId : StageOrder.getInstance().getAllStageIds()) {
            List<RuleLine> ruleLines = new ArrayList<>();
            for (var rp : com.enviouse.progressivestages.server.triggers.StageTriggerEvaluator
                    .describeProgress(player, stageId)) {
                List<CondLine> conds = new ArrayList<>();
                for (var cp : rp.conditions()) {
                    conds.add(new CondLine(
                        conditionLabel(cp.condition()),
                        (int) Math.min(Integer.MAX_VALUE, cp.current()),
                        (int) Math.min(Integer.MAX_VALUE, cp.threshold()),
                        cp.satisfied()));
                }
                ruleLines.add(new RuleLine(rp.mode().name().toLowerCase(java.util.Locale.ROOT), rp.description(), rp.satisfied(), conds));
            }
            out.add(new StageProgress(
                stageId.getResourceLocation(),
                ruleLines,
                unlockSample.getOrDefault(stageId, List.of()),
                unlockTotal.getOrDefault(stageId, 0),
                computeCostInfo(player, stageId),
                whyLines(stageId),
                challengeLines(player, stageId),
                modifierLines(player, stageId),
                historyLines(player, stageId)));
        }
        PacketDistributor.sendToPlayer(player, new StageGuiDataPayload(out));
    }

    // ============ v2.4 skill-tree purchase ============

    private static CostInfo computeCostInfo(ServerPlayer player, StageId stageId) {
        var defOpt = StageOrder.getInstance().getStageDefinition(stageId);
        if (defOpt.isEmpty() || !defOpt.get().isPurchasable()) return CostInfo.NONE;
        com.enviouse.progressivestages.common.config.StageCost cost = defOpt.get().getCost();
        StringBuilder sb = new StringBuilder();
        if (cost.xpLevels() > 0) sb.append(cost.xpLevels()).append(" lvl");
        for (var ic : cost.items()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(ic.count()).append("x ").append(ic.item().getPath());
        }
        if (sb.length() == 0) sb.append("free");
        if (cost.cooldownSeconds() > 0) sb.append(", ").append(cost.cooldownSeconds()).append("s cooldown");
        return new CostInfo(true, cost.xpLevels(), sb.toString(), canPurchase(player, defOpt.get()));
    }

    private static List<WhyLine> whyLines(StageId stage) {
        List<WhyLine> output = new ArrayList<>();
        var engine = com.enviouse.progressivestages.server.rehaul.RehaulRuntime.get().rules();
        for (var trace : engine.history().stream().skip(Math.max(0, engine.history().size() - 40)).toList()) {
            boolean relevant = trace.candidates().stream().anyMatch(candidate -> engine.findRule(candidate.ruleId())
                .map(rule -> rule.ownerStage().equals(stage)).orElse(false));
            if (!relevant) continue;
            output.add(new WhyLine(trace.category(), trace.action(), trace.target(),
                trace.winner().map(Object::toString).orElse(""),
                trace.winningEffect() == null ? "" : trace.winningEffect().name().toLowerCase(java.util.Locale.ROOT),
                trace.blocked(), trace.explanation()));
        }
        return List.copyOf(output);
    }

    private static List<ChallengeLine> challengeLines(ServerPlayer player, StageId stage) {
        return com.enviouse.progressivestages.server.rehaul.RehaulRuntime.get().challenges()
            .sessions(player.getUUID().toString()).stream()
            .filter(session -> session.challenge().getPath().contains(stage.getPath()))
            .map(session -> new ChallengeLine(session.challenge(),
                session.status().name().toLowerCase(java.util.Locale.ROOT), session.currentStep(),
                session.attempts(), session.budgetValues().entrySet().stream().map(entry ->
                    entry.getKey() + " " + Math.round(entry.getValue() * 100.0) / 100.0).toList(),
                session.explanation())).toList();
    }

    private static List<String> modifierLines(ServerPlayer player, StageId stage) {
        var compiled = com.enviouse.progressivestages.server.loader.StageFileLoader.getInstance()
            .getCompiledSnapshot().stages().get(stage);
        if (compiled == null) return List.of();
        List<String> output = new ArrayList<>();
        compiled.progression().modifiers().stream().map(modifier -> modifier.id() + ". "
            + modifier.items().stream().map(item -> item.raw()).toList() + ". " + modifier.contexts()
            + ". Priority " + modifier.priority()).forEach(output::add);
        var held = player.getMainHandItem();
        if (held.isEmpty()) return List.copyOf(output);
        ResourceLocation item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem());
        if (LockRegistry.getInstance().getRequiredStages(held.getItem()).contains(stage)
                && !StageManager.getInstance().hasStage(player, stage)) {
            output.add("Equipment preview. " + item + " is locked until this stage is owned.");
        }
        Set<ResourceLocation> modifierIds = compiled.progression().modifiers().stream()
            .map(com.enviouse.progressivestages.common.rehaul.modifier.CompiledModifier::id).collect(java.util.stream.Collectors.toSet());
        com.enviouse.progressivestages.server.enforcement.ContextualModifierApplier.preview(player).stream()
            .filter(value -> modifierIds.contains(value.sourceRule())).forEach(value -> {
                output.add("Equipment preview. " + item + ". Active modifier " + value.sourceRule()
                    + ". Multiplier " + value.multiplier() + ". Priority " + value.priority() + ".");
                value.attributes().forEach(change -> output.add("Attribute " + change.attribute() + ". Amount "
                    + change.amount() + ". Operation " + change.operation().name().toLowerCase(java.util.Locale.ROOT) + "."));
                value.transforms().forEach(transform -> output.add("Transform " + transform.type() + ". Add "
                    + transform.add() + ". Multiply " + transform.multiply() + "."));
            });
        Set<ResourceLocation> profileIds = compiled.progression().profiles().stream()
            .map(com.enviouse.progressivestages.common.rehaul.profile.AffinityProfile::id).collect(java.util.stream.Collectors.toSet());
        var affinity = com.enviouse.progressivestages.server.rehaul.RehaulRuntime.get().affinity(player,
            com.enviouse.progressivestages.server.enforcement.ContextualModifierApplier.target(held)).orElse(null);
        if (affinity != null && profileIds.contains(affinity.profile())) {
            output.add("Affinity preview. " + item + ". Profile " + affinity.profile() + ". Level "
                + affinity.level() + ". Effect " + affinity.effect().name().toLowerCase(java.util.Locale.ROOT)
                + ". Priority " + affinity.priority() + ".");
            affinity.transforms().forEach(transform -> output.add("Affinity transform " + transform.type()
                + ". Add " + transform.add() + ". Multiply " + transform.multiply() + "."));
        }
        if (output.isEmpty()) output.add("Equipment preview. " + item + " behaves normally for this stage.");
        return List.copyOf(output);
    }

    private static List<HistoryLine> historyLines(ServerPlayer player, StageId stage) {
        return com.enviouse.progressivestages.server.rehaul.RehaulRuntime.get().transitionHistory().entries().stream()
            .filter(entry -> entry.subject().equals(player.getUUID().toString()) && entry.stage().equals(stage))
            .skip(Math.max(0, com.enviouse.progressivestages.server.rehaul.RehaulRuntime.get()
                .transitionHistory().entries().stream().filter(entry -> entry.subject().equals(player.getUUID().toString())
                    && entry.stage().equals(stage)).count() - 20))
            .map(entry -> new HistoryLine(entry.timestamp(),
                entry.direction().name().toLowerCase(java.util.Locale.ROOT), entry.committed(), entry.explanation()))
            .toList();
    }

    /** Authoritative purchase check: purchasable, not owned, prereq stages met, triggers met (unless bypass), affordable. */
    private static boolean canPurchase(ServerPlayer player, StageDefinition def) {
        if (!def.isPurchasable()) return false;
        StageManager sm = StageManager.getInstance();
        if (sm.hasStage(player, def.getId())) return false;
        if (!sm.getMissingDependencies(player, def.getId()).isEmpty()) return false;
        com.enviouse.progressivestages.common.config.StageCost cost = def.getCost();
        if (!cost.bypassRequirements()
                && !com.enviouse.progressivestages.server.triggers.StageTriggerEvaluator.triggersSatisfied(player, def.getId())) {
            return false;
        }
        if (player.experienceLevel < cost.xpLevels()) return false;
        for (var ic : cost.items()) {
            if (countItem(player, ic.item()) < ic.count()) return false;
        }
        return purchaseCooldownRemainingMillis(player, cost) <= 0L;
    }

    private static int countItem(ServerPlayer player, ResourceLocation itemId) {
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item == null) return 0;
        int n = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (!s.isEmpty() && s.is(item)) n += s.getCount();
        }
        return n;
    }

    /** v3.0: per-player skill-tree purchase cooldown tracking (transient, in-memory). */
    private static final java.util.Map<java.util.UUID, Long> lastPurchase = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<java.util.UUID, Long> acknowledgedClientSnapshots = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Integer> challengeHudFingerprints = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Long, byte[]> clientSnapshotHistory = java.util.Collections.synchronizedMap(
        new java.util.LinkedHashMap<>() {
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<Long, byte[]> eldest) {
                return size() > 16;
            }
        });

    public static void clearServerRuntimeState() {
        lastPurchase.clear();
        acknowledgedClientSnapshots.clear();
        challengeHudFingerprints.clear();
        clientSnapshotHistory.clear();
    }

    public static void clearPlayerRuntimeState(java.util.UUID player) {
        acknowledgedClientSnapshots.remove(player);
        challengeHudFingerprints.remove(player);
    }

    public static void sendCompiledSnapshot(ServerPlayer player) {
        long base = acknowledgedClientSnapshots.getOrDefault(player.getUUID(), 0L);
        sendCompiledSnapshot(player, base);
    }

    private static void sendCompiledSnapshot(ServerPlayer player, long base) {
        var snapshot = com.enviouse.progressivestages.server.loader.StageFileLoader.getInstance().getCompiledSnapshot();
        byte[] baseBytes = clientSnapshotHistory.get(base);
        var prepared = com.enviouse.progressivestages.common.rehaul.client.ClientSnapshotCodec.prepare(snapshot, base, baseBytes);
        clientSnapshotHistory.put(snapshot.revision(),
            com.enviouse.progressivestages.common.rehaul.client.ClientSnapshotCodec.encode(snapshot));
        PacketDistributor.sendToPlayer(player, new ClientSnapshotManifestPayload(prepared.manifest()));
        for (var chunk : prepared.chunks()) {
            PacketDistributor.sendToPlayer(player, new ClientSnapshotChunkPayload(chunk));
        }
    }

    private static void handleClientSnapshotManifest(ClientSnapshotManifestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                com.enviouse.progressivestages.client.ClientCompiledSnapshotCache.begin(payload.manifest());
            } catch (RuntimeException error) {
                com.enviouse.progressivestages.client.ClientCompiledSnapshotCache.clear();
                PacketDistributor.sendToServer(new ClientSnapshotRequestPayload(0));
            }
        });
    }

    private static void handleClientSnapshotChunk(ClientSnapshotChunkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (com.enviouse.progressivestages.client.ClientCompiledSnapshotCache.accept(payload.chunk())) {
                PacketDistributor.sendToServer(new ClientSnapshotAckPayload(payload.chunk().revision(),
                    com.enviouse.progressivestages.client.ClientCompiledSnapshotCache.checksum()));
            }
        });
    }

    private static void handleClientSnapshotAck(ClientSnapshotAckPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                var snapshot = com.enviouse.progressivestages.server.loader.StageFileLoader.getInstance().getCompiledSnapshot();
                var expected = com.enviouse.progressivestages.common.rehaul.client.ClientSnapshotCodec.prepare(snapshot, 0).manifest();
                if (payload.revision() == snapshot.revision() && payload.checksum().equals(expected.checksum())) {
                    acknowledgedClientSnapshots.put(player.getUUID(), payload.revision());
                }
            }
        });
    }

    private static void handleClientSnapshotRequest(ClientSnapshotRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (payload.knownRevision() == 0) acknowledgedClientSnapshots.remove(player.getUUID());
                sendCompiledSnapshot(player, payload.knownRevision());
            }
        });
    }

    public static void sendEditorOpen(ServerPlayer player,
                                      com.enviouse.progressivestages.server.editor.EditorSessionOpen session) {
        PacketDistributor.sendToPlayer(player, new EditorOpenPayload(session.sessionId(), session.secret(),
            session.draftId(), session.expiresAt(), session.configurationRevision(), session.catalogRevision(),
            session.protocolVersion()));
    }

    private static void handleEditorOpen(EditorOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.enviouse.progressivestages.client.editor.LoopbackEditorBridge.open(payload));
    }

    private static void handleEditorRequest(EditorRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            String response = com.enviouse.progressivestages.server.editor.EditorSessionService.get()
                .handle(player, payload.sessionId(), payload.secret(), payload.body());
            PacketDistributor.sendToPlayer(player, new EditorResponsePayload(payload.requestId(), response));
        });
    }

    private static void handleEditorResponse(EditorResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.enviouse.progressivestages.client.editor.EditorBridgeTransport
            .complete(payload.requestId(), payload.body()));
    }

    private static long purchaseCooldownRemainingMillis(ServerPlayer player,
                                                         com.enviouse.progressivestages.common.config.StageCost cost) {
        if (cost.cooldownSeconds() <= 0) return 0L;
        Long last = lastPurchase.get(player.getUUID());
        if (last == null) return 0L;
        long elapsed = Math.max(0L, System.currentTimeMillis() - last);
        return Math.max(0L, cost.cooldownSeconds() * 1000L - elapsed);
    }

    private static void handlePurchaseServer(RequestPurchasePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            StageId stageId = StageId.fromResourceLocation(payload.stageId());
            StageDefinition def = StageOrder.getInstance().getStageDefinition(stageId).orElse(null);
            if (def == null || !def.isPurchasable()) {
                sendStageGuiData(player);
                return;
            }
            com.enviouse.progressivestages.common.config.StageCost cost = def.getCost();
            long cooldownRemaining = purchaseCooldownRemainingMillis(player, cost);
            if (cooldownRemaining > 0L) {
                long remain = Math.max(1L, (cooldownRemaining + 999L) / 1000L);
                player.sendSystemMessage(com.enviouse.progressivestages.common.util.TextUtil
                    .parseColorCodes("&cPurchase on cooldown. &f" + remain + "s&c left."));
                sendStageGuiData(player);
                return;
            }
            if (!canPurchase(player, def)) {
                sendStageGuiData(player);
                return;
            }
            if (cost.xpLevels() > 0) player.giveExperienceLevels(-cost.xpLevels());
            for (var ic : cost.items()) consumeItem(player, ic.item(), ic.count());
            StageManager.getInstance().grantStageWithCause(player, stageId,
                com.enviouse.progressivestages.common.api.StageCause.PURCHASE);
            if (!StageManager.getInstance().hasStage(player, stageId)) {
                restoreCost(player, cost);
                player.sendSystemMessage(com.enviouse.progressivestages.common.util.TextUtil
                    .parseColorCodes("&cThe purchase could not be completed. Your cost was restored."));
                sendStageGuiData(player);
                return;
            }
            StageManager.getInstance().markPurchased(player, stageId);
            if (cost.cooldownSeconds() > 0) lastPurchase.put(player.getUUID(), System.currentTimeMillis());
            sendStageGuiData(player);
        });
    }

    private static void restoreCost(ServerPlayer player,
                                    com.enviouse.progressivestages.common.config.StageCost cost) {
        if (cost.xpLevels() > 0) player.giveExperienceLevels(cost.xpLevels());
        for (var itemCost : cost.items()) {
            Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getOptional(itemCost.item()).orElse(null);
            if (item == null) continue;
            int remaining = itemCost.count();
            int maximum = Math.max(1, new net.minecraft.world.item.ItemStack(item).getMaxStackSize());
            while (remaining > 0) {
                int count = Math.min(remaining, maximum);
                var stack = new net.minecraft.world.item.ItemStack(item, count);
                if (!player.getInventory().add(stack) || !stack.isEmpty()) player.drop(stack, false);
                remaining -= count;
            }
        }
    }

    private static void consumeItem(ServerPlayer player, ResourceLocation itemId, int count) {
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item == null) return;
        int remaining = count;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            var s = inv.getItem(i);
            if (!s.isEmpty() && s.is(item)) {
                int take = Math.min(remaining, s.getCount());
                s.shrink(take);
                remaining -= take;
            }
        }
        inv.setChanged();
    }

    private static String conditionLabel(com.enviouse.progressivestages.common.trigger.TriggerCondition c) {
        String type = c.type().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        String base = c.target().isEmpty() ? type : (type + " " + c.target());
        return c.with().isEmpty() ? base : (base + " with " + c.with());
    }

    private static void handleRequestStageGuiServer(RequestStageGuiPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                sendStageGuiData(sp);
            }
        });
    }

    private static void handleStageGuiDataClient(StageGuiDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
            com.enviouse.progressivestages.client.ClientTriggerProgress.acceptAndOpen(payload.stages()));
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

        // v3.0: entity locks — for the Jade/WTHIT "Requires <stage>" overlay on locked mobs.
        List<LockEntry> entityLocks = new ArrayList<>();
        for (var type : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation eid = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (eid == null) continue;
            java.util.Set<com.enviouse.progressivestages.common.api.StageId> gating = registry.getRequiredStagesForEntity(type);
            if (gating.isEmpty()) continue;
            for (com.enviouse.progressivestages.common.api.StageId s : gating) {
                entityLocks.add(new LockEntry(eid, s.getResourceLocation()));
            }
        }

        List<LockEntry> blockLocks = new ArrayList<>();
        for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
            if (id == null) continue;
            for (StageId stage : registry.getRequiredStagesForBlock(block)) {
                blockLocks.add(new LockEntry(id, stage.getResourceLocation()));
            }
        }

        List<LockEntry> fluidLocks = new ArrayList<>();
        for (var fluid : net.minecraft.core.registries.BuiltInRegistries.FLUID) {
            ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
            if (id == null) continue;
            for (StageId stage : registry.getRequiredStagesForFluid(id)) {
                fluidLocks.add(new LockEntry(id, stage.getResourceLocation()));
            }
        }

        // Pseudo id namespace carries the mod id; this supports abstract EMI/JEI ingredients whose
        // concrete registry id does not identify the implementation mod (e.g. chemicals).
        List<LockEntry> modLocks = new ArrayList<>();
        for (String modId : registry.getAllLockedMods()) {
            ResourceLocation pseudo = ResourceLocation.fromNamespaceAndPath(modId, "__mod__");
            for (StageId stage : registry.getRequiredStagesForMod(modId)) {
                modLocks.add(new LockEntry(pseudo, stage.getResourceLocation()));
            }
        }

        PacketDistributor.sendToPlayer(player, new LockSyncPayload(
            itemLocks, blockLocks, fluidLocks, recipeLocks, recipeItemLocks, entityLocks, modLocks));
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
                // v2.3: resolve each per-stage [display] override against the global default.
                definitions.add(new StageDefinitionEntry(
                    stageId.getResourceLocation(),
                    def.getDisplayName(),
                    deps,
                    def.getDependencyMode().configName(),
                    def.getDependencyCount(),
                    def.getDescription() == null ? "" : def.getDescription(),
                    def.getIcon().map(ResourceLocation::toString).orElse(""),
                    resolveFlag(def.getDisplayAsUnknownItem(),
                        com.enviouse.progressivestages.common.config.StageConfig.isMaskLockedItemNames()),
                    resolveFlag(def.getObscureIcon(),
                        com.enviouse.progressivestages.common.config.StageConfig.isObscureLockedItemIcons()),
                    resolveFlag(def.getShowTooltip(),
                        com.enviouse.progressivestages.common.config.StageConfig.isShowTooltip()),
                    resolveFlag(def.getShowDescriptionOnTooltip(),
                        com.enviouse.progressivestages.common.config.StageConfig.isShowStageDescriptionOnTooltip()),
                    def.hasTriggers(),
                    def.isHidden(),
                    def.getColor(),
                    def.getCategory(),
                    def.getUiX().isPresent() && def.getUiY().isPresent(),
                    def.getUiX().orElse(0),
                    def.getUiY().orElse(0),
                    def.getUiFrame(),
                    def.getUiBackground(),
                    def.getUiReveal(),
                    def.getUiSortOrder()
                ));
            });
        }

        PacketDistributor.sendToPlayer(player, new StageDefinitionsSyncPayload(definitions));
    }

    /** v2.3: a per-stage [display] override (nullable) falls back to the global default. */
    private static boolean resolveFlag(Boolean override, boolean globalDefault) {
        return override != null ? override : globalDefault;
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

            Map<ResourceLocation, StageId> blockLocks = new HashMap<>();
            Map<ResourceLocation, java.util.Set<StageId>> blockMulti = new HashMap<>();
            for (LockEntry entry : payload.blockLocks()) {
                StageId sid = StageId.fromResourceLocation(entry.stageId());
                blockLocks.putIfAbsent(entry.itemId(), sid);
                blockMulti.computeIfAbsent(entry.itemId(), k -> new java.util.LinkedHashSet<>()).add(sid);
            }
            ClientLockCache.setBlockLocks(blockLocks);
            ClientLockCache.setBlockMultiLocks(blockMulti);

            Map<ResourceLocation, java.util.Set<StageId>> fluidMulti = new HashMap<>();
            for (LockEntry entry : payload.fluidLocks()) {
                fluidMulti.computeIfAbsent(entry.itemId(), k -> new java.util.LinkedHashSet<>())
                    .add(StageId.fromResourceLocation(entry.stageId()));
            }
            ClientLockCache.setFluidMultiLocks(fluidMulti);

            Map<String, java.util.Set<StageId>> modMulti = new HashMap<>();
            for (LockEntry entry : payload.modLocks()) {
                modMulti.computeIfAbsent(entry.itemId().getNamespace(), k -> new java.util.LinkedHashSet<>())
                    .add(StageId.fromResourceLocation(entry.stageId()));
            }
            ClientLockCache.setModMultiLocks(modMulti);

            // v3.0: entity locks → multi-stage map for the Jade/WTHIT overlay.
            Map<ResourceLocation, java.util.Set<StageId>> entityMulti = new HashMap<>();
            for (LockEntry entry : payload.entityLocks()) {
                entityMulti.computeIfAbsent(entry.itemId(), k -> new java.util.LinkedHashSet<>())
                    .add(StageId.fromResourceLocation(entry.stageId()));
            }
            ClientLockCache.setEntityMultiLocks(entityMulti);

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
            // Populate the multi-stage view first: setRecipeItemLocks triggers an EMI/JEI reload,
            // which reads the multi-stage map for correctness.
            ClientLockCache.setRecipeItemMultiLocks(recipeItemMulti);
            ClientLockCache.setRecipeItemLocks(recipeItemLocks);
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
                java.util.Optional<ResourceLocation> icon = entry.icon().isEmpty()
                    ? java.util.Optional.empty()
                    : java.util.Optional.ofNullable(ResourceLocation.tryParse(entry.icon()));
                definitions.put(stageId, new ClientStageCache.StageDefinitionData(
                    stageId,
                    entry.displayName(),
                    deps,
                    entry.dependencyMode(),
                    entry.dependencyCount(),
                    entry.description(),
                    icon,
                    entry.displayAsUnknownItem(),
                    entry.obscureIcon(),
                    entry.showTooltip(),
                    entry.showDescriptionOnTooltip(),
                    entry.hasTriggers(),
                    entry.hidden(),
                    entry.color(),
                    entry.category(),
                    entry.hasUiPosition() ? entry.uiX() : null,
                    entry.hasUiPosition() ? entry.uiY() : null,
                    entry.uiFrame(),
                    entry.uiBackground(),
                    entry.uiReveal(),
                    entry.uiSortOrder()
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

    /**
     * v2.0.1: Ore-spoof delta applier. Runs on the client. Updates the local block
     * states, the client-side spoofed-positions cache (for light recompute), and
     * triggers a per-section light update so glowstone/lava don't leak fake light.
     */
    private static void handleOreSpoofClient(OreSpoofPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.enviouse.progressivestages.client.OreSpoofClientState.applyDelta(payload);
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
    public record LockSyncPayload(List<LockEntry> itemLocks, List<LockEntry> blockLocks,
                                  List<LockEntry> fluidLocks, List<LockEntry> recipeLocks,
                                  List<LockEntry> recipeItemLocks, List<LockEntry> entityLocks,
                                  List<LockEntry> modLocks) implements CustomPacketPayload {
        public static final Type<LockSyncPayload> TYPE = new Type<>(Constants.LOCK_SYNC_PACKET);

        private static final StreamCodec<FriendlyByteBuf, List<LockEntry>> LIST_CODEC =
            LockEntry.STREAM_CODEC.apply(ByteBufCodecs.list());

        public static final StreamCodec<FriendlyByteBuf, LockSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                LIST_CODEC.encode(buf, payload.itemLocks());
                LIST_CODEC.encode(buf, payload.blockLocks());
                LIST_CODEC.encode(buf, payload.fluidLocks());
                LIST_CODEC.encode(buf, payload.recipeLocks());
                LIST_CODEC.encode(buf, payload.recipeItemLocks());
                LIST_CODEC.encode(buf, payload.entityLocks());
                LIST_CODEC.encode(buf, payload.modLocks());
            },
            buf -> new LockSyncPayload(
                LIST_CODEC.decode(buf), LIST_CODEC.decode(buf), LIST_CODEC.decode(buf),
                LIST_CODEC.decode(buf), LIST_CODEC.decode(buf), LIST_CODEC.decode(buf),
                LIST_CODEC.decode(buf))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Stage definition entry for network serialization.
     *
     * <p>v2.3: carries description, icon, the resolved per-stage [display] flags, and a
     * hasTriggers bit for the GUI tree viewer + tooltip handler. Hand-written codec because
     * the field count exceeds {@code StreamCodec.composite}'s arity.
     */
    public record StageDefinitionEntry(ResourceLocation stageId, String displayName,
                                       List<ResourceLocation> dependencies,
                                       String dependencyMode, int dependencyCount, String description,
                                       String icon, boolean displayAsUnknownItem, boolean obscureIcon,
                                       boolean showTooltip, boolean showDescriptionOnTooltip,
                                       boolean hasTriggers,
                                       boolean hidden, String color, String category,
                                       boolean hasUiPosition, int uiX, int uiY, String uiFrame,
                                       String uiBackground, String uiReveal, int uiSortOrder) {

        private static final StreamCodec<io.netty.buffer.ByteBuf, List<ResourceLocation>> DEPS_CODEC =
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list());

        public static final StreamCodec<FriendlyByteBuf, StageDefinitionEntry> STREAM_CODEC = StreamCodec.of(
            (buf, e) -> {
                ResourceLocation.STREAM_CODEC.encode(buf, e.stageId());
                ByteBufCodecs.STRING_UTF8.encode(buf, e.displayName());
                DEPS_CODEC.encode(buf, e.dependencies());
                ByteBufCodecs.STRING_UTF8.encode(buf, e.dependencyMode());
                buf.writeVarInt(e.dependencyCount());
                ByteBufCodecs.STRING_UTF8.encode(buf, e.description());
                ByteBufCodecs.STRING_UTF8.encode(buf, e.icon());
                buf.writeBoolean(e.displayAsUnknownItem());
                buf.writeBoolean(e.obscureIcon());
                buf.writeBoolean(e.showTooltip());
                buf.writeBoolean(e.showDescriptionOnTooltip());
                buf.writeBoolean(e.hasTriggers());
                // v2.5: presentation metadata for the GUI tree.
                buf.writeBoolean(e.hidden());
                ByteBufCodecs.STRING_UTF8.encode(buf, e.color());
                ByteBufCodecs.STRING_UTF8.encode(buf, e.category());
                buf.writeBoolean(e.hasUiPosition());
                if (e.hasUiPosition()) {
                    buf.writeVarInt(e.uiX());
                    buf.writeVarInt(e.uiY());
                }
                ByteBufCodecs.STRING_UTF8.encode(buf, e.uiFrame());
                ByteBufCodecs.STRING_UTF8.encode(buf, e.uiBackground());
                ByteBufCodecs.STRING_UTF8.encode(buf, e.uiReveal());
                buf.writeVarInt(e.uiSortOrder());
            },
            buf -> {
                ResourceLocation stageId = ResourceLocation.STREAM_CODEC.decode(buf);
                String displayName = ByteBufCodecs.STRING_UTF8.decode(buf);
                List<ResourceLocation> dependencies = DEPS_CODEC.decode(buf);
                String dependencyMode = ByteBufCodecs.STRING_UTF8.decode(buf);
                int dependencyCount = buf.readVarInt();
                String description = ByteBufCodecs.STRING_UTF8.decode(buf);
                String icon = ByteBufCodecs.STRING_UTF8.decode(buf);
                boolean displayAsUnknownItem = buf.readBoolean();
                boolean obscureIcon = buf.readBoolean();
                boolean showTooltip = buf.readBoolean();
                boolean showDescriptionOnTooltip = buf.readBoolean();
                boolean hasTriggers = buf.readBoolean();
                boolean hidden = buf.readBoolean();
                String color = ByteBufCodecs.STRING_UTF8.decode(buf);
                String category = ByteBufCodecs.STRING_UTF8.decode(buf);
                boolean hasUiPosition = buf.readBoolean();
                int uiX = hasUiPosition ? buf.readVarInt() : 0;
                int uiY = hasUiPosition ? buf.readVarInt() : 0;
                String uiFrame = ByteBufCodecs.STRING_UTF8.decode(buf);
                String uiBackground = ByteBufCodecs.STRING_UTF8.decode(buf);
                String uiReveal = ByteBufCodecs.STRING_UTF8.decode(buf);
                int uiSortOrder = buf.readVarInt();
                return new StageDefinitionEntry(stageId, displayName, dependencies, dependencyMode,
                    dependencyCount, description,
                    icon, displayAsUnknownItem, obscureIcon, showTooltip, showDescriptionOnTooltip,
                    hasTriggers, hidden, color, category, hasUiPosition, uiX, uiY, uiFrame,
                    uiBackground, uiReveal, uiSortOrder);
            }
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

    // ============ v2.0.1 Ore spoof payload ============

    /**
     * One spoofed position + the displayed block state id (as
     * {@link net.minecraft.world.level.block.Block#getId(net.minecraft.world.level.block.state.BlockState)}).
     */
    public record OreSpoofEntry(long packedPos, int blockStateId) {
        public static final StreamCodec<FriendlyByteBuf, OreSpoofEntry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, OreSpoofEntry::packedPos,
            ByteBufCodecs.VAR_INT,  OreSpoofEntry::blockStateId,
            OreSpoofEntry::new
        );
    }

    /**
     * Ore-spoof delta packet. Applies (additions) and clears (removals) atomically.
     * When {@code dimensionReset} is true, the client wipes its entire spoof set
     * for this dimension before applying — used on stage grant / login.
     */
    public record OreSpoofPayload(java.util.List<OreSpoofEntry> additions,
                                  java.util.List<Long> removals,
                                  boolean dimensionReset) implements CustomPacketPayload {
        public static final Type<OreSpoofPayload> TYPE = new Type<>(Constants.ORE_SPOOF_PACKET);

        public static final StreamCodec<FriendlyByteBuf, OreSpoofPayload> STREAM_CODEC = StreamCodec.composite(
            OreSpoofEntry.STREAM_CODEC.apply(ByteBufCodecs.list()),
            OreSpoofPayload::additions,
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()),
            OreSpoofPayload::removals,
            ByteBufCodecs.BOOL,
            OreSpoofPayload::dimensionReset,
            OreSpoofPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ============ v2.3 stage-tree GUI payloads ============

    /** One condition's progress for the GUI: a human label + current/threshold + satisfied. */
    public record CondLine(String label, int current, int threshold, boolean satisfied) {
        public static final StreamCodec<FriendlyByteBuf, CondLine> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CondLine::label,
            ByteBufCodecs.VAR_INT, CondLine::current,
            ByteBufCodecs.VAR_INT, CondLine::threshold,
            ByteBufCodecs.BOOL, CondLine::satisfied,
            CondLine::new
        );
    }

    /** One trigger rule's progress: its mode, description, satisfied flag, and conditions. */
    public record RuleLine(String mode, String description, boolean satisfied, List<CondLine> conditions) {
        public static final StreamCodec<FriendlyByteBuf, RuleLine> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RuleLine::mode,
            ByteBufCodecs.STRING_UTF8, RuleLine::description,
            ByteBufCodecs.BOOL, RuleLine::satisfied,
            CondLine.STREAM_CODEC.apply(ByteBufCodecs.list()), RuleLine::conditions,
            RuleLine::new
        );
    }

    /** v2.4: a purchasable stage's cost summary + whether the player can buy it right now. */
    public record CostInfo(boolean purchasable, int costXp, String summary, boolean canPurchase) {
        public static final CostInfo NONE = new CostInfo(false, 0, "", false);
        public static final StreamCodec<FriendlyByteBuf, CostInfo> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, CostInfo::purchasable,
            ByteBufCodecs.VAR_INT, CostInfo::costXp,
            ByteBufCodecs.STRING_UTF8, CostInfo::summary,
            ByteBufCodecs.BOOL, CostInfo::canPurchase,
            CostInfo::new
        );
    }

    public record WhyLine(String category, String action, ResourceLocation target, String winner,
                          String effect, boolean blocked, String explanation) {
        public static final StreamCodec<FriendlyByteBuf, WhyLine> STREAM_CODEC = new StreamCodec<>() {
            @Override public WhyLine decode(FriendlyByteBuf buffer) {
                return new WhyLine(buffer.readUtf(), buffer.readUtf(), ResourceLocation.STREAM_CODEC.decode(buffer),
                    buffer.readUtf(), buffer.readUtf(), buffer.readBoolean(), buffer.readUtf());
            }
            @Override public void encode(FriendlyByteBuf buffer, WhyLine value) {
                buffer.writeUtf(value.category()); buffer.writeUtf(value.action());
                ResourceLocation.STREAM_CODEC.encode(buffer, value.target());
                buffer.writeUtf(value.winner()); buffer.writeUtf(value.effect()); buffer.writeBoolean(value.blocked());
                buffer.writeUtf(value.explanation());
            }
        };
    }

    public record ChallengeLine(ResourceLocation id, String status, int step, int attempts,
                                List<String> budgets, String explanation) {
        public static final StreamCodec<FriendlyByteBuf, ChallengeLine> STREAM_CODEC = new StreamCodec<>() {
            @Override public ChallengeLine decode(FriendlyByteBuf buffer) {
                return new ChallengeLine(ResourceLocation.STREAM_CODEC.decode(buffer), buffer.readUtf(),
                    buffer.readVarInt(), buffer.readVarInt(), ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buffer),
                    buffer.readUtf());
            }
            @Override public void encode(FriendlyByteBuf buffer, ChallengeLine value) {
                ResourceLocation.STREAM_CODEC.encode(buffer, value.id()); buffer.writeUtf(value.status());
                buffer.writeVarInt(value.step()); buffer.writeVarInt(value.attempts());
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buffer, value.budgets());
                buffer.writeUtf(value.explanation());
            }
        };
    }

    public record HistoryLine(long timestamp, String direction, boolean committed, String explanation) {
        public static final StreamCodec<FriendlyByteBuf, HistoryLine> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, HistoryLine::timestamp, ByteBufCodecs.STRING_UTF8, HistoryLine::direction,
            ByteBufCodecs.BOOL, HistoryLine::committed, ByteBufCodecs.STRING_UTF8, HistoryLine::explanation,
            HistoryLine::new);
    }

    /**
     * Per-stage GUI data: trigger-rule progress, a preview of the items this stage unlocks
     * (a capped sample for icon rendering + the true total count), and purchase info.
     */
    public record StageProgress(ResourceLocation stageId, List<RuleLine> rules,
                                List<ResourceLocation> unlockSample, int unlockTotal, CostInfo cost,
                                List<WhyLine> why, List<ChallengeLine> challenges,
                                List<String> modifiers, List<HistoryLine> history) {
        public static final StreamCodec<FriendlyByteBuf, StageProgress> STREAM_CODEC = new StreamCodec<>() {
            @Override public StageProgress decode(FriendlyByteBuf buffer) {
                return new StageProgress(ResourceLocation.STREAM_CODEC.decode(buffer),
                    RuleLine.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer),
                    ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer),
                    buffer.readVarInt(), CostInfo.STREAM_CODEC.decode(buffer),
                    WhyLine.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer),
                    ChallengeLine.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer),
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buffer),
                    HistoryLine.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer));
            }
            @Override public void encode(FriendlyByteBuf buffer, StageProgress value) {
                ResourceLocation.STREAM_CODEC.encode(buffer, value.stageId());
                RuleLine.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, value.rules());
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, value.unlockSample());
                buffer.writeVarInt(value.unlockTotal()); CostInfo.STREAM_CODEC.encode(buffer, value.cost());
                WhyLine.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, value.why());
                ChallengeLine.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, value.challenges());
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buffer, value.modifiers());
                HistoryLine.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, value.history());
            }
        };
    }

    /** Server -> client: per-stage trigger progress; the client opens the GUI when it arrives. */
    public record StageGuiDataPayload(List<StageProgress> stages) implements CustomPacketPayload {
        public static final Type<StageGuiDataPayload> TYPE = new Type<>(Constants.STAGE_GUI_DATA_PACKET);

        public static final StreamCodec<FriendlyByteBuf, StageGuiDataPayload> STREAM_CODEC = StreamCodec.composite(
            StageProgress.STREAM_CODEC.apply(ByteBufCodecs.list()),
            StageGuiDataPayload::stages,
            StageGuiDataPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: request a fresh stage-GUI data push (and open). No fields. */
    public record RequestStageGuiPayload() implements CustomPacketPayload {
        public static final Type<RequestStageGuiPayload> TYPE = new Type<>(Constants.REQUEST_STAGE_GUI_PACKET);
        public static final RequestStageGuiPayload INSTANCE = new RequestStageGuiPayload();

        public static final StreamCodec<FriendlyByteBuf, RequestStageGuiPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: show an advancement-style toast for an unlocked stage (v2.4). */
    public record UnlockToastPayload(String title, String subtitle, String iconItem) implements CustomPacketPayload {
        public static final Type<UnlockToastPayload> TYPE = new Type<>(Constants.UNLOCK_TOAST_PACKET);
        public static final StreamCodec<FriendlyByteBuf, UnlockToastPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, UnlockToastPayload::title,
            ByteBufCodecs.STRING_UTF8, UnlockToastPayload::subtitle,
            ByteBufCodecs.STRING_UTF8, UnlockToastPayload::iconItem,
            UnlockToastPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server -> client: the active-goal HUD bar (label + % to next stage); show=false hides it (v2.4). */
    public record ActiveGoalPayload(String label, float percent, boolean show) implements CustomPacketPayload {
        public static final Type<ActiveGoalPayload> TYPE = new Type<>(Constants.ACTIVE_GOAL_PACKET);
        public static final StreamCodec<FriendlyByteBuf, ActiveGoalPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ActiveGoalPayload::label,
            ByteBufCodecs.FLOAT, ActiveGoalPayload::percent,
            ByteBufCodecs.BOOL, ActiveGoalPayload::show,
            ActiveGoalPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ChallengeHudLine(ResourceLocation id, String title, String status, int currentStep,
                                   int totalSteps, int attempts, long startedAt, long timeoutMillis,
                                   List<String> budgets, String session, String successCriteria,
                                   String explanation, String placement, double scale, String color,
                                   String icon, String animation, boolean compact) {
        public static final StreamCodec<FriendlyByteBuf, ChallengeHudLine> STREAM_CODEC = new StreamCodec<>() {
            @Override public ChallengeHudLine decode(FriendlyByteBuf buffer) {
                return new ChallengeHudLine(ResourceLocation.STREAM_CODEC.decode(buffer), buffer.readUtf(), buffer.readUtf(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarLong(), buffer.readVarLong(),
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buffer), buffer.readUtf(), buffer.readUtf(),
                    buffer.readUtf(), buffer.readUtf(), buffer.readDouble(), buffer.readUtf(), buffer.readUtf(),
                    buffer.readUtf(), buffer.readBoolean());
            }
            @Override public void encode(FriendlyByteBuf buffer, ChallengeHudLine value) {
                ResourceLocation.STREAM_CODEC.encode(buffer, value.id());
                buffer.writeUtf(value.title()); buffer.writeUtf(value.status());
                buffer.writeVarInt(value.currentStep()); buffer.writeVarInt(value.totalSteps());
                buffer.writeVarInt(value.attempts()); buffer.writeVarLong(value.startedAt());
                buffer.writeVarLong(value.timeoutMillis());
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buffer, value.budgets());
                buffer.writeUtf(value.session()); buffer.writeUtf(value.successCriteria());
                buffer.writeUtf(value.explanation()); buffer.writeUtf(value.placement());
                buffer.writeDouble(value.scale()); buffer.writeUtf(value.color()); buffer.writeUtf(value.icon());
                buffer.writeUtf(value.animation()); buffer.writeBoolean(value.compact());
            }
        };
    }

    public record ChallengeHudPayload(List<ChallengeHudLine> entries) implements CustomPacketPayload {
        public static final Type<ChallengeHudPayload> TYPE = new Type<>(Constants.CHALLENGE_HUD_PACKET);
        public static final StreamCodec<FriendlyByteBuf, ChallengeHudPayload> STREAM_CODEC = StreamCodec.composite(
            ChallengeHudLine.STREAM_CODEC.apply(ByteBufCodecs.list()), ChallengeHudPayload::entries,
            ChallengeHudPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client -> server: buy a purchasable stage from the GUI (v2.4 skill-tree mode). */
    public record RequestPurchasePayload(ResourceLocation stageId) implements CustomPacketPayload {
        public static final Type<RequestPurchasePayload> TYPE = new Type<>(Constants.REQUEST_PURCHASE_PACKET);

        public static final StreamCodec<FriendlyByteBuf, RequestPurchasePayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, RequestPurchasePayload::stageId,
            RequestPurchasePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ClientSnapshotManifestPayload(
            com.enviouse.progressivestages.common.rehaul.client.ClientSnapshotManifest manifest)
            implements CustomPacketPayload {
        public static final Type<ClientSnapshotManifestPayload> TYPE = new Type<>(Constants.CLIENT_SNAPSHOT_MANIFEST_PACKET);
        public static final StreamCodec<FriendlyByteBuf, ClientSnapshotManifestPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override public ClientSnapshotManifestPayload decode(FriendlyByteBuf buffer) {
                    int protocol = buffer.readVarInt();
                    int schema = buffer.readVarInt();
                    long revision = buffer.readVarLong();
                    long base = buffer.readVarLong();
                    String checksum = buffer.readUtf(128);
                    int chunks = buffer.readVarInt();
                    int compressed = buffer.readVarInt();
                    int uncompressed = buffer.readVarInt();
                    int count = buffer.readVarInt();
                    Set<String> capabilities = new LinkedHashSet<>();
                    for (int index = 0; index < count; index++) capabilities.add(buffer.readUtf(128));
                    boolean delta = buffer.readBoolean();
                    return new ClientSnapshotManifestPayload(new com.enviouse.progressivestages.common.rehaul.client.ClientSnapshotManifest(
                        protocol, schema, revision, base, checksum, chunks, compressed, uncompressed, capabilities, delta));
                }
                @Override public void encode(FriendlyByteBuf buffer, ClientSnapshotManifestPayload payload) {
                    var value = payload.manifest();
                    buffer.writeVarInt(value.protocolVersion());
                    buffer.writeVarInt(value.schemaVersion());
                    buffer.writeVarLong(value.configurationRevision());
                    buffer.writeVarLong(value.baseRevision());
                    buffer.writeUtf(value.checksum(), 128);
                    buffer.writeVarInt(value.chunks());
                    buffer.writeVarInt(value.compressedBytes());
                    buffer.writeVarInt(value.uncompressedBytes());
                    buffer.writeVarInt(value.capabilities().size());
                    for (String capability : value.capabilities()) buffer.writeUtf(capability, 128);
                    buffer.writeBoolean(value.delta());
                }
            };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ClientSnapshotChunkPayload(
            com.enviouse.progressivestages.common.rehaul.client.ClientSnapshotChunk chunk)
            implements CustomPacketPayload {
        public static final Type<ClientSnapshotChunkPayload> TYPE = new Type<>(Constants.CLIENT_SNAPSHOT_CHUNK_PACKET);
        public static final StreamCodec<FriendlyByteBuf, ClientSnapshotChunkPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override public ClientSnapshotChunkPayload decode(FriendlyByteBuf buffer) {
                    return new ClientSnapshotChunkPayload(new com.enviouse.progressivestages.common.rehaul.client.ClientSnapshotChunk(
                        buffer.readVarLong(), buffer.readVarInt(), buffer.readByteArray(
                            com.enviouse.progressivestages.common.rehaul.client.ClientSnapshotCodec.MAX_CHUNK_BYTES)));
                }
                @Override public void encode(FriendlyByteBuf buffer, ClientSnapshotChunkPayload payload) {
                    buffer.writeVarLong(payload.chunk().revision());
                    buffer.writeVarInt(payload.chunk().sequence());
                    buffer.writeByteArray(payload.chunk().data());
                }
            };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ClientSnapshotAckPayload(long revision, String checksum) implements CustomPacketPayload {
        public static final Type<ClientSnapshotAckPayload> TYPE = new Type<>(Constants.CLIENT_SNAPSHOT_ACK_PACKET);
        public static final StreamCodec<FriendlyByteBuf, ClientSnapshotAckPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_LONG, ClientSnapshotAckPayload::revision,
                ByteBufCodecs.STRING_UTF8, ClientSnapshotAckPayload::checksum, ClientSnapshotAckPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ClientSnapshotRequestPayload(long knownRevision) implements CustomPacketPayload {
        public static final Type<ClientSnapshotRequestPayload> TYPE = new Type<>(Constants.CLIENT_SNAPSHOT_REQUEST_PACKET);
        public static final StreamCodec<FriendlyByteBuf, ClientSnapshotRequestPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_LONG, ClientSnapshotRequestPayload::knownRevision,
                ClientSnapshotRequestPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record EditorOpenPayload(UUID sessionId, String secret, UUID draftId, long expiresAt,
                                    long configurationRevision, long catalogRevision, int protocolVersion)
            implements CustomPacketPayload {
        public static final Type<EditorOpenPayload> TYPE = new Type<>(Constants.EDITOR_OPEN_PACKET);
        public static final StreamCodec<FriendlyByteBuf, EditorOpenPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override public EditorOpenPayload decode(FriendlyByteBuf buffer) {
                return new EditorOpenPayload(buffer.readUUID(), buffer.readUtf(128), buffer.readUUID(),
                    buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(), buffer.readVarInt());
            }
            @Override public void encode(FriendlyByteBuf buffer, EditorOpenPayload payload) {
                buffer.writeUUID(payload.sessionId());
                buffer.writeUtf(payload.secret(), 128);
                buffer.writeUUID(payload.draftId());
                buffer.writeVarLong(payload.expiresAt());
                buffer.writeVarLong(payload.configurationRevision());
                buffer.writeVarLong(payload.catalogRevision());
                buffer.writeVarInt(payload.protocolVersion());
            }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record EditorRequestPayload(UUID sessionId, UUID requestId, String secret, String body)
            implements CustomPacketPayload {
        public static final Type<EditorRequestPayload> TYPE = new Type<>(Constants.EDITOR_REQUEST_PACKET);
        public static final StreamCodec<FriendlyByteBuf, EditorRequestPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override public EditorRequestPayload decode(FriendlyByteBuf buffer) {
                return new EditorRequestPayload(buffer.readUUID(), buffer.readUUID(), buffer.readUtf(128),
                    buffer.readUtf(com.enviouse.progressivestages.server.editor.EditorSessionService.MAX_REQUEST_BYTES));
            }
            @Override public void encode(FriendlyByteBuf buffer, EditorRequestPayload payload) {
                buffer.writeUUID(payload.sessionId());
                buffer.writeUUID(payload.requestId());
                buffer.writeUtf(payload.secret(), 128);
                buffer.writeUtf(payload.body(), com.enviouse.progressivestages.server.editor.EditorSessionService.MAX_REQUEST_BYTES);
            }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record EditorResponsePayload(UUID requestId, String body) implements CustomPacketPayload {
        public static final Type<EditorResponsePayload> TYPE = new Type<>(Constants.EDITOR_RESPONSE_PACKET);
        public static final StreamCodec<FriendlyByteBuf, EditorResponsePayload> STREAM_CODEC = new StreamCodec<>() {
            @Override public EditorResponsePayload decode(FriendlyByteBuf buffer) {
                return new EditorResponsePayload(buffer.readUUID(),
                    buffer.readUtf(com.enviouse.progressivestages.server.editor.EditorSessionService.MAX_RESPONSE_BYTES));
            }
            @Override public void encode(FriendlyByteBuf buffer, EditorResponsePayload payload) {
                buffer.writeUUID(payload.requestId());
                buffer.writeUtf(payload.body(), com.enviouse.progressivestages.server.editor.EditorSessionService.MAX_RESPONSE_BYTES);
            }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
