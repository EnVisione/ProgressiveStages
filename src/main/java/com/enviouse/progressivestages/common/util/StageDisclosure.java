package com.enviouse.progressivestages.common.util;

import com.enviouse.progressivestages.common.config.StageConfig;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side chokepoint for "may we name the restricting stage to this player?"
 *
 * The client-side equivalent lives in {@link com.enviouse.progressivestages.client.util.ClientStageDisclosure}
 * to keep client classes (Minecraft, LocalPlayer) out of common/server bytecode —
 * NeoForge's RuntimeDistCleaner refuses to classload them on a dedicated server.
 */
public final class StageDisclosure {

    private StageDisclosure() {}

    public static boolean mayShowRestrictingStageName(ServerPlayer player) {
        if (!StageConfig.isRevealStageNamesOnlyToOperators()) return true;
        return player != null && player.hasPermissions(2);
    }
}
