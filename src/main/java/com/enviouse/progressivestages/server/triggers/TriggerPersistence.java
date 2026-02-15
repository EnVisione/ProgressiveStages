package com.enviouse.progressivestages.server.triggers;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.*;

/**
 * Persists "already triggered" state for one-time triggers (dimensions, bosses, etc.).
 *
 * <p>This prevents re-granting stages after server restart or datapack reload
 * for triggers that should only fire once per player/team.
 *
 * <p><b>Storage:</b> Anchored to the overworld's data storage, making it <b>global</b>
 * across all dimensions. Data persists in: {@code world/data/progressivestages_triggers.dat}
 *
 * <p><b>Important:</b> Always call {@link #setDirty()} after mutations to ensure persistence.
 */
public class TriggerPersistence extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "progressivestages_triggers";

    // Map: trigger type + trigger key -> Set of player/team UUIDs that have triggered it
    // Example: "dimension:minecraft:the_nether" -> {player1UUID, player2UUID}
    private final Map<String, Set<UUID>> triggeredMap = new HashMap<>();

    public TriggerPersistence() {
    }

    /**
     * Load from NBT.
     */
    public static TriggerPersistence load(CompoundTag tag, HolderLookup.Provider provider) {
        TriggerPersistence persistence = new TriggerPersistence();
        persistence.loadFromTag(tag);
        return persistence;
    }

    /**
     * Get or create the trigger persistence for a server.
     */
    public static TriggerPersistence get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new Factory<>(TriggerPersistence::new, TriggerPersistence::load),
            DATA_NAME
        );
    }

    /**
     * Check if a trigger has already been activated for a player/team.
     *
     * @param triggerType The type of trigger (e.g., "dimension", "boss")
     * @param triggerKey The specific trigger key (e.g., "minecraft:the_nether")
     * @param targetId The player or team UUID
     * @return true if already triggered, false otherwise
     */
    public boolean hasTriggered(String triggerType, String triggerKey, UUID targetId) {
        String key = triggerType + ":" + triggerKey;
        Set<UUID> triggered = triggeredMap.get(key);
        return triggered != null && triggered.contains(targetId);
    }

    /**
     * Mark a trigger as activated for a player/team.
     *
     * @param triggerType The type of trigger (e.g., "dimension", "boss")
     * @param triggerKey The specific trigger key (e.g., "minecraft:the_nether")
     * @param targetId The player or team UUID
     */
    public void markTriggered(String triggerType, String triggerKey, UUID targetId) {
        String key = triggerType + ":" + triggerKey;
        triggeredMap.computeIfAbsent(key, k -> new HashSet<>()).add(targetId);
        setDirty();

        LOGGER.debug("[ProgressiveStages] Marked trigger as activated: {} for {}", key, targetId);
    }

    /**
     * Clear a specific trigger for a player/team (useful for testing/admin).
     */
    public void clearTrigger(String triggerType, String triggerKey, UUID targetId) {
        String key = triggerType + ":" + triggerKey;
        Set<UUID> triggered = triggeredMap.get(key);
        if (triggered != null) {
            triggered.remove(targetId);
            if (triggered.isEmpty()) {
                triggeredMap.remove(key);
            }
            setDirty();
        }
    }

    /**
     * Clear all triggers for a player/team.
     */
    public void clearAllTriggersFor(UUID targetId) {
        boolean changed = false;
        Iterator<Map.Entry<String, Set<UUID>>> it = triggeredMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Set<UUID>> entry = it.next();
            if (entry.getValue().remove(targetId)) {
                changed = true;
                if (entry.getValue().isEmpty()) {
                    it.remove();
                }
            }
        }
        if (changed) {
            setDirty();
        }
    }

    // ============ Serialization ============

    private void loadFromTag(CompoundTag tag) {
        CompoundTag triggers = tag.getCompound("triggers");
        for (String key : triggers.getAllKeys()) {
            ListTag uuidList = triggers.getList(key, Tag.TAG_STRING);
            Set<UUID> uuids = new HashSet<>();
            for (int i = 0; i < uuidList.size(); i++) {
                try {
                    uuids.add(UUID.fromString(uuidList.getString(i)));
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("[ProgressiveStages] Invalid UUID in trigger data: {}", uuidList.getString(i));
                }
            }
            if (!uuids.isEmpty()) {
                triggeredMap.put(key, uuids);
            }
        }

        LOGGER.debug("[ProgressiveStages] Loaded {} trigger entries", triggeredMap.size());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag triggers = new CompoundTag();
        for (Map.Entry<String, Set<UUID>> entry : triggeredMap.entrySet()) {
            ListTag uuidList = new ListTag();
            for (UUID uuid : entry.getValue()) {
                uuidList.add(StringTag.valueOf(uuid.toString()));
            }
            triggers.put(entry.getKey(), uuidList);
        }
        tag.put("triggers", triggers);
        return tag;
    }
}

