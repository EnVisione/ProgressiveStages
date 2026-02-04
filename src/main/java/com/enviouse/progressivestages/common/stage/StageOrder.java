package com.enviouse.progressivestages.common.stage;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;

/**
 * Manages the linear ordering of stages.
 * Provides methods to determine stage prerequisites and successors.
 */
public class StageOrder {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Ordered list of stages (by order field)
    private final List<StageId> orderedStages = new ArrayList<>();

    // Stage ID -> Order number
    private final Map<StageId, Integer> stageToOrder = new HashMap<>();

    // Stage definitions
    private final Map<StageId, StageDefinition> stageDefinitions = new HashMap<>();

    private static StageOrder INSTANCE;

    public static StageOrder getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new StageOrder();
        }
        return INSTANCE;
    }

    private StageOrder() {}

    /**
     * Clear all stage ordering data
     */
    public void clear() {
        orderedStages.clear();
        stageToOrder.clear();
        stageDefinitions.clear();
    }

    /**
     * Register a stage in the ordering system
     */
    public void registerStage(StageDefinition stage) {
        StageId id = stage.getId();
        int order = stage.getOrder();

        // Check for duplicate orders
        for (Map.Entry<StageId, Integer> entry : stageToOrder.entrySet()) {
            if (entry.getValue() == order && !entry.getKey().equals(id)) {
                LOGGER.warn("Duplicate stage order {}: {} and {}", order, id, entry.getKey());
            }
        }

        stageToOrder.put(id, order);
        stageDefinitions.put(id, stage);

        // Rebuild ordered list
        rebuildOrderedList();
    }

    private void rebuildOrderedList() {
        orderedStages.clear();

        List<Map.Entry<StageId, Integer>> entries = new ArrayList<>(stageToOrder.entrySet());
        entries.sort(Comparator.comparingInt(Map.Entry::getValue));

        for (Map.Entry<StageId, Integer> entry : entries) {
            orderedStages.add(entry.getKey());
        }
    }

    /**
     * Get the order number for a stage
     */
    public Optional<Integer> getOrder(StageId stageId) {
        return Optional.ofNullable(stageToOrder.get(stageId));
    }

    /**
     * Get the stage definition
     */
    public Optional<StageDefinition> getStageDefinition(StageId stageId) {
        return Optional.ofNullable(stageDefinitions.get(stageId));
    }

    /**
     * Get all stages in order (earliest first)
     */
    public List<StageId> getOrderedStages() {
        return Collections.unmodifiableList(orderedStages);
    }

    /**
     * Get all prerequisites for a stage (all stages with lower order)
     */
    public Set<StageId> getPrerequisites(StageId stageId) {
        Set<StageId> prerequisites = new LinkedHashSet<>();

        Integer targetOrder = stageToOrder.get(stageId);
        if (targetOrder == null) {
            return prerequisites;
        }

        for (StageId id : orderedStages) {
            Integer order = stageToOrder.get(id);
            if (order != null && order < targetOrder) {
                prerequisites.add(id);
            }
        }

        return prerequisites;
    }

    /**
     * Get all stages that require this stage as a prerequisite (all stages with higher order)
     */
    public Set<StageId> getSuccessors(StageId stageId) {
        Set<StageId> successors = new LinkedHashSet<>();

        Integer targetOrder = stageToOrder.get(stageId);
        if (targetOrder == null) {
            return successors;
        }

        for (StageId id : orderedStages) {
            Integer order = stageToOrder.get(id);
            if (order != null && order > targetOrder) {
                successors.add(id);
            }
        }

        return successors;
    }

    /**
     * Get the next stage after the given stage
     */
    public Optional<StageId> getNextStage(StageId stageId) {
        int index = orderedStages.indexOf(stageId);
        if (index >= 0 && index < orderedStages.size() - 1) {
            return Optional.of(orderedStages.get(index + 1));
        }
        return Optional.empty();
    }

    /**
     * Get the previous stage before the given stage
     */
    public Optional<StageId> getPreviousStage(StageId stageId) {
        int index = orderedStages.indexOf(stageId);
        if (index > 0) {
            return Optional.of(orderedStages.get(index - 1));
        }
        return Optional.empty();
    }

    /**
     * Get the first (starting) stage
     */
    public Optional<StageId> getFirstStage() {
        if (orderedStages.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(orderedStages.get(0));
    }

    /**
     * Get the last (final) stage
     */
    public Optional<StageId> getLastStage() {
        if (orderedStages.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(orderedStages.get(orderedStages.size() - 1));
    }

    /**
     * Check if stageA comes before stageB in the progression
     */
    public boolean isBefore(StageId stageA, StageId stageB) {
        Integer orderA = stageToOrder.get(stageA);
        Integer orderB = stageToOrder.get(stageB);

        if (orderA == null || orderB == null) {
            return false;
        }

        return orderA < orderB;
    }

    /**
     * Check if stageA comes after stageB in the progression
     */
    public boolean isAfter(StageId stageA, StageId stageB) {
        return isBefore(stageB, stageA);
    }

    /**
     * Check if a stage exists
     */
    public boolean stageExists(StageId stageId) {
        return stageToOrder.containsKey(stageId);
    }

    /**
     * Get progress as "current/total" format
     */
    public String getProgressString(StageId currentStage) {
        int current = orderedStages.indexOf(currentStage) + 1;
        int total = orderedStages.size();

        if (current <= 0) {
            return "0/" + total;
        }

        return current + "/" + total;
    }

    /**
     * Get all stage IDs
     */
    public Set<StageId> getAllStageIds() {
        return Collections.unmodifiableSet(stageToOrder.keySet());
    }

    /**
     * Get the total number of stages
     */
    public int getStageCount() {
        return orderedStages.size();
    }
}
