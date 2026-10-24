package com.enviouse.progressivestages.server.triggers;

import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.config.StageGameModeGrant;
import com.enviouse.progressivestages.common.data.StageAttachments;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@GameTestHolder("progressivestages")
@PrefixGameTestTemplate(false)
public final class GameModeStageGrantGameTests {
    private GameModeStageGrantGameTests() {}

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void modeChangesWithdrawOnlyTheirOwnStageSource(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.getPlayerList().getPlayers().isEmpty(), "Requires an isolated test server");
        StageId id = StageId.parse("progressivestages:gamemode_grant_fixture");
        StageOrder order = StageOrder.getInstance();
        helper.assertTrue(!order.stageExists(id), "Fixture stage ID must be unused");
        var previous = order.getOrderedStages().stream().map(stage -> order.getStageDefinition(stage).orElseThrow()).toList();
        UUID subject = new UUID(0x6A6DL, 0x324L);
        var actor = new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(),
            new GameProfile(subject, "mode-test"));
        var data = server.overworld().getData(StageAttachments.TEAM_STAGES);
        var manager = StageManager.getInstance();
        try {
            StageDefinition definition = StageDefinition.builder(id).teamStage(false)
                .gameModeGrant(new StageGameModeGrant(Set.of(GameType.CREATIVE))).build();
            order.clear();
            order.registerStage(definition);
            GameModeStageGrants.rebuild(List.of(definition));
            actor.setGameMode(GameType.SURVIVAL);
            GameModeStageGrants.reconcile(actor);
            helper.assertTrue(!manager.hasStage(actor, id), "Survival should not grant the stage");
            actor.setGameMode(GameType.CREATIVE);
            GameModeStageGrants.reconcile(actor);
            helper.assertTrue(manager.getStageSources(actor, id).contains("gamemode"),
                "Creative should add the game mode source");
            manager.grantStageFromSource(actor, id, "independent", StageCause.API);
            actor.setGameMode(GameType.SURVIVAL);
            GameModeStageGrants.reconcile(actor);
            helper.assertTrue(manager.hasStage(actor, id)
                && manager.getStageSources(actor, id).equals(Set.of("independent")),
                "Leaving creative must preserve the independent source");
            actor.setGameMode(GameType.CREATIVE);
            GameModeStageGrants.reconcile(actor);
            helper.assertTrue(manager.getStageSources(actor, id).equals(Set.of("independent", "gamemode")),
                "Entering creative must also add its source to an independently owned stage");
            helper.succeed();
        } finally {
            data.revokePersonalStage(subject, id);
            order.clear();
            previous.forEach(order::registerStage);
            GameModeStageGrants.rebuild(previous);
            actor.discard();
        }
    }
}
