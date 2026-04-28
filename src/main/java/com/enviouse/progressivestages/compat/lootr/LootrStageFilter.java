package com.enviouse.progressivestages.compat.lootr;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.server.enforcement.NearestPlayerCheck;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.util.RandomSource;
import noobanidus.mods.lootr.common.api.data.LootFiller;
import noobanidus.mods.lootr.common.api.filter.ILootrFilter;

import java.util.Iterator;

/**
 * Lootr filter that strips locked items out of a Lootr per-player loot roll.
 *
 * <p>Flow: Lootr calls {@code mutate(toMutate, state, context, random)} with the loot
 * stack it's about to give the player. We pull the player out of the LootContext
 * ({@code THIS_ENTITY} for chest opens, {@code LAST_DAMAGE_PLAYER} for mob kills,
 * nearest-to-origin as a final fallback), then drop every item that's locked for them.
 * The return value is always {@code true} — we always want the mutation result used,
 * whether or not anything changed.
 */
public final class LootrStageFilter implements ILootrFilter {

    static final String NAME = "progressivestages:stage_filter";
    static final int PRIORITY = 1000; // Run late so we filter after any additive mutators.

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean mutate(ObjectArrayList<ItemStack> loot, LootFiller.LootFillerState state,
                          LootContext context, RandomSource random) {
        if (!StageConfig.isBlockLootDrops()) return true;
        if (loot == null || loot.isEmpty()) return true;

        ServerPlayer gate = resolvePlayer(context);
        if (gate == null) return true;
        if (StageConfig.isAllowCreativeBypass() && gate.isCreative()) return true;

        LockRegistry reg = LockRegistry.getInstance();

        Iterator<ItemStack> it = loot.iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next();
            if (stack == null || stack.isEmpty()) continue;
            // v2.0: multi-stage — drop stack if ANY required stage is missing.
            if (reg.isLootBlockedFor(gate, stack.getItem())) {
                it.remove();
            }
        }
        return true;
    }

    private static ServerPlayer resolvePlayer(LootContext context) {
        Entity killer = context.getParamOrNull(LootContextParams.LAST_DAMAGE_PLAYER);
        if (killer instanceof ServerPlayer sp) return sp;
        Entity self = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (self instanceof ServerPlayer sp) return sp;

        var origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin != null && context.getLevel() instanceof ServerLevel sl) {
            return NearestPlayerCheck.findNearest(sl,
                origin.x, origin.y, origin.z, StageConfig.getMobSpawnCheckRadius());
        }
        return null;
    }
}
