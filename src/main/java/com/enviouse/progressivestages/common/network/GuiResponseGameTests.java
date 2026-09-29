package com.enviouse.progressivestages.common.network;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@GameTestHolder("progressivestages")
@PrefixGameTestTemplate(false)
public final class GuiResponseGameTests {
    private GuiResponseGameTests() {}

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 400, batch = "progressivestages_gui_packet")
    public static void guiPacketPurchaseAndCommandBurstsShareOneResponseBudget(GameTestHelper helper) throws Exception {
        var capture = new Capture();
        var player = player(helper, capture);
        var gui = NetworkHandler.class.getDeclaredMethod("handleRequestStageGuiServer",
            NetworkHandler.RequestStageGuiPayload.class, IPayloadContext.class);
        var purchase = NetworkHandler.class.getDeclaredMethod("handlePurchaseServer",
            NetworkHandler.RequestPurchasePayload.class, IPayloadContext.class);
        gui.setAccessible(true);
        purchase.setAccessible(true);
        var context = context(player);
        var guiPayload = new NetworkHandler.RequestStageGuiPayload();
        var invalidPurchase = new NetworkHandler.RequestPurchasePayload(StageId.parse("progressivestages:missing_gui_test").getResourceLocation());
        var source = player.createCommandSourceStack().withPermission(0).withSuppressedOutput();
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        boolean wasEnabled = bean.isThreadAllocatedMemoryEnabled();
        try {
            for (int index = 0; index < 128; index++) gui.invoke(null, guiPayload, context);
            bean.setThreadAllocatedMemoryEnabled(true);
            long thread = Thread.currentThread().threadId();
            long before = bean.getThreadAllocatedBytes(thread);
            long started = System.nanoTime();
            for (int index = 0; index < 1000; index++) {
                if (index % 3 == 0) gui.invoke(null, guiPayload, context);
                else if (index % 3 == 1) purchase.invoke(null, invalidPurchase, context);
                else helper.getLevel().getServer().getCommands().performPrefixedCommand(source, "stage gui");
            }
            long elapsed = System.nanoTime() - started;
            long allocated = bean.getThreadAllocatedBytes(thread) - before;
            com.mojang.logging.LogUtils.getLogger().info(
                "GUI request burst. Requests 1000, responses {}, elapsed nanoseconds {}, allocated bytes {}",
                capture.responses, elapsed, allocated);
            helper.assertTrue(capture.responses == 1,
                "GUI packets, rejected purchases and public commands must share one immediate response.");
            helper.assertTrue(allocated >= 0 && allocated < 8 * 1024 * 1024,
                "Repeated GUI requests must not rebuild the full registry and stage view.");
            helper.succeed();
        } finally {
            bean.setThreadAllocatedMemoryEnabled(wasEnabled);
            NetworkHandler.clearPlayerRuntimeState(player.getUUID());
            cleanupPlayer(player);
        }
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 100, batch = "progressivestages_gui_queue")
    @SuppressWarnings("unchecked")
    public static void queuedGuiResponsesUseCurrentDataAndStopAfterDisconnect(GameTestHelper helper) throws Exception {
        var capture = new Capture();
        var otherCapture = new Capture();
        var player = player(helper, capture);
        var other = player(helper, otherCapture);
        var field = PlayerList.class.getDeclaredField("playersByUUID");
        field.setAccessible(true);
        var players = (Map<UUID, ServerPlayer>) field.get(helper.getLevel().getServer().getPlayerList());
        var order = StageOrder.getInstance();
        var definitions = order.getOrderedStages().stream().map(id -> order.getStageDefinition(id).orElseThrow()).toList();
        var added = StageId.parse("progressivestages:gui_response_current");
        helper.assertTrue(!order.stageExists(added), "The GUI fixture stage must be unused.");
        Runnable cleanup = () -> {
            players.remove(player.getUUID(), player);
            players.remove(other.getUUID(), other);
            order.clear();
            definitions.forEach(order::registerStage);
            NetworkHandler.clearPlayerRuntimeState(player.getUUID());
            NetworkHandler.clearPlayerRuntimeState(other.getUUID());
            cleanupPlayer(player);
            cleanupPlayer(other);
        };
        try {
            players.put(player.getUUID(), player);
            players.put(other.getUUID(), other);
            NetworkHandler.sendStageGuiData(player);
            NetworkHandler.sendStageGuiData(other);
            helper.assertTrue(capture.responses == 1 && otherCapture.responses == 1,
                "Different players must receive independent immediate GUI responses.");
            NetworkHandler.sendStageGuiData(player);
            NetworkHandler.sendStageGuiData(other);
            order.registerStage(StageDefinition.builder(added).build());
            NetworkHandler.clearPlayerRuntimeState(other.getUUID());
            players.remove(other.getUUID(), other);
            helper.runAfterDelay(25, () -> {
                try {
                    helper.assertTrue(capture.responses == 2 && otherCapture.responses == 1,
                        "Server ticks must send one pending response and cancel disconnected responses.");
                    helper.assertTrue(capture.last.stages().stream().anyMatch(stage -> stage.stageId().equals(added.getResourceLocation())),
                        "The deferred response must use current definitions rather than cached GUI data.");
                    NetworkHandler.clearPlayerRuntimeState(player.getUUID());
                    NetworkHandler.sendStageGuiData(player);
                    helper.assertTrue(capture.responses == 3,
                        "A new connection must not inherit the prior GUI cooldown.");
                    helper.succeed();
                } finally { cleanup.run(); }
            });
        } catch (Exception | AssertionError error) {
            cleanup.run();
            throw error;
        }
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 100, batch = "progressivestages_gui_explicit")
    @SuppressWarnings("unchecked")
    public static void explicitGuiCommandsOpenWhileQueuedResponsesOnlyRefresh(GameTestHelper helper) throws Exception {
        var capture = new Capture();
        capture.explicitOpening = true;
        var legacyCapture = new Capture();
        var player = player(helper, capture);
        var legacy = player(helper, legacyCapture);
        var server = helper.getLevel().getServer();
        var field = PlayerList.class.getDeclaredField("playersByUUID");
        field.setAccessible(true);
        var players = (Map<UUID, ServerPlayer>) field.get(server.getPlayerList());
        Runnable cleanup = () -> {
            players.remove(player.getUUID(), player);
            NetworkHandler.clearPlayerRuntimeState(player.getUUID());
            NetworkHandler.clearPlayerRuntimeState(legacy.getUUID());
            cleanupPlayer(player);
            cleanupPlayer(legacy);
        };
        try {
            players.put(player.getUUID(), player);
            var source = player.createCommandSourceStack().withPermission(0).withSuppressedOutput();
            server.getCommands().performPrefixedCommand(source, "stage gui");
            helper.assertTrue(capture.opens == 1 && capture.responses == 1,
                "An explicit command must open the screen and send its first data response.");
            server.getCommands().performPrefixedCommand(source, "pstages");
            helper.assertTrue(capture.opens == 2 && capture.responses == 1,
                "An explicit alias must reopen immediately while data remains queued.");
            var scripts = new com.enviouse.progressivestages.compat.kubejs.PSKubeBindings();
            scripts.openGui(player);
            helper.assertTrue(capture.opens == 3 && capture.responses == 1,
                "An explicit script call must reopen immediately while data remains queued.");
            var request = NetworkHandler.class.getDeclaredMethod("handleRequestStageGuiServer",
                NetworkHandler.RequestStageGuiPayload.class, IPayloadContext.class);
            request.setAccessible(true);
            request.invoke(null, NetworkHandler.RequestStageGuiPayload.INSTANCE, context(player));
            var purchase = NetworkHandler.class.getDeclaredMethod("handlePurchaseServer",
                NetworkHandler.RequestPurchasePayload.class, IPayloadContext.class);
            purchase.setAccessible(true);
            purchase.invoke(null, new NetworkHandler.RequestPurchasePayload(
                StageId.parse("progressivestages:missing_gui_test").getResourceLocation()), context(player));
            helper.assertTrue(capture.opens == 3,
                "A data request or purchase response must not issue another screen opening.");
            server.getCommands().performPrefixedCommand(
                legacy.createCommandSourceStack().withPermission(0).withSuppressedOutput(), "stages");
            helper.assertTrue(legacyCapture.opens == 0 && legacyCapture.responses == 1,
                "A peer without the optional channel must receive only the legacy data payload.");
            NetworkHandler.clearPlayerRuntimeState(legacy.getUUID());
            scripts.openGui(legacy);
            helper.assertTrue(legacyCapture.opens == 0 && legacyCapture.responses == 2,
                "A script call must preserve legacy opening without an unsupported packet.");
            helper.runAfterDelay(25, () -> {
                try {
                    helper.assertTrue(capture.responses == 2 && capture.opens == 3,
                        "Queued data must refresh the view without repeating the opening instruction.");
                    helper.succeed();
                } finally { cleanup.run(); }
            });
        } catch (Exception | AssertionError error) {
            cleanup.run();
            throw error;
        }
    }

    private static FakePlayer player(GameTestHelper helper, Capture capture) {
        var profile = new GameProfile(UUID.randomUUID(), "gui_response");
        var player = new FakePlayer(helper.getLevel(), profile);
        player.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(),
            new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false)) {
            @Override public boolean hasChannel(net.minecraft.resources.ResourceLocation channel) {
                return channel.equals(NetworkHandler.OpenStageGuiPayload.TYPE.id()) && capture.explicitOpening;
            }
            @Override public void send(Packet<?> packet) {
                if (packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof NetworkHandler.OpenStageGuiPayload) capture.opens++;
                if (packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof NetworkHandler.StageGuiDataPayload data) {
                    capture.responses++;
                    capture.last = data;
                }
            }
        };
        return player;
    }

    private static void cleanupPlayer(ServerPlayer player) {
        player.discard();
    }

    private static IPayloadContext context(ServerPlayer player) {
        return (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
            new Class<?>[] {IPayloadContext.class}, (proxy, method, arguments) -> switch (method.getName()) {
                case "player" -> player;
                case "enqueueWork" -> {
                    ((Runnable) arguments[0]).run();
                    yield CompletableFuture.completedFuture(null);
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static final class Capture {
        int responses;
        int opens;
        boolean explicitOpening;
        NetworkHandler.StageGuiDataPayload last;
    }
}
