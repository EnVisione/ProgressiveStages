package com.enviouse.progressivestages.common;

import com.enviouse.progressivestages.common.util.Constants;
import com.enviouse.progressivestages.server.enforcement.StageLootModifier;
import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Registry holder for our Global Loot Modifier codec. The codec is registered
 * against {@link NeoForgeRegistries.Keys#GLOBAL_LOOT_MODIFIER_SERIALIZERS}; the
 * modifier itself gets instantiated from the built-in datapack JSON at
 * {@code data/progressivestages/loot_modifiers/stage_filter.json}.
 */
public final class LootModifiers {

    private LootModifiers() {}

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
        DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Constants.MOD_ID);

    public static final Supplier<MapCodec<? extends IGlobalLootModifier>> STAGE_FILTER =
        LOOT_MODIFIERS.register("stage_filter", () -> StageLootModifier.CODEC);

    public static void register(IEventBus modBus) {
        LOOT_MODIFIERS.register(modBus);
    }
}
