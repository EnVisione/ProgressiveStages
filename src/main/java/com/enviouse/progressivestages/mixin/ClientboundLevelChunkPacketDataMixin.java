package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.server.enforcement.OreSpoofChunkRewriter;
import com.enviouse.progressivestages.server.enforcement.OreSpoofChunkRewriter.SwapContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v2.0.2: chunk-rewriter — phase 2.
 *
 * <p>Two hooks on {@code ClientboundLevelChunkPacketData}:
 * <ol>
 *   <li>{@link #progressivestages$resizeBuffer} — redirects the
 *       {@code calculateChunkSize} call inside the constructor so the
 *       pre-allocated {@code byte[]} accommodates the <em>rewritten</em>
 *       sections. Cloning a section's palette can promote its bit-width (e.g.
 *       a 1-bit pure-ore section grows to 2 bits when stone is added by the
 *       swap), and the rewritten section can therefore be larger than the
 *       original. Without this, vanilla pre-sizes the buffer to the original
 *       chunk and writes overflow as IndexOutOfBoundsException.</li>
 *   <li>{@link #progressivestages$rewriteExtract} — replaces the body of
 *       {@code extractChunkData} when there are swaps for the current player.
 *       For each section, if there are no swaps, vanilla bytes are written;
 *       otherwise the cloned (swapped) palette container is written.</li>
 * </ol>
 *
 * <p>Both hooks share a single per-call {@link SwapContext} via the
 * thread-local cache in {@link OreSpoofChunkRewriter}, so the swap map is
 * computed and the palette clones are built exactly once per chunk send.
 */
@Mixin(net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData.class)
public abstract class ClientboundLevelChunkPacketDataMixin {

    /**
     * Redirect the {@code calculateChunkSize(chunk)} call inside the
     * constructor so the buffer is sized for the rewritten output.
     */
    @Redirect(
        method = "<init>(Lnet/minecraft/world/level/chunk/LevelChunk;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundLevelChunkPacketData;calculateChunkSize(Lnet/minecraft/world/level/chunk/LevelChunk;)I"
        )
    )
    private static int progressivestages$resizeBuffer(LevelChunk chunk) {
        try {
            SwapContext ctx = OreSpoofChunkRewriter.getOrBuildSwapContext(chunk);
            if (ctx == null) {
                // No spoofs for this player+chunk → vanilla size, summed locally
                // to avoid recursing into the redirected calculateChunkSize.
                int i = 0;
                for (LevelChunkSection s : chunk.getSections()) {
                    i += s.getSerializedSize();
                }
                return i;
            }

            // Recompute size accounting for cloned sections.
            int total = 0;
            LevelChunkSection[] sections = chunk.getSections();
            for (int si = 0; si < sections.length; si++) {
                LevelChunkSection section = sections[si];
                if (ctx.clonedStates[si] == null) {
                    total += section.getSerializedSize();
                } else {
                    // Replicate LevelChunkSection.getSerializedSize() with the
                    // cloned BlockState palette container.
                    total += 2 // nonEmptyBlockCount (short)
                           + ctx.clonedStates[si].getSerializedSize()
                           + section.getBiomes().getSerializedSize();
                }
            }
            return total;
        } catch (Throwable t) {
            // Never let an error in our code kill the server tick. Fall back to
            // vanilla size; the extract path will then also fall back to
            // vanilla bytes via the same SwapContext lookup returning null.
            org.slf4j.LoggerFactory.getLogger("ProgressiveStages")
                .error("[OreSpoof] resizeBuffer fell through to vanilla after error", t);
            // Force-clear the swap context so the extract injection takes the
            // vanilla path consistently.
            OreSpoofChunkRewriter.clearCurrentSendTarget();
            int i = 0;
            for (LevelChunkSection s : chunk.getSections()) {
                i += s.getSerializedSize();
            }
            return i;
        }
    }

    /**
     * Replace {@code extractChunkData} when the swap context is non-null.
     * Writes per-section: cloned bytes for spoofed sections, vanilla bytes
     * for everything else.
     */
    @Inject(
        method = "extractChunkData(Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/world/level/chunk/LevelChunk;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void progressivestages$rewriteExtract(
            FriendlyByteBuf buf, LevelChunk chunk, CallbackInfo ci) {
        try {
            SwapContext ctx = OreSpoofChunkRewriter.getOrBuildSwapContext(chunk);
            if (ctx == null) return;

            LevelChunkSection[] sections = chunk.getSections();
            for (int si = 0; si < sections.length; si++) {
                LevelChunkSection section = sections[si];
                PalettedContainer<net.minecraft.world.level.block.state.BlockState> cloned =
                    ctx.clonedStates[si];
                if (cloned == null) {
                    // No swaps in this section → vanilla bytes.
                    section.write(buf);
                    continue;
                }
                // Manually replicate LevelChunkSection.write(buf) with cloned
                // states. The ore→stone swap preserves "non-air" status (both
                // are non-air), so nonEmptyBlockCount is unchanged. The swap
                // map is built to refuse air targets, so this invariant holds.
                short nonEmpty = ((LevelChunkSectionAccessor) (Object) section)
                    .progressivestages$getNonEmptyBlockCount();
                buf.writeShort(nonEmpty);
                cloned.write(buf);
                section.getBiomes().write(buf);
            }

            ci.cancel();
        } catch (Throwable t) {
            // If anything in our rewrite path throws, do NOT cancel — let
            // vanilla finish writing the (possibly partial) original chunk.
            // The pre-sized buffer is large enough for vanilla output (it's
            // ≥ vanilla size when our redirect succeeded, == vanilla size when
            // the redirect's catch fell through). Log and bail.
            org.slf4j.LoggerFactory.getLogger("ProgressiveStages")
                .error("[OreSpoof] rewriteExtract fell through to vanilla after error", t);
        }
    }
}
