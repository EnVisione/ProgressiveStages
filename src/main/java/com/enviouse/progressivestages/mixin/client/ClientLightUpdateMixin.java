package com.enviouse.progressivestages.mixin.client;

import com.enviouse.progressivestages.client.OreSpoofClientState;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v2.0.1: after vanilla applies a server-driven light update for a chunk, re-check
 * any spoofed positions inside the chunk so the light engine uses the FAKE block's
 * emission (e.g. an iron-ore-spoofed-as-stone contributes 0 light, even if the real
 * block under it was emitting). Fixes light leaks for emissive ores / blocks.
 *
 * <p>Fast-path: returns immediately when the client has no spoofed positions.
 */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientPacketListener", remap = true)
public class ClientLightUpdateMixin {

    @Inject(method = "applyLightData", at = @At("TAIL"), remap = true)
    private void progressivestages$recheckSpoofedLight(int chunkX, int chunkZ,
                                                       ClientboundLightUpdatePacketData data,
                                                       CallbackInfo ci) {
        // For each section in the chunk's vertical range, re-light spoofed positions.
        // Cheap when our cache is empty (single Set.isEmpty() inside).
        for (int sy = -4; sy < 20; sy++) { // covers vanilla -64..319 chunk height
            OreSpoofClientState.onLightUpdateReceived(SectionPos.of(chunkX, sy, chunkZ));
        }
    }
}
