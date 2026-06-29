package com.enviouse.progressivestages.common.stage;

import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.config.StageDefinition;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;

/**
 * Manages stage definitions and their dependency relationships.
 *
 * <p>v1.3 changes: Replaced order-based linear progression with dependency graph.
 * Dependencies are now explicitly defined per stage, not inferred from order numbers.
 */
public class StageOrder {

    private static final Logger LOGGER = LogUtils.getLogger();

    // All registered stages (insertion order preserved for iteration)
    private final List<StageId> registeredStages = new ArrayList<>();

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
     * Clear all stage data
     */
    public void clear() {
        registeredStages.clear();
        stageDefinitions.clear();
    }

    /**
     * Register a stage
     */
    public void registerStage(StageDefinition stage) {
        StageId id = stage.getId();

        // Check for duplicates
        if (stageDefinitions.containsKey(id)) {
            LOGGER.warn("Duplicate stage registration: {}", id);
            return;
        }

        stageDefinitions.put(id, stage);
        registeredStages.add(id);

        LOGGER.debug("Registered stage: {} with {} dependencies", id, stage.getDependencies().size());
    }

    /**
     * Get the stage definition
     */
    public Optional<StageDefinition> getStageDefinition(StageId stageId) {
        return Optional.ofNullable(stageDefinitions.get(stageId));
    }

    /**
     * Get all stages (in registration order)
     */
    public List<StageId> getOrderedStages() {
        return Collections.unmodifiableList(registeredStages);
    }

    /**
     * Get direct dependencies for a stage (stages that must be unlocked first).
     * Returns only the explicitly declared dependencies, not transitive ones.
     */
    public Set<StageId> getDependencies(StageId stageId) {
        StageDefinition def = stageDefinitions.get(stageId);
        if (def == null) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(def.getDependencies());
    }

    /**
     * Get all dependencies (transitive) for a stage.
     * This includes dependencies of dependencies, recursively.
     */
    public Set<StageId> getAllDependencies(StageId stageId) {
        Set<StageId> allDeps = new LinkedHashSet<>();
        collectDependencies(stageId, allDeps, new HashSet<>());
        return allDeps;
    }

    private void collectDependencies(StageId stageId, Set<StageId> collected, Set<StageId> visited) {
        if (visited.contains(stageId)) {
            LOGGER.warn("Circular dependency detected involving stage: {}", stageId);
            return;
        }
        visited.add(stageId);

        StageDefinition def = stageDefinitions.get(stageId);
        if (def == null) return;

        for (StageId dep : def.getDependencies()) {
            if (!collected.contains(dep)) {
                collected.add(dep);
                collectDependencies(dep, collected, visited);
            }
        }
    }

    /**
     * @deprecated Use {@link #getDependencies(StageId)} instead.
     * For compatibility with v1.2 code that expected order-based prerequisites.
     */
    @Deprecated(forRemoval = true)
    public Set<StageId> getPrerequisites(StageId stageId) {
        return getAllDependencies(stageId);
    }

    /**
     * Get stages that depend on this stage (stages that require this one).
     */
    public Set<StageId> getDependents(StageId stageId) {
        Set<StageId> dependents = new LinkedHashSet<>();

        for (StageDefinition def : stageDefinitions.values()) {
            if (def.getDependencies().contains(stageId)) {
                dependents.add(def.getId());
            }
        }

        return dependents;
    }

    /**
     * Get all dependents (transitive) - stages that would need this stage revoked first
     * if we're using strict dependency enforcement.
     */
    public Set<StageId> getAllDependents(StageId stageId) {
        Set<StageId> allDependents = new LinkedHashSet<>();
        collectDependents(stageId, allDependents, new HashSet<>());
        return allDependents;
    }

    private void collectDependents(StageId stageId, Set<StageId> collected, Set<StageId> visited) {
        if (visited.contains(stageId)) return;
        visited.add(stageId);

        for (StageDefinition def : stageDefinitions.values()) {
            if (def.getDependencies().contains(stageId) && !collected.contains(def.getId())) {
                collected.add(def.getId());
                collectDependents(def.getId(), collected, visited);
            }
        }
    }

    /**
     * @deprecated Use {@link #getDependents(StageId)} instead.
     */
    @Deprecated(forRemoval = true)
    public Set<StageId> getSuccessors(StageId stageId) {
        return getAllDependents(stageId);
    }

    /**
     * Check if a stage exists
     */
    public boolean stageExists(StageId stageId) {
        return stageDefinitions.containsKey(stageId);
    }

    /**
     * Check if all dependencies for a stage are satisfied.
     * @param playerStages The set of stages the player currently has
     * @param targetStage The stage being checked
     * @return List of missing dependencies (empty if all satisfied)
     */
    public List<StageId> getMissingDependencies(Set<StageId> playerStages, StageId targetStage) {
        StageDefinition def = stageDefinitions.get(targetStage);
        if (def == null) {
            return Collections.emptyList();
        }

        List<StageId> missing = new ArrayList<>();
        for (StageId dep : def.getDependencies()) {
            if (!playerStages.contains(dep)) {
                missing.add(dep);
            }
        }
        return missing;
    }

    /**
     * @deprecated Order-based progression removed in v1.3.
     */
    @Deprecated(forRemoval = true)
    public Optional<Integer> getOrder(StageId stageId) {
        int index = registeredStages.indexOf(stageId);
        return index >= 0 ? Optional.of(index) : Optional.empty();
    }

    /**
     * Get all stage IDs
     */
    public Set<StageId> getAllStageIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(registeredStages));
    }

    /**
     * Get the total number of stages
     */
    public int getStageCount() {
        return registeredStages.size();
    }

    /**
     * @deprecated Use dependency-based progress tracking
     */
    @Deprecated(forRemoval = true)
    public String getProgressString(StageId currentStage) {
        int index = registeredStages.indexOf(currentStage);
        return (index + 1) + "/" + registeredStages.size();
    }

    /**
     * Validate the dependency graph.
     *
     * <p>v2.5 deepened this beyond the original dead-dependency + self-cycle check. It now reports:
     * <ul>
     *   <li><b>Dead dependency targets</b> — a stage depends on an id no stage file defines.</li>
     *   <li><b>Dependency cycles</b> — full multi-node cycles ({@code a → b → c → a}) found via a
     *       white/grey/black DFS, not just the {@code a → a} self-loop the old code could see.</li>
     *   <li><b>Transitively unreachable stages</b> — a stage whose dependency closure contains a
     *       cycle member or a non-existent stage, so its prerequisites can never all be satisfied
     *       through legitimate progression.</li>
     * </ul>
     *
     * @return list of human-readable validation messages (empty if the graph is clean)
     */
    public List<String> validateDependencies() {
        List<String> errors = new ArrayList<>();

        // 1. Dead dependency targets.
        Set<StageId> hasDeadDep = new HashSet<>();
        for (StageDefinition def : stageDefinitions.values()) {
            for (StageId dep : def.getDependencies()) {
                if (!stageDefinitions.containsKey(dep)) {
                    errors.add("Stage '" + def.getId() + "' depends on non-existent stage: " + dep);
                    hasDeadDep.add(def.getId());
                }
            }
        }

        // 2. Full cycle detection (white/grey/black DFS). Each detected cycle is reported once.
        List<List<StageId>> cycles = findCycles();
        Set<StageId> cycleMembers = new HashSet<>();
        for (List<StageId> cycle : cycles) {
            cycleMembers.addAll(cycle);
            StringBuilder sb = new StringBuilder();
            for (StageId s : cycle) sb.append(s.getPath()).append(" → ");
            sb.append(cycle.get(0).getPath()); // close the loop visually
            errors.add("Circular dependency: " + sb);
        }

        // 3. Transitively unreachable stages — depend (directly or transitively) on a cycle member
        //    or a non-existent stage, so the dependency closure can never be fully satisfied. We
        //    skip stages already named above to keep the report focused on the blast radius.
        Set<StageId> tainted = new HashSet<>(cycleMembers);
        tainted.addAll(hasDeadDep);
        for (StageDefinition def : stageDefinitions.values()) {
            StageId id = def.getId();
            if (tainted.contains(id)) continue;
            Set<StageId> closure = new LinkedHashSet<>();
            collectDependencies(id, closure, new HashSet<>());
            for (StageId dep : closure) {
                if (cycleMembers.contains(dep) || !stageDefinitions.containsKey(dep)) {
                    errors.add("Stage '" + id + "' is unreachable — its prerequisite '" + dep
                        + "' can never be obtained (cycle or missing stage upstream)");
                    break;
                }
            }
        }

        return errors;
    }

    /**
     * Find every dependency cycle in the graph using an iterative-safe recursive DFS with
     * white/grey/black colouring. A back-edge to a grey (on the current path) node closes a cycle;
     * the path segment from that node to the current node is recorded. Non-existent dependency
     * targets are ignored here (reported separately).
     */
    private List<List<StageId>> findCycles() {
        final int WHITE = 0, GREY = 1, BLACK = 2;
        Map<StageId, Integer> color = new HashMap<>();
        List<List<StageId>> cycles = new ArrayList<>();
        Set<String> seenCycleKeys = new HashSet<>();
        Deque<StageId> path = new ArrayDeque<>();
        for (StageId start : registeredStages) {
            if (color.getOrDefault(start, WHITE) == WHITE) {
                cycleDfs(start, color, path, cycles, seenCycleKeys, WHITE, GREY, BLACK);
            }
        }
        return cycles;
    }

    private void cycleDfs(StageId node, Map<StageId, Integer> color, Deque<StageId> path,
                          List<List<StageId>> cycles, Set<String> seenCycleKeys,
                          int WHITE, int GREY, int BLACK) {
        color.put(node, GREY);
        path.addLast(node);
        StageDefinition def = stageDefinitions.get(node);
        if (def != null) {
            for (StageId dep : def.getDependencies()) {
                if (!stageDefinitions.containsKey(dep)) continue; // dead dep handled elsewhere
                int c = color.getOrDefault(dep, WHITE);
                if (c == GREY) {
                    // Back-edge: extract the cycle (from dep's position in the path to the end).
                    List<StageId> cycle = new ArrayList<>();
                    boolean collecting = false;
                    for (StageId p : path) {
                        if (p.equals(dep)) collecting = true;
                        if (collecting) cycle.add(p);
                    }
                    if (!cycle.isEmpty()) {
                        // Canonical key (sorted) so the same cycle reached via different entry
                        // points is reported only once.
                        List<String> sorted = new ArrayList<>();
                        for (StageId s : cycle) sorted.add(s.toString());
                        Collections.sort(sorted);
                        if (seenCycleKeys.add(String.join(",", sorted))) cycles.add(cycle);
                    }
                } else if (c == WHITE) {
                    cycleDfs(dep, color, path, cycles, seenCycleKeys, WHITE, GREY, BLACK);
                }
            }
        }
        path.removeLast();
        color.put(node, BLACK);
    }
}
