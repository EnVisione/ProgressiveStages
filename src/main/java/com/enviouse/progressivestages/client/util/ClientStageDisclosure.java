package com.enviouse.progressivestages.client.util;

import com.enviouse.progressivestages.client.ClientStageCache;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Client-side counterpart of {@link com.enviouse.progressivestages.common.util.StageDisclosure}.
 * Lives under client/ so its references to {@link Minecraft} and the {@link Player} typed
 * via {@code Minecraft.getInstance().player} (which is a {@code LocalPlayer}) never reach
 * a dedicated server's classloader.
 */
public final class ClientStageDisclosure {

    private ClientStageDisclosure() {}

    public static boolean mayShowRestrictingStageName() {
        if (!ClientStageCache.isHideStageNamesFromNonOps()) return true;
        Player p = Minecraft.getInstance().player;
        return p != null && p.hasPermissions(2);
    }
}
