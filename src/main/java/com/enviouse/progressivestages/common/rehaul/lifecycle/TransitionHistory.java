package com.enviouse.progressivestages.common.rehaul.lifecycle;

import java.util.ArrayDeque;
import java.util.List;

public final class TransitionHistory {

    private final int capacity;
    private final ArrayDeque<TransitionHistoryEntry> entries = new ArrayDeque<>();

    public TransitionHistory(int capacity) {
        if (capacity < 1 || capacity > 100_000) throw new IllegalArgumentException("History capacity is outside the allowed range");
        this.capacity = capacity;
    }

    public synchronized void add(TransitionHistoryEntry entry) {
        entries.addLast(entry);
        while (entries.size() > capacity) entries.removeFirst();
    }

    public synchronized List<TransitionHistoryEntry> entries() {
        return List.copyOf(entries);
    }

    public synchronized void restore(List<TransitionHistoryEntry> snapshot) {
        entries.clear();
        if (snapshot == null) return;
        int start = Math.max(0, snapshot.size() - capacity);
        for (int index = start; index < snapshot.size(); index++) entries.addLast(snapshot.get(index));
    }

    public synchronized void clear() {
        entries.clear();
    }
}
