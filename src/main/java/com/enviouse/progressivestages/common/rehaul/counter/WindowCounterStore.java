package com.enviouse.progressivestages.common.rehaul.counter;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WindowCounterStore {

    private final Map<CounterKey, Double> totals = new ConcurrentHashMap<>();
    private final Map<CounterKey, ArrayDeque<CounterSnapshot.TimedAmount>> rolling = new ConcurrentHashMap<>();

    public double add(CounterKey key, double amount, long timestamp) {
        if (!Double.isFinite(amount)) throw new IllegalArgumentException("Counter amount must be finite");
        if (key.window().kind() == WindowKind.ROLLING_DURATION) {
            ArrayDeque<CounterSnapshot.TimedAmount> values = rolling.computeIfAbsent(key, ignored -> new ArrayDeque<>());
            synchronized (values) {
                values.addLast(new CounterSnapshot.TimedAmount(timestamp, amount));
                prune(values, timestamp - key.window().durationMillis());
                return values.stream().mapToDouble(CounterSnapshot.TimedAmount::amount).sum();
            }
        }
        return totals.merge(key, amount, Double::sum);
    }

    public double value(CounterKey key, long timestamp) {
        if (key.window().kind() != WindowKind.ROLLING_DURATION) return totals.getOrDefault(key, 0.0);
        ArrayDeque<CounterSnapshot.TimedAmount> values = rolling.get(key);
        if (values == null) return 0;
        synchronized (values) {
            prune(values, timestamp - key.window().durationMillis());
            return values.stream().mapToDouble(CounterSnapshot.TimedAmount::amount).sum();
        }
    }

    public void reset(CounterKey key) {
        totals.remove(key);
        rolling.remove(key);
    }

    public void reset(String subject, ResetPolicy policy) {
        totals.keySet().removeIf(key -> key.subject().equals(subject) && key.window().resetPolicy() == policy);
        rolling.keySet().removeIf(key -> key.subject().equals(subject) && key.window().resetPolicy() == policy);
    }

    public CounterSnapshot snapshot() {
        Map<CounterKey, List<CounterSnapshot.TimedAmount>> rollingCopy = new LinkedHashMap<>();
        rolling.forEach((key, values) -> {
            synchronized (values) {
                rollingCopy.put(key, List.copyOf(values));
            }
        });
        return new CounterSnapshot(new LinkedHashMap<>(totals), rollingCopy);
    }

    public void restore(CounterSnapshot snapshot) {
        totals.clear();
        rolling.clear();
        totals.putAll(snapshot.totals());
        snapshot.rolling().forEach((key, values) -> rolling.put(key, new ArrayDeque<>(values)));
    }

    public void clear() {
        totals.clear();
        rolling.clear();
    }

    private static void prune(ArrayDeque<CounterSnapshot.TimedAmount> values, long cutoff) {
        while (!values.isEmpty() && values.getFirst().timestamp() < cutoff) values.removeFirst();
    }
}
