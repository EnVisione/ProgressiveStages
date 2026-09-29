package com.enviouse.progressivestages.server.integration.luckperms;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class LuckPermsProjectionContextsTest {
    private static final UUID SUBJECT = new UUID(0, 51);

    @Test
    void onlyTheConfirmedCurrentTargetReceivesTheContext() {
        Fixture fixture = new Fixture();
        try (var contexts = fixture.contexts) {
            Object target = new Object();
            long ticket = contexts.prepare(SUBJECT, target);
            assertTrue(fixture.read(target).isEmpty());
            assertTrue(contexts.publish(SUBJECT, ticket));
            assertEquals(Map.of("progressivestages_bridge", "active"), fixture.read(target));
            assertTrue(fixture.read(new Object()).isEmpty());
            assertEquals(List.of(true), fixture.observedActive);
            contexts.invalidate(SUBJECT);
            assertTrue(fixture.read(target).isEmpty());
            assertEquals(List.of(true, false), fixture.observedActive);
            assertFalse(contexts.publish(SUBJECT, ticket));
        }
        assertNull(fixture.calculator);
        assertEquals(1, fixture.unregisters);
    }

    @Test
    void ownedSignalsAreScopedToTheCurrentThreadAndExactTarget() {
        Fixture fixture = new Fixture();
        Object target = new Object();
        Object another = new Object();
        try (var contexts = fixture.contexts) {
            fixture.onSignal = () -> {
                assertTrue(contexts.isSignaling(target));
                assertFalse(contexts.isSignaling(another));
                assertFalse(CompletableFuture.supplyAsync(() -> contexts.isSignaling(target)).join());
            };
            assertTrue(contexts.publish(SUBJECT, contexts.prepare(SUBJECT, target)));
            assertFalse(contexts.isSignaling(target));
            fixture.failSignal = true;
            assertThrows(IllegalStateException.class, () -> contexts.invalidate(SUBJECT));
            assertFalse(contexts.isSignaling(target));
            fixture.failSignal = false;
            contexts.invalidate(SUBJECT);
            assertFalse(contexts.isSignaling(target));
        } finally {
            fixture.onSignal = null;
            fixture.failSignal = false;
            fixture.contexts.close();
        }
    }

    @Test
    void providerInvalidationsAreImmediateWithoutProviderQueriesOrNotifications() {
        Fixture fixture = new Fixture();
        try (var contexts = fixture.contexts) {
            Object first = new Object();
            Object second = new Object();
            UUID another = new UUID(0, 53);
            long firstTicket = contexts.prepare(SUBJECT, first);
            long secondTicket = contexts.prepare(another, second);
            assertTrue(contexts.publish(SUBJECT, firstTicket));
            assertTrue(contexts.publish(another, secondTicket));
            int notifications = fixture.observedActive.size();
            contexts.markInvalid(SUBJECT);
            assertTrue(fixture.read(first).isEmpty());
            assertFalse(fixture.read(second).isEmpty());
            assertFalse(contexts.publish(SUBJECT, firstTicket));
            contexts.markAllInvalid();
            assertTrue(fixture.read(second).isEmpty());
            assertFalse(contexts.publish(another, secondTicket));
            assertEquals(notifications, fixture.observedActive.size());
            assertTrue(contexts.publish(another, contexts.prepare(another, second)));
            assertTrue(fixture.read(first).isEmpty());
            assertFalse(fixture.read(second).isEmpty());
        }
    }

    @Test
    void invalidationAndReconnectRejectOldPublicationTickets() {
        Fixture fixture = new Fixture();
        try (var contexts = fixture.contexts) {
            Object first = new Object();
            long old = contexts.prepare(SUBJECT, first);
            assertTrue(contexts.publish(SUBJECT, old));
            Object replacement = new Object();
            long current = contexts.prepare(SUBJECT, replacement);
            assertFalse(contexts.publish(SUBJECT, old));
            assertTrue(fixture.read(first).isEmpty());
            assertTrue(fixture.read(replacement).isEmpty());
            assertTrue(contexts.publish(SUBJECT, current));
            assertFalse(fixture.read(replacement).isEmpty());
            contexts.invalidateAll();
            assertTrue(fixture.read(replacement).isEmpty());
            assertFalse(contexts.publish(SUBJECT, current));
        }
    }

    @Test
    void invalidationCanRacePublicationWithoutRestoringTheMarker() {
        Fixture fixture = new Fixture();
        try (var contexts = fixture.contexts) {
            Object target = new Object();
            long ticket = contexts.prepare(SUBJECT, target);
            fixture.onSignal = () -> {
                fixture.onSignal = null;
                CompletableFuture.runAsync(() -> contexts.invalidate(SUBJECT)).join();
            };
            assertFalse(contexts.publish(SUBJECT, ticket));
            assertTrue(fixture.read(target).isEmpty());
            assertFalse(contexts.publish(SUBJECT, ticket));
        }
    }

    @Test
    void failedContextNotificationsLeaveNoActiveMarkerAndRemainRetryable() {
        Fixture fixture = new Fixture();
        Object target = new Object();
        try (var contexts = fixture.contexts) {
            long ticket = contexts.prepare(SUBJECT, target);
            fixture.failSignal = true;
            assertThrows(IllegalStateException.class, () -> contexts.publish(SUBJECT, ticket));
            assertTrue(fixture.read(target).isEmpty());
            assertFalse(contexts.publish(SUBJECT, ticket));
            assertFalse(contexts.invalidateAll());
            fixture.failSignal = false;
            assertTrue(contexts.invalidateAll());
            long retry = contexts.prepare(SUBJECT, target);
            assertTrue(contexts.publish(SUBJECT, retry));
        } finally {
            fixture.failSignal = false;
            fixture.contexts.close();
        }
    }

    @Test
    void cleanupDoesNotNotifyForAProjectionThatWasNeverPublished() {
        Fixture fixture = new Fixture();
        Object target = new Object();
        try (var contexts = fixture.contexts) {
            long ticket = contexts.prepare(SUBJECT, target);
            fixture.failSignal = true;

            assertTrue(contexts.invalidateAll());
            assertFalse(contexts.tracksSubject(SUBJECT));
            assertTrue(fixture.observedActive.isEmpty());
            assertFalse(contexts.publish(SUBJECT, ticket));
        } finally {
            fixture.failSignal = false;
        }
        assertNull(fixture.calculator);
        assertEquals(1, fixture.unregisters);
    }

    @Test
    void shutdownWithdrawsEveryMarkerBeforeSignalingAndRetriesUnregistration() {
        Fixture fixture = new Fixture();
        Object first = new Object();
        Object second = new Object();
        var contexts = fixture.contexts;
        long ticket = contexts.prepare(SUBJECT, first);
        contexts.publish(SUBJECT, ticket);
        UUID another = new UUID(0, 52);
        contexts.publish(another, contexts.prepare(another, second));
        fixture.onSignal = () -> {
            assertTrue(fixture.read(first).isEmpty());
            assertTrue(fixture.read(second).isEmpty());
        };
        fixture.failUnregister = true;
        try {
            assertThrows(IllegalStateException.class, contexts::close);
            assertTrue(fixture.read(first).isEmpty());
            assertTrue(fixture.read(second).isEmpty());
            assertFalse(contexts.publish(SUBJECT, ticket));
            assertEquals(-1, contexts.prepare(SUBJECT, first));
            assertNotNull(fixture.calculator);
        } finally {
            fixture.failUnregister = false;
            contexts.close();
        }
        assertNull(fixture.calculator);
        assertEquals(2, fixture.unregisters);
    }

    @Test
    void anExternalGuardInvalidatesMarkerReadsWithoutCallingTheProvider() {
        Fixture fixture = new Fixture();
        var current = new java.util.concurrent.atomic.AtomicBoolean(true);
        try (var contexts = fixture.contexts) {
            Object target = new Object();
            long ticket = contexts.prepare(SUBJECT, target);
            assertTrue(contexts.publish(SUBJECT, ticket, current::get));
            int signals = fixture.observedActive.size();
            CompletableFuture.runAsync(() -> current.set(false)).join();
            assertTrue(fixture.read(target).isEmpty());
            assertEquals(signals, fixture.observedActive.size());
            assertFalse(contexts.publish(SUBJECT, ticket, current::get));
        }
    }

    @Test
    void aGuardChangeInsidePublicationCannotKeepTheMarkerActive() {
        Fixture fixture = new Fixture();
        var current = new java.util.concurrent.atomic.AtomicBoolean(true);
        try (var contexts = fixture.contexts) {
            Object target = new Object();
            long ticket = contexts.prepare(SUBJECT, target);
            fixture.onSignal = () -> {
                fixture.onSignal = null;
                current.set(false);
                assertTrue(fixture.read(target).isEmpty());
            };
            assertFalse(contexts.publish(SUBJECT, ticket, current::get));
            assertTrue(fixture.read(target).isEmpty());
            assertTrue(contexts.publish(SUBJECT, contexts.prepare(SUBJECT, target), () -> true));
            assertFalse(fixture.read(target).isEmpty());
        }
    }

    @Test
    void rejectingPublicationWithdrawsAnAlreadyPublishedTicket() {
        Fixture fixture = new Fixture();
        try (var contexts = fixture.contexts) {
            Object target = new Object();
            long ticket = contexts.prepare(SUBJECT, target);
            assertTrue(contexts.publish(SUBJECT, ticket));
            assertFalse(contexts.publish(SUBJECT, ticket, () -> false));
            assertTrue(fixture.read(target).isEmpty());
            assertFalse(contexts.publish(SUBJECT, ticket));
            assertEquals(List.of(true, false), fixture.observedActive);
        }
    }

    private static final class Fixture {
        ContextCalculator<Object> calculator;
        final List<Boolean> observedActive = new ArrayList<>();
        final LuckPermsProjectionContexts contexts;
        Runnable onSignal;
        boolean failSignal;
        boolean failUnregister;
        int unregisters;

        @SuppressWarnings("unchecked")
        Fixture() {
            var manager = (ContextManager) Proxy.newProxyInstance(ContextManager.class.getClassLoader(),
                new Class<?>[] {ContextManager.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "registerCalculator" -> {
                            assertNull(calculator);
                            calculator = (ContextCalculator<Object>) args[0];
                        }
                        case "unregisterCalculator" -> {
                            assertSame(calculator, args[0]);
                            unregisters++;
                            if (failUnregister) throw new IllegalStateException("Unregistration failed");
                            calculator = null;
                        }
                        case "signalContextUpdate" -> {
                            observedActive.add(!read(args[0]).isEmpty());
                            if (onSignal != null) onSignal.run();
                            if (failSignal) throw new IllegalStateException("Context notification failed");
                        }
                        default -> throw new AssertionError("Unexpected provider query or mutation " + method);
                    }
                    return null;
                });
            var api = Proxy.newProxyInstance(LuckPerms.class.getClassLoader(), new Class<?>[] {LuckPerms.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getContextManager")) return manager;
                    throw new AssertionError("Unexpected provider query or mutation " + method);
                });
            contexts = new LuckPermsProjectionContexts(api);
            contexts.register();
        }

        Map<String, String> read(Object target) {
            var result = new java.util.HashMap<String, String>();
            if (calculator != null) calculator.calculate(target, result::put);
            return Map.copyOf(result);
        }
    }
}
