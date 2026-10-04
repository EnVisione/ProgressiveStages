package com.enviouse.progressivestages.server.integration.luckperms;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.LuckPermsStageOptions;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.data.StageAttachments;
import com.enviouse.progressivestages.common.stage.OwnerKind;
import com.enviouse.progressivestages.common.stage.OwnerRef;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.common.stage.StageOrder;
import com.enviouse.progressivestages.common.team.TeamProvider;
import com.enviouse.progressivestages.server.triggers.StageRegressionData;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@GameTestHolder("progressivestages")
@PrefixGameTestTemplate(false)
public final class LuckPermsPublicationGameTests {
    private LuckPermsPublicationGameTests() {}

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", batch = "progressivestages_isolated_luckperms_publication")
    public static void unrelatedSubjectChangesPreservePublishedPermissions(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.getPlayerList().getPlayers().isEmpty(), "The publication fixture requires an isolated server.");
        var manager = StageManager.getInstance();
        var order = StageOrder.getInstance();
        var definitions = order.getOrderedStages().stream().map(id -> order.getStageDefinition(id).orElseThrow()).toList();
        var original = server.overworld().getData(StageAttachments.TEAM_STAGES);
        var stage = StageId.parse("progressivestages:publication_scope");
        var first = new FakePlayer(helper.getLevel(), new GameProfile(new UUID(0x5740, 1), "publication-a"));
        var second = new FakePlayer(helper.getLevel(), new GameProfile(new UUID(0x5740, 2), "publication-b"));
        var players = List.of(first, second);
        var clocks = StageRegressionData.get(server);
        Map<OwnerRef, Long> previousClocks = new HashMap<>();
        players.forEach(player -> {
            var owner = new OwnerRef(OwnerKind.PERSONAL, player.getUUID());
            previousClocks.put(owner, clocks.getGrantTime(owner, stage));
        });
        var adapter = new PublicationAdapter();
        var bridge = LuckPermsBridge.getInstance();
        try {
            bridge.setAdapterForTests(adapter);
            server.overworld().setData(StageAttachments.TEAM_STAGES, original.copy());
            order.clear();
            order.registerStage(StageDefinition.builder(stage).teamStage(false).luckPerms(new LuckPermsStageOptions(
                true, true, LuckPermsStageOptions.InboundMode.SYNCHRONIZED, List.of(),
                List.of(new LuckPermsStageOptions.OutboundRule("output", LuckPermsStageOptions.OutboundKind.PERMISSION,
                    "fixture.profession", Map.of())), List.of())).build());

            manager.grantStage(first, stage);
            LuckPermsBridge.reconcile(first);
            helper.assertTrue(adapter.allowed(first.getUUID()), "The first player must receive the configured permission.");
            manager.grantStage(second, stage);
            helper.assertTrue(adapter.allowed(first.getUUID()), "Another player's personal grant must not expire published output.");
            LuckPermsBridge.reconcile(second);
            helper.assertTrue(adapter.allowed(first.getUUID()) && adapter.allowed(second.getUUID()),
                "Both personal stages must publish independently.");

            adapter.change(second.getUUID());
            helper.assertTrue(adapter.allowed(first.getUUID()) && !adapter.allowed(second.getUUID()),
                "A provider user change must invalidate only its subject before reconciliation.");
            LuckPermsBridge.reconcile(second);
            TeamProvider.getInstance().invalidateMembership();
            LuckPermsBridge.membershipChanged(second.getUUID());
            helper.assertTrue(adapter.allowed(first.getUUID()) && !adapter.allowed(second.getUUID()),
                "A membership change must not leave unrelated permissions inactive.");
            LuckPermsBridge.reconcile(second);

            manager.revokeStage(second, stage);
            helper.assertTrue(adapter.allowed(first.getUUID()) && !adapter.allowed(second.getUUID()),
                "Revocation must immediately withdraw only the affected player's marker.");
            LuckPermsBridge.reconcile(second);
            helper.assertTrue(adapter.allowed(first.getUUID()) && !adapter.allowed(second.getUUID()),
                "Reconciliation must retain unrelated output and remove revoked contributions.");

            adapter.changeAll();
            helper.assertTrue(!adapter.allowed(first.getUUID()), "Provider wide changes must invalidate every publication.");
            LuckPermsBridge.reconcile(first);
            helper.assertTrue(adapter.allowed(first.getUUID()), "Fresh provider input must recover current output.");
            LuckPermsBridge.reconcileAll();
            helper.assertTrue(!adapter.allowed(first.getUUID()), "A definition reload must invalidate published permissions.");
            LuckPermsBridge.reconcile(first);
            helper.assertTrue(adapter.allowed(first.getUUID()), "The current definition must republish after reload.");
            LuckPermsBridge.disconnect(first);
            helper.assertTrue(!adapter.allowed(first.getUUID()), "Disconnect must withdraw the player's owned output.");
            helper.succeed();
        } finally {
            players.forEach(LuckPermsBridge::disconnect);
            bridge.setAdapterForTests(null);
            server.overworld().setData(StageAttachments.TEAM_STAGES, original);
            order.clear();
            definitions.forEach(order::registerStage);
            previousClocks.forEach((owner, clock) -> {
                if (clock <= 0) clocks.clear(owner, stage); else clocks.markGranted(owner, stage, clock);
            });
            players.forEach(FakePlayer::discard);
        }
    }

    private static final class PublicationAdapter implements LuckPermsAdapter {
        private final InMemoryLuckPermsAdapter nodes = new InMemoryLuckPermsAdapter();
        private final Map<UUID, Long> tickets = new HashMap<>();
        private final Map<UUID, BooleanSupplier> publications = new HashMap<>();
        private Consumer<UUID> subjectChanged;
        private Runnable allChanged;
        private long sequence;

        boolean allowed(UUID subject) {
            BooleanSupplier publication = publications.get(subject);
            return publication != null && publication.getAsBoolean()
                && nodes.effectivePermission(subject, "fixture.profession") == PermissionValue.TRUE;
        }
        void change(UUID subject) { invalidateProjection(subject); subjectChanged.accept(subject); }
        void changeAll() { invalidateProjections(); allChanged.run(); }
        @Override public State state() { return State.READY; }
        @Override public SubjectSnapshot snapshot(UUID subject) { return nodes.snapshot(subject); }
        @Override public PermissionResult permissionResult(UUID subject, String node) { return nodes.permissionResult(subject, node); }
        @Override public boolean groupExists(String group) { return false; }
        @Override public boolean subscribeChanges(Consumer<UUID> subjectChanged, Runnable allChanged) {
            this.subjectChanged = subjectChanged;
            this.allChanged = allChanged;
            return true;
        }
        @Override public long prepareProjection(UUID subject, Object target) {
            invalidateProjection(subject);
            tickets.put(subject, ++sequence);
            return sequence;
        }
        @Override public boolean publishProjection(UUID subject, long ticket, BooleanSupplier current) {
            if (!Long.valueOf(ticket).equals(tickets.get(subject)) || !current.getAsBoolean()) return false;
            publications.put(subject, current);
            return true;
        }
        @Override public boolean invalidateProjection(UUID subject) {
            tickets.remove(subject);
            publications.remove(subject);
            return true;
        }
        @Override public boolean invalidateProjections() { tickets.clear(); publications.clear(); return true; }
        @Override public MutationResult addTransient(UUID subject, NodeKind kind, String value, Map<String, String> contexts, String owner) {
            return nodes.addTransient(subject, kind, value, contexts, owner);
        }
        @Override public MutationResult removeTransient(UUID subject, NodeKind kind, String value, Map<String, String> contexts, String owner) {
            return nodes.removeTransient(subject, kind, value, contexts, owner);
        }
        @Override public boolean cleanupTransientNodes() { return nodes.cleanupTransientNodes(); }
    }
}
