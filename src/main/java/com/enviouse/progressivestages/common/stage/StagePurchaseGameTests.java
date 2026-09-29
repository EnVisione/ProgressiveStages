package com.enviouse.progressivestages.common.stage;

import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageCost;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.data.StageAttachments;
import com.enviouse.progressivestages.common.team.TeamProvider;
import com.enviouse.progressivestages.common.network.NetworkHandler;
import com.enviouse.progressivestages.server.triggers.StagePurchaseData;
import com.enviouse.progressivestages.server.triggers.StageRegressionData;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@GameTestHolder("progressivestages")
@PrefixGameTestTemplate(false)
public final class StagePurchaseGameTests {
    private StagePurchaseGameTests() {}

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    @SuppressWarnings("unchecked")
    public static void rewardRevocationUsesTheCommittedPurchaseReceipt(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.getPlayerList().getPlayers().isEmpty(), "Purchases require an isolated fixture server.");
        UUID actorId = new UUID(0x5735, 5);
        var actor = new FakePlayer(helper.getLevel(), new GameProfile(actorId, "purchase_reward"));
        var lookup = PlayerList.class.getDeclaredField("playersByUUID");
        lookup.setAccessible(true);
        var players = (Map<UUID, ServerPlayer>) lookup.get(server.getPlayerList());
        helper.assertTrue(!players.containsKey(actorId), "The purchase actor must be unused.");
        var order = StageOrder.getInstance();
        var definitions = order.getOrderedStages().stream().map(id -> order.getStageDefinition(id).orElseThrow()).toList();
        var original = server.overworld().getData(StageAttachments.TEAM_STAGES);
        var originalPurchases = StagePurchaseData.get(server);
        var stage = StageId.parse("progressivestages:purchase_reward_revoke");
        var owner = new OwnerRef(OwnerKind.PERSONAL, actorId);
        var cooldownField = NetworkHandler.class.getDeclaredField("lastPurchase");
        cooldownField.setAccessible(true);
        var cooldowns = (Map<UUID, Long>) cooldownField.get(null);
        helper.assertTrue(!cooldowns.containsKey(actorId), "The purchase fixture cooldown must be unused.");
        var clocks = StageRegressionData.get(server);
        helper.assertTrue(clocks.getGrantTime(owner, stage) < 0, "The purchase fixture clock must be unused.");
        var manager = StageManager.getInstance();
        try {
            var parsed = com.enviouse.progressivestages.server.loader.StageFileParser.parseText("""
                [stage]
                id = "progressivestages:purchase_reward_revoke"
                team_stage = false
                [cost]
                xp_levels = 10
                items = ["minecraft:bread:4"]
                bypass_requirements = true
                cooldown_seconds = 60
                refund_percent = 50
                [rewards]
                items = ["minecraft:diamond:1"]
                commands = ["stage revoke @s progressivestages:purchase_reward_revoke"]
                """, "purchase_reward_revoke.toml", "purchase fixture", false);
            helper.assertTrue(parsed.isSuccess(), "The reward revocation fixture must parse.");
            order.clear();
            order.registerStage(parsed.getStageDefinition());
            server.overworld().setData(StageAttachments.TEAM_STAGES, original.copy());
            server.overworld().getDataStorage().set("progressivestages_purchases", new StagePurchaseData());
            players.put(actorId, actor);
            actor.giveExperienceLevels(20);
            actor.getInventory().add(new ItemStack(Items.BREAD, 8));
            purchase(actor, stage);
            helper.assertTrue(!manager.hasStage(actor, stage) && actor.getInventory().countItem(Items.DIAMOND) == 1,
                "The configured reward command must revoke the granted stage after its item reward.");
            helper.assertTrue(actor.experienceLevel == 15 && actor.getInventory().countItem(Items.BREAD) == 6,
                "A reward revocation must refund only the purchased share. Actual levels "
                    + actor.experienceLevel + " and bread " + actor.getInventory().countItem(Items.BREAD) + ".");
            helper.assertTrue(StagePurchaseData.get(server).getActorPurchase(owner, stage).isEmpty()
                && StagePurchaseData.get(server).getPendingActorRefunds(actorId).isEmpty(),
                "The reward command must consume the purchase receipt without leaving a later refund.");
            purchase(actor, stage);
            manager.revokeStageWithCause(actor, stage, StageCause.COMMAND);
            helper.assertTrue(actor.experienceLevel == 15 && actor.getInventory().countItem(Items.BREAD) == 6
                && actor.getInventory().countItem(Items.DIAMOND) == 1,
                "The completed purchase must keep its cooldown and must not repeat rewards or refunds.");
            helper.succeed();
        } finally {
            players.remove(actorId, actor);
            actor.discard();
            NetworkHandler.clearPlayerRuntimeState(actorId);
            cooldowns.remove(actorId);
            clocks.clear(owner, stage);
            server.overworld().getDataStorage().set("progressivestages_purchases", originalPurchases);
            server.overworld().setData(StageAttachments.TEAM_STAGES, original);
            order.clear();
            definitions.forEach(order::registerStage);
        }
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void refundsReturnOnlyToThePayerAcrossOfflineDelivery(GameTestHelper helper) throws Exception {
        verifyRefunds(helper, false);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void serverPurchasesPreservePayersAndPersonalHistory(GameTestHelper helper) throws Exception {
        verifyRefunds(helper, true);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    @SuppressWarnings("unchecked")
    public static void purchasesCreateIndependentOwnershipWithoutConsumingTemporaryAccess(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.getPlayerList().getPlayers().isEmpty(), "Purchases require an isolated fixture server.");
        UUID actorId = new UUID(0x5735, 4);
        var actor = new FakePlayer(helper.getLevel(), new GameProfile(actorId, "purchase-lease"));
        var lookup = PlayerList.class.getDeclaredField("playersByUUID");
        lookup.setAccessible(true);
        var players = (Map<UUID, ServerPlayer>) lookup.get(server.getPlayerList());
        helper.assertTrue(!players.containsKey(actorId), "The purchase actor must be unused.");
        var order = StageOrder.getInstance();
        var definitions = order.getOrderedStages().stream().map(id -> order.getStageDefinition(id).orElseThrow()).toList();
        var original = server.overworld().getData(StageAttachments.TEAM_STAGES);
        var originalPurchases = StagePurchaseData.get(server);
        var stage = StageId.parse("progressivestages:purchase_with_lease");
        var prerequisite = StageId.parse("progressivestages:purchase_lease_prerequisite");
        var owner = new OwnerRef(OwnerKind.PERSONAL, actorId);
        var clocks = StageRegressionData.get(server);
        helper.assertTrue(clocks.getGrantTime(owner, stage) < 0 && clocks.getGrantTime(owner, prerequisite) < 0,
            "The purchase fixture clocks must be unused.");
        var cost = new StageCost(10, List.of(new StageCost.ItemCost(ResourceLocation.parse("minecraft:bread"), 4)), true, 0, 50);
        var reward = new com.enviouse.progressivestages.common.config.StageRewards(
            List.of(new StageCost.ItemCost(ResourceLocation.parse("minecraft:diamond"), 1)), List.of(), List.of(), "", 0, 0);
        var manager = StageManager.getInstance();
        try {
            order.clear();
            order.registerStage(StageDefinition.builder(prerequisite).teamStage(false).build());
            order.registerStage(StageDefinition.builder(stage).teamStage(false).addDependency(prerequisite)
                .cost(cost).rewards(reward).build());
            server.overworld().setData(StageAttachments.TEAM_STAGES, original.copy());
            server.overworld().getDataStorage().set("progressivestages_purchases", new StagePurchaseData());
            players.put(actorId, actor);
            actor.giveExperienceLevels(20);
            actor.getInventory().add(new ItemStack(Items.BREAD, 8));
            helper.assertTrue(manager.grantTemporaryStage(actor, stage, StageCause.STRUCTURE_ENTER)
                && manager.hasStage(actor, stage) && !manager.hasIndependentStage(actor, stage),
                "The actor must begin with temporary access and no independent ownership.");
            helper.assertTrue(purchaseOffer(actor, stage).purchasable() && !purchaseOffer(actor, stage).canPurchase(),
                "Temporary access must retain a disabled offer while prerequisites are missing.");
            purchase(actor, stage);
            helper.assertTrue(!manager.hasIndependentStage(actor, stage) && actor.experienceLevel == 20
                && actor.getInventory().countItem(Items.BREAD) == 8
                && StagePurchaseData.get(server).getActorPurchase(owner, stage).isEmpty(),
                "Temporary access must not bypass purchase prerequisites or incur a partial charge.");
            manager.grantStageWithCause(actor, prerequisite, StageCause.COMMAND);
            actor.giveExperienceLevels(-15);
            helper.assertTrue(purchaseOffer(actor, stage).purchasable() && !purchaseOffer(actor, stage).canPurchase(),
                "The offer must remain visible but disabled when the actor cannot afford it.");
            purchase(actor, stage);
            helper.assertTrue(!manager.hasIndependentStage(actor, stage) && actor.experienceLevel == 5
                && actor.getInventory().countItem(Items.BREAD) == 8
                && StagePurchaseData.get(server).getActorPurchase(owner, stage).isEmpty(),
                "Temporary access must not bypass affordability or create a receipt.");
            actor.giveExperienceLevels(15);
            helper.assertTrue(purchaseOffer(actor, stage).purchasable() && purchaseOffer(actor, stage).canPurchase(),
                "A qualified actor must receive an enabled offer despite temporary access.");
            purchase(actor, stage);
            helper.assertTrue(manager.hasIndependentStage(actor, stage),
                "A qualified purchase must earn independent ownership while temporary access is active.");
            helper.assertTrue(actor.experienceLevel == 10 && actor.getInventory().countItem(Items.BREAD) == 4
                && actor.getInventory().countItem(Items.DIAMOND) == 1,
                "Independent acquisition must charge once and give its ordinary reward once.");
            var data = server.overworld().getData(StageAttachments.TEAM_STAGES);
            helper.assertTrue(data.getSources(owner, stage).equals(Set.of("temporary", "independent")),
                "Purchasing must retain the separate temporary source.");
            var receipt = StagePurchaseData.get(server).getActorPurchase(owner, stage).orElseThrow();
            helper.assertTrue(receipt.payer().equals(actorId) && receipt.owner().equals(owner)
                && receipt.stage().equals(stage) && receipt.cost().xpLevels() == cost.xpLevels()
                && receipt.cost().items().equals(cost.items())
                && receipt.cost().refundPercent() == cost.refundPercent(),
                "The purchase must retain its initiating player and exact refund terms.");
            helper.assertTrue(!purchaseOffer(actor, stage).purchasable(),
                "The GUI must stop offering a stage after independent acquisition.");
            purchase(actor, stage);
            helper.assertTrue(actor.experienceLevel == 10 && actor.getInventory().countItem(Items.BREAD) == 4
                && actor.getInventory().countItem(Items.DIAMOND) == 1
                && StagePurchaseData.get(server).getActorPurchase(owner, stage).orElseThrow().equals(receipt),
                "A duplicate purchase must not charge, reward or replace its receipt.");
            helper.assertTrue(manager.revokeTemporaryStage(actor, stage, StageCause.STRUCTURE_LEAVE)
                && manager.hasIndependentStage(actor, stage) && manager.hasStage(actor, stage)
                && data.getSources(owner, stage).equals(Set.of("independent")),
                "Leaving temporary access must preserve purchased ownership.");
            helper.assertTrue(actor.experienceLevel == 10 && actor.getInventory().countItem(Items.BREAD) == 4,
                "Removing the lease must not refund an independent purchase.");
            manager.revokeStageWithCause(actor, stage, StageCause.COMMAND);
            helper.assertTrue(!manager.hasStage(actor, stage) && actor.experienceLevel == 15
                && actor.getInventory().countItem(Items.BREAD) == 6
                && actor.getInventory().countItem(Items.DIAMOND) == 1,
                "Explicit revocation must apply the purchased refund once without repeating its reward.");
            helper.assertTrue(purchaseOffer(actor, stage).purchasable() && purchaseOffer(actor, stage).canPurchase(),
                "Revocation must restore the ordinary purchase offer.");
            helper.succeed();
        } finally {
            players.remove(actorId, actor);
            actor.discard();
            NetworkHandler.clearPlayerRuntimeState(actorId);
            clocks.clear(owner, stage);
            clocks.clear(owner, prerequisite);
            server.overworld().getDataStorage().set("progressivestages_purchases", originalPurchases);
            server.overworld().setData(StageAttachments.TEAM_STAGES, original);
            order.clear();
            definitions.forEach(order::registerStage);
        }
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    @SuppressWarnings("unchecked")
    public static void repeatedItemCostsRequireTheFullPaymentBeforeAnyMutation(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.getPlayerList().getPlayers().isEmpty(), "Purchases require an isolated fixture server.");
        UUID actorId = new UUID(0x5735, 3);
        var actor = new FakePlayer(helper.getLevel(), new GameProfile(actorId, "purchase-cost"));
        var lookup = PlayerList.class.getDeclaredField("playersByUUID");
        lookup.setAccessible(true);
        var players = (Map<UUID, ServerPlayer>) lookup.get(server.getPlayerList());
        helper.assertTrue(!players.containsKey(actorId), "The purchase actor must be unused.");
        var order = StageOrder.getInstance();
        var definitions = order.getOrderedStages().stream().map(id -> order.getStageDefinition(id).orElseThrow()).toList();
        var original = server.overworld().getData(StageAttachments.TEAM_STAGES);
        var originalPurchases = StagePurchaseData.get(server);
        var stage = StageId.parse("progressivestages:purchase_repeated_cost");
        var overflowStage = StageId.parse("progressivestages:purchase_large_cost");
        var owner = new OwnerRef(OwnerKind.PERSONAL, actorId);
        var clocks = StageRegressionData.get(server);
        helper.assertTrue(clocks.getGrantTime(owner, stage) < 0 && clocks.getGrantTime(owner, overflowStage) < 0,
            "The purchase fixture clocks must be unused.");
        var bread = ResourceLocation.parse("minecraft:bread");
        var iron = ResourceLocation.parse("minecraft:iron_ingot");
        var cost = new StageCost(10, List.of(new StageCost.ItemCost(bread, 4),
            new StageCost.ItemCost(bread, 4), new StageCost.ItemCost(iron, 2)), true, 0, 50);
        var manager = StageManager.getInstance();
        try {
            order.clear();
            var parsed = com.enviouse.progressivestages.server.loader.StageFileParser.parseText("""
                [stage]
                id = "progressivestages:purchase_repeated_cost"
                team_stage = false
                [cost]
                xp_levels = 10
                items = ["minecraft:bread:4", "minecraft:bread:4", "minecraft:iron_ingot:2"]
                bypass_requirements = true
                refund_percent = 50
                """, "purchase_repeated_cost.toml", "purchase fixture", false);
            helper.assertTrue(parsed.isSuccess() && cost.equals(parsed.getStageDefinition().getCost()),
                "The TOML parser must preserve the complete repeated cost fixture.");
            order.registerStage(parsed.getStageDefinition());
            order.registerStage(StageDefinition.builder(overflowStage).teamStage(false)
                .cost(new StageCost(0, List.of(new StageCost.ItemCost(bread, Integer.MAX_VALUE),
                    new StageCost.ItemCost(bread, Integer.MAX_VALUE)), true, 0, 0)).build());
            server.overworld().setData(StageAttachments.TEAM_STAGES, original.copy());
            server.overworld().getDataStorage().set("progressivestages_purchases", new StagePurchaseData());
            players.put(actorId, actor);
            actor.giveExperienceLevels(20);
            actor.getInventory().add(new ItemStack(Items.BREAD, 4));
            actor.getInventory().add(new ItemStack(Items.IRON_INGOT, 2));
            purchase(actor, stage);
            helper.assertTrue(!manager.hasStage(actor, stage) && actor.experienceLevel == 20
                && actor.getInventory().countItem(Items.BREAD) == 4
                && actor.getInventory().countItem(Items.IRON_INGOT) == 2
                && StagePurchaseData.get(server).getActorPurchase(owner, stage).isEmpty(),
                "Insufficient combined item costs must not grant a stage, charge anything or create a receipt.");
            purchase(actor, overflowStage);
            helper.assertTrue(!manager.hasStage(actor, overflowStage)
                && actor.getInventory().countItem(Items.BREAD) == 4,
                "Combined item counts must not overflow into an affordable cost.");
            actor.getInventory().add(new ItemStack(Items.BREAD, 4));
            purchase(actor, stage);
            helper.assertTrue(manager.hasIndependentStage(actor, stage) && actor.experienceLevel == 10
                && actor.getInventory().countItem(Items.BREAD) == 0
                && actor.getInventory().countItem(Items.IRON_INGOT) == 0,
                "A fully funded purchase must consume every configured cost row once.");
            purchase(actor, stage);
            helper.assertTrue(actor.experienceLevel == 10, "Repeating an owned purchase must not charge again.");
            manager.revokeStageWithCause(actor, stage, StageCause.COMMAND);
            helper.assertTrue(actor.experienceLevel == 15 && actor.getInventory().countItem(Items.BREAD) == 4
                && actor.getInventory().countItem(Items.IRON_INGOT) == 1,
                "The refund must return only the configured share of the fully paid cost.");
            manager.revokeStageWithCause(actor, stage, StageCause.COMMAND);
            helper.assertTrue(actor.experienceLevel == 15 && actor.getInventory().countItem(Items.BREAD) == 4,
                "A repeated revoke must not repeat the refund.");
            helper.succeed();
        } finally {
            players.remove(actorId, actor);
            actor.discard();
            NetworkHandler.clearPlayerRuntimeState(actorId);
            clocks.clear(owner, stage);
            clocks.clear(owner, overflowStage);
            server.overworld().getDataStorage().set("progressivestages_purchases", originalPurchases);
            server.overworld().setData(StageAttachments.TEAM_STAGES, original);
            order.clear();
            definitions.forEach(order::registerStage);
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifyRefunds(GameTestHelper helper, boolean serverScope) throws Exception {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.getPlayerList().getPlayers().isEmpty(), "Purchases require an isolated fixture server.");
        UUID firstId = new UUID(0x5735, 1), secondId = new UUID(0x5735, 2);
        var first = new FakePlayer(helper.getLevel(), new GameProfile(firstId, "purchase-first"));
        var second = new FakePlayer(helper.getLevel(), new GameProfile(secondId, "purchase-second"));
        var lookup = PlayerList.class.getDeclaredField("playersByUUID");
        lookup.setAccessible(true);
        var players = (Map<UUID, ServerPlayer>) lookup.get(server.getPlayerList());
        helper.assertTrue(!players.containsKey(firstId) && !players.containsKey(secondId), "Purchase actors must be unused.");
        var provider = TeamProvider.getInstance();
        var integrationField = TeamProvider.class.getDeclaredField("ftbIntegration");
        integrationField.setAccessible(true);
        var originalIntegration = integrationField.get(provider);
        var order = StageOrder.getInstance();
        var definitions = order.getOrderedStages().stream().map(id -> order.getStageDefinition(id).orElseThrow()).toList();
        var original = server.overworld().getData(StageAttachments.TEAM_STAGES);
        var originalPurchases = StagePurchaseData.get(server);
        var manager = StageManager.getInstance();
        var stage = StageId.parse("progressivestages:purchase_refund");
        var owner = serverScope ? new OwnerRef(OwnerKind.SERVER, StageManager.SERVER_TEAM)
            : new OwnerRef(OwnerKind.TEAM, firstId);
        var clocks = StageRegressionData.get(server);
        helper.assertTrue(clocks.getGrantTime(owner, stage) < 0, "The purchase fixture clock must be unused.");
        var cost = new StageCost(10, List.of(new StageCost.ItemCost(ResourceLocation.parse("minecraft:bread"), 4)), true, 0, 50);
        try {
            integrationField.set(provider, new TeamProvider.ITeamIntegration() {
                @Override public UUID getTeamId(ServerPlayer actor) { return firstId; }
                @Override public Set<ServerPlayer> getTeamMembers(UUID team, ServerPlayer actor) { return Set.of(first, second); }
            });
            order.clear();
            var definition = StageDefinition.builder(stage).cost(cost);
            if (serverScope) definition.scope("server"); else definition.teamStage(true);
            order.registerStage(definition.build());
            server.overworld().setData(StageAttachments.TEAM_STAGES, original.copy());
            server.overworld().getDataStorage().set("progressivestages_purchases", new StagePurchaseData());
            players.put(firstId, first);
            players.put(secondId, second);
            first.giveExperienceLevels(30);
            first.getInventory().add(new ItemStack(Items.BREAD, 12));
            purchase(first, stage);
            helper.assertTrue(manager.hasStage(second, stage) && first.experienceLevel == 20
                && first.getInventory().countItem(Items.BREAD) == 8, "The real purchase handler must charge only its actor.");
            manager.revokeStageWithCause(second, stage, StageCause.COMMAND);
            assertBalances(helper, first, second, 25, 10);
            manager.revokeStageWithCause(second, stage, StageCause.COMMAND);
            assertBalances(helper, first, second, 25, 10);
            purchase(first, stage);
            assertBalances(helper, first, second, 15, 6);
            players.remove(firstId, first);
            manager.revokeStageWithCause(second, stage, StageCause.COMMAND);
            assertBalances(helper, first, second, 15, 6);
            helper.assertTrue(StagePurchaseData.get(server).getPendingActorRefunds(firstId).size() == 1,
                "The offline payer must retain one refund rather than paying an online teammate.");
            var persisted = StagePurchaseData.get(server).save(new net.minecraft.nbt.CompoundTag(), server.registryAccess());
            server.overworld().getDataStorage().set("progressivestages_purchases", StagePurchaseData.load(persisted, server.registryAccess()));
            order.clear();
            order.registerStage(StageDefinition.builder(stage).teamStage(false)
                .cost(new StageCost(100, List.of(), true, 0, 100)).build());
            manager.syncStagesOnLogin(second);
            assertBalances(helper, first, second, 15, 6);
            players.put(firstId, first);
            manager.syncStagesOnLogin(first);
            assertBalances(helper, first, second, 20, 8);
            manager.syncStagesOnLogin(first);
            assertBalances(helper, first, second, 20, 8);
            helper.assertTrue(StagePurchaseData.get(server).getPendingActorRefunds(firstId).isEmpty(),
                "The exact stored refund must be consumed once despite definition and owner changes.");
            order.clear();
            order.registerStage(StageDefinition.builder(stage).teamStage(false).cost(cost).build());
            purchase(first, stage);
            assertBalances(helper, first, second, 10, 4);
            StagePurchaseData.get(server).markPaid(firstId, stage);
            manager.revokeStageWithCause(second, stage, StageCause.COMMAND);
            assertBalances(helper, first, second, 10, 4);
            helper.assertTrue(manager.hasStage(first, stage), "Another actor cannot revoke a personal purchase.");
            manager.revokeStageWithCause(first, stage, StageCause.COMMAND);
            assertBalances(helper, first, second, 15, 6);
            helper.assertTrue(StagePurchaseData.get(server).isPaid(firstId, stage),
                "A personal refund must preserve colliding legacy team purchase history.");
            helper.succeed();
        } finally {
            players.remove(firstId, first);
            players.remove(secondId, second);
            first.discard();
            second.discard();
            NetworkHandler.clearPlayerRuntimeState(firstId);
            NetworkHandler.clearPlayerRuntimeState(secondId);
            clocks.clear(owner, stage);
            clocks.clear(new OwnerRef(OwnerKind.PERSONAL, firstId), stage);
            server.overworld().getDataStorage().set("progressivestages_purchases", originalPurchases);
            server.overworld().setData(StageAttachments.TEAM_STAGES, original);
            integrationField.set(provider, originalIntegration);
            order.clear();
            definitions.forEach(order::registerStage);
        }
    }

    private static NetworkHandler.CostInfo purchaseOffer(ServerPlayer player, StageId stage) throws Exception {
        var method = NetworkHandler.class.getDeclaredMethod("computeCostInfo", ServerPlayer.class, StageId.class);
        method.setAccessible(true);
        return (NetworkHandler.CostInfo) method.invoke(null, player, stage);
    }

    private static void purchase(ServerPlayer player, StageId stage) throws Exception {
        var context = (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
            new Class<?>[] {IPayloadContext.class}, (proxy, method, arguments) -> switch (method.getName()) {
                case "player" -> player;
                case "enqueueWork" -> {
                    ((Runnable) arguments[0]).run();
                    yield CompletableFuture.completedFuture(null);
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        var method = NetworkHandler.class.getDeclaredMethod("handlePurchaseServer", NetworkHandler.RequestPurchasePayload.class, IPayloadContext.class);
        method.setAccessible(true);
        method.invoke(null, new NetworkHandler.RequestPurchasePayload(stage.getResourceLocation()), context);
    }

    private static void assertBalances(GameTestHelper helper, ServerPlayer first, ServerPlayer second, int xp, int bread) {
        helper.assertTrue(first.experienceLevel == xp && first.getInventory().countItem(Items.BREAD) == bread
            && second.experienceLevel == 0 && second.getInventory().countItem(Items.BREAD) == 0,
            "Only the payer may receive the exact purchased item and experience refund.");
    }
}
