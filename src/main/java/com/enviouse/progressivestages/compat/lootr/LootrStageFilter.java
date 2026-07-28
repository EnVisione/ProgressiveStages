package com.enviouse.progressivestages.compat.lootr;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.server.enforcement.EnchantEnforcer;
import com.enviouse.progressivestages.server.enforcement.LootPlayerResolver;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.util.RandomSource;
import noobanidus.mods.lootr.common.api.data.LootFiller;
import noobanidus.mods.lootr.common.api.filter.ILootrFilter;

import java.util.Iterator;

/**
 * Filters locked items and sanitizes locked or over level enchantments in Lootr rolls.
 * The responsible player is preferred. A nearby player may be used as a final safety fallback.
 * The mutation result is always retained.
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
        if (loot == null || loot.isEmpty()) return true;
        boolean filterItems = StageConfig.isBlockLootDrops();
        boolean filterEnchants = LockRegistry.getInstance().isEnchantmentRetentionConfigured();
        if (!filterItems && !filterEnchants) return true;

        ServerPlayer gate = LootPlayerResolver.resolve(context);
        if (gate == null) return true;
        if (StageConfig.isAllowCreativeBypass() && gate.isCreative()) return true;

        LockRegistry reg = LockRegistry.getInstance();

        Iterator<ItemStack> it = loot.iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next();
            if (stack == null || stack.isEmpty()) continue;
            if (filterItems && reg.isLootBlockedFor(gate, stack.getItem())) {
                it.remove();
                continue;
            }
            if (filterEnchants) EnchantEnforcer.stripLockedEnchants(gate, stack);
        }
        return true;
    }
}
