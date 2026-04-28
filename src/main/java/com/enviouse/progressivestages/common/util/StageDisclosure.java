package com.enviouse.progressivestages.common.util;

import com.enviouse.progressivestages.client.ClientStageCache;
import com.enviouse.progressivestages.common.config.StageConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Single chokepoint for "may we show the restricting stage name?" gating.
 * Server side checks player.hasPermissions(2) when the config is true; client side
 * uses Minecraft.getInstance().player against the mirrored flag from the server.
 */
public final class StageDisclosure {

    private StageDisclosure() {}

    public static boolean mayShowRestrictingStageName(ServerPlayer player) {
        if (!StageConfig.isRevealStageNamesOnlyToOperators()) return true;
        return player != null && player.hasPermissions(2);
    }

    public static boolean mayShowRestrictingStageNameClient() {
        if (!ClientStageCache.isHideStageNamesFromNonOps()) return true;
        Player p = Minecraft.getInstance().player;
        return p != null && p.hasPermissions(2);
    }
}
