package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Enforces {@code [loot] locked = [...]}.
 *
 * <p>Filters {@link ItemEntity} drop collections produced by
 * {@code LivingDropsEvent} and {@code BlockDropsEvent}. If the nearest player
 * (or the explicit killer, when we have one) lacks the stage for a locked item,
 * the drop is removed from the collection before it spills into the world.
 *
 * <p>Note: loot tables for generated chests are not filtered here — those
 * require a Global Loot Modifier and are deferred.
 */
public final class LootEnforcer {

    private LootEnforcer() {}

    /** Filter a LivingDropsEvent-style collection in place. */
    public static void filterLivingDrops(Collection<ItemEntity> drops, ServerLevel level,
                                         double x, double y, double z, Player explicitKiller) {
        if (!StageConfig.isBlockLootDrops()) return;
        if (drops.isEmpty()) return;

        ServerPlayer gate = (explicitKiller instanceof ServerPlayer sp)
            ? sp
            : NearestPlayerCheck.findNearest(level, x, y, z, StageConfig.getMobSpawnCheckRadius());
        if (gate == null) return;
        if (StageConfig.isAllowCreativeBypass() && gate.isCreative()) return;

        Iterator<ItemEntity> it = drops.iterator();
        while (it.hasNext()) {
            ItemEntity entity = it.next();
            if (isStackLockedFor(gate, entity.getItem())) {
                it.remove();
            }
        }
    }

    /**
     * Filter a block-drops list (NeoForge's {@code BlockDropsEvent#getDrops()}).
     * {@code breaker} may be null for drops from explosions or pistons.
     */
    public static void filterBlockDrops(List<ItemEntity> drops, ServerLevel level,
                                        double x, double y, double z, Player breaker) {
        filterLivingDrops(drops, level, x, y, z, breaker);
    }

    /**
     * Crop-specific harvest filter: when the broken block is a crop that's locked for the
     * nearest player, keep only seed items and drop everything else (wheat produce, carrots,
     * potatoes, etc.). Heuristic: a drop "looks like a seed" if its registry path contains
     * the substring {@code seed} — covers vanilla ({@code wheat_seeds}, {@code beetroot_seeds})
     * and most modded crops that follow the same naming.
     *
     * <p>v2.0: multi-stage — only strips non-seed drops when the gate's full multi-stage
     * requirement is missing for the nearest/breaker player.
     */
    public static void filterCropHarvest(List<ItemEntity> drops, ServerLevel level,
                                         net.minecraft.world.level.block.Block crop,
                                         double x, double y, double z, Player breaker) {
        if (!StageConfig.isBlockCropGrowth()) return;
        if (drops.isEmpty()) return;

        // Only filter if the crop itself is actually in the [crops] category.
        java.util.Set<StageId> gating = LockRegistry.getInstance().getRequiredStagesForCrop(crop);
        if (gating.isEmpty()) return;

        ServerPlayer gate = (breaker instanceof ServerPlayer sp) ? sp
            : NearestPlayerCheck.findNearest(level, x, y, z, StageConfig.getMobSpawnCheckRadius());
        if (gate == null) return;
        if (StageConfig.isAllowCreativeBypass() && gate.isCreative()) return;
        if (LockRegistry.getInstance().playerHasAllStages(gate, gating)) return;

        drops.removeIf(entity -> {
            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(entity.getItem().getItem());
            return id == null || !id.getPath().contains("seed");
        });
    }

    private static boolean isStackLockedFor(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return false;
        return LockRegistry.getInstance().isLootBlockedFor(player, stack.getItem());
    }
}
