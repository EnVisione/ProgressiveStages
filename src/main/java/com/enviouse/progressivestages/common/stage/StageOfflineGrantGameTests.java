package com.enviouse.progressivestages.common.stage;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageActorChangeEvent;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.config.StageCost;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.config.StageRewards;
import com.enviouse.progressivestages.common.data.StageAttachments;
import com.enviouse.progressivestages.common.data.TeamStageData;
import com.enviouse.progressivestages.common.team.TeamProvider;
import com.enviouse.progressivestages.server.triggers.StageRegressionData;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder("progressivestages")
@PrefixGameTestTemplate(false)
public final class StageOfflineGrantGameTests {
    private StageOfflineGrantGameTests() {}

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", batch = "progressivestages_isolated_offline_personal_grants")
    public static void offlinePersonalAcquisitionsDeliverOriginalRewardsOnce(GameTestHelper helper) throws Exception {
        verifyGrant(helper, OwnerKind.PERSONAL, false);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", batch = "progressivestages_isolated_offline_team_grants")
    public static void offlineTeamAcquisitionsPreserveTheRewardActor(GameTestHelper helper) throws Exception {
        verifyGrant(helper, OwnerKind.TEAM, false);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", batch = "progressivestages_isolated_offline_server_grants")
    public static void offlineServerAcquisitionsPreserveTheRewardActor(GameTestHelper helper) throws Exception {
        verifyGrant(helper, OwnerKind.SERVER, false);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", batch = "progressivestages_isolated_offline_prerequisites")
    public static void offlinePrerequisitesResolveTheirOwnOwners(GameTestHelper helper) throws Exception {
        verifyGrant(helper, OwnerKind.TEAM, true);
    }

    @SuppressWarnings("unchecked")
    private static void verifyGrant(GameTestHelper helper, OwnerKind kind, boolean linear) throws Exception {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.getPlayerList().getPlayers().isEmpty(), "Offline grants require an isolated server.");
        UUID actor = new UUID(0x5738, 1), other = new UUID(0x5738, 2);
        var lookup = PlayerList.class.getDeclaredField("playersByUUID");
        lookup.setAccessible(true);
        var players = (Map<UUID, ServerPlayer>) lookup.get(server.getPlayerList());
        var onlineLookup = PlayerList.class.getDeclaredField("players");
        onlineLookup.setAccessible(true);
        var onlinePlayers = (List<ServerPlayer>) onlineLookup.get(server.getPlayerList());
        helper.assertTrue(!players.containsKey(actor) && !players.containsKey(other), "Fixture actors must be unused.");
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new GameProfile(actor, "offline_earner"), false);
        var first = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        net.luckperms.api.LuckPermsProvider.get().getUserManager().loadUser(actor, "offline_earner").join();
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        first.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server,
            connection, first, cookie);
        var second = new FakePlayer(helper.getLevel(), new GameProfile(other, "offline-member"));
        var order = StageOrder.getInstance();
        var definitions = order.getOrderedStages().stream().map(id -> order.getStageDefinition(id).orElseThrow()).toList();
        var original = server.overworld().getData(StageAttachments.TEAM_STAGES);
        var provider = TeamProvider.getInstance();
        var integrationField = TeamProvider.class.getDeclaredField("ftbIntegration");
        integrationField.setAccessible(true);
        var integration = integrationField.get(provider);
        var linearField = StageConfig.class.getDeclaredField("linearProgression");
        linearField.setAccessible(true);
        boolean previousLinear = linearField.getBoolean(null);
        var manager = StageManager.getInstance();
        var prerequisite = StageId.parse("progressivestages:offline_qualification");
        var root = StageId.parse("progressivestages:offline_acquisition");
        var owner = new OwnerRef(kind, kind == OwnerKind.SERVER ? StageManager.SERVER_TEAM : actor);
        var personal = new OwnerRef(OwnerKind.PERSONAL, actor);
        var rewards = new StageRewards(List.of(new StageCost.ItemCost(ResourceLocation.parse("minecraft:bread"), 4)),
            List.of(new StageRewards.EffectReward(ResourceLocation.parse("minecraft:speed"), 1200, 1)),
            List.of("experience add offline_earner 1 levels"), "1 180 1", 5, 0);
        var events = new ArrayList<StageActorChangeEvent>();
        Consumer<StageActorChangeEvent> listener = (StageActorChangeEvent event) -> {
            if (linear && event.getChangeType() == com.enviouse.progressivestages.common.api.StageChangeType.GRANTED) {
                helper.assertTrue(StageRegressionData.get(server).getGrantTime(owner, root) > 0
                    && StageRegressionData.get(server).getGrantTime(personal, prerequisite) > 0,
                    "An acquisition observer must see every prerequisite and target clock already committed.");
            }
            events.add(event);
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            linearField.setBoolean(null, linear);
            integrationField.set(provider, new TeamProvider.ITeamIntegration() {
                @Override public UUID getTeamId(ServerPlayer player) { return actor; }
                @Override public Set<ServerPlayer> getTeamMembers(UUID team, ServerPlayer player) { return Set.of(); }
                @Override public Optional<UUID> getOfflineTeamId(UUID subject) { return Optional.of(actor); }
            });
            order.clear();
            order.registerStage(StageDefinition.builder(prerequisite).teamStage(false).build());
            var definition = StageDefinition.builder(root).dependencies(List.of(prerequisite)).rewards(rewards);
            if (kind == OwnerKind.SERVER) definition.scope("server"); else definition.teamStage(kind == OwnerKind.TEAM);
            order.registerStage(definition.build());
            var data = new TeamStageData();
            data.grantPersonalStage(other, prerequisite);
            server.overworld().setData(StageAttachments.TEAM_STAGES, data);
            var occupied = StageId.parse("progressivestages:offline_occupied_slot");
            var deniedSlot = StageId.parse("progressivestages:offline_denied_slot");
            order.registerStage(StageDefinition.builder(occupied).teamStage(false).slotGroup("offline_fixture").slotLimit(1).build());
            order.registerStage(StageDefinition.builder(deniedSlot).teamStage(false).slotGroup("offline_fixture").slotLimit(1).rewards(rewards).build());
            data.grantPersonalStage(actor, occupied);
            var beforeSlot = TeamStageData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
            var slotResult = ProgressiveStagesAPI.mutateStage(ProgressiveStagesAPI.resolveActorOwner(actor, deniedSlot),
                deniedSlot, StageOperation.GRANT, StageCause.COMMAND);
            helper.assertTrue(!slotResult.changed() && slotResult.reason().equals("slot_denied")
                && events.isEmpty() && data.getPendingRewards(actor).isEmpty()
                && beforeSlot.equals(TeamStageData.CODEC.encodeStart(NbtOps.INSTANCE, server.overworld().getData(StageAttachments.TEAM_STAGES)).getOrThrow()),
                "A denied offline slot must not install partial ownership or reserve acquisition effects.");
            var context = ProgressiveStagesAPI.resolveActorOwner(actor, root);
            if (!linear) {
                var denied = ProgressiveStagesAPI.mutateStage(context, root, StageOperation.GRANT, StageCause.COMMAND);
                helper.assertTrue(!denied.changed() && denied.reason().equals("dependency_denied")
                    && data.getPendingRewards(actor).isEmpty() && events.isEmpty(),
                    "Another member's personal prerequisite cannot qualify an offline actor or reserve a reward.");
                data.grantPersonalStage(actor, prerequisite);
                String derived = new PermissionStageSource(actor, "offline", true).label();
                if (kind == OwnerKind.PERSONAL) data.grantPersonalStageFromSource(actor, root, derived);
                else data.grantStageFromSource(owner.id(), root, derived);
            }
            var result = ProgressiveStagesAPI.mutateStage(context, root, StageOperation.GRANT, StageCause.COMMAND);
            data = server.overworld().getData(StageAttachments.TEAM_STAGES);
            helper.assertTrue(result.changed() && result.affectedOwners().contains(owner)
                && data.getSources(owner, root).contains("independent") && data.getPendingRewards(actor).size() == 1,
                "Independent earning must commit the stage and one actor receipt even when derived access already exists.");
            helper.assertTrue(data.hasPersonalStage(actor, prerequisite) && !data.hasStage(actor, prerequisite),
                "Automatic prerequisites must use their own personal owner.");
            long grantTime = StageRegressionData.get(server).getGrantTime(owner, root);
            helper.assertTrue(grantTime > 0 && first.experienceLevel == 0 && first.getInventory().countItem(Items.BREAD) == 0,
                "The grant clock starts immediately while rewards wait for the offline actor.");
            int eventCount = events.size();
            var repeated = ProgressiveStagesAPI.mutateStage(context, root, StageOperation.GRANT, StageCause.COMMAND);
            helper.assertTrue(!repeated.changed() && events.size() == eventCount && data.getPendingRewards(actor).size() == 1
                && StageRegressionData.get(server).getGrantTime(owner, root) == grantTime, "Duplicate grants cannot refresh clocks or reward receipts.");
            var saved = TeamStageData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
            data = TeamStageData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
            server.overworld().setData(StageAttachments.TEAM_STAGES, data);
            order.clear();
            order.registerStage(StageDefinition.builder(prerequisite).teamStage(false).build());
            order.registerStage(StageDefinition.builder(root).teamStage(false)
                .rewards(new StageRewards(List.of(), List.of(), List.of(), "", 100, 0)).build());
            manager.syncStagesOnLogin(second);
            helper.assertTrue(second.experienceLevel == 0 && second.getInventory().countItem(Items.BREAD) == 0
                && data.getPendingRewards(actor).size() == 1, "A teammate cannot consume the actor's reserved acquisition.");
            onlinePlayers.add(first);
            players.put(actor, first);
            manager.syncStagesOnLogin(first);
            helper.assertTrue(first.experienceLevel == 6 && first.getInventory().countItem(Items.BREAD) == 4
                && first.hasEffect(MobEffects.MOVEMENT_SPEED) && first.position().distanceToSqr(1, 180, 1) < 0.01
                && data.getPendingRewards(actor).isEmpty(),
                "The returning actor receives the original items effect teleport experience and command once. Observed "
                    + first.experienceLevel + " levels and " + first.getInventory().countItem(Items.BREAD) + " bread at " + first.position());
            manager.syncStagesOnLogin(first);
            var consumed = TeamStageData.CODEC.parse(NbtOps.INSTANCE,
                TeamStageData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow()).getOrThrow();
            server.overworld().setData(StageAttachments.TEAM_STAGES, consumed);
            manager.syncStagesOnLogin(first);
            helper.assertTrue(first.experienceLevel == 6 && first.getInventory().countItem(Items.BREAD) == 4
                && events.size() == eventCount && StageRegressionData.get(server).getGrantTime(owner, root) == grantTime,
                "Repeat login and persisted consumed receipts must not replay acquisition events rewards or expiry.");
            helper.succeed();
        } finally {
            players.remove(actor, first);
            onlinePlayers.remove(first);
            var user = net.luckperms.api.LuckPermsProvider.get().getUserManager().getUser(actor);
            if (user != null) net.luckperms.api.LuckPermsProvider.get().getUserManager().cleanupUser(user);
            players.remove(other, second);
            first.discard();
            second.discard();
            NeoForge.EVENT_BUS.unregister(listener);
            StageRegressionData.get(server).clear(owner, root);
            StageRegressionData.get(server).clear(personal, root);
            StageRegressionData.get(server).clear(personal, prerequisite);
            integrationField.set(provider, integration);
            linearField.setBoolean(null, previousLinear);
            server.overworld().setData(StageAttachments.TEAM_STAGES, original);
            order.clear();
            definitions.forEach(order::registerStage);
        }
    }
}
