package com.enviouse.progressivestages.server.structure;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.api.structure.StructureSessionId;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class StructureLeaseData extends SavedData {
    private static final String DATA_NAME = "progressivestages_structure_leases";
    private final Set<String> introducedStages = new LinkedHashSet<>();
    private final Set<PersistedParticipant> participants = new LinkedHashSet<>();

    public record PersistedParticipant(UUID owner, StageId stage, ResourceLocation providerId,
                                       StructureSessionId sessionId, UUID participantId) {}

    public static StructureLeaseData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new Factory<>(StructureLeaseData::new, StructureLeaseData::load), DATA_NAME);
    }

    public boolean wasIntroduced(UUID owner, StageId stage) {
        return introducedStages.contains(key(owner, stage));
    }

    public void markIntroduced(UUID owner, StageId stage) {
        if (introducedStages.add(key(owner, stage))) setDirty();
    }

    public void clear(UUID owner, StageId stage) {
        boolean changed = introducedStages.remove(key(owner, stage));
        changed |= participants.removeIf(participant -> participant.owner().equals(owner)
            && participant.stage().equals(stage));
        if (changed) setDirty();
    }

    public Set<String> snapshot() {
        return Set.copyOf(introducedStages);
    }

    public Set<StageId> stagesFor(UUID owner) {
        String prefix = owner + "|";
        Set<StageId> result = new LinkedHashSet<>();
        for (String entry : introducedStages) {
            if (!entry.startsWith(prefix)) continue;
            StageId stage = StageId.tryParse(entry.substring(prefix.length()));
            if (stage != null) result.add(stage);
        }
        return Set.copyOf(result);
    }

    public void markParticipant(UUID owner, StageId stage,
                                ResourceLocation providerId, StructureSessionId sessionId,
                                UUID participantId) {
        if (participants.add(new PersistedParticipant(owner, stage, providerId,
                sessionId, participantId))) setDirty();
    }

    public void clearParticipant(UUID owner, StageId stage,
                                 ResourceLocation providerId, StructureSessionId sessionId,
                                 UUID participantId) {
        if (participants.remove(new PersistedParticipant(owner, stage, providerId,
                sessionId, participantId))) setDirty();
    }

    public Set<PersistedParticipant> participantsFor(UUID participantId) {
        Set<PersistedParticipant> result = new LinkedHashSet<>();
        for (PersistedParticipant participant : participants) {
            if (participant.participantId().equals(participantId)) result.add(participant);
        }
        return Set.copyOf(result);
    }

    public long participantCount(UUID owner, StageId stage) {
        return participants.stream()
            .filter(participant -> participant.owner().equals(owner) && participant.stage().equals(stage))
            .count();
    }

    public static StructureLeaseData load(CompoundTag tag, HolderLookup.Provider provider) {
        StructureLeaseData data = new StructureLeaseData();
        CompoundTag entries = tag.getCompound("introduced");
        for (String key : entries.getAllKeys()) {
            if (entries.getBoolean(key)) data.introducedStages.add(key);
        }
        CompoundTag participants = tag.getCompound("participants");
        for (String key : participants.getAllKeys()) {
            if (!participants.getBoolean(key)) continue;
            PersistedParticipant participant = parseParticipant(key);
            if (participant != null) data.participants.add(participant);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag entries = new CompoundTag();
        for (String key : introducedStages) entries.putBoolean(key, true);
        tag.put("introduced", entries);
        CompoundTag participantEntries = new CompoundTag();
        for (PersistedParticipant participant : participants) {
            participantEntries.putBoolean(participantKey(participant), true);
        }
        tag.put("participants", participantEntries);
        return tag;
    }

    private static String key(UUID owner, StageId stage) {
        return owner + "|" + stage;
    }

    private static String participantKey(PersistedParticipant participant) {
        return participant.owner() + "|" + participant.stage() + "|" + participant.providerId()
            + "|" + participant.sessionId() + "|" + participant.participantId();
    }

    private static PersistedParticipant parseParticipant(String key) {
        try {
            String[] parts = key.split("\\|", 5);
            if (parts.length != 5) return null;
            return new PersistedParticipant(UUID.fromString(parts[0]), StageId.parse(parts[1]),
                ResourceLocation.parse(parts[2]), StructureSessionId.parse(parts[3]),
                UUID.fromString(parts[4]));
        } catch (RuntimeException error) {
            return null;
        }
    }
}
