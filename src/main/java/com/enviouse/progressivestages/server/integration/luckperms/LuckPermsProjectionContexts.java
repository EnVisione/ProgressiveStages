package com.enviouse.progressivestages.server.integration.luckperms;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextConsumer;
import net.luckperms.api.context.ContextManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class LuckPermsProjectionContexts implements ContextCalculator<Object>, AutoCloseable {
    private final ContextManager contexts;
    private final Map<UUID, Projection> subjects = new HashMap<>();
    private final Map<Object, Projection> targets = new ConcurrentHashMap<>();
    private final ThreadLocal<java.util.ArrayDeque<Object>> notifications = new ThreadLocal<>();
    private long sequence;
    private volatile long generation;
    private volatile boolean closing;
    private boolean registered;

    LuckPermsProjectionContexts(Object api) {
        contexts = ((LuckPerms) api).getContextManager();
    }

    void register() {
        if (registered || closing) return;
        registered = true;
        contexts.registerCalculator(this);
    }

    long prepare(UUID subject, Object target) {
        invalidate(subject);
        synchronized (this) {
            if (closing || !registered) return -1;
            Projection projection = new Projection(target, ++sequence, generation);
            subjects.put(subject, projection);
            targets.put(target, projection);
            return projection.ticket;
        }
    }

    boolean publish(UUID subject, long ticket) {
        return publish(subject, ticket, () -> true);
    }

    boolean publish(UUID subject, long ticket, java.util.function.BooleanSupplier current) {
        Projection projection;
        synchronized (this) {
            projection = subjects.get(subject);
            if (closing || projection == null || !projection.valid || projection.ticket != ticket
                || projection.generation != generation) return false;
            projection.current = current;
            projection.active = current.getAsBoolean();
            if (!projection.active) projection.valid = false;
            else projection.contextMayBeCached = true;
        }
        if (projection.contextMayBeCached) {
            try {
                signalUpdate(projection.target);
            } catch (RuntimeException | LinkageError failure) {
                projection.active = false;
                projection.valid = false;
                try {
                    signalUpdate(projection.target);
                    projection.contextMayBeCached = false;
                } catch (RuntimeException | LinkageError invalidation) {
                    failure.addSuppressed(invalidation);
                }
                throw failure;
            }
            if (!projection.active) projection.contextMayBeCached = false;
        }
        return projection.valid && projection.active && !closing && projection.generation == generation
            && projection.current.getAsBoolean();
    }

    synchronized void markInvalid(UUID subject) {
        Projection projection = subjects.get(subject);
        if (projection != null) {
            projection.valid = false;
            projection.active = false;
        }
    }

    synchronized boolean tracksSubject(UUID subject) { return subjects.containsKey(subject); }

    synchronized void markAllInvalid() {
        generation++;
    }

    void invalidate(UUID subject) {
        Projection projection;
        synchronized (this) {
            projection = subjects.get(subject);
            if (projection == null) return;
            projection.active = false;
            projection.valid = false;
        }
        if (projection.contextMayBeCached) {
            signalUpdate(projection.target);
            projection.contextMayBeCached = false;
        }
        synchronized (this) {
            subjects.remove(subject, projection);
            targets.remove(projection.target, projection);
        }
    }

    boolean invalidateAll() {
        Map<UUID, Projection> pending;
        synchronized (this) {
            pending = Map.copyOf(subjects);
            pending.values().forEach(projection -> {
                projection.active = false;
                projection.valid = false;
            });
        }
        boolean complete = true;
        for (UUID subject : pending.keySet()) {
            try {
                invalidate(subject);
            } catch (RuntimeException | LinkageError failure) {
                complete = false;
            }
        }
        return complete;
    }

    boolean isSignaling(Object target) {
        var pending = notifications.get();
        if (pending != null) for (Object subject : pending) if (subject == target) return true;
        return false;
    }

    private void signalUpdate(Object target) {
        var pending = notifications.get();
        if (pending == null) {
            pending = new java.util.ArrayDeque<>();
            notifications.set(pending);
        }
        pending.push(target);
        try {
            contexts.signalContextUpdate(target);
        } finally {
            pending.pop();
            if (pending.isEmpty()) notifications.remove();
        }
    }

    @Override
    public void calculate(Object target, ContextConsumer consumer) {
        Projection projection = targets.get(target);
        if (!closing && projection != null && projection.valid && projection.active
            && projection.generation == generation && projection.current.getAsBoolean()) {
            consumer.accept("progressivestages_bridge", "active");
        }
    }

    @Override
    public void close() {
        closing = true;
        boolean complete = invalidateAll();
        if (registered) {
            contexts.unregisterCalculator(this);
            registered = false;
        }
        if (!complete) throw new IllegalStateException("LuckPerms projection context invalidation is incomplete");
    }

    private static final class Projection {
        final Object target;
        final long ticket;
        final long generation;
        volatile boolean valid = true;
        volatile boolean active;
        volatile boolean contextMayBeCached;
        volatile java.util.function.BooleanSupplier current = () -> true;

        Projection(Object target, long ticket, long generation) {
            this.target = java.util.Objects.requireNonNull(target);
            this.ticket = ticket;
            this.generation = generation;
        }
    }
}
