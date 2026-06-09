package com.enviouse.progressivestages.client;

import com.enviouse.progressivestages.common.network.NetworkHandler.OreSpoofEntry;
import com.enviouse.progressivestages.common.network.NetworkHandler.OreSpoofPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v2.0.1: client-side cache of currently spoofed block positions.
 *
 * <p>Maintained by the {@code OreSpoofPayload} delta packets. Used for two things:
 * <ol>
 *   <li>Applying spoofed states to the local {@link ClientLevel} (visual replacement).</li>
 *   <li>Telling the light engine which positions to re-evaluate after a server-driven
 *       light update so emitting blocks (lava, glowstone) recomputed from the FAKE
 *       block's emission rather than the real one — kills the "light leak" issue
 *       that pure block-update-packet spoofing would otherwise show.</li>
 * </ol>
 */
public final class OreSpoofClientState {

    private OreSpoofClientState() {}

    /** Currently spoofed positions on the client (BlockPos.asLong). Thread-safe. */
    private static final Set<Long> SPOOFED_POSITIONS = ConcurrentHashMap.newKeySet();

    public static boolean isSpoofed(BlockPos pos) {
        return SPOOFED_POSITIONS.contains(pos.asLong());
    }

    public static boolean isSpoofedSection(SectionPos sp) {
        // Cheap check: scan our set for any pos in the section. For radius-8 the
        // set is ~5000 entries max; we early-return on first hit.
        long minX = sp.minBlockX();
        long minY = sp.minBlockY();
        long minZ = sp.minBlockZ();
        for (Long packed : SPOOFED_POSITIONS) {
            int x = BlockPos.getX(packed);
            if (x < minX || x > minX + 15) continue;
            int y = BlockPos.getY(packed);
            if (y < minY || y > minY + 15) continue;
            int z = BlockPos.getZ(packed);
            if (z < minZ || z > minZ + 15) continue;
            return true;
        }
        return false;
    }

    /** Apply a delta packet — runs on the render thread. */
    public static void applyDelta(OreSpoofPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        if (payload.dimensionReset()) {
            // Snappy un-spoof — drop everything; subsequent additions repopulate.
            // For each previously spoofed position, request its real state from the
            // packet stream that the server is about to send (or already sent).
            // We simply forget; vanilla block-update packets handle the visual revert.
            SPOOFED_POSITIONS.clear();
        }

        // Removals: forget + request a re-light at each removed position so light
        // recomputes from the now-real block.
        Set<SectionPos> dirtySections = new HashSet<>();
        for (Long packed : payload.removals()) {
            if (SPOOFED_POSITIONS.remove(packed)) {
                BlockPos p = BlockPos.of(packed);
                dirtySections.add(SectionPos.of(p));
            }
        }

        // Additions: apply state to client level + register pos as spoofed.
        for (OreSpoofEntry e : payload.additions()) {
            BlockPos p = BlockPos.of(e.packedPos());
            BlockState fake = Block.stateById(e.blockStateId());
            if (fake == null) continue;
            // Set the state visually. The server also sends a vanilla block-update
            // packet; this dual application is idempotent and ensures we win
            // the race regardless of arrival order.
            level.setBlock(p, fake, 0); // flags=0: no neighbor updates, no light auto-trigger
            SPOOFED_POSITIONS.add(e.packedPos());
            dirtySections.add(SectionPos.of(p));
        }

        // Light recompute: trigger checkBlock on the affected positions so the light
        // engine recomputes emission from the *fake* state (e.g. iron-ore-as-stone
        // contributes 0 light even if the real block was glowstone). This fixes the
        // "light leak through fake block" issue you flagged.
        LevelLightEngine lle = level.getLightEngine();
        // Re-check each spoofed position in dirty sections
        for (SectionPos sp : dirtySections) {
            int minX = sp.minBlockX();
            int minY = sp.minBlockY();
            int minZ = sp.minBlockZ();
            for (int dx = 0; dx < 16; dx++) {
                for (int dy = 0; dy < 16; dy++) {
                    for (int dz = 0; dz < 16; dz++) {
                        BlockPos p = new BlockPos(minX + dx, minY + dy, minZ + dz);
                        // checkBlock recomputes light at this position from current state.
                        // Cheap when the block is non-emissive (early-out in the engine).
                        lle.checkBlock(p);
                    }
                }
            }
        }
    }

    /**
     * Called by the LightUpdate mixin after vanilla applies a server light packet —
     * if the affected section contains any spoofed positions, re-check them so the
     * spoofed (low/zero) light emission overrides the server's "real" light.
     */
    public static void onLightUpdateReceived(SectionPos section) {
        if (SPOOFED_POSITIONS.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        LevelLightEngine lle = level.getLightEngine();
        int minX = section.minBlockX();
        int minY = section.minBlockY();
        int minZ = section.minBlockZ();
        // Walk only our cache, not all 4096 cells in the section
        for (Long packed : SPOOFED_POSITIONS) {
            int x = BlockPos.getX(packed);
            if (x < minX || x > minX + 15) continue;
            int y = BlockPos.getY(packed);
            if (y < minY || y > minY + 15) continue;
            int z = BlockPos.getZ(packed);
            if (z < minZ || z > minZ + 15) continue;
            lle.checkBlock(BlockPos.of(packed));
        }
    }

    /** Called on world unload / disconnect. */
    public static void clear() {
        SPOOFED_POSITIONS.clear();
    }
}
