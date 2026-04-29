package com.enviouse.progressivestages.server;

import com.enviouse.progressivestages.common.config.StageConfig;
import com.enviouse.progressivestages.common.util.TextUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Sends a chat warning to players entering creative mode that creative bypasses
 * most ProgressiveStages locks, with hints for both the per-player opt-out
 * (/progressivestages no-creative-popup) and the global config disable.
 *
 * Per-player preference persists across respawn and relog via the vanilla
 * {@link Player#PERSISTED_NBT_TAG} compound.
 */
public final class CreativeBypassNotifier {

    private static final String SUBKEY = "ProgressiveStages";
    private static final String FLAG = "hideCreativePopup";

    private CreativeBypassNotifier() {}

    /** Send the three-line popup if the global toggle is on, creative bypass is on, and the player hasn't opted out. */
    public static void sendPopupIfEligible(ServerPlayer player) {
        if (player == null) return;
        if (!StageConfig.isShowCreativeBypassPopup()) return;
        if (!StageConfig.isAllowCreativeBypass()) return;
        if (isPopupHidden(player)) return;

        player.sendSystemMessage(TextUtil.parseColorCodes(StageConfig.getMsgCreativeBypassPopupLine1()));
        player.sendSystemMessage(TextUtil.parseColorCodes(StageConfig.getMsgCreativeBypassPopupLine2()));
        player.sendSystemMessage(TextUtil.parseColorCodes(StageConfig.getMsgCreativeBypassPopupLine3()));
    }

    /** Toggle the per-player popup-hidden flag. Returns the NEW state (true = popup is now hidden). */
    public static boolean toggleHidden(ServerPlayer player) {
        boolean newState = !isPopupHidden(player);
        setHidden(player, newState);
        return newState;
    }

    public static boolean isPopupHidden(ServerPlayer player) {
        CompoundTag persisted = player.getPersistentData();
        if (!persisted.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) return false;
        CompoundTag root = persisted.getCompound(Player.PERSISTED_NBT_TAG);
        if (!root.contains(SUBKEY, Tag.TAG_COMPOUND)) return false;
        return root.getCompound(SUBKEY).getBoolean(FLAG);
    }

    private static void setHidden(ServerPlayer player, boolean hidden) {
        CompoundTag persisted = player.getPersistentData();
        CompoundTag root = persisted.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)
            ? persisted.getCompound(Player.PERSISTED_NBT_TAG)
            : new CompoundTag();
        CompoundTag mod = root.contains(SUBKEY, Tag.TAG_COMPOUND)
            ? root.getCompound(SUBKEY)
            : new CompoundTag();
        mod.putBoolean(FLAG, hidden);
        root.put(SUBKEY, mod);
        persisted.put(Player.PERSISTED_NBT_TAG, root);
    }
}
