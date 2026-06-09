package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.server.enforcement.OreSpoofChunkRewriter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * v2.0.3: rewrite per-block update packets at the send-site so the masquerade
 * doesn't leak via vanilla side-channels (right-click block sync, neighbor
 * updates, server-rejected place-block bouncebacks, etc.).
 *
 * <p>The chunk-rewriter handles the bulk path (full chunks), but vanilla also
 * sends single-block updates from many places — {@code ServerPlayerGameMode},
 * {@code ServerGamePacketListenerImpl.handleUseItemOn}, {@code ChunkHolder
 * .broadcastChanges}, etc. All of those construct a {@code
 * ClientboundBlockUpdatePacket} from the real {@code BlockState} and send it
 * straight through {@code ServerCommonPacketListenerImpl.send}. Without this
 * mixin, right-clicking a spoofed block would refresh the client to the real
 * state and the masquerade falls apart.
 *
 * <p>We intercept {@code send(Packet, PacketSendListener)} via
 * {@code @ModifyVariable} on the packet argument, swap it for a rewritten
 * instance when needed, and let vanilla send it. Cost is one volatile read
 * per packet when the feature is off, and one registry lookup + one
 * comparison when the packet isn't one of the two we care about.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {

    @ModifyVariable(
        method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Packet<?> progressivestages$maybeRewritePacket(Packet<?> packet) {
        try {
            if (!LockRegistry.getInstance().isOreSpoofActive()) return packet;
            ServerPlayer player = progressivestages$asPlayer();
            if (player == null) return packet;

            if (packet instanceof ClientboundBlockUpdatePacket bup) {
                return rewriteBlockUpdate(bup, player);
            }
            if (packet instanceof ClientboundSectionBlocksUpdatePacket sup) {
                return rewriteSectionBlocksUpdate(sup, player);
            }
            return packet;
        } catch (Throwable t) {
            // Never let an error in our rewrite kill packet send.
            org.slf4j.LoggerFactory.getLogger("ProgressiveStages")
                .error("[OreSpoof] block-update packet rewrite failed; sending original", t);
            return packet;
        }
    }

    /** Return the ServerPlayer this listener belongs to, or null for non-game listeners. */
    private ServerPlayer progressivestages$asPlayer() {
        Object self = this;
        if (self instanceof ServerGamePacketListenerImpl gp) {
            return gp.player;
        }
        return null;
    }

    @org.spongepowered.asm.mixin.Unique
    private static Packet<?> rewriteBlockUpdate(ClientboundBlockUpdatePacket original, ServerPlayer player) {
        BlockState state = original.getBlockState();
        if (state == null || state.isAir()) return original;
        Block real = state.getBlock();
        java.util.Optional<LockRegistry.OreOverrideEntry> ov =
            LockRegistry.getInstance().findActiveOreOverride(player, real);
        if (ov.isEmpty()) return original;
        Block display = BuiltInRegistries.BLOCK.get(ov.get().displayAs);
        if (display == null || display == Blocks.AIR) return original;
        BlockState fake = display.defaultBlockState();
        if (fake.isAir()) return original;
        return new ClientboundBlockUpdatePacket(original.getPos(), fake);
    }

    @org.spongepowered.asm.mixin.Unique
    private static Packet<?> rewriteSectionBlocksUpdate(
            ClientboundSectionBlocksUpdatePacket original, ServerPlayer player) {
        // ClientboundSectionBlocksUpdatePacket is shared across players for
        // broadcast (ChunkHolder.broadcastChanges sends one packet to a list of
        // players). We must NOT mutate the original — build a fresh packet for
        // any player that needs spoofs, and pass the original through for others.
        LockRegistry reg = LockRegistry.getInstance();
        SectionPos sectionPos =
            ((ClientboundSectionBlocksUpdatePacketAccessor) (Object) original)
                .progressivestages$getSectionPos();

        boolean[] dirty = {false};
        java.util.List<short[]> packedPositions = new java.util.ArrayList<>();
        java.util.List<BlockState> rewrittenStates = new java.util.ArrayList<>();

        original.runUpdates((pos, blockState) -> {
            BlockState s = blockState;
            if (s != null && !s.isAir()) {
                java.util.Optional<LockRegistry.OreOverrideEntry> ov =
                    reg.findActiveOreOverride(player, s.getBlock());
                if (ov.isPresent()) {
                    Block display = BuiltInRegistries.BLOCK.get(ov.get().displayAs);
                    if (display != null && display != Blocks.AIR) {
                        BlockState fake = display.defaultBlockState();
                        if (!fake.isAir()) {
                            s = fake;
                            dirty[0] = true;
                        }
                    }
                }
            }
            // Vanilla packs section-rel coords as (X<<8) | (Z<<4) | Y — see
            // SectionPos.sectionRelativeX/Y/Z. Reconstruct that here.
            int relX = pos.getX() & 15;
            int relY = pos.getY() & 15;
            int relZ = pos.getZ() & 15;
            short packed = (short) ((relX << 8) | (relZ << 4) | relY);
            packedPositions.add(new short[]{packed});
            rewrittenStates.add(s);
        });

        if (!dirty[0]) return original;

        // Build a fresh packet via STREAM_CODEC round-trip. Only happens when
        // at least one position in this section needs rewriting for this
        // player — rare. Avoids reflection on the private constructor and
        // keeps the original packet object untouched for other players.
        io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer(
            8 + 5 + packedPositions.size() * 10);
        net.minecraft.network.FriendlyByteBuf fbb = new net.minecraft.network.FriendlyByteBuf(buf);
        try {
            fbb.writeLong(sectionPos.asLong());
            fbb.writeVarInt(packedPositions.size());
            for (int i = 0; i < packedPositions.size(); i++) {
                short pos = packedPositions.get(i)[0];
                int stateId = Block.getId(rewrittenStates.get(i));
                fbb.writeVarLong(((long) stateId << 12) | (pos & 0xFFFL));
            }
            return ClientboundSectionBlocksUpdatePacket.STREAM_CODEC.decode(fbb);
        } finally {
            buf.release();
        }
    }
}
