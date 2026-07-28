package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public final class EnchantmentSelectionPolicy {

    private EnchantmentSelectionPolicy() {}

    public static List<EnchantmentInstance> selectForPlayer(
            @Nullable ServerPlayer player,
            RandomSource random,
            ItemStack stack,
            int level,
            Stream<Holder<Enchantment>> possibleEnchantments) {
        if (!isActiveFor(player)) {
            return EnchantmentHelper.selectEnchantment(random, stack, level, possibleEnchantments);
        }

        List<EnchantmentInstance> selected = new ArrayList<>();
        int enchantability = stack.getEnchantmentValue();
        if (enchantability <= 0) return selected;

        level += 1 + random.nextInt(enchantability / 4 + 1) + random.nextInt(enchantability / 4 + 1);
        float variance = (random.nextFloat() + random.nextFloat() - 1.0F) * 0.15F;
        level = Mth.clamp(Math.round(level + level * variance), 1, Integer.MAX_VALUE);

        List<EnchantmentInstance> available = getAvailableResults(player, level, stack, possibleEnchantments);
        if (available.isEmpty()) return selected;

        pickTableCandidate(player, random, available).ifPresent(selected::add);
        while (random.nextInt(50) <= level) {
            if (!selected.isEmpty()) {
                EnchantmentHelper.filterCompatibleEnchantments(available, Util.lastOf(selected));
            }
            if (available.isEmpty()) break;
            pickTableCandidate(player, random, available).ifPresent(selected::add);
            level /= 2;
        }
        return selected;
    }

    public static ItemStack enchantWithLevels(
            @Nullable ServerPlayer player,
            RandomSource random,
            ItemStack stack,
            int level,
            RegistryAccess registryAccess,
            Optional<? extends HolderSet<Enchantment>> possibleEnchantments) {
        if (!isActiveFor(player)) {
            return EnchantmentHelper.enchantItem(random, stack, level, registryAccess, possibleEnchantments);
        }
        Stream<Holder<Enchantment>> candidates = possibleEnchantments
            .map(HolderSet::stream)
            .orElseGet(() -> registryAccess.registryOrThrow(Registries.ENCHANTMENT)
                .holders().map(Function.identity()));
        return applySelected(stack, selectForPlayer(player, random, stack, level, candidates));
    }

    public static Optional<Holder<Enchantment>> selectRandomLootEnchantment(
            @Nullable ServerPlayer player,
            List<Holder<Enchantment>> candidates,
            RandomSource random) {
        if (!isActiveFor(player)) return Util.getRandomSafe(candidates, random);

        List<WeightedCandidate<Holder<Enchantment>>> available = new ArrayList<>();
        LockRegistry registry = LockRegistry.getInstance();
        for (Holder<Enchantment> holder : candidates) {
            ResourceLocation id = idOf(holder);
            if (id == null || registry.isEnchantmentBlockedFor(player, id, holder)) continue;
            int cap = registry.effectiveEnchantCap(player, id);
            if (cap < holder.value().getMinLevel()) continue;
            int weight = registry.effectiveEnchantSelectionWeight(player, id, 1);
            if (weight > 0) available.add(new WeightedCandidate<>(holder, weight));
        }
        return pickWeighted(random, available);
    }

    public static ItemStack applyRandomLootEnchantment(
            @Nullable ServerPlayer player,
            ItemStack stack,
            Holder<Enchantment> enchantment,
            RandomSource random) {
        if (!isActiveFor(player)) {
            int level = Mth.nextInt(random, enchantment.value().getMinLevel(), enchantment.value().getMaxLevel());
            return applySingle(stack, enchantment, level);
        }

        ResourceLocation id = idOf(enchantment);
        if (id == null) return stack;
        LockRegistry registry = LockRegistry.getInstance();
        if (registry.isEnchantmentBlockedFor(player, id, enchantment)) return stack;
        int maximum = Math.min(enchantment.value().getMaxLevel(), registry.effectiveEnchantCap(player, id));
        if (maximum < enchantment.value().getMinLevel()) return stack;
        int level = Mth.nextInt(random, enchantment.value().getMinLevel(), maximum);
        return applySingle(stack, enchantment, level);
    }

    static <T> Optional<T> pickWeighted(RandomSource random, List<WeightedCandidate<T>> candidates) {
        if (candidates.isEmpty()) return Optional.empty();
        List<WeightedEntry.Wrapper<T>> weighted = candidates.stream()
            .filter(candidate -> candidate.weight() > 0)
            .map(candidate -> WeightedEntry.wrap(candidate.value(), candidate.weight()))
            .toList();
        return WeightedRandom.getRandomItem(random, weighted).map(WeightedEntry.Wrapper::data);
    }

    record WeightedCandidate<T>(T value, int weight) {}

    private static List<EnchantmentInstance> getAvailableResults(
            ServerPlayer player,
            int level,
            ItemStack stack,
            Stream<Holder<Enchantment>> possibleEnchantments) {
        List<EnchantmentInstance> available = new ArrayList<>();
        LockRegistry registry = LockRegistry.getInstance();
        possibleEnchantments.filter(stack::isPrimaryItemFor).forEach(holder -> {
            ResourceLocation id = idOf(holder);
            if (id == null || registry.isEnchantmentBlockedFor(player, id, holder)) return;
            if (registry.effectiveEnchantSelectionWeight(
                    player, id, holder.value().getWeight()) <= 0) return;
            int maximum = Math.min(holder.value().getMaxLevel(), registry.effectiveEnchantCap(player, id));
            for (int candidateLevel = maximum; candidateLevel >= holder.value().getMinLevel(); candidateLevel--) {
                if (level >= holder.value().getMinCost(candidateLevel)
                        && level <= holder.value().getMaxCost(candidateLevel)) {
                    available.add(new EnchantmentInstance(holder, candidateLevel));
                    break;
                }
            }
        });
        return available;
    }

    private static Optional<EnchantmentInstance> pickTableCandidate(
            ServerPlayer player,
            RandomSource random,
            List<EnchantmentInstance> candidates) {
        LockRegistry registry = LockRegistry.getInstance();
        List<WeightedCandidate<EnchantmentInstance>> weighted = new ArrayList<>(candidates.size());
        for (EnchantmentInstance candidate : candidates) {
            ResourceLocation id = idOf(candidate.enchantment);
            if (id == null) continue;
            int weight = registry.effectiveEnchantSelectionWeight(
                player, id, candidate.enchantment.value().getWeight());
            if (weight > 0) weighted.add(new WeightedCandidate<>(candidate, weight));
        }
        return pickWeighted(random, weighted);
    }

    private static ItemStack applySelected(ItemStack stack, List<EnchantmentInstance> selected) {
        if (selected.isEmpty()) return stack;
        if (stack.is(Items.BOOK)) stack = new ItemStack(Items.ENCHANTED_BOOK);
        for (EnchantmentInstance candidate : selected) {
            stack.enchant(candidate.enchantment, candidate.level);
        }
        return stack;
    }

    private static ItemStack applySingle(ItemStack stack, Holder<Enchantment> enchantment, int level) {
        if (stack.is(Items.BOOK)) stack = new ItemStack(Items.ENCHANTED_BOOK);
        stack.enchant(enchantment, level);
        return stack;
    }

    private static boolean isActiveFor(@Nullable ServerPlayer player) {
        if (player == null || !LockRegistry.getInstance().isEnchantmentEnforcementConfigured()) return false;
        return !StageConfig.isAllowCreativeBypass() || !player.isCreative();
    }

    private static ResourceLocation idOf(Holder<Enchantment> holder) {
        return holder.unwrapKey().map(key -> key.location()).orElse(null);
    }
}
