package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.config.StageRewards;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * v3.0: hands out a stage's {@code [rewards]} the moment it's granted — items, status effects, a
 * teleport, xp, and/or server commands. Runs from the same grant path as {@code [unlock]} juice, so
 * it fires exactly once per actual grant (not on every login/sync). No-op for stages with no rewards.
 */
public final class StageRewardApplier {

    private static final Logger LOGGER = LogUtils.getLogger();

    private StageRewardApplier() {}

    public static void apply(ServerPlayer player, StageDefinition def) {
        StageRewards r = def.getRewards();
        if (r == null || r.isEmpty() || player.server == null) return;

        // Items — split across stacks; drop whatever doesn't fit.
        for (var ic : r.items()) {
            Item item = BuiltInRegistries.ITEM.get(ic.item());
            if (item == null || item == net.minecraft.world.item.Items.AIR) continue;
            int max = Math.max(1, new ItemStack(item).getMaxStackSize());
            int remaining = ic.count();
            while (remaining > 0) {
                int n = Math.min(remaining, max);
                ItemStack stack = new ItemStack(item, n);
                if (!player.getInventory().add(stack) || !stack.isEmpty()) player.drop(stack, false);
                remaining -= n;
            }
        }

        // Status effects.
        for (StageRewards.EffectReward e : r.effects()) {
            Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.getHolder(e.effect()).orElse(null);
            if (holder != null) {
                player.addEffect(new MobEffectInstance(holder, e.durationTicks(), e.amplifier()));
            }
        }

        // XP.
        if (r.xpLevels() > 0) player.giveExperienceLevels(r.xpLevels());
        if (r.xpPoints() > 0) player.giveExperiencePoints(r.xpPoints());

        // Teleport.
        applyTeleport(player, r.teleport());

        // Server commands (run as the player at permission level 2; {player} → player name).
        if (!r.commands().isEmpty()) {
            var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            String name = player.getName().getString();
            for (String cmd : r.commands()) {
                try {
                    player.server.getCommands().performPrefixedCommand(source, cmd.replace("{player}", name));
                } catch (Throwable t) {
                    LOGGER.warn("[ProgressiveStages] reward command failed for {}: {}", def.getId(), cmd, t);
                }
            }
        }
    }

    private static void applyTeleport(ServerPlayer player, String tp) {
        if (tp == null || tp.isBlank()) return;
        String[] parts = tp.trim().split("\\s+");
        ServerLevel level = (ServerLevel) player.level();
        int idx = 0;
        if (parts.length == 4) {
            ResourceLocation dimId = ResourceLocation.tryParse(parts[0]);
            if (dimId != null) {
                ServerLevel target = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
                if (target != null) level = target;
            }
            idx = 1;
        }
        if (parts.length - idx < 3) return;
        try {
            double x = Double.parseDouble(parts[idx]);
            double y = Double.parseDouble(parts[idx + 1]);
            double z = Double.parseDouble(parts[idx + 2]);
            player.teleportTo(level, x, y, z, java.util.Set.of(), player.getYRot(), player.getXRot());
        } catch (NumberFormatException ignored) {}
    }
}
