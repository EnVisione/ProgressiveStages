package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Global Loot Modifier that filters locked items out of every loot-table result —
 * chests, fishing, archeology, mob drops, block drops, entity drops. Registered
 * as a codec in {@link com.enviouse.progressivestages.common.LootModifiers}.
 *
 * <p>Player resolution for the filter:
 * <ol>
 *   <li>Prefer {@link LootContextParams#LAST_DAMAGE_PLAYER} (mob/block drops).</li>
 *   <li>Fall back to {@link LootContextParams#THIS_ENTITY} if it's a player (chest open).</li>
 *   <li>Fall back to the nearest player via {@link LootContextParams#ORIGIN}
 *       within the configured mob-spawn radius (structure chests, explosion drops).</li>
 * </ol>
 * If no player is in range at all, loot passes through unmodified — same policy
 * as the mob-spawn gate.
 */
public final class StageLootModifier extends LootModifier {

    public static final MapCodec<StageLootModifier> CODEC = RecordCodecBuilder.mapCodec(
        inst -> LootModifier.codecStart(inst).apply(inst, StageLootModifier::new));

    public StageLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (!StageConfig.isBlockLootDrops()) return loot;
        if (loot.isEmpty()) return loot;

        ServerPlayer player = resolvePlayer(context);
        if (player == null) return loot;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return loot;

        ObjectArrayList<ItemStack> filtered = new ObjectArrayList<>(loot.size());
        LockRegistry registry = LockRegistry.getInstance();
        for (ItemStack stack : loot) {
            if (stack.isEmpty()) { filtered.add(stack); continue; }
            Item item = stack.getItem();
            // v2.0: multi-stage — drops stack if ANY required stage is missing.
            if (registry.isLootBlockedFor(player, item)) {
                continue; // drop this stack from the result
            }
            filtered.add(stack);
        }
        return filtered;
    }

    private static ServerPlayer resolvePlayer(LootContext context) {
        Entity killer = context.getParamOrNull(LootContextParams.LAST_DAMAGE_PLAYER);
        if (killer instanceof ServerPlayer sp) return sp;

        Entity self = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (self instanceof ServerPlayer sp) return sp;

        // Origin-based fallback (structure chests, explosion drops)
        var origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin != null && context.getLevel() instanceof ServerLevel sl) {
            return NearestPlayerCheck.findNearest(sl,
                origin.x, origin.y, origin.z,
                StageConfig.getMobSpawnCheckRadius());
        }
        return null;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
