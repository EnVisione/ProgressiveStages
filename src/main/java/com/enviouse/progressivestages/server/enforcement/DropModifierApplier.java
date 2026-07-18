package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.rehaul.modifier.DropModifierResolver;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorMatcherRegistry;
import com.enviouse.progressivestages.common.rehaul.selector.SelectorTarget;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.server.rehaul.MinecraftConditionContextFactory;
import com.enviouse.progressivestages.server.rehaul.RehaulRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DropModifierApplier {

    private DropModifierApplier() {}

    public static void apply(BlockDropsEvent event, ServerPlayer player) {
        RehaulRuntime runtime = RehaulRuntime.get();
        var rules = runtime.snapshot().stages().values().stream()
            .flatMap(stage -> stage.progression().dropModifiers().stream()).toList();
        if (rules.isEmpty() || event.getDrops().isEmpty()) return;
        ServerLevel level = event.getLevel();
        ItemStack tool = event.getTool();
        SelectorTarget toolTarget = tool.isEmpty() ? null : ContextualModifierApplier.target(tool);
        SelectorTarget blockTarget = target(event.getState().getBlock());
        var condition = MinecraftConditionContextFactory.create(player, runtime, Set.of("block_drops"));
        var resolver = new DropModifierResolver(SelectorMatcherRegistry.get(), runtime.conditionEvaluator());
        var stages = StageManager.getInstance().getStages(player);
        List<ItemEntity> additions = new ArrayList<>();
        event.getDrops().removeIf(entity -> {
            ItemStack stack = entity.getItem();
            int changed = resolver.resolve(rules, blockTarget, ContextualModifierApplier.target(stack),
                toolTarget, stages, condition, enchantment -> enchantmentLevel(level, tool, enchantment),
                stack.getCount());
            if (changed <= 0) return true;
            resize(entity, changed, additions);
            return false;
        });
        event.getDrops().addAll(additions);
    }

    public static SelectorTarget target(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        Set<ResourceLocation> tags = new LinkedHashSet<>();
        BuiltInRegistries.BLOCK.getHolder(id).ifPresent(holder ->
            holder.tags().forEach(tag -> tags.add(tag.location())));
        return new SelectorTarget(id, BuiltInRegistries.BLOCK.key().location(), tags, Map.of());
    }

    private static int enchantmentLevel(ServerLevel level, ItemStack tool, ResourceLocation id) {
        if (tool.isEmpty()) return 0;
        ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, id);
        var holder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key).orElse(null);
        return holder == null ? 0 : EnchantmentHelper.getEnchantmentsForCrafting(tool).getLevel(holder);
    }

    private static void resize(ItemEntity entity, int count, List<ItemEntity> additions) {
        ItemStack original = entity.getItem();
        int stackSize = Math.max(1, original.getMaxStackSize());
        ItemStack first = original.copy();
        first.setCount(Math.min(stackSize, count));
        entity.setItem(first);
        int remaining = count - first.getCount();
        while (remaining > 0) {
            ItemStack split = original.copy();
            split.setCount(Math.min(stackSize, remaining));
            ItemEntity extra = new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), split);
            extra.setDeltaMovement(entity.getDeltaMovement());
            extra.setDefaultPickUpDelay();
            additions.add(extra);
            remaining -= split.getCount();
        }
    }
}
