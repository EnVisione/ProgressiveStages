package com.enviouse.progressivestages.common.lock;

public record ActiveLockDefinition(Scope scope, CategoryLocks items, boolean blockItemUse) {
    public enum Scope {
        STRUCTURE_SESSION
    }

    public static final ActiveLockDefinition EMPTY = new ActiveLockDefinition(
        Scope.STRUCTURE_SESSION, CategoryLocks.EMPTY, false);

    public ActiveLockDefinition {
        scope = scope == null ? Scope.STRUCTURE_SESSION : scope;
        items = items == null ? CategoryLocks.EMPTY : items;
    }

    public boolean isEmpty() {
        return items.isEmpty() || !blockItemUse;
    }
}
