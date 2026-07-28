package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Filters locked items and sanitizes locked or over level enchantments after loot generation.
 * Player aware generation mixins handle enchantment candidate selection before this final safety pass.
 * The responsible player is preferred. A nearby player may be used for loot contexts without one.
 * Loot remains unchanged when no player can be resolved.
 */
public final class StageLootModifier extends LootModifier {

    public static final MapCodec<StageLootModifier> CODEC = RecordCodecBuilder.mapCodec(
        inst -> LootModifier.codecStart(inst).apply(inst, StageLootModifier::new));

    public StageLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (loot.isEmpty()) return loot;
        boolean filterItems = StageConfig.isBlockLootDrops();
        boolean filterEnchants = LockRegistry.getInstance().isEnchantmentRetentionConfigured();
        if (!filterItems && !filterEnchants) return loot;

        ServerPlayer player = LootPlayerResolver.resolve(context);
        if (player == null) return loot;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return loot;

        ObjectArrayList<ItemStack> filtered = new ObjectArrayList<>(loot.size());
        LockRegistry registry = LockRegistry.getInstance();
        for (ItemStack stack : loot) {
            if (stack.isEmpty()) { filtered.add(stack); continue; }
            if (filterItems && registry.isLootBlockedFor(player, stack.getItem())) continue;
            if (filterEnchants) EnchantEnforcer.stripLockedEnchants(player, stack);
            filtered.add(stack);
        }
        return filtered;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
