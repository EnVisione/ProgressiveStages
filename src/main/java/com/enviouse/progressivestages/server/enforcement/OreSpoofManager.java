package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v2.0.1: per-player ore-spoof manager.
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>Each loaded chunk is scanned ONCE for override-target blocks; the result is
 *       cached in {@link #chunkSpoofCandidates}. Chunk-unload evicts the entry.</li>
 *   <li>Per-player tick (gated by chunk-section threshold + 5s safety): compute the
 *       set of positions within the cube radius that should currently be spoofed,
 *       diff against the previously-spoofed set, send only deltas via
 *       {@link ClientboundBlockUpdatePacket}.</li>
 *   <li>On stage grant / bulk change: snappy un-spoof — push truth packets for any
 *       previously spoofed positions the player can now access.</li>
 *   <li>On block break / place: invalidate chunk cache and the per-player set.</li>
 * </ol>
 *
 * <h3>TPS budget</h3>
 * Fast-path: {@link LockRegistry#isOreSpoofActive()} returns false → all hooks become
 * one boolean read and exit. When active, per-player work is O(R³) at most every
 * 16 movement-blocks or every 100 ticks (whichever first); typical R=8 → ~5k positions
 * scanned, almost all rejected by the chunk-section palette test.
 */
public final class OreSpoofManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final OreSpoofManager INSTANCE = new OreSpoofManager();
    public static OreSpoofManager get() { return INSTANCE; }
    private OreSpoofManager() {}

    /** Re-scan interval (ticks) as a safety net even if the player doesn't move. */
    private static final int RESCAN_INTERVAL_TICKS = 100;
    /** Movement threshold (chebyshev block distance) that forces a rescan. */
    private static final int MOVEMENT_RESCAN_THRESHOLD = 4;
    /** Max block updates to send to a single player per tick (rate-limit on snappy paths). */
    private static final int MAX_PACKETS_PER_TICK = 256;

    /** Per-player state. */
    private static final class PlayerSpoofState {
        /** Currently spoofed positions for this player (BlockPos.asLong → displayed Block). */
        final Map<Long, Block> currentlySpoofed = new HashMap<>();
        BlockPos lastScanCenter;
        int ticksSinceScan = 0;
        ResourceLocation lastDimension;
    }

    private final Map<UUID, PlayerSpoofState> playerStates = new ConcurrentHashMap<>();

    /**
     * Per-chunk cache of positions that hold an override-target block.
     * Keyed by (dim,chunkPos) as a packed long. Value is a list of (BlockPos, Block).
     * Built lazily on first need, evicted on chunk unload / block change.
     */
    private final Map<ChunkCacheKey, ChunkCandidates> chunkSpoofCandidates = new ConcurrentHashMap<>();

    private static final class ChunkCacheKey {
        final ResourceLocation dim;
        final long chunkPos;
        ChunkCacheKey(ResourceLocation d, long c) { this.dim = d; this.chunkPos = c; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof ChunkCacheKey k)) return false;
            return chunkPos == k.chunkPos && dim.equals(k.dim);
        }
        @Override public int hashCode() {
            return java.util.Objects.hash(dim, chunkPos);
        }
    }

    private static final class ChunkCandidates {
        /** Packed BlockPos.asLong → target Block. */
        final Map<Long, Block> targetsByPos;
        ChunkCandidates(Map<Long, Block> t) { this.targetsByPos = t; }
    }

    // ================================================================
    // Public entry points
    // ================================================================

    /** Called every tick from the global server tick (cheap pre-check). */
    public void tickPlayer(ServerPlayer player) {
        LockRegistry reg = LockRegistry.getInstance();
        if (!reg.isOreSpoofActive()) return;
        if (player.level().isClientSide) return;
        if (!(player.level() instanceof ServerLevel sl)) return;

        PlayerSpoofState st = playerStates.computeIfAbsent(player.getUUID(), k -> new PlayerSpoofState());
        ResourceLocation dim = sl.dimension().location();
        // Dimension change → flush state and force rescan
        if (st.lastDimension != null && !st.lastDimension.equals(dim)) {
            st.currentlySpoofed.clear();
            st.lastScanCenter = null;
        }
        st.lastDimension = dim;
        st.ticksSinceScan++;

        boolean rescan = false;
        if (st.lastScanCenter == null) rescan = true;
        else if (st.ticksSinceScan >= RESCAN_INTERVAL_TICKS) rescan = true;
        else {
            BlockPos p = player.blockPosition();
            int dx = Math.abs(p.getX() - st.lastScanCenter.getX());
            int dy = Math.abs(p.getY() - st.lastScanCenter.getY());
            int dz = Math.abs(p.getZ() - st.lastScanCenter.getZ());
            int cheby = Math.max(dx, Math.max(dy, dz));
            if (cheby >= MOVEMENT_RESCAN_THRESHOLD) rescan = true;
        }
        if (rescan) {
            rescanPlayer(player, sl, st);
            st.ticksSinceScan = 0;
            st.lastScanCenter = player.blockPosition();
        }
    }

    /** Snappy un-spoof — called when a player gains stages. Pushes truth packets and clears state. */
    public void onStageGained(ServerPlayer player) {
        LockRegistry reg = LockRegistry.getInstance();
        if (!reg.isOreSpoofActive()) return;
        PlayerSpoofState st = playerStates.get(player.getUUID());
        if (st == null || st.currentlySpoofed.isEmpty()) return;
        if (!(player.level() instanceof ServerLevel sl)) return;

        // Compute which positions are no longer spoofed (player now has the stage).
        java.util.List<Long> toRemove = new java.util.ArrayList<>();
        Iterator<Map.Entry<Long, Block>> it = st.currentlySpoofed.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Block> e = it.next();
            BlockPos pos = BlockPos.of(e.getKey());
            BlockState real = sl.getBlockState(pos);
            java.util.Optional<LockRegistry.OreOverrideEntry> stillSpoofed =
                reg.findActiveOreOverride(player, real.getBlock());
            if (stillSpoofed.isEmpty()) {
                toRemove.add(e.getKey());
                it.remove();
            }
        }

        if (!toRemove.isEmpty()) {
            // Single batched packet — kills the per-position burst.
            com.enviouse.progressivestages.common.network.NetworkHandler.OreSpoofPayload pl =
                new com.enviouse.progressivestages.common.network.NetworkHandler.OreSpoofPayload(
                    java.util.List.of(), toRemove, false);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, pl);

            // Also send vanilla block-update packets so the visual flips immediately
            // even before our client-side delta handler runs. Batched per-section if
            // possible: for now use ClientboundBlockUpdatePacket per position (only
            // toRemove.size() packets, which is small after the initial fix).
            for (Long packed : toRemove) {
                BlockPos pos = BlockPos.of(packed);
                player.connection.send(new ClientboundBlockUpdatePacket(pos, sl.getBlockState(pos)));
            }
        }
        // Force a rescan next tick to catch up
        st.lastScanCenter = null;
    }

    /** Called when a player logs out. */
    public void onPlayerLogout(ServerPlayer player) {
        playerStates.remove(player.getUUID());
    }

    /** Invalidate chunk cache when a block changes. */
    public void onBlockChanged(ServerLevel level, BlockPos pos) {
        LockRegistry reg = LockRegistry.getInstance();
        if (!reg.isOreSpoofActive()) return;
        ChunkCacheKey key = new ChunkCacheKey(level.dimension().location(), new ChunkPos(pos).toLong());
        chunkSpoofCandidates.remove(key);

        // Remove this position from any player's spoofed set; vanilla will send the
        // correct block to anyone who can see it on the next block-change packet.
        long packed = pos.asLong();
        for (PlayerSpoofState st : playerStates.values()) {
            st.currentlySpoofed.remove(packed);
        }
    }

    /**
     * Called from ChunkWatchEvent.Sent. Vanilla just sent the real chunk data to the
     * player; if any positions in that chunk should be spoofed for the player, push
     * the spoof packets RIGHT NOW so the player never sees the real ore. Also seeds
     * the per-player state so the next rescan diff is correct.
     *
     * <p>This is the canonical fix for "the ore re-appears after a moment" and
     * "only one out of N blocks gets spoofed" — both symptoms of relying on the
     * rescan loop alone, which can't keep up with chunk send cadence.
     */
    public void onChunkSent(ServerPlayer player, ServerLevel sl, LevelChunk chunk) {
        LockRegistry reg = LockRegistry.getInstance();
        if (!reg.isOreSpoofActive()) return;
        if (player.isSpectator()) return;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return;

        PlayerSpoofState st = playerStates.computeIfAbsent(player.getUUID(), k -> new PlayerSpoofState());
        // First time the chunk goes to this player → ensure the chunk's candidate
        // cache is built (cheap palette pre-check inside).
        ChunkCacheKey key = new ChunkCacheKey(sl.dimension().location(),
            chunk.getPos().toLong());
        ChunkCandidates cc = chunkSpoofCandidates.computeIfAbsent(key,
            k -> buildChunkCandidates(chunk));
        if (cc.targetsByPos.isEmpty()) return;

        PlayerPlacedBlocksData placedData = PlayerPlacedBlocksData.get(sl);
        int radius = reg.getMaxOreSpoofRadius();
        BlockPos center = player.blockPosition();

        for (Map.Entry<Long, Block> e : cc.targetsByPos.entrySet()) {
            BlockPos p = BlockPos.of(e.getKey());
            // Radius check: avoid spoofing distant chunks that aren't supposed to be
            // gated by the player's current position. The chunk-watch range can be
            // larger than the spoof radius (server view-distance vs. per-stage radius).
            if (radius > 0) {
                int dx = Math.abs(p.getX() - center.getX());
                int dy = Math.abs(p.getY() - center.getY());
                int dz = Math.abs(p.getZ() - center.getZ());
                if (Math.max(dx, Math.max(dy, dz)) > radius) continue;
            }
            if (placedData.isPlayerPlaced(p)) continue;
            Block live = sl.getBlockState(p).getBlock();
            if (live != e.getValue()) continue;
            java.util.Optional<LockRegistry.OreOverrideEntry> ov =
                reg.findActiveOreOverride(player, live);
            if (ov.isEmpty()) continue;
            Block display = BuiltInRegistries.BLOCK.get(ov.get().displayAs);
            if (display == null || display == Blocks.AIR) continue;

            // Send a per-position block-update packet right after vanilla's chunk
            // packet. The client renders the spoof immediately.
            BlockState fakeState = display.defaultBlockState();
            player.connection.send(new ClientboundBlockUpdatePacket(p, fakeState));
            st.currentlySpoofed.put(e.getKey(), display);
        }
    }

    /** Query whether a given position is currently spoofed for a player. Used by the drop-replacement hook. */
    public boolean isSpoofedFor(ServerPlayer player, BlockPos pos) {
        PlayerSpoofState st = playerStates.get(player.getUUID());
        if (st == null) return false;
        return st.currentlySpoofed.containsKey(pos.asLong());
    }

    /** Used by the drop-replacement hook to know which displayAs block to mimic. */
    public Block getSpoofedDisplayFor(ServerPlayer player, BlockPos pos) {
        PlayerSpoofState st = playerStates.get(player.getUUID());
        if (st == null) return null;
        return st.currentlySpoofed.get(pos.asLong());
    }

    // ================================================================
    // Core rescan
    // ================================================================

    private void rescanPlayer(ServerPlayer player, ServerLevel sl, PlayerSpoofState st) {
        LockRegistry reg = LockRegistry.getInstance();
        int radius = reg.getMaxOreSpoofRadius();
        if (radius <= 0) return;

        // Spectators & creative bypass: clear and bail.
        if (player.isSpectator()) {
            clearAllSpoofed(player, sl, st);
            return;
        }
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) {
            clearAllSpoofed(player, sl, st);
            return;
        }

        BlockPos center = player.blockPosition();
        ResourceLocation dim = sl.dimension().location();
        PlayerPlacedBlocksData placedData = PlayerPlacedBlocksData.get(sl);

        // Iterate chunks within the radius; for each chunk consult the cache (build if absent).
        int minX = (center.getX() - radius) >> 4;
        int maxX = (center.getX() + radius) >> 4;
        int minZ = (center.getZ() - radius) >> 4;
        int maxZ = (center.getZ() + radius) >> 4;

        // Build desired set
        Map<Long, Block> desired = new HashMap<>();
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (!sl.hasChunk(cx, cz)) continue;
                LevelChunk chunk = sl.getChunk(cx, cz);
                ChunkCacheKey key = new ChunkCacheKey(dim, ChunkPos.asLong(cx, cz));
                ChunkCandidates cc = chunkSpoofCandidates.computeIfAbsent(key,
                    k -> buildChunkCandidates(chunk));
                if (cc.targetsByPos.isEmpty()) continue;

                for (Map.Entry<Long, Block> e : cc.targetsByPos.entrySet()) {
                    BlockPos p = BlockPos.of(e.getKey());
                    int dx = Math.abs(p.getX() - center.getX());
                    int dy = Math.abs(p.getY() - center.getY());
                    int dz = Math.abs(p.getZ() - center.getZ());
                    if (Math.max(dx, Math.max(dy, dz)) > radius) continue;
                    // Player-placed → never spoof
                    if (placedData.isPlayerPlaced(p)) continue;
                    // Verify the live block still matches (the cache is a hint; the chunk may have changed under us)
                    Block live = sl.getBlockState(p).getBlock();
                    if (live != e.getValue()) continue;
                    // Active override for this player?
                    java.util.Optional<LockRegistry.OreOverrideEntry> ov =
                        reg.findActiveOreOverride(player, live);
                    if (ov.isEmpty()) continue;
                    // Resolve displayAs block once
                    Block display = BuiltInRegistries.BLOCK.get(ov.get().displayAs);
                    if (display == null || display == Blocks.AIR) continue;
                    desired.put(e.getKey(), display);
                }
            }
        }

        // Diff against currentlySpoofed
        java.util.List<Long> toRemove = new java.util.ArrayList<>();
        java.util.List<com.enviouse.progressivestages.common.network.NetworkHandler.OreSpoofEntry> toAdd =
            new java.util.ArrayList<>();

        // Removals: previously spoofed, no longer in desired
        Iterator<Map.Entry<Long, Block>> it = st.currentlySpoofed.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Block> entry = it.next();
            if (!desired.containsKey(entry.getKey())) {
                toRemove.add(entry.getKey());
                it.remove();
            }
        }
        // Additions: in desired, not currently spoofed
        for (Map.Entry<Long, Block> entry : desired.entrySet()) {
            if (st.currentlySpoofed.containsKey(entry.getKey())) continue;
            int stateId = Block.getId(entry.getValue().defaultBlockState());
            toAdd.add(new com.enviouse.progressivestages.common.network.NetworkHandler
                .OreSpoofEntry(entry.getKey(), stateId));
            st.currentlySpoofed.put(entry.getKey(), entry.getValue());
        }

        if (toAdd.isEmpty() && toRemove.isEmpty()) return;

        // ── ONE batched payload — kills the packet burst ──
        com.enviouse.progressivestages.common.network.NetworkHandler.OreSpoofPayload pl =
            new com.enviouse.progressivestages.common.network.NetworkHandler.OreSpoofPayload(
                toAdd, toRemove, false);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, pl);

        // Also send a vanilla ClientboundBlockUpdatePacket per affected position so
        // a player without the client mod still gets the block visually replaced
        // (light leaks aren't fixed without the client side, but the gating still
        // appears to work). For client-modded players, this is a redundant safety —
        // the OreSpoofClientState handler already applied the state.
        for (Long packed : toRemove) {
            BlockPos pos = BlockPos.of(packed);
            player.connection.send(new ClientboundBlockUpdatePacket(pos, sl.getBlockState(pos)));
        }
        for (com.enviouse.progressivestages.common.network.NetworkHandler.OreSpoofEntry e : toAdd) {
            BlockPos pos = BlockPos.of(e.packedPos());
            BlockState fake = Block.stateById(e.blockStateId());
            player.connection.send(new ClientboundBlockUpdatePacket(pos, fake));
        }
    }

    private void clearAllSpoofed(ServerPlayer player, ServerLevel sl, PlayerSpoofState st) {
        if (st.currentlySpoofed.isEmpty()) return;
        java.util.List<Long> toRemove = new java.util.ArrayList<>(st.currentlySpoofed.keySet());
        st.currentlySpoofed.clear();

        com.enviouse.progressivestages.common.network.NetworkHandler.OreSpoofPayload pl =
            new com.enviouse.progressivestages.common.network.NetworkHandler.OreSpoofPayload(
                java.util.List.of(), toRemove, true);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, pl);

        for (Long packed : toRemove) {
            BlockPos pos = BlockPos.of(packed);
            player.connection.send(new ClientboundBlockUpdatePacket(pos, sl.getBlockState(pos)));
        }
    }

    /**
     * Scan a chunk's section palettes for override-target blocks. Uses palette
     * containsState short-circuit to avoid touching sections that don't hold targets.
     */
    private ChunkCandidates buildChunkCandidates(LevelChunk chunk) {
        LockRegistry reg = LockRegistry.getInstance();
        Map<Long, Block> out = new HashMap<>();

        LevelChunkSection[] sections = chunk.getSections();
        int minY = chunk.getMinBuildHeight();
        for (int si = 0; si < sections.length; si++) {
            LevelChunkSection sec = sections[si];
            if (sec == null || sec.hasOnlyAir()) continue;

            // Palette early-out: skip the section if none of its states map to any
            // registered override target. This avoids the 4096-state walk for chunks
            // that don't actually contain ores. maybeHas() consults the palette only.
            if (!sec.maybeHas(s -> !reg.getOreOverridesFor(s.getBlock()).isEmpty())) continue;

            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(si));
            ChunkPos cp = chunk.getPos();
            int baseX = cp.getMinBlockX();
            int baseZ = cp.getMinBlockZ();

            // Section confirmed to contain at least one target. Walk full 4096 cells
            // to enumerate the actual positions.
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = sec.getBlockState(x, y, z);
                        Block b = state.getBlock();
                        if (reg.getOreOverridesFor(b).isEmpty()) continue;
                        long packed = BlockPos.asLong(baseX + x, baseY + y, baseZ + z);
                        out.put(packed, b);
                    }
                }
            }
        }
        return new ChunkCandidates(out);
    }

    /**
     * Public accessor for the chunk-rewriter mixins. Builds the candidate cache
     * on demand and returns the (BlockPos.asLong → real-Block) map. Empty map
     * means "no spoof candidates" — caller should short-circuit.
     */
    public java.util.Map<Long, Block> getOrBuildChunkCandidates(ServerLevel sl, LevelChunk chunk) {
        ChunkCacheKey key = new ChunkCacheKey(sl.dimension().location(), chunk.getPos().toLong());
        ChunkCandidates cc = chunkSpoofCandidates.computeIfAbsent(key,
            k -> buildChunkCandidates(chunk));
        return cc.targetsByPos;
    }

    /**
     * Re-send chunks in the player's view distance that contain spoofable
     * targets, so the chunk-rewriter can flip the masquerade on/off. We
     * deliberately skip chunks with no spoofables — re-forgetting them would
     * cause unnecessary client-side rerender and entity re-tracking, costing
     * a noticeable hitch on every stage change.
     *
     * <p>Uses vanilla's ForgetLevelChunkPacket + markChunkPendingToSend to
     * force a clean resend through the standard pipeline.
     */
    public void resendChunksInView(ServerPlayer player) {
        LockRegistry reg = LockRegistry.getInstance();
        if (!reg.isOreSpoofActive()) return;
        if (player == null || player.connection == null) return;
        if (!(player.level() instanceof ServerLevel sl)) return;

        int viewDist = sl.getServer().getPlayerList().getViewDistance();
        net.minecraft.world.level.ChunkPos center = player.chunkPosition();

        for (int dx = -viewDist; dx <= viewDist; dx++) {
            for (int dz = -viewDist; dz <= viewDist; dz++) {
                int cx = center.x + dx;
                int cz = center.z + dz;
                LevelChunk lc = sl.getChunkSource().getChunkNow(cx, cz);
                if (lc == null) continue;

                // Skip chunks that have no spoofable targets at all — both now
                // and post-grant they would render identically, so forgetting
                // them is pure cost.
                java.util.Map<Long, Block> candidates = getOrBuildChunkCandidates(sl, lc);
                if (candidates.isEmpty()) continue;

                net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(cx, cz);
                player.connection.send(new net.minecraft.network.protocol.game
                    .ClientboundForgetLevelChunkPacket(cp));
                player.connection.chunkSender.markChunkPendingToSend(lc);
            }
        }
    }
}
