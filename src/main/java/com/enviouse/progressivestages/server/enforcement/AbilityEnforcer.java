package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.stage.StageManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v2.4: gates player abilities behind stages via {@code [abilities].locked}. Currently supports
 * {@code "elytra"} — a player without every elytra-gating stage they're missing is dropped out of
 * gliding each tick. (Other movement abilities are better expressed via {@code [attribute]} modifiers
 * or KubeJS, so they aren't force-cancelled here.)
 */
public final class AbilityEnforcer {

    private static final Set<StageId> elytraGaters = ConcurrentHashMap.newKeySet();

    private AbilityEnforcer() {}

    public static void rebuild(Collection<StageDefinition> stages) {
        elytraGaters.clear();
        for (StageDefinition def : stages) {
            if (def.getLockedAbilities().contains("elytra")) elytraGaters.add(def.getId());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (elytraGaters.isEmpty()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.isFallFlying()) return;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return;
        for (StageId s : elytraGaters) {
            if (!StageManager.getInstance().hasStage(player, s)) {
                player.stopFallFlying();
                return;
            }
        }
    }
}
