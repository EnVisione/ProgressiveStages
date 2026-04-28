package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.server.enforcement.ItemEnforcer;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Gates the enchanting table at the menu level:
 *
 * <ul>
 *   <li>After {@link EnchantmentMenu#slotsChanged}, any offer whose primary enchantment
 *       is locked for the nearest player has its {@code enchantClue} / {@code levelClue}
 *       cleared so the UI renders "???" instead of the locked enchant name.</li>
 *   <li>{@link EnchantmentMenu#clickMenuButton} is cancelled (returns {@code false},
 *       burning neither XP nor lapis) if the slot's primary enchantment is locked.</li>
 * </ul>
 *
 * <p>Secondary enchants from {@code getEnchantmentList} can still slip through on a hit
 * where the <em>primary</em> preview happens to be unlocked but the actual pick chain
 * includes a locked secondary. Those are cleaned up by the inventory scan's
 * {@code EnchantEnforcer.stripLockedEnchants}, so the player can't hold a locked enchant
 * for long. The UI + XP gate covers the common case cleanly.
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {

    @Shadow @Final public int[] enchantClue;
    @Shadow @Final public int[] levelClue;
    @Shadow @Final private ContainerLevelAccess access;

    @Inject(method = "slotsChanged", at = @At("TAIL"))
    private void progressivestages$filterClues(Container inventory, CallbackInfo ci) {
        this.access.execute((level, pos) -> {
            ServerPlayer player = nearestPlayer(level, pos);
            if (player == null) return;
            if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return;

            IdMap<Holder<Enchantment>> idmap = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT).asHolderIdMap();

            for (int i = 0; i < enchantClue.length; i++) {
                int clue = enchantClue[i];
                if (clue < 0) continue;
                Holder<Enchantment> holder = idmap.byId(clue);
                if (holder == null) continue;
                if (isLocked(player, holder)) {
                    enchantClue[i] = -1;
                    levelClue[i] = -1;
                }
            }
        });
    }

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void progressivestages$refuseLockedApply(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (StageConfig.isAllowCreativeBypass() && sp.isCreative()) return;
        if (id < 0 || id >= enchantClue.length) return;

        int clue = enchantClue[id];
        if (clue < 0) return;

        final Holder<Enchantment>[] resolved = new Holder[]{null};
        this.access.execute((level, pos) -> {
            IdMap<Holder<Enchantment>> idmap = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT).asHolderIdMap();
            resolved[0] = idmap.byId(clue);
        });
        if (resolved[0] == null) return;

        if (isLocked(sp, resolved[0])) {
            Optional<StageId> required = primaryRestrictingFor(sp, resolved[0]);
            required.ifPresent(stage -> ItemEnforcer.notifyLockedWithCooldown(sp, stage, StageConfig.getMsgTypeLabelEnchantment()));
            cir.setReturnValue(false);
        }
    }

    private static boolean isLocked(ServerPlayer player, Holder<Enchantment> holder) {
        var id = holder.unwrapKey().map(k -> k.location()).orElse(null);
        if (id == null) return false;
        // v2.0 multi-stage: blocked if ANY gating stage is missing.
        return LockRegistry.getInstance().isEnchantmentBlockedFor(player, id, holder);
    }

    private static Optional<StageId> primaryRestrictingFor(ServerPlayer player, Holder<Enchantment> holder) {
        var id = holder.unwrapKey().map(k -> k.location()).orElse(null);
        if (id == null) return Optional.empty();
        return LockRegistry.getInstance().primaryRestrictingStageForEnchantment(player, id, holder);
    }

    /**
     * The access pair (level, pos) doesn't carry a Player directly. Use the nearest
     * player to the enchanting-table position — on a survival server that's the player
     * holding the menu open. On multiplayer with two players side-by-side at one table,
     * whichever player is closer drives the preview; acceptable trade-off since stages
     * are team-scoped and team members share progression anyway.
     */
    private static ServerPlayer nearestPlayer(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel sl)) return null;
        return com.enviouse.progressivestages.server.enforcement.NearestPlayerCheck
            .findNearest(sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8.0);
    }
}
