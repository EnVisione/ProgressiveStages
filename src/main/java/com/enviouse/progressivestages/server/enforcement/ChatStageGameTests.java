package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageChatOptions;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.data.StageAttachments;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.server.ServerEventHandler;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder("progressivestages")
@PrefixGameTestTemplate(false)
public final class ChatStageGameTests {
    private ChatStageGameTests() {}

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void playerChatRequiresTheConfiguredStage(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.getPlayerList().getPlayers().isEmpty(), "Requires an isolated test server");
        StageId id = StageId.parse("progressivestages:chat_gate_fixture");
        StageOrder order = StageOrder.getInstance();
        helper.assertTrue(!order.stageExists(id), "Fixture stage ID must be unused");
        var previous = order.getOrderedStages().stream().map(stage -> order.getStageDefinition(stage).orElseThrow()).toList();
        UUID subject = new UUID(0xC4A7L, 0x324L);
        var actor = new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(),
            new GameProfile(subject, "chat-test"));
        var data = server.overworld().getData(StageAttachments.TEAM_STAGES);
        try {
            actor.setGameMode(GameType.SURVIVAL);
            order.clear();
            order.registerStage(StageDefinition.builder(id).teamStage(false)
                .chat(new StageChatOptions(true, List.of("Create"))).build());
            ServerChatEvent denied = new ServerChatEvent(actor, "hello", Component.literal("hello"));
            ServerEventHandler.onPlayerChat(denied);
            helper.assertTrue(denied.isCanceled(), "Chat must be denied before the stage is owned");
            StageManager.getInstance().grantStageFromSource(actor, id, "independent", StageCause.API);
            ServerChatEvent allowed = new ServerChatEvent(actor, "hello", Component.literal("hello"));
            ServerEventHandler.onPlayerChat(allowed);
            helper.assertTrue(!allowed.isCanceled(), "Chat must be allowed once the stage is owned");
            helper.succeed();
        } finally {
            data.revokePersonalStage(subject, id);
            order.clear();
            previous.forEach(order::registerStage);
            actor.discard();
        }
    }
}
