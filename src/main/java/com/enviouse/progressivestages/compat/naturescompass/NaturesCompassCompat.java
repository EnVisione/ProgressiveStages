package com.enviouse.progressivestages.compat.naturescompass;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.lock.LockRegistry;
import com.enviouse.progressivestages.server.enforcement.ItemEnforcer;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

import java.util.Set;

/**
 * Nature's Compass compat: when the player right-clicks the Nature's Compass item and
 * they lack the stage for ANY locked dimension, refuse the use. The search GUI shows
 * biomes across all loaded dimensions, which the plan §2.2 calls out as needing to be
 * "unavailable" — the cleanest enforcement is simply preventing the search from happening.
 *
 * <p>Covers two items (the mod ships both):
 * {@code naturescompass:natures_compass} and {@code naturescompass:map}.
 *
 * <p>If a modpack wants finer-grained filtering (show only unlocked biomes), they should
 * add the compass item to {@code [screens] locked} with a specific stage requirement instead.
 */
public final class NaturesCompassCompat {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<ResourceLocation> COMPASS_ITEMS = Set.of(
        ResourceLocation.parse("naturescompass:natures_compass"),
        ResourceLocation.parse("naturescompass:map")
    );

    private NaturesCompassCompat() {}

    public static void init() {
        NeoForge.EVENT_BUS.register(NaturesCompassCompat.class);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemUse(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return;
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        Item item = stack.getItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null || !COMPASS_ITEMS.contains(id)) return;

        // v2.0: multi-stage dimension gate. If the player lacks ANY stage gating ANY locked
        // dimension, refuse the compass scan and emit the first missing stage in the message.
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        LockRegistry reg = LockRegistry.getInstance();
        for (var level : server.getAllLevels()) {
            ResourceLocation dimId = level.dimension().location();
            if (reg.isDimensionBlockedFor(player, dimId)) {
                event.setCanceled(true);
                reg.primaryRestrictingStageForDimension(player, dimId).ifPresent(stage ->
                    ItemEnforcer.notifyLockedWithCooldown(player, stage, "Scanning dimensions"));
                return;
            }
        }
    }
}
