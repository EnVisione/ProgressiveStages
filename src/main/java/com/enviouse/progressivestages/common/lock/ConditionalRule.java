package com.enviouse.progressivestages.common.lock;

import com.enviouse.progressivestages.common.api.StageId;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One priority based lock or unlock rule attached to a stage file.
 */
public final class ConditionalRule {

    public enum Effect { LOCK, UNLOCK }

    public enum Activation { LIVE, TRIGGERED }

    public enum StageState { OWNED, MISSING, ALWAYS }

    public enum ContextMode { ALL, ANY }

    public enum TriggerType { MANUAL, COMBAT, ATTACK, HURT, KILL }

    public enum TargetType {
        ITEM,
        BLOCK,
        FLUID,
        ENTITY,
        RECIPE,
        DIMENSION,
        STRUCTURE,
        ABILITY
    }

    private final ResourceLocation id;
    private final StageId ownerStage;
    private final Effect effect;
    private final Activation activation;
    private final StageState stageState;
    private final int priority;
    private final Context context;
    private final Targets targets;
    private final TriggerType triggerType;
    private final List<PrefixEntry> triggerEntities;
    private final long durationMillis;
    private final boolean refreshDuration;

    public ConditionalRule(ResourceLocation id, StageId ownerStage, Effect effect, Activation activation,
                           StageState stageState, int priority, Context context, Targets targets,
                           TriggerType triggerType, List<PrefixEntry> triggerEntities,
                           long durationMillis, boolean refreshDuration) {
        this.id = id;
        this.ownerStage = ownerStage;
        this.effect = effect;
        this.activation = activation;
        this.stageState = stageState;
        this.priority = priority;
        this.context = context != null ? context : Context.EMPTY;
        this.targets = targets != null ? targets : Targets.EMPTY;
        this.triggerType = triggerType != null ? triggerType : TriggerType.MANUAL;
        this.triggerEntities = triggerEntities != null ? List.copyOf(triggerEntities) : List.of();
        this.durationMillis = durationMillis;
        this.refreshDuration = refreshDuration;
    }

    public ResourceLocation id() { return id; }
    public StageId ownerStage() { return ownerStage; }
    public Effect effect() { return effect; }
    public Activation activation() { return activation; }
    public StageState stageState() { return stageState; }
    public int priority() { return priority; }
    public Context context() { return context; }
    public Targets targets() { return targets; }
    public TriggerType triggerType() { return triggerType; }
    public List<PrefixEntry> triggerEntities() { return triggerEntities; }
    public long durationMillis() { return durationMillis; }
    public boolean refreshDuration() { return refreshDuration; }

    public boolean isTriggered() { return activation == Activation.TRIGGERED; }

    public record Context(ContextMode mode,
                          List<PrefixEntry> dimensions,
                          List<PrefixEntry> structures,
                          List<PrefixEntry> biomes,
                          Integer minY,
                          Integer maxY,
                          Double minHealth,
                          Double maxHealth,
                          Set<StageId> requiredStages,
                          Set<StageId> missingStages,
                          Set<ResourceLocation> effects,
                          Boolean sneaking,
                          Boolean sprinting,
                          Boolean swimming,
                          Boolean riding,
                          Boolean onGround,
                          String scriptCondition) {

        public static final Context EMPTY = new Context(ContextMode.ALL, List.of(), List.of(), List.of(),
            null, null, null, null, Set.of(), Set.of(), Set.of(), null, null, null, null, null, "");

        public Context {
            mode = mode != null ? mode : ContextMode.ALL;
            dimensions = dimensions != null ? List.copyOf(dimensions) : List.of();
            structures = structures != null ? List.copyOf(structures) : List.of();
            biomes = biomes != null ? List.copyOf(biomes) : List.of();
            requiredStages = requiredStages != null ? Set.copyOf(requiredStages) : Set.of();
            missingStages = missingStages != null ? Set.copyOf(missingStages) : Set.of();
            effects = effects != null ? Set.copyOf(effects) : Set.of();
            scriptCondition = scriptCondition != null ? scriptCondition : "";
        }

        public boolean isEmpty() {
            return dimensions.isEmpty() && structures.isEmpty() && biomes.isEmpty()
                && minY == null && maxY == null && minHealth == null && maxHealth == null
                && requiredStages.isEmpty() && missingStages.isEmpty() && effects.isEmpty()
                && sneaking == null && sprinting == null && swimming == null && riding == null
                && onGround == null && scriptCondition.isEmpty();
        }
    }

    public static final class Targets {
        public static final Targets EMPTY = new Targets(Map.of(), Map.of());

        private final Map<TargetType, List<PrefixEntry>> included;
        private final Map<TargetType, List<PrefixEntry>> excluded;

        public Targets(Map<TargetType, List<PrefixEntry>> included,
                       Map<TargetType, List<PrefixEntry>> excluded) {
            this.included = immutableCopy(included);
            this.excluded = immutableCopy(excluded);
        }

        private static Map<TargetType, List<PrefixEntry>> immutableCopy(
                Map<TargetType, List<PrefixEntry>> source) {
            if (source == null || source.isEmpty()) return Map.of();
            EnumMap<TargetType, List<PrefixEntry>> copy = new EnumMap<>(TargetType.class);
            for (Map.Entry<TargetType, List<PrefixEntry>> entry : source.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) continue;
                copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
            }
            return Collections.unmodifiableMap(copy);
        }

        public List<PrefixEntry> included(TargetType type) {
            return included.getOrDefault(type, List.of());
        }

        public List<PrefixEntry> excluded(TargetType type) {
            return excluded.getOrDefault(type, List.of());
        }

        public Set<TargetType> types() {
            return Collections.unmodifiableSet(new LinkedHashSet<>(included.keySet()));
        }

        public boolean has(TargetType type) {
            return type != null && !included(type).isEmpty();
        }

        public boolean isEmpty() {
            return included.isEmpty();
        }
    }
}
