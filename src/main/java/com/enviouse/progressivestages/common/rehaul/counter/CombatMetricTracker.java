package com.enviouse.progressivestages.common.rehaul.counter;

import com.enviouse.progressivestages.common.rehaul.condition.SubjectScope;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatMetricTracker {

    private final WindowCounterStore counters;
    private final Map<String, Float> lastHealth = new ConcurrentHashMap<>();
    private final Map<String, Long> lastDamageAt = new ConcurrentHashMap<>();

    public CombatMetricTracker(WindowCounterStore counters) {
        this.counters = counters;
    }

    public void healthChanged(String subject, float previous, float current, long now,
                              CounterWindow window) {
        lastHealth.put(subject, current);
        if (current > previous) add(subject, "health_gained", current - previous, now, window);
        if (current < previous) add(subject, "health_lost", previous - current, now, window);
    }

    public void damageTaken(String subject, double raw, double applied, boolean countHit,
                            long now, CounterWindow window) {
        add(subject, "damage_taken", applied, now, window);
        add(subject, "raw_damage_taken", raw, now, window);
        if (countHit) add(subject, "hits_taken", 1, now, window);
        lastDamageAt.put(subject, now);
    }

    public void damageDealt(String subject, double raw, double applied, boolean countHit,
                            long now, CounterWindow window) {
        add(subject, "damage_dealt", applied, now, window);
        add(subject, "raw_damage_dealt", raw, now, window);
        if (countHit) add(subject, "hits_dealt", 1, now, window);
    }

    public void death(String subject, long now, CounterWindow window) {
        add(subject, "death", 1, now, window);
        counters.reset(subject, ResetPolicy.ON_DEATH);
    }

    public void respawn(String subject, long now, CounterWindow window) {
        add(subject, "respawn", 1, now, window);
        counters.reset(subject, ResetPolicy.ON_RESPAWN);
    }

    public long noDamageFor(String subject, long now) {
        return Math.max(0, now - lastDamageAt.getOrDefault(subject, now));
    }

    public boolean crossed(String subject, float current, float threshold, boolean upward) {
        Float previous = lastHealth.put(subject, current);
        if (previous == null) return false;
        return upward ? previous < threshold && current >= threshold : previous > threshold && current <= threshold;
    }

    private void add(String subject, String metric, double amount, long now, CounterWindow window) {
        counters.add(new CounterKey(subject, SubjectScope.PLAYER,
            ResourceLocation.fromNamespaceAndPath("progressivestages", metric), window), amount, now);
    }
}
