package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.common.lock.LockRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * v3.0: per-player beacon-effect gating via {@code [beacon].locked}. Beacon effects are applied to
 * every player in range by {@code BeaconBlockEntity.applyEffects}; this redirects each
 * {@code Player.addEffect} so a player missing the gating stage for an effect simply doesn't receive
 * it (other players are unaffected). No-op when no stage declares a beacon lock.
 */
@Mixin(BeaconBlockEntity.class)
public abstract class BeaconBlockEntityMixin {

    @Redirect(
        method = "applyEffects",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"))
    private static boolean progressivestages$gateBeaconEffect(Player player, MobEffectInstance instance) {
        if (player instanceof ServerPlayer sp && instance != null
                && LockRegistry.getInstance().hasBeaconLocks()) {
            ResourceLocation eid = instance.getEffect().unwrapKey().map(k -> k.location()).orElse(null);
            if (LockRegistry.getInstance().isBeaconEffectBlockedFor(sp, eid)) {
                return false; // gated — don't apply this effect to this player
            }
        }
        return player.addEffect(instance);
    }
}
