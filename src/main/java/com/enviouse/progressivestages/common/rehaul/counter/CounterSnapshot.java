package com.enviouse.progressivestages.common.rehaul.counter;

import java.util.List;
import java.util.Map;

public record CounterSnapshot(Map<CounterKey, Double> totals,
                              Map<CounterKey, List<TimedAmount>> rolling) {

    public CounterSnapshot {
        totals = totals == null ? Map.of() : Map.copyOf(totals);
        rolling = rolling == null ? Map.of() : rolling.entrySet().stream().collect(
            java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
                entry -> List.copyOf(entry.getValue())));
    }

    public record TimedAmount(long timestamp, double amount) {
        public TimedAmount {
            if (!Double.isFinite(amount)) throw new IllegalArgumentException("Counter amount must be finite");
        }
    }
}
