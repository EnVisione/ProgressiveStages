package com.enviouse.progressivestages.server.integration.luckperms;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.LuckPermsStageOptions;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.data.StageAttachments;
import com.enviouse.progressivestages.common.data.TeamStageData;
import com.enviouse.progressivestages.common.stage.OwnerKind;
import com.enviouse.progressivestages.common.stage.OwnerRef;
import com.enviouse.progressivestages.common.stage.PermissionEpisode;
import com.enviouse.progressivestages.common.stage.PermissionStageSource;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.server.triggers.StageRegressionData;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.enviouse.progressivestages.server.integration.luckperms.LuckPermsAdapter.PermissionValue.*;

@GameTestHolder("progressivestages")
@PrefixGameTestTemplate(false)
public final class PermissionEpisodeGameTests {
    private PermissionEpisodeGameTests() {}

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", batch = "progressivestages_isolated_luckperms_permission_revocation")
    public static void permissionRevocationSurvivesReloadAndOnlyIndependentLossRearms(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.define(false, -1, Map.of("world", List.of("overworld")));
            fixture.adapter.permission(fixture.subject, "professions.chef", TRUE);
            fixture.adapter.context(fixture.subject, "world", "nether");
            fixture.reconcile();
            helper.assertTrue(!fixture.manager.hasStage(fixture.player, fixture.stage) && fixture.episode().acquiredAt() == 0,
                "A matching permission outside the configured world must not start an acquisition.");
            fixture.adapter.context(fixture.subject, "world", "overworld");
            fixture.reconcile();
            helper.assertTrue(fixture.manager.hasStage(fixture.player, fixture.stage), "The initial eligible grant must succeed.");
            fixture.manager.revokeStageWithCause(fixture.player, fixture.stage, StageCause.COMMAND);
            fixture.reload();
            fixture.reconcile();
            helper.assertTrue(!fixture.manager.hasStage(fixture.player, fixture.stage), "Reload must preserve a manual revoke.");
            fixture.adapter.state(LuckPermsAdapter.State.STARTING);
            fixture.reconcile();
            fixture.adapter.state(LuckPermsAdapter.State.READY);
            fixture.adapter.context(fixture.subject, "world", "nether");
            fixture.adapter.permission(fixture.subject, "professions.chef", FALSE);
            fixture.reconcile();
            fixture.adapter.context(fixture.subject, "world", "overworld");
            fixture.adapter.permission(fixture.subject, "professions.chef", TRUE);
            fixture.define(true, -1, Map.of("world", List.of("overworld")));
            fixture.reconcile();
            helper.assertTrue(!fixture.manager.hasStage(fixture.player, fixture.stage),
                "Provider loss, context changes and retention edits must not clear a suppression.");
            fixture.adapter.permission(fixture.subject, "professions.chef", FALSE);
            fixture.reconcile();
            fixture.reload();
            fixture.adapter.permission(fixture.subject, "professions.chef", TRUE);
            fixture.reconcile();
            helper.assertTrue(fixture.manager.hasStage(fixture.player, fixture.stage),
                "Authoritative loss followed by return must start a new episode across reload.");
            fixture.manager.revokeStageWithCause(fixture.player, fixture.stage, StageCause.COMMAND);
            fixture.manager.grantStageWithCause(fixture.player, fixture.stage, StageCause.COMMAND);
            fixture.reconcile();
            helper.assertTrue(fixture.manager.getStageSources(fixture.player, fixture.stage).containsAll(Set.of(
                "independent", new PermissionStageSource(fixture.subject, "chef", true).label())),
                "A deliberate administrative grant must clear suppression without losing independent ownership.");
            helper.succeed();
        }
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", batch = "progressivestages_isolated_luckperms_permission_expiry")
    public static void permissionExpirySurvivesContextLossAndOfflineReconciliation(GameTestHelper helper) {
        for (boolean permanent : new boolean[] {false, true}) {
            try (Fixture fixture = new Fixture(helper)) {
                fixture.define(permanent, 60000);
                fixture.adapter.permission(fixture.subject, "professions.chef", TRUE);
                fixture.reconcile();
                var first = fixture.episode();
                fixture.adapter.state(LuckPermsAdapter.State.STARTING);
                fixture.reconcile();
                fixture.reload();
                fixture.adapter.state(LuckPermsAdapter.State.READY);
                fixture.reconcile();
                helper.assertTrue(fixture.episode().acquiredAt() == first.acquiredAt()
                    && fixture.episode().expiresAt() == first.expiresAt()
                    && StageRegressionData.get(fixture.player.server).getGrantTime(fixture.owner, fixture.stage) == first.acquiredAt(),
                    "Withdrawal and reactivation must retain the original acquisition and expiry.");
                long expiredStart = System.currentTimeMillis() - 120000;
                fixture.data().putPermissionEpisode(new PermissionEpisode(fixture.owner, fixture.stage, fixture.subject,
                    "chef", first.observation(), true, false, expiredStart, expiredStart + 60000));
                fixture.reload();
                if (!permanent) {
                    com.enviouse.progressivestages.server.triggers.StageRegressionHandler.onLogin(
                        new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(fixture.player));
                    helper.assertTrue(fixture.manager.getStageSources(fixture.player, fixture.stage).isEmpty(),
                        "Expiry polling must remove the source and synchronize its view without waiting for a provider callback.");
                }
                var source = new PermissionStageSource(fixture.subject, "chef", permanent);
                var desired = Map.of(fixture.stage, Set.of(source.label()));
                var positive = new StageManager.PermissionObservation(first.observation(), true);
                helper.assertTrue(fixture.manager.reconcileOfflinePermissionSources(
                    fixture.manager.captureOfflinePermissionContext(fixture.subject), desired,
                    Map.of(fixture.stage, Map.of(source, positive)), () -> true), "The offline result must be current.");
                helper.assertTrue(!fixture.manager.hasStage(fixture.player, fixture.stage)
                    && fixture.episode().acquiredAt() == expiredStart, "Offline positive input must not renew an expired episode.");
                var negative = new StageManager.PermissionObservation(first.observation(), false);
                fixture.manager.reconcileOfflinePermissionSources(fixture.manager.captureOfflinePermissionContext(fixture.subject),
                    Map.of(), Map.of(fixture.stage, Map.of(source, negative)), () -> true);
                fixture.manager.reconcileOfflinePermissionSources(fixture.manager.captureOfflinePermissionContext(fixture.subject),
                    desired, Map.of(fixture.stage, Map.of(source, positive)), () -> true);
                helper.assertTrue(fixture.manager.hasStage(fixture.player, fixture.stage)
                    && fixture.episode().acquiredAt() > expiredStart + 60000
                    && StageRegressionData.get(fixture.player.server).getGrantTime(fixture.owner, fixture.stage) == fixture.episode().acquiredAt(),
                    "Genuine offline loss and return must establish a fresh clock.");
                var before = fixture.episode();
                int[] observations = {0};
                fixture.manager.reconcileOfflinePermissionSources(fixture.manager.captureOfflinePermissionContext(fixture.subject),
                    Map.of(), Map.of(fixture.stage, Map.of(source, negative)), () -> ++observations[0] == 1);
                helper.assertTrue(fixture.episode().equals(before), "An invalidated completion must not commit eligibility loss.");
                if (permanent) {
                    fixture.adapter.permission(fixture.subject, "professions.chef", FALSE);
                    fixture.reconcile();
                    fixture.data().putPermissionEpisode(new PermissionEpisode(fixture.owner, fixture.stage, fixture.subject,
                        "chef", first.observation(), false, false, expiredStart, expiredStart + 60000));
                    fixture.adapter.permission(fixture.subject, "professions.chef", TRUE);
                    fixture.reconcile();
                    helper.assertTrue(fixture.manager.hasStage(fixture.player, fixture.stage)
                        && fixture.episode().acquiredAt() > expiredStart + 60000,
                        "A returning rank must expire the old permanent source before opening its new episode.");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", batch = "progressivestages_isolated_luckperms_independent_earning")
    public static void independentEarningAfterPermissionAccessEmitsOneAcquisition(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.define(false, -1);
            var events = new java.util.ArrayList<com.enviouse.progressivestages.common.api.StageChangeEvent>();
            java.util.function.Consumer<com.enviouse.progressivestages.common.api.StageChangeEvent> listener = event -> {
                if (event.getStageId().equals(fixture.stage)) events.add(event);
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
            try {
                fixture.adapter.permission(fixture.subject, "professions.chef", TRUE);
                fixture.reconcile();
                fixture.adapter.state(LuckPermsAdapter.State.STARTING);
                fixture.reconcile();
                fixture.adapter.state(LuckPermsAdapter.State.READY);
                fixture.reconcile();
                helper.assertTrue(events.isEmpty(), "Derived reconciliation must not emit ordinary acquisition or revocation events.");
                fixture.manager.grantStageWithCause(fixture.player, fixture.stage, StageCause.QUEST_REWARD);
                fixture.manager.grantStageWithCause(fixture.player, fixture.stage, StageCause.QUEST_REWARD);
                helper.assertTrue(events.size() == 1 && events.getFirst().getCause() == StageCause.QUEST_REWARD
                    && events.getFirst().getChangeType() == com.enviouse.progressivestages.common.api.StageChangeType.GRANTED,
                    "The first independent earning must emit exactly one normal acquisition event.");
                fixture.adapter.permission(fixture.subject, "professions.chef", FALSE);
                fixture.reconcile();
                helper.assertTrue(fixture.manager.getStageSources(fixture.player, fixture.stage).equals(Set.of("independent")),
                    "Permission loss must retain the independently earned source.");
                helper.succeed();
            } finally {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
            }
        }
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", batch = "progressivestages_isolated_luckperms_administrative_revocation")
    public static void administrativeRevokesSuppressUnavailableAndUnqualifiedEpisodes(GameTestHelper helper) throws Exception {
        for (String route : List.of("api", "bulk", "tag", "category", "script")) {
            for (boolean permanent : new boolean[] {false, true}) {
                try (Fixture fixture = new Fixture(helper)) {
                    fixture.define(permanent, -1);
                    fixture.adapter.permission(fixture.subject, "professions.chef", TRUE);
                    fixture.reconcile();
                    fixture.adapter.state(LuckPermsAdapter.State.STARTING);
                    fixture.reconcile();
                    var mutations = new java.util.ArrayList<com.enviouse.progressivestages.common.stage.StageMutationResult>();
                    try (var subscription = fixture.manager.subscribeCommittedStageChanges(mutations::add)) {
                        int changed = fixture.revoke(route);
                        helper.assertTrue(changed == 1 && fixture.episode().suppressed(),
                            route + " must suppress the current episode even without active access.");
                        helper.assertTrue(mutations.stream().anyMatch(result -> result.changed()
                            && result.affectedOwners().contains(fixture.owner)),
                            "Suppression must publish its addressed owner as a committed change.");
                        helper.assertTrue(fixture.revoke(route) == 0, "Repeating a revoke must report no new mutation.");
                    }
                    fixture.reload();
                    fixture.adapter.state(LuckPermsAdapter.State.READY);
                    fixture.reconcile();
                    helper.assertTrue(!fixture.manager.hasStage(fixture.player, fixture.stage),
                        route + " suppression must survive provider return and reload.");
                    fixture.adapter.permission(fixture.subject, "professions.chef", FALSE);
                    fixture.reconcile();
                    fixture.adapter.permission(fixture.subject, "professions.chef", TRUE);
                    fixture.reconcile();
                    helper.assertTrue(fixture.manager.hasStage(fixture.player, fixture.stage),
                        "A later independent loss and gain must still rearm the addressed stage.");
                }
            }
        }
        try (Fixture fixture = new Fixture(helper)) {
            fixture.define(false, -1, Map.of("world", List.of("overworld")));
            fixture.adapter.permission(fixture.subject, "professions.chef", TRUE);
            fixture.adapter.context(fixture.subject, "world", "nether");
            fixture.reconcile();
            helper.assertTrue(!fixture.manager.hasStoredStage(fixture.player, fixture.stage),
                "The unqualified fixture must have history without a stored entitlement.");
            helper.assertTrue(fixture.revoke("bulk") == 1 && fixture.episode().suppressed(),
                "Bulk reset must address a positive episode that never acquired its stage.");
            fixture.adapter.context(fixture.subject, "world", "overworld");
            fixture.reconcile();
            helper.assertTrue(!fixture.manager.hasStage(fixture.player, fixture.stage),
                "Becoming qualified alone must not undo administrative suppression.");
            StageId unknown = StageId.parse("progressivestages:unknown_revoke_fixture");
            long revision = fixture.manager.getMutationRevision();
            helper.assertTrue(!ProgressiveStagesAPI.revokeStage(fixture.player, unknown, StageCause.COMMAND)
                && revision == fixture.manager.getMutationRevision(), "Unknown stages must remain a no op.");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", batch = "progressivestages_isolated_luckperms_administrative_grant")
    public static void administrativeGrantsRecordIndependentOwnershipAfterDerivedAccess(GameTestHelper helper) throws Exception {
        for (String route : List.of("api", "bypass", "bulk", "tag", "category", "direct", "script")) {
            try (Fixture fixture = new Fixture(helper)) {
                fixture.define(false, -1);
                fixture.adapter.permission(fixture.subject, "professions.chef", TRUE);
                fixture.reconcile();
                var mutations = new java.util.ArrayList<com.enviouse.progressivestages.common.stage.StageMutationResult>();
                try (var subscription = fixture.manager.subscribeCommittedStageChanges(mutations::add)) {
                    helper.assertTrue(fixture.grant(route) == 1,
                        route + " must report the first independent acquisition despite existing derived access.");
                    helper.assertTrue(fixture.manager.hasIndependentStage(fixture.player, fixture.stage),
                        "The addressed owner must receive independent ownership.");
                    helper.assertTrue(mutations.stream().anyMatch(result -> result.changed()
                        && result.affectedOwners().contains(fixture.owner)),
                        "A source change must be published even when the effective stage set is unchanged.");
                    long revision = fixture.manager.getMutationRevision();
                    helper.assertTrue(fixture.grant(route) == 0 && revision == fixture.manager.getMutationRevision(),
                        "A repeated independent grant must report no new ownership or mutation.");
                }
                fixture.adapter.permission(fixture.subject, "professions.chef", FALSE);
                fixture.reconcile();
                helper.assertTrue(fixture.manager.getStageSources(fixture.player, fixture.stage).equals(Set.of("independent")),
                    route + " must retain independent ownership after rank loss.");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", batch = "progressivestages_isolated_luckperms_attachment_persistence")
    public static void unreadableEpisodeAttachmentsRemainIntactAfterSave(GameTestHelper helper) {
        var provider = helper.getLevel().registryAccess();
        var payload = new net.minecraft.nbt.CompoundTag();
        payload.putInt("ownership_schema", 1);
        payload.put("team_stages", new net.minecraft.nbt.CompoundTag());
        payload.putInt("permission_episode_schema", 2);
        payload.putString("future_history", "preserve this record");
        var outer = new net.minecraft.nbt.CompoundTag();
        outer.put("progressivestages:team_stages", payload.copy());
        var holder = new net.neoforged.neoforge.attachment.AttachmentHolder.AsField(helper.getLevel());
        holder.deserializeInternal(provider, outer);
        var data = holder.getData(StageAttachments.TEAM_STAGES);
        boolean rejected = false;
        try {
            data.grantStage(new UUID(0, 999), StageId.parse("progressivestages:unknown_episode_save"));
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "Unreadable history must disable progression mutations instead of creating empty ownership.");
        helper.assertTrue(holder.serializeAttachments(provider).equals(outer), "Saving must preserve the complete unreadable attachment.");
        holder.setData(StageAttachments.TEAM_STAGES, data.copy());
        helper.assertTrue(holder.serializeAttachments(provider).equals(outer), "An attachment copy must preserve the original payload.");
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final UUID subject = new UUID(0x5728, 1);
        final StageId stage = StageId.parse("progressivestages:permission_episode_fixture");
        final OwnerRef owner = new OwnerRef(OwnerKind.PERSONAL, subject);
        final StageManager manager = StageManager.getInstance();
        final StageOrder order = StageOrder.getInstance();
        final List<StageDefinition> definitions = order.getOrderedStages().stream().map(id -> order.getStageDefinition(id).orElseThrow()).toList();
        final TeamStageData original;
        final Map<StageId, StageDefinition> loaded;
        final Map<StageId, StageDefinition> previousLoaded;
        final FakePlayer player;
        final InMemoryLuckPermsAdapter adapter = new InMemoryLuckPermsAdapter();
        final long previousClock;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            var server = helper.getLevel().getServer();
            helper.assertTrue(server.getPlayerList().getPlayers().isEmpty(), "The episode fixture requires an isolated server.");
            try {
                var field = com.enviouse.progressivestages.server.loader.StageFileLoader.class.getDeclaredField("loadedStages");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                var definitions = (Map<StageId, StageDefinition>) field.get(
                    com.enviouse.progressivestages.server.loader.StageFileLoader.getInstance());
                loaded = definitions;
                previousLoaded = new java.util.LinkedHashMap<>(loaded);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot isolate loaded stage definitions for the command fixture.", error);
            }
            original = server.overworld().getData(StageAttachments.TEAM_STAGES);
            server.overworld().setData(StageAttachments.TEAM_STAGES, original.copy());
            player = new FakePlayer(helper.getLevel(), new GameProfile(subject, "episode_test"));
            net.luckperms.api.LuckPermsProvider.get().getUserManager().loadUser(subject, "episode_test").join();
            previousClock = StageRegressionData.get(server).getGrantTime(owner, stage);
            LuckPermsBridge.getInstance().setAdapterForTests(adapter);
        }

        void define(boolean permanent, long duration) { define(permanent, duration, Map.of()); }

        void define(boolean permanent, long duration, Map<String, List<String>> contexts) {
            order.clear();
            var row = new LuckPermsStageOptions.InboundRule("chef", List.of(), List.of("professions.chef"),
                LuckPermsStageOptions.Match.ALL, contexts);
            var definition = StageDefinition.builder(stage).teamStage(false).durationMillis(duration)
                .tags(List.of("episode_fixture")).category("episode_fixture")
                .luckPerms(new LuckPermsStageOptions(true, true, permanent ? LuckPermsStageOptions.InboundMode.PERMANENT
                    : LuckPermsStageOptions.InboundMode.SYNCHRONIZED, List.of(row), List.of(), List.of())).build();
            order.registerStage(definition);
            loaded.clear();
            loaded.put(stage, definition);
        }

        int revoke(String route) throws Exception {
            if (route.equals("script")) return new com.enviouse.progressivestages.compat.kubejs.PSKubeBindings().revokeAll(player);
            if (route.equals("api")) return ProgressiveStagesAPI.revokeStage(player, stage, StageCause.COMMAND) ? 1 : 0;
            return command("stage " + route + " revoke @s" + (route.equals("bulk") ? "" : " episode_fixture"));
        }

        int grant(String route) throws Exception {
            if (route.equals("script")) return new com.enviouse.progressivestages.compat.kubejs.PSKubeBindings().grantAll(player);
            if (route.equals("api")) return ProgressiveStagesAPI.grantStage(player, stage, StageCause.COMMAND) ? 1 : 0;
            if (route.equals("bypass")) return ProgressiveStagesAPI.grantStageBypass(player, stage, StageCause.COMMAND) ? 1 : 0;
            if (route.equals("direct")) return command("stage grant @s " + stage);
            return command("stage " + route + " grant @s" + (route.equals("bulk") ? "" : " episode_fixture"));
        }

        int command(String command) throws Exception {
            return player.server.getCommands().getDispatcher().execute(command, player.createCommandSourceStack().withPermission(4));
        }

        TeamStageData data() { return player.server.overworld().getData(StageAttachments.TEAM_STAGES); }
        PermissionEpisode episode() { return data().getPermissionEpisode(owner, stage, new PermissionStageSource(subject, "chef", false)); }
        void reconcile() { LuckPermsBridge.reconcile(player); }
        void reload() {
            player.server.overworld().setData(StageAttachments.TEAM_STAGES, TeamStageData.CODEC.parse(JsonOps.INSTANCE,
                TeamStageData.CODEC.encodeStart(JsonOps.INSTANCE, data()).getOrThrow()).getOrThrow());
        }

        @Override public void close() {
            loaded.clear();
            loaded.putAll(previousLoaded);
            player.server.overworld().setData(StageAttachments.TEAM_STAGES, original);
            if (previousClock <= 0) StageRegressionData.get(player.server).clear(owner, stage);
            else StageRegressionData.get(player.server).markGranted(owner, stage, previousClock);
            LuckPermsBridge.disconnect(player);
            LuckPermsBridge.getInstance().setAdapterForTests(null);
            var user = net.luckperms.api.LuckPermsProvider.get().getUserManager().getUser(subject);
            if (user != null) net.luckperms.api.LuckPermsProvider.get().getUserManager().cleanupUser(user);
            order.clear();
            definitions.forEach(order::registerStage);
            player.discard();
        }
    }
}
