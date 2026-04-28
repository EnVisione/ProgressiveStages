package com.enviouse.progressivestages.compat.mekanism;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.server.enforcement.NearestPlayerCheck;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.slf4j.Logger;

/**
 * Mekanism compat — best-effort gating without a hard dependency on Mekanism's API.
 *
 * <p>Coverage matrix (what actually gets gated):
 * <ul>
 *   <li><b>Machine blocks (factories, infusers, tanks)</b> — fully gated via the existing
 *       {@code [blocks]} / {@code [screens]} categories. Users add
 *       {@code "mod:mekanism"} or tag-based entries to those lists.</li>
 *   <li><b>Fluid placement in world</b> — {@code [fluids]} category + {@code FluidPlaceBlockEvent}.</li>
 *   <li><b>Mekanism entities (Robit)</b> — {@code [entities]} + entity spawn category.</li>
 *   <li><b>Tube / pipe transport of fluids between machines</b> — <i>only partially gated:</i>
 *       Mekanism pipes call their own chemical/fluid APIs which don't emit vanilla events, so
 *       we can't intercept the transfer call itself. The practical workaround is to lock the
 *       <em>source</em> machine block (players can't open it to configure it) which
 *       effectively blocks pipe extraction in most setups.</li>
 * </ul>
 *
 * <p>What this class actually does: when a Mekanism block entity loads into the world, we
 * check whether its mod-namespace is in {@code [blocks]} locked and if so log a debug line.
 * No runtime gating beyond what the vanilla-event enforcers already provide.
 *
 * <p>The hooks below are conservative — Mekanism's API shape varies across versions. If
 * either the classloader can't find Mekanism's block entity classes or the check throws,
 * the compat quietly disables itself.
 */
public final class MekanismCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    private MekanismCompat() {}

    public static void init() {
        NeoForge.EVENT_BUS.register(MekanismCompat.class);
        LOGGER.info("[ProgressiveStages] Mekanism compat active — vanilla-event coverage. "
            + "Pipe transport of locked fluids requires locking the source machine block in [blocks] / [screens].");
    }

    /**
     * When a Mekanism-owned entity (Robit etc.) attempts to join the level near a player
     * who lacks the stage, cancel the join. This supplements {@code MobSpawnEnforcer}
     * which runs on FinalizeSpawnEvent — Mekanism occasionally adds entities through
     * {@code level.addFreshEntity()} without firing FinalizeSpawnEvent first.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!StageConfig.isBlockMobSpawns()) return;
        Entity entity = event.getEntity();
        if (entity == null || event.loadedFromDisk()) return;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id == null || !"mekanism".equals(id.getNamespace())) return;

        if (!(entity.level() instanceof ServerLevel sl)) return;
        // v2.0: multi-stage spawn gate.
        java.util.Set<StageId> gating = LockRegistry.getInstance().getRequiredStagesForSpawn(entity.getType());
        if (gating.isEmpty()) return;
        if (NearestPlayerCheck.nearestPlayerLacksAll(sl, entity.getX(), entity.getY(), entity.getZ(),
                StageConfig.getMobSpawnCheckRadius(), gating)) {
            event.setCanceled(true);
        }
    }

    /**
     * When a player breaks a Mekanism machine block, any fluid/gas it contained is
     * normally ejected into the world. If that fluid is gated, our FluidPlaceBlockEvent
     * already cancels re-placement; this is a courtesy log so modpack makers can see
     * the gate firing.
     *
     * <p>v2.0: multi-stage — debug log fires when the player lacks ANY required stage.
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!StageConfig.isDebugLogging()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (be == null) return;
        ResourceLocation beId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
        if (beId == null || !"mekanism".equals(beId.getNamespace())) return;

        if (LockRegistry.getInstance().isBlockBlockedFor(player, event.getState().getBlock())) {
            var primary = LockRegistry.getInstance().primaryRestrictingStageForBlock(player, event.getState().getBlock());
            primary.ifPresent(stage -> LOGGER.debug(
                "[ProgressiveStages] Mekanism block {} broken by player lacking stage {} — vanilla break path gated.",
                beId, stage));
        }
    }
}
