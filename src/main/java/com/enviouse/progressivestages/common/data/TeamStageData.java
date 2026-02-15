package com.enviouse.progressivestages.common.data;

import com.enviouse.progressivestages.common.api.StageId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Data class representing a team's stages.
 * This is stored as a data attachment on the server level.
 */
public class TeamStageData {

    public static final Codec<TeamStageData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.unboundedMap(
                Codec.STRING,
                Codec.list(ResourceLocation.CODEC)
            ).fieldOf("team_stages").forGetter(TeamStageData::serializeTeamStages)
        ).apply(instance, TeamStageData::new)
    );

    // Team UUID -> Set of stage IDs
    private final Map<UUID, Set<StageId>> teamStages = new HashMap<>();

    public TeamStageData() {}

    private TeamStageData(Map<String, List<ResourceLocation>> serialized) {
        for (Map.Entry<String, List<ResourceLocation>> entry : serialized.entrySet()) {
            try {
                UUID teamId = UUID.fromString(entry.getKey());
                Set<StageId> stages = new HashSet<>();
                for (ResourceLocation rl : entry.getValue()) {
                    stages.add(StageId.fromResourceLocation(rl));
                }
                teamStages.put(teamId, stages);
            } catch (IllegalArgumentException e) {
                // Invalid UUID, skip
            }
        }
    }

    private Map<String, List<ResourceLocation>> serializeTeamStages() {
        Map<String, List<ResourceLocation>> result = new HashMap<>();
        for (Map.Entry<UUID, Set<StageId>> entry : teamStages.entrySet()) {
            List<ResourceLocation> stages = new ArrayList<>();
            for (StageId stageId : entry.getValue()) {
                stages.add(stageId.getResourceLocation());
            }
            result.put(entry.getKey().toString(), stages);
        }
        return result;
    }

    /**
     * Check if a team has a specific stage
     */
    public boolean hasStage(UUID teamId, StageId stageId) {
        Set<StageId> stages = teamStages.get(teamId);
        return stages != null && stages.contains(stageId);
    }

    /**
     * Grant a stage to a team
     * @return true if the stage was newly granted, false if already had it
     */
    public boolean grantStage(UUID teamId, StageId stageId) {
        Set<StageId> stages = teamStages.computeIfAbsent(teamId, k -> new HashSet<>());
        return stages.add(stageId);
    }

    /**
     * Revoke a stage from a team
     * @return true if the stage was revoked, false if didn't have it
     */
    public boolean revokeStage(UUID teamId, StageId stageId) {
        Set<StageId> stages = teamStages.get(teamId);
        if (stages != null) {
            return stages.remove(stageId);
        }
        return false;
    }

    /**
     * Get all stages for a team
     */
    public Set<StageId> getStages(UUID teamId) {
        Set<StageId> stages = teamStages.get(teamId);
        return stages != null ? Collections.unmodifiableSet(stages) : Collections.emptySet();
    }

    /**
     * Set stages for a team (replaces existing)
     */
    public void setStages(UUID teamId, Set<StageId> stages) {
        teamStages.put(teamId, new HashSet<>(stages));
    }

    /**
     * Remove all data for a team
     */
    public void removeTeam(UUID teamId) {
        teamStages.remove(teamId);
    }

    /**
     * Get all team IDs with stage data
     */
    public Set<UUID> getAllTeamIds() {
        return Collections.unmodifiableSet(teamStages.keySet());
    }

    /**
     * Get the "most advanced" stage a team has reached.
     * v1.3: Uses dependency depth instead of order number.
     * A stage with more dependencies is considered more advanced.
     */
    public Optional<StageId> getHighestStage(UUID teamId) {
        Set<StageId> stages = teamStages.get(teamId);
        if (stages == null || stages.isEmpty()) {
            return Optional.empty();
        }

        StageId highest = null;
        int highestDepth = -1;

        for (StageId stageId : stages) {
            // v1.3: Use dependency depth instead of order
            int depth = com.enviouse.progressivestages.common.stage.StageOrder.getInstance()
                .getAllDependencies(stageId).size();
            if (depth > highestDepth) {
                highestDepth = depth;
                highest = stageId;
            }
        }

        return Optional.ofNullable(highest);
    }

    /**
     * Create a copy of this data
     */
    public TeamStageData copy() {
        TeamStageData copy = new TeamStageData();
        for (Map.Entry<UUID, Set<StageId>> entry : teamStages.entrySet()) {
            copy.teamStages.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return copy;
    }
}
