package com.enviouse.progressivestages.server.triggers;

import com.enviouse.progressivestages.common.api.StageId;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * v3.0: records which stages a team actually PURCHASED from the skill tree, so {@code [cost]}'s
 * {@code refund_percent} only ever refunds stages that were paid for. Without this, a stage earned
 * via a trigger/command/quest — or a purchasable temporary stage that auto-expires and is re-bought —
 * would mint free items/xp on every revoke. Anchored to the overworld; saved in
 * {@code world/data/progressivestages_purchases.dat}.
 */
public class StagePurchaseData extends SavedData {

    private static final String DATA_NAME = "progressivestages_purchases";

    /** Set of keys "teamId|stageId" for stages this team has paid for and not yet been refunded. */
    private final Set<String> paid = new HashSet<>();

    public static StagePurchaseData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new Factory<>(StagePurchaseData::new, StagePurchaseData::load), DATA_NAME);
    }

    private static String key(UUID teamId, StageId stageId) {
        return teamId.toString() + "|" + stageId.toString();
    }

    public void markPaid(UUID teamId, StageId stageId) {
        if (paid.add(key(teamId, stageId))) setDirty();
    }

    public boolean isPaid(UUID teamId, StageId stageId) {
        return paid.contains(key(teamId, stageId));
    }

    /** Consume the paid flag (call when refunding) so a stage is refunded at most once per purchase. */
    public boolean consumePaid(UUID teamId, StageId stageId) {
        boolean removed = paid.remove(key(teamId, stageId));
        if (removed) setDirty();
        return removed;
    }

    public static StagePurchaseData load(CompoundTag tag, HolderLookup.Provider provider) {
        StagePurchaseData d = new StagePurchaseData();
        net.minecraft.nbt.ListTag list = tag.getList("paid", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) d.paid.add(list.getString(i));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (String k : paid) list.add(net.minecraft.nbt.StringTag.valueOf(k));
        tag.put("paid", list);
        return tag;
    }
}
