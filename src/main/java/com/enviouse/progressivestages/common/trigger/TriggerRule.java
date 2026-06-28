package com.enviouse.progressivestages.common.trigger;

import java.util.Collections;
import java.util.List;

/**
 * One {@code [[triggers]]} rule on a stage. A rule grants its owning stage when its
 * {@link #conditions} are satisfied according to {@link #mode} ({@code all_of} / {@code any_of}).
 *
 * <p>A stage may declare several rules; the rules are OR-ed together — the stage is granted
 * the moment <i>any</i> rule is fully satisfied. This lets a pack author offer multiple
 * unlock paths (e.g. "kill the dragon" OR "earn the end-credits advancement").
 */
public final class TriggerRule {

    private final TriggerMode mode;
    private final List<TriggerCondition> conditions;
    private final String description; // optional, surfaced in the GUI / progress command

    public TriggerRule(TriggerMode mode, List<TriggerCondition> conditions, String description) {
        this.mode = mode == null ? TriggerMode.ALL_OF : mode;
        this.conditions = Collections.unmodifiableList(List.copyOf(conditions));
        this.description = description == null ? "" : description;
    }

    public TriggerMode mode()                  { return mode; }
    public List<TriggerCondition> conditions() { return conditions; }
    public String description()                { return description; }

    @Override
    public String toString() {
        return "TriggerRule{" + mode + " " + conditions + "}";
    }
}
