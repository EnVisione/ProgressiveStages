package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.lock.LockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.util.HashMap;
import java.util.Map;

/**
 * v2.0.2: SevTech-style server-side chunk-packet rewriting for ore masquerade.
 *
 * <p>Replaces the per-tick block-update scan with a pipeline hook that mutates
 * chunk packets <em>at send time</em>, per-player. The client receives one
 * chunk packet that is byte-identical in shape to a vanilla packet, but with
 * the spoofed block IDs already substituted in the palette.
 *
 * <h3>Pipeline</h3>
 * <ol>
 *   <li>{@code PlayerChunkSender.sendChunk} sets a ThreadLocal&lt;ServerPlayer&gt;.</li>
 *   <li>{@code ClientboundLevelChunkPacketData.<init>(chunk)} redirects
 *       {@code calculateChunkSize} so the byte[] is allocated at the
 *       <em>rewritten</em> size — cloning a section's palette can promote its
 *       bit-width (e.g. 1-bit pure-ore section + stone swap → 2-bit), which
 *       makes the section's serialized size larger than the original.</li>
 *   <li>{@code extractChunkData} writes the rewritten section bytes.</li>
 *   <li>The ThreadLocals are cleared at sendChunk RETURN.</li>
 * </ol>
 *
 * <p>Both the size redirect and the extract injection consult the same
 * thread-local {@link SwapContext} so the per-chunk swap map and the cloned
 * {@link PalettedContainer}s are computed exactly once per chunk send.
 */
public final class OreSpoofChunkRewriter {

    private OreSpoofChunkRewriter() {}

    /** The player the current chunk packet is being built for. */
    private static final ThreadLocal<ServerPlayer> CURRENT_SEND_TARGET = new ThreadLocal<>();

    /** Cached swap context for the current chunk-send call. */
    private static final ThreadLocal<SwapContext> CURRENT_SWAP = new ThreadLocal<>();

    public static void setCurrentSendTarget(ServerPlayer player) {
        CURRENT_SEND_TARGET.set(player);
    }

    public static void clearCurrentSendTarget() {
        CURRENT_SEND_TARGET.remove();
        CURRENT_SWAP.remove();
    }

    public static ServerPlayer getCurrentSendTarget() {
        return CURRENT_SEND_TARGET.get();
    }

    /**
     * Cached per-section data for one (player, chunk) packet build. Holds:
     * <ul>
     *   <li>{@code sectionSwaps[i]} — packed-rel → fake state map for section i,
     *       or null if no swaps in that section.</li>
     *   <li>{@code clonedStates[i]} — cloned palette container with swaps
     *       applied, or null if no swaps in that section.</li>
     * </ul>
     */
    public static final class SwapContext {
        public final LevelChunk chunk;
        public final Map<Short, BlockState>[] sectionSwaps;
        public final PalettedContainer<BlockState>[] clonedStates;
        public final boolean anySwaps;

        @SuppressWarnings("unchecked")
        SwapContext(LevelChunk chunk, int sectionCount) {
            this.chunk = chunk;
            this.sectionSwaps = new Map[sectionCount];
            this.clonedStates = new PalettedContainer[sectionCount];
            this.anySwaps = false; // overwritten by builder
        }

        /** Final-after-build flag; we cheat with a small wrapper. */
        private boolean any;
        public boolean any() { return any; }
        void setAny(boolean v) { this.any = v; }
    }

    /**
     * Compute (or return cached) swap context for the current send target +
     * given chunk. Returns null when there is nothing to spoof for this
     * player/chunk pair — caller should fall through to vanilla.
     */
    public static SwapContext getOrBuildSwapContext(LevelChunk chunk) {
        ServerPlayer target = CURRENT_SEND_TARGET.get();
        if (target == null) return null;
        SwapContext cached = CURRENT_SWAP.get();
        if (cached != null && cached.chunk == chunk) {
            return cached.any() ? cached : null;
        }

        LockRegistry reg = LockRegistry.getInstance();
        if (!reg.isOreSpoofActive()) return null;
        if (target.isSpectator()) return null;
        if (com.enviouse.progressivestages.common.config.StageConfig.isAllowCreativeBypass()
                && target.isCreative()) {
            return null;
        }
        if (!(chunk.getLevel() instanceof ServerLevel sl)) return null;

        Map<Long, Block> targets = OreSpoofManager.get().getOrBuildChunkCandidates(sl, chunk);
        if (targets.isEmpty()) {
            // Cache "no swaps" so the size redirect and extract injection both
            // short-circuit consistently within this same chunk-send call.
            SwapContext empty = new SwapContext(chunk, chunk.getSections().length);
            empty.setAny(false);
            CURRENT_SWAP.set(empty);
            return null;
        }

        PlayerPlacedBlocksData placed = PlayerPlacedBlocksData.get(sl);
        LevelChunkSection[] sections = chunk.getSections();
        SwapContext ctx = new SwapContext(chunk, sections.length);

        boolean any = false;
        for (Map.Entry<Long, Block> e : targets.entrySet()) {
            BlockPos p = BlockPos.of(e.getKey());
            if (placed.isPlayerPlaced(p)) continue;
            BlockState liveState = chunk.getBlockState(p);
            if (liveState.getBlock() != e.getValue()) continue;
            java.util.Optional<LockRegistry.OreOverrideEntry> ov =
                reg.findActiveOreOverride(target, liveState.getBlock());
            if (ov.isEmpty()) continue;
            Block display = BuiltInRegistries.BLOCK.get(ov.get().displayAs);
            if (display == null || display == Blocks.AIR) continue;
            BlockState fakeState = display.defaultBlockState();
            // Hard refuse: swapping a non-air block for an air display would
            // desync the section's nonEmptyBlockCount which we copy unchanged
            // from the original (vanilla clients use it as a fast-path skip).
            if (fakeState.isAir()) continue;

            int sectionIdx = chunk.getSectionIndex(p.getY());
            if (sectionIdx < 0 || sectionIdx >= sections.length) continue;
            int relX = p.getX() & 15;
            int relY = p.getY() & 15;
            int relZ = p.getZ() & 15;
            short packed = (short) ((relY << 8) | (relZ << 4) | relX);

            Map<Short, BlockState> sm = ctx.sectionSwaps[sectionIdx];
            if (sm == null) {
                sm = new HashMap<>();
                ctx.sectionSwaps[sectionIdx] = sm;
            }
            sm.put(packed, fakeState);
            any = true;
        }

        if (!any) {
            ctx.setAny(false);
            CURRENT_SWAP.set(ctx);
            return null;
        }

        // Build cloned palette containers for sections that have swaps.
        for (int si = 0; si < sections.length; si++) {
            Map<Short, BlockState> sm = ctx.sectionSwaps[si];
            if (sm == null || sm.isEmpty()) continue;
            ctx.clonedStates[si] = cloneAndSwap(sections[si], sm);
        }

        ctx.setAny(true);
        CURRENT_SWAP.set(ctx);
        return ctx;
    }

    /**
     * Build a fresh {@link PalettedContainer} populated from {@code section},
     * substituting the given per-position swaps. We deliberately do NOT use
     * {@code PalettedContainer.copy() + set()} — the copied palette's
     * {@code PaletteResize} callback still points at the <em>original</em>
     * container, so growing the palette (e.g. adding stone to a pure-iron-ore
     * section) resizes the original's storage but not the copy's, and the next
     * {@code set} fails bounds because the new state id exceeds the copy's
     * (un-grown) storage bit-width.
     *
     * <p>A fresh container wires its own resize callback and grows safely.
     * The cost is one full 4096-cell walk per spoofed section — acceptable
     * because sections with spoofs are rare (only ones holding override
     * targets pass the palette pre-check upstream).
     */
    public static PalettedContainer<BlockState> cloneAndSwap(
            LevelChunkSection section, Map<Short, BlockState> swaps) {
        PalettedContainer<BlockState> fresh = new PalettedContainer<>(
            Block.BLOCK_STATE_REGISTRY,
            Blocks.AIR.defaultBlockState(),
            PalettedContainer.Strategy.SECTION_STATES);

        PalettedContainer<BlockState> src = section.getStates();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    short packed = (short) ((y << 8) | (z << 4) | x);
                    BlockState swap = swaps.get(packed);
                    BlockState s = swap != null ? swap : src.get(x, y, z);
                    fresh.set(x, y, z, s);
                }
            }
        }
        return fresh;
    }
}
