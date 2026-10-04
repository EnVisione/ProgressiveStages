package com.enviouse.progressivestages.fixture;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.api.structure.*;
import com.enviouse.progressivestages.common.data.StageAttachments;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.compat.kubejs.PSKubeBindings;
import com.enviouse.progressivestages.server.triggers.StagePurchaseData;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mod("purchase_fixture")
public final class PurchaseLeaseFixture implements StructureContextProvider {
    private static final ResourceLocation PROVIDER = ResourceLocation.parse("purchase_fixture:arena");
    private static final StageId ACCESS = StageId.parse("progressivestages:purchase_fixture_access");
    private static final StageId PURCHASE = StageId.parse("progressivestages:purchase_fixture_lease");
    private static final StructureSessionId SESSION = new StructureSessionId(new UUID(0x5735, 5));
    private static final StructureBounds BOUNDS = new StructureBounds(0, 200, 0, 10, 210, 10);
    private StructureSessionSpec assigned;

    public PurchaseLeaseFixture() {
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::started);
    }

    private void started(ServerStartedEvent event) {
        ProgressiveStagesAPI.registerStructureContextProvider(PROVIDER, this);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        var root = Commands.literal("purchasefixture").requires(source -> source.hasPermission(4));
        root.then(Commands.literal("assign").then(Commands.argument("player", EntityArgument.player())
            .executes(context -> {
                ServerPlayer player = EntityArgument.getPlayer(context, "player");
                assigned = new StructureSessionSpec(PROVIDER, SESSION,
                    new StructureInstanceKey(Level.OVERWORLD, ResourceLocation.parse("minecraft:igloo"), BlockPos.ZERO),
                    BOUNDS, player.getUUID(), StructureOwnershipScope.PLAYER, ACCESS, Optional.of(PURCHASE),
                    false, StructureCleanupPolicy.KEEP_ACCESS, StructureSessionAvailability.AVAILABLE);
                context.getSource().sendSuccess(() -> Component.literal("Purchase fixture assignment ready."), false);
                return 1;
            })));
        root.then(Commands.literal("status").then(Commands.argument("player", EntityArgument.player())
            .executes(context -> {
                ServerPlayer player = EntityArgument.getPlayer(context, "player");
                var manager = StageManager.getInstance();
                var owner = manager.getStageOwner(player, PURCHASE);
                var sources = player.server.overworld().getData(StageAttachments.TEAM_STAGES)
                    .getSources(owner, PURCHASE).stream().sorted().toList();
                var receipt = StagePurchaseData.get(player.server).getActorPurchase(owner, PURCHASE);
                String status = "Purchase fixture. Effective " + manager.hasStage(player, PURCHASE)
                    + ", independent " + manager.hasIndependentStage(player, PURCHASE)
                    + ", sources " + sources + ", levels " + player.experienceLevel
                    + ", bread " + player.getInventory().countItem(Items.BREAD)
                    + ", diamonds " + player.getInventory().countItem(Items.DIAMOND)
                    + ", receipt " + receipt.isPresent();
                context.getSource().sendSuccess(() -> Component.literal(status), false);
                return 1;
            })));
        root.then(Commands.literal("open").then(Commands.argument("player", EntityArgument.player())
            .executes(context -> {
                new PSKubeBindings().openGui(EntityArgument.getPlayer(context, "player"));
                return 1;
            })));
        event.getDispatcher().register(root);
    }

    @Override
    public StructureAccessDecision evaluate(StructureAccessRequest request) {
        if (assigned == null || !request.level().dimension().equals(Level.OVERWORLD)
                || !BOUNDS.contains(request.position())) return StructureAccessDecision.pass();
        if (!assigned.assignmentOwner().equals(request.player().getUUID())) {
            return StructureAccessDecision.deny(StructureAccessDecision.Reason.WRONG_OWNER, ACCESS, SESSION, BOUNDS);
        }
        return StructureAccessDecision.permit(SESSION, BOUNDS);
    }

    @Override
    public Collection<StructureSessionSpec> sessionsFor(ServerPlayer player) {
        return assigned != null && assigned.assignmentOwner().equals(player.getUUID()) ? List.of(assigned) : List.of();
    }

    @Override
    public Optional<StructureSessionSpec> session(StructureSessionId sessionId) {
        return assigned != null && SESSION.equals(sessionId) ? Optional.of(assigned) : Optional.empty();
    }
}
