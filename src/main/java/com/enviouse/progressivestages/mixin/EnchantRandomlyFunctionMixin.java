package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.server.enforcement.EnchantmentSelectionPolicy;
import com.enviouse.progressivestages.server.enforcement.LootPlayerResolver;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.Optional;

@Mixin(EnchantRandomlyFunction.class)
public abstract class EnchantRandomlyFunctionMixin {

    @Redirect(
        method = "run",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/Util;getRandomSafe(Ljava/util/List;Lnet/minecraft/util/RandomSource;)Ljava/util/Optional;"
        ),
        require = 1
    )
    private Optional<Holder<Enchantment>> progressivestages$selectForPlayer(
            List<Holder<Enchantment>> candidates,
            RandomSource random,
            ItemStack stack,
            LootContext context) {
        ServerPlayer player = LootPlayerResolver.resolveResponsiblePlayer(context);
        return EnchantmentSelectionPolicy.selectRandomLootEnchantment(player, candidates, random);
    }

    @Redirect(
        method = "run",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/loot/functions/EnchantRandomlyFunction;enchantItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Holder;Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/item/ItemStack;"
        ),
        require = 1
    )
    private ItemStack progressivestages$applyForPlayer(
            ItemStack stack,
            Holder<Enchantment> enchantment,
            RandomSource random,
            ItemStack originalStack,
            LootContext context) {
        ServerPlayer player = LootPlayerResolver.resolveResponsiblePlayer(context);
        return EnchantmentSelectionPolicy.applyRandomLootEnchantment(
            player, stack, enchantment, random);
    }
}
