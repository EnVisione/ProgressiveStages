package com.enviouse.progressivestages.mixin;

import com.enviouse.progressivestages.server.enforcement.EnchantmentSelectionPolicy;
import com.enviouse.progressivestages.server.enforcement.LootPlayerResolver;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.EnchantWithLevelsFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

@Mixin(EnchantWithLevelsFunction.class)
public abstract class EnchantWithLevelsFunctionMixin {

    @Redirect(
        method = "run",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;enchantItem(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/core/RegistryAccess;Ljava/util/Optional;)Lnet/minecraft/world/item/ItemStack;"
        ),
        require = 1
    )
    private ItemStack progressivestages$enchantForPlayer(
            RandomSource random,
            ItemStack stack,
            int level,
            RegistryAccess registryAccess,
            Optional<HolderSet<Enchantment>> possibleEnchantments,
            ItemStack originalStack,
            LootContext context) {
        ServerPlayer player = LootPlayerResolver.resolveResponsiblePlayer(context);
        return EnchantmentSelectionPolicy.enchantWithLevels(
            player, random, stack, level, registryAccess, possibleEnchantments);
    }
}
