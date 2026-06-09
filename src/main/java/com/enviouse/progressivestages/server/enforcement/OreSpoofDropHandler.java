package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.lock.LockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.List;

/**
 * v2.0.1: replace ore drops with displayAs-equivalent drops when the broken block
 * was spoofed for the breaker.
 *
 * <p>Behavior (option b): when the spoofed-as block is e.g. stone, normal-break
 * drops what stone would drop (cobblestone), silk-touch drops 1 stone — same as
 * actually breaking stone. Uses the displayAs block's vanilla loot table when no
 * dropAs is configured. If dropAs IS configured (e.g. dropAs = "minecraft:cobblestone"),
 * the player simply gets 1 of that item on normal break and silk-touch alike.
 */
public final class OreSpoofDropHandler {

    private OreSpoofDropHandler() {}

    public static void maybeReplaceDrops(BlockDropsEvent event, ServerPlayer player,
                                         ServerLevel sl, BlockPos pos) {
        // Authoritative: does the REAL block have an active override for this player?
        // We deliberately do NOT consult the per-player render state map here. The
        // render state can lag behind reality (chunk just loaded, player just walked
        // in, last rescan hasn't happened yet) — but the player's stage gating is
        // always authoritative, and that's what determines drops.
        LockRegistry reg = LockRegistry.getInstance();
        Block realBlock = event.getState().getBlock();
        java.util.Optional<LockRegistry.OreOverrideEntry> entryOpt =
            reg.findActiveOreOverride(player, realBlock);
        if (entryOpt.isEmpty()) return;
        LockRegistry.OreOverrideEntry entry = entryOpt.get();
        Block spoofedDisplay = BuiltInRegistries.BLOCK.get(entry.displayAs);
        if (spoofedDisplay == null) return;

        // Discard whatever drops vanilla / mods already queued for the real block.
        event.getDrops().clear();

        // Build replacement drops
        java.util.List<ItemEntity> replacements;
        if (entry.dropAs != null) {
            // Explicit dropAs override — drop exactly one of that item, regardless
            // of silk-touch (pack author chose the masquerade outcome).
            ItemStack drop = new ItemStack(BuiltInRegistries.ITEM.get(entry.dropAs));
            if (drop.isEmpty()) return;
            replacements = java.util.List.of(spawnDropEntity(sl, pos, drop));
        } else {
            // Option (b): mimic the displayAs block's loot table fully — silk-touch
            // and fortune behaviors come along for free.
            ItemStack tool = event.getTool() == null ? ItemStack.EMPTY : event.getTool();
            List<ItemStack> stacks = spoofedDisplay.defaultBlockState().getDrops(
                new LootParams.Builder(sl)
                    .withParameter(LootContextParams.ORIGIN, pos.getCenter())
                    .withParameter(LootContextParams.TOOL, tool)
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                    .withOptionalParameter(LootContextParams.BLOCK_ENTITY, null));
            if (stacks.isEmpty()) return;
            java.util.List<ItemEntity> out = new java.util.ArrayList<>(stacks.size());
            for (ItemStack s : stacks) {
                if (s != null && !s.isEmpty()) out.add(spawnDropEntity(sl, pos, s));
            }
            replacements = out;
        }
        event.getDrops().addAll(replacements);
    }

    private static ItemEntity spawnDropEntity(ServerLevel sl, BlockPos pos, ItemStack stack) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        ItemEntity ie = new ItemEntity(sl, cx, cy, cz, stack);
        ie.setDefaultPickUpDelay();
        return ie;
    }
}
