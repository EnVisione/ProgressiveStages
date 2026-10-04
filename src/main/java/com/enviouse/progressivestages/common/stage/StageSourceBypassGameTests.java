package com.enviouse.progressivestages.common.stage;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.data.StageAttachments;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpListEntry;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@GameTestHolder("progressivestages")
@PrefixGameTestTemplate(false)
public final class StageSourceBypassGameTests {
    private static final StageId PREREQUISITE = StageId.parse("progressivestages:source_bypass_prerequisite");
    private static final StageId TARGET = StageId.parse("progressivestages:source_bypass_target");
    private static final String SOURCE = "progressivequests:source_bypass_fixture";

    private StageSourceBypassGameTests() {}

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    @SuppressWarnings("unchecked")
    public static void privilegedSourceBypassChangesOnlyItsTargetAndRetainsAuditContext(GameTestHelper helper)
            throws Exception {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.getPlayerList().getPlayers().isEmpty(), "The fixture requires an isolated server.");
        var playersByIdField = PlayerList.class.getDeclaredField("playersByUUID");
        playersByIdField.setAccessible(true);
        var players = (Map<UUID, ServerPlayer>) playersByIdField.get(server.getPlayerList());
        UUID actorId = new UUID(0x53544259L, 1L);
        helper.assertTrue(!players.containsKey(actorId), "The fixture actor identity must be unused.");

        var order = StageOrder.getInstance();
        var previousStages = order.getOrderedStages().stream()
            .map(id -> order.getStageDefinition(id).orElseThrow()).toList();
        var originalData = server.overworld().getData(StageAttachments.TEAM_STAGES);
        var manager = StageManager.getInstance();
        var publications = new ArrayList<StageMutationResult>();
        var player = new FakePlayer(helper.getLevel(), new GameProfile(actorId, "source-bypass-test"));
        helper.assertTrue(!order.stageExists(PREREQUISITE) && !order.stageExists(TARGET),
            "Fixture stage definitions must be unused.");

        try {
            server.overworld().setData(StageAttachments.TEAM_STAGES, originalData.copy());
            order.clear();
            previousStages.forEach(order::registerStage);
            order.registerStage(StageDefinition.builder(PREREQUISITE).teamStage(false).build());
            order.registerStage(StageDefinition.builder(TARGET).teamStage(false).dependencies(List.of(PREREQUISITE)).build());
            players.put(actorId, player);

            try (var subscription = manager.subscribeCommittedStageChanges(publications::add)) {
                long initialRevision = manager.getMutationRevision();
                helper.assertTrue(server.getProfilePermissions(player.getGameProfile()) < 3,
                    "The fixture actor must begin unprivileged.");
                helper.assertTrue(!ProgressiveStagesAPI.grantStageFromSourceWithDependencyBypass(
                        player, TARGET, SOURCE, "Approved test override", StageCause.QUEST_REWARD),
                    "A non-operator must not use the dependency bypass.");
                helper.assertTrue(manager.getMutationRevision() == initialRevision && publications.isEmpty(),
                    "Denied force must not mutate or publish state.");

                server.getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(), 3, false));
                helper.assertTrue(server.getProfilePermissions(player.getGameProfile()) >= 3,
                    "The fixture actor must receive level-3 operator authority.");
                helper.assertTrue(!ProgressiveStagesAPI.grantStageFromSourceWithDependencyBypass(
                        player, TARGET, SOURCE + " ", "Approved test override", StageCause.QUEST_REWARD),
                    "A source that storage would normalize must be rejected to preserve audit lineage.");
                helper.assertTrue(manager.getMutationRevision() == initialRevision && publications.isEmpty(),
                    "A non-canonical source must not mutate or publish state.");
                String oversizedSource = SOURCE + "x".repeat(257 - SOURCE.length());
                helper.assertTrue(!ProgressiveStagesAPI.grantStageFromSourceWithDependencyBypass(
                        player, TARGET, oversizedSource, "Approved test override", StageCause.QUEST_REWARD),
                    "A source identity longer than 256 characters must be rejected.");
                helper.assertTrue(manager.getMutationRevision() == initialRevision && publications.isEmpty(),
                    "An oversized source must not mutate or publish state.");
                helper.assertTrue(!ProgressiveStagesAPI.grantStageFromSourceWithDependencyBypass(
                        player, TARGET, SOURCE, "x".repeat(257), StageCause.QUEST_REWARD),
                    "An audit reason longer than 256 characters must be rejected.");
                helper.assertTrue(manager.getMutationRevision() == initialRevision && publications.isEmpty(),
                    "An oversized audit reason must not mutate or publish state.");
                helper.assertTrue(!ProgressiveStagesAPI.grantStageFromSourceWithDependencyBypass(
                        player, TARGET, SOURCE, "\ninvalid audit entry", StageCause.QUEST_REWARD),
                    "A reason containing control characters must be rejected.");
                helper.assertTrue(manager.getMutationRevision() == initialRevision && publications.isEmpty(),
                    "Invalid audit context must not mutate or publish state.");
                String bidiAuditReason = "Invalid " + (char) 0x202E + " audit entry";
                helper.assertTrue(!ProgressiveStagesAPI.grantStageFromSourceWithDependencyBypass(
                        player, TARGET, SOURCE, bidiAuditReason, StageCause.QUEST_REWARD),
                    "An audit reason containing bidi format controls must be rejected.");
                helper.assertTrue(manager.getMutationRevision() == initialRevision && publications.isEmpty(),
                    "Invalid audit display text must not mutate or publish state.");

                helper.assertTrue(ProgressiveStagesAPI.grantStageFromSourceWithDependencyBypass(
                        player, TARGET, SOURCE, "Approved quest dependency override", StageCause.QUEST_REWARD),
                    "An operator must be able to grant the one source-attributed target despite its prerequisite.");
                var owner = ProgressiveStagesAPI.getStageOwner(player, TARGET);
                var data = server.overworld().getData(StageAttachments.TEAM_STAGES);
                helper.assertTrue(manager.hasStage(player, TARGET), "The target stage must be granted.");
                helper.assertTrue(!manager.hasStage(player, PREREQUISITE), "The prerequisite must remain untouched.");
                helper.assertTrue(data.getSources(owner, TARGET).equals(Set.of(SOURCE)),
                    "The grant must retain only its exact source and must not become independent ownership.");
                helper.assertTrue(!manager.hasIndependentStage(player, TARGET),
                    "A source grant must not become independent ownership.");
                helper.assertTrue(manager.getMutationRevision() == initialRevision + 1,
                    "The source grant must commit as one mutation.");
                helper.assertTrue(publications.size() == 1 && publications.getFirst().changed()
                        && publications.getFirst().reason().equals("source_added_dependency_bypass"),
                    "The committed mutation must publish one source-bypass audit reason.");

                long committedRevision = manager.getMutationRevision();
                helper.assertTrue(!ProgressiveStagesAPI.grantStageFromSourceWithDependencyBypass(
                        player, TARGET, SOURCE, "Approved quest dependency override", StageCause.QUEST_REWARD),
                    "Repeating the same source grant must be idempotent.");
                helper.assertTrue(manager.getMutationRevision() == committedRevision && publications.size() == 1,
                    "An idempotent replay must not commit or publish a second mutation.");
            }
            helper.succeed();
        } finally {
            server.getPlayerList().getOps().remove(player.getGameProfile());
            players.remove(actorId, player);
            player.discard();
            server.overworld().setData(StageAttachments.TEAM_STAGES, originalData);
            order.clear();
            previousStages.forEach(order::registerStage);
        }
    }
}
