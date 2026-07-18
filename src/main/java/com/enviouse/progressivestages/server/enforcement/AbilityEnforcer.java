package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.lock.ConditionalRule;
import com.enviouse.progressivestages.common.stage.StageManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * v2.4 / v3.0: gates player movement abilities behind stages via {@code [abilities].locked}. Each
 * tick, a player who lacks every stage gating an ability is dropped out of that action:
 *
 * <ul>
 *   <li>{@code elytra} — stops gliding</li>
 *   <li>{@code sprint} — cancels sprinting</li>
 *   <li>{@code swim} — cancels the swimming pose</li>
 *   <li>{@code climb} — clamps upward motion on ladders/vines (can't climb up)</li>
 * </ul>
 *
 * <p>({@code crawl} on land isn't separately enforceable in vanilla — the prone pose doesn't set the
 * swim flag and the player is physically wedged in a 1-block gap — so it's intentionally not gated.)
 *
 * <p>Other abilities are better expressed via {@code [attribute]} modifiers or KubeJS, so they
 * aren't force-cancelled here. Unknown ability names simply do nothing.
 */
public final class AbilityEnforcer {

    /** ability name → stages that gate it. Empty map = feature unused (cheap fast-path). */
    private static final Map<String, Set<StageId>> GATERS = new ConcurrentHashMap<>();

    private AbilityEnforcer() {}

    public static void rebuild(Collection<StageDefinition> stages) {
        GATERS.clear();
        for (StageDefinition def : stages) {
            for (String ability : def.getLockedAbilities()) {
                GATERS.computeIfAbsent(ability, k -> ConcurrentHashMap.newKeySet()).add(def.getId());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (GATERS.isEmpty() && !ConditionalLockEngine.hasRules(ConditionalRule.TargetType.ABILITY)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return;

        cancelIfLacking("elytra", player, player::isFallFlying, player::stopFallFlying);
        cancelIfLacking("sprint", player, player::isSprinting, () -> player.setSprinting(false));
        cancelIfLacking("swim", player, player::isSwimming, () -> player.setSwimming(false));

        // climb — clamp any upward velocity while on a ladder/vine the player can't yet climb.
        if (lacks("climb", player) && player.onClimbable()) {
            Vec3 m = player.getDeltaMovement();
            if (m.y > 0) player.setDeltaMovement(m.x, Math.min(0.0, m.y), m.z);
        }
    }

    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (StageConfig.isAllowCreativeBypass() && player.isCreative()) return;
        if (!lacks("jump", player)) return;
        Vec3 movement = player.getDeltaMovement();
        if (movement.y > 0.0D) player.setDeltaMovement(movement.x, 0.0D, movement.z);
    }

    private static void cancelIfLacking(String ability, ServerPlayer player, BooleanSupplier active, Runnable cancel) {
        if (active.getAsBoolean() && lacks(ability, player)) cancel.run();
    }

    /** True if the player is missing at least one stage that gates {@code ability}. */
    private static boolean lacks(String ability, ServerPlayer player) {
        Set<StageId> set = GATERS.get(ability);
        boolean staticBlocked = false;
        if (set != null) {
            for (StageId stage : set) {
                if (!StageManager.getInstance().hasStage(player, stage)) {
                    staticBlocked = true;
                    break;
                }
            }
        }
        ResourceLocation id = ResourceLocation.tryParse(ability);
        if (id == null) id = ResourceLocation.withDefaultNamespace(ability);
        return ConditionalLockEngine.isBlocked(player, ConditionalRule.TargetType.ABILITY,
            id, null, staticBlocked);
    }
}
