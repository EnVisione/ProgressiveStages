package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.server.enforcement.EnchantmentSelectionPolicy;
import com.enviouse.progressivestages.server.enforcement.ItemEnforcer;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {

    @Shadow @Final public int[] enchantClue;
    @Shadow @Final public int[] levelClue;
    @Shadow @Final public int[] costs;
    @Shadow @Final private ContainerLevelAccess access;

    @Unique
    private ServerPlayer progressivestages$player;

    @Inject(
        method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
        at = @At("TAIL"),
        require = 1
    )
    private void progressivestages$capturePlayer(
            int containerId, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        if (inventory.player instanceof ServerPlayer player) {
            this.progressivestages$player = player;
        }
    }

    @Redirect(
        method = "getEnchantmentList",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;selectEnchantment(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/item/ItemStack;ILjava/util/stream/Stream;)Ljava/util/List;"
        ),
        require = 1
    )
    private List<EnchantmentInstance> progressivestages$selectEnchantments(
            RandomSource random,
            ItemStack stack,
            int level,
            Stream<Holder<Enchantment>> possibleEnchantments) {
        return EnchantmentSelectionPolicy.selectForPlayer(
            this.progressivestages$player, random, stack, level, possibleEnchantments);
    }

    @Inject(method = "slotsChanged", at = @At("TAIL"))
    private void progressivestages$filterClues(Container inventory, CallbackInfo ci) {
        ServerPlayer player = this.progressivestages$player;
        if (player == null || !LockRegistry.getInstance().isEnchantmentLockConfigured()) return;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return;

        this.access.execute((level, pos) -> {
            IdMap<Holder<Enchantment>> idmap = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT).asHolderIdMap();
            boolean changed = false;
            for (int i = 0; i < enchantClue.length; i++) {
                int clue = enchantClue[i];
                if (clue < 0) {
                    changed |= costs[i] != 0 || levelClue[i] != -1;
                    costs[i] = 0;
                    levelClue[i] = -1;
                    continue;
                }
                Holder<Enchantment> holder = idmap.byId(clue);
                if (holder != null && isLocked(player, holder)) {
                    changed = true;
                    costs[i] = 0;
                    enchantClue[i] = -1;
                    levelClue[i] = -1;
                }
            }
            if (changed) {
                ((EnchantmentMenu) (Object) this).broadcastChanges();
            }
        });
    }

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void progressivestages$refuseLockedApply(
            Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || !LockRegistry.getInstance().isEnchantmentLockConfigured()) return;
        if (StageConfig.isAllowCreativeBypass() && serverPlayer.isCreative()) return;
        if (id < 0 || id >= enchantClue.length) return;

        int clue = enchantClue[id];
        if (clue < 0) return;

        AtomicReference<Holder<Enchantment>> resolved = new AtomicReference<>();
        this.access.execute((level, pos) -> {
            IdMap<Holder<Enchantment>> idmap = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT).asHolderIdMap();
            resolved.set(idmap.byId(clue));
        });
        Holder<Enchantment> enchantment = resolved.get();
        if (enchantment == null || !isLocked(serverPlayer, enchantment)) return;

        Optional<StageId> required = primaryRestrictingFor(serverPlayer, enchantment);
        required.ifPresent(stage -> ItemEnforcer.notifyLockedWithCooldown(
            serverPlayer, stage, StageConfig.getMsgTypeLabelEnchantment()));
        cir.setReturnValue(false);
    }

    private static boolean isLocked(ServerPlayer player, Holder<Enchantment> holder) {
        var id = holder.unwrapKey().map(key -> key.location()).orElse(null);
        return id != null && LockRegistry.getInstance().isEnchantmentBlockedFor(player, id, holder);
    }

    private static Optional<StageId> primaryRestrictingFor(
            ServerPlayer player, Holder<Enchantment> holder) {
        var id = holder.unwrapKey().map(key -> key.location()).orElse(null);
        if (id == null) return Optional.empty();
        return LockRegistry.getInstance().primaryRestrictingStageForEnchantment(player, id, holder);
    }
}
