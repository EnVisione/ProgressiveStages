package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.server.enforcement.EntityPresenceEnforcer;
import com.enviouse.progressivestages.server.enforcement.TrackedEntityBridge;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class ChunkMapTrackedEntityMixin implements TrackedEntityBridge {

    @Shadow @Final private Entity entity;
    @Shadow @Final private ServerEntity serverEntity;
    @Shadow @Final private Set<ServerPlayerConnection> seenBy;

    @Shadow
    public abstract void updatePlayer(ServerPlayer player);

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void progressivestages$filterTracking(ServerPlayer player, CallbackInfo ci) {
        if (!EntityPresenceEnforcer.shouldConcealTracking(player, entity)) return;
        if (seenBy.remove(player.connection)) serverEntity.removePairing(player);
        ci.cancel();
    }

    @Override
    public void progressivestages$refreshPlayer(ServerPlayer player) {
        updatePlayer(player);
    }
}
