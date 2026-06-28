package com.enviouse.progressivestages.server.enforcement;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageAttribute;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.enviouse.progressivestages.common.stage.StageManager;
import com.enviouse.progressivestages.server.loader.StageFileLoader;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;

/**
 * v2.4: applies a stage's {@code [attribute]} modifiers to players while their team owns the stage.
 *
 * <p>Uses TRANSIENT modifiers (not written to player NBT) with a stable id per (stage, index), and
 * fully reconciles on every grant/revoke/login — so there's no duplication across relogs and a
 * revoked stage's bonus disappears cleanly. Reconcile is idempotent: it removes every PS modifier
 * it manages, then re-adds only the ones for currently-owned stages.
 */
public final class StageAttributeApplier {

    private static final Logger LOGGER = LogUtils.getLogger();

    private StageAttributeApplier() {}

    /** Modifier id for the i-th attribute of a stage; stable so removeModifier always matches. */
    private static ResourceLocation modifierId(StageId stageId, int index) {
        return ResourceLocation.fromNamespaceAndPath("progressivestages", "attr/" + stageId.getPath() + "/" + index);
    }

    /** Re-apply all stage attribute modifiers for one player based on their team's owned stages. */
    public static void reconcile(ServerPlayer player) {
        if (player == null) return;
        Set<StageId> owned = StageManager.getInstance().getStages(player);
        boolean healthTouched = false;

        for (StageDefinition def : StageFileLoader.getInstance().getAllStages()) {
            List<StageAttribute> attrs = def.getAttributes();
            if (attrs.isEmpty()) continue;
            boolean has = owned.contains(def.getId());

            for (int i = 0; i < attrs.size(); i++) {
                StageAttribute a = attrs.get(i);
                Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(a.attribute()).orElse(null);
                if (holder == null) {
                    LOGGER.debug("[ProgressiveStages] Unknown attribute '{}' on stage {}", a.attribute(), def.getId());
                    continue;
                }
                AttributeInstance inst = player.getAttribute(holder);
                if (inst == null) continue;

                ResourceLocation modId = modifierId(def.getId(), i);
                inst.removeModifier(modId);
                if (has) {
                    inst.addTransientModifier(new AttributeModifier(modId, a.amount(), a.operation()));
                }
                if (a.attribute().equals(BuiltInRegistries.ATTRIBUTE.getKey(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH.value()))) {
                    healthTouched = true;
                }
            }
        }

        // Clamp current health if max health dropped (e.g. a +health stage was revoked).
        if (healthTouched && player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /** Reconcile every online member of a team (call after a team grant/revoke). */
    public static void reconcileTeam(net.minecraft.server.MinecraftServer server, java.util.UUID teamId) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (com.enviouse.progressivestages.common.team.TeamProvider.getInstance().getTeamId(p).equals(teamId)) {
                reconcile(p);
            }
        }
    }
}
