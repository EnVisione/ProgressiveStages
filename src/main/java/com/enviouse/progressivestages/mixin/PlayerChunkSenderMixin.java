package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.server.enforcement.OreSpoofChunkRewriter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v2.0.2: chunk-rewriter — phase 1.
 *
 * <p>Sets a {@code ThreadLocal&lt;ServerPlayer&gt;} around the static
 * {@code sendChunk} call so {@link ClientboundLevelChunkPacketDataMixin}
 * downstream knows which player the packet is destined for. The packet
 * construction happens inside {@code sendChunk} (via
 * {@code new ClientboundLevelChunkWithLightPacket(...)} → {@code new
 * ClientboundLevelChunkPacketData(chunk)} → {@code extractChunkData}), so the
 * thread-local is in scope the entire time.
 */
@Mixin(PlayerChunkSender.class)
public abstract class PlayerChunkSenderMixin {

    @Inject(method = "sendChunk", at = @At("HEAD"))
    private static void progressivestages$markSendTarget(
            ServerGamePacketListenerImpl listener,
            ServerLevel level,
            LevelChunk chunk,
            CallbackInfo ci) {
        // Defensive: clear any leaked target from a prior call that threw
        // before RETURN. sendChunk runs single-threaded on the server thread,
        // but if any vanilla / mod throws between HEAD and RETURN, the
        // ThreadLocal would otherwise persist into the next sendChunk call.
        OreSpoofChunkRewriter.clearCurrentSendTarget();
        if (listener != null && listener.player != null) {
            OreSpoofChunkRewriter.setCurrentSendTarget(listener.player);
        }
    }

    @Inject(method = "sendChunk", at = @At("RETURN"))
    private static void progressivestages$clearSendTarget(
            ServerGamePacketListenerImpl listener,
            ServerLevel level,
            LevelChunk chunk,
            CallbackInfo ci) {
        OreSpoofChunkRewriter.clearCurrentSendTarget();
    }
}
