package com.enviouse.progressivestages.server.enforcement;

import net.minecraft.server.level.ServerPlayer;

public interface TrackedEntityBridge {
    void progressivestages$refreshPlayer(ServerPlayer player);
}
