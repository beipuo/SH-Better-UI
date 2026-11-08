package com.dishanhai.sh_better_ui.client.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：扩展工厂的上下文隔离、循环诊断、失败恢复与依赖资源释放。 */
class UiApiContextTest {
    @Test
    void factoriesAreLazyAndEachContextOwnsItsInstance() {
        var registry = new UiExtensionRegistry();
        var calls = new AtomicInteger();
        registry.register("test:value", Object.class, context -> {
            calls.incrementAndGet();
            assertSame(registry, context.extensions());
            return new Object();
        });

        try (var first = new UiApiContext(registry); var second = new UiApiContext(registry)) {
            assertEquals(0, calls.get());
            Object value = first.extension("test:value");
            assertSame(value, first.extension("test:value", Object.class));
            assertNotSame(value, second.extension("test:value"));
            assertEquals(2, calls.get());
        }
    }

    @Test
    void dependencyFactoriesUseTheSameContextAndCloseDependentsFirst() {
        var registry = new UiExtensionRegistry();
        var closed = new ArrayList<String>();
        registry.register("test:dependency", CloseProbe.class, context -> new CloseProbe("dependency", closed, null));
        registry.register("test:dependent", CloseProbe.class, context -> {
            context.extension("test:dependency", CloseProbe.class);
            return new CloseProbe("dependent", closed, null);
        });
        var context = new UiApiContext(registry);
        context.extension("test:dependent");

        context.close();
        context.close();

        assertEquals(List.of("dependent", "dependency"), closed);
        assertTrue(context.isClosed());
    }

    @Test
    void registrationChangesAffectUnresolvedContexts() {
        var registry = new UiExtensionRegistry();
        Object original = new Object();
        var registration = registry.register("test:replace", context -> original);
        try (var resolved = new UiApiContext(registry); var fresh = new UiApiContext(registry)) {
            assertSame(original, resolved.extension("test:replace"));
            registration.close();
            assertSame(original, resolved.extension("test:replace"));
            assertThrows(IllegalArgumentException.class, () -> fresh.extension("test:replace"));

            Object replacement = new Object();
            registry.register("test:replace", context -> replacement);
            registration.close();
            assertTrue(registry.contains("test:replace"));
            assertSame(original, resolved.extension("test:replace"));
            assertSame(replacement, fresh.extension("test:replace"));
        }
    }

    @Test
    void cyclesReportTheirPathAndDoNotPoisonLaterResolution() {
        var registry = new UiExtensionRegistry();
        var first = registry.register("test:first", context -> context.extension("test:second"));
        registry.register("test:second", context -> context.extension("test:first"));
        try (var context = new UiApiContext(registry)) {
            var error = assertThrows(IllegalStateException.class, () -> context.extension("test:first"));
            assertTrue(error.getMessage().contains("test:first -> test:second -> test:first"));

            first.close();
            registry.register("test:first", ignored -> "resolved");
            assertEquals("resolved", context.extension("test:second", String.class));
        }
    }

    @Test
    void failingFactoriesCanRetryAndKeepResolvedDependencies() {
        var registry = new UiExtensionRegistry();
        var attempts = new AtomicInteger();
        var dependencyCalls = new AtomicInteger();
        registry.register("test:dependency", context -> dependencyCalls.incrementAndGet());
        registry.register("test:retry", String.class, context -> {
            context.extension("test:dependency");
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("retry");
            return "ready";
        });
        try (var context = new UiApiContext(registry)) {
            assertThrows(IllegalStateException.class, () -> context.extension("test:retry"));
            assertEquals("ready", context.extension("test:retry", String.class));
            assertEquals(2, attempts.get());
            assertEquals(1, dependencyCalls.get());
        }
    }

    @Test
    void aRequestedTypeMismatchDoesNotReplaceTheCachedInstance() {
        var registry = new UiExtensionRegistry();
        registry.register("test:text", String.class, context -> "value");
        try (var context = new UiApiContext(registry)) {
            assertThrows(ClassCastException.class, () -> context.extension("test:text", Integer.class));
            assertEquals("value", context.extension("test:text", String.class));
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void factoryResultsMustMatchTheRegisteredTypeAndMayNotBeNull() {
        var registry = new UiExtensionRegistry();
        Function invalid = context -> "wrong type";
        registry.register("test:number", Number.class, invalid);
        var attempts = new AtomicInteger();
        registry.register("test:nullable", context -> attempts.incrementAndGet() == 1 ? null : "ready");
        try (var context = new UiApiContext(registry)) {
            assertThrows(ClassCastException.class, () -> context.extension("test:number"));
            assertThrows(NullPointerException.class, () -> context.extension("test:nullable"));
            assertEquals("ready", context.extension("test:nullable"));
        }
    }

    @Test
    void sharedResourcesAreClosedOnceAndEqualResourcesRemainIndependent() {
        var registry = new UiExtensionRegistry();
        var closed = new ArrayList<String>();
        var shared = new CloseProbe("shared", closed, null);
        registry.register("test:original", context -> shared);
        registry.register("test:alias", context -> context.extension("test:original"));
        try (var context = new UiApiContext(registry)) {
            assertSame(shared, context.manage(shared));
            context.extension("test:alias");
            context.manage(new CloseProbe("equal", closed, null));
            context.manage(new CloseProbe("equal", closed, null));
        }
        assertEquals(List.of("equal", "equal", "shared"), closed);
    }

    @Test
    void cleanupTriesEveryResourceAndReportsAdditionalFailures() {
        var context = new UiApiContext(new UiExtensionRegistry());
        var closed = new ArrayList<String>();
        var firstError = new IllegalStateException("first");
        var secondError = new IllegalArgumentException("second");
        context.manage(new CloseProbe("last", closed, secondError));
        context.manage(new CloseProbe("middle", closed, null));
        context.manage(new CloseProbe("first", closed, firstError));

        assertSame(firstError, assertThrows(IllegalStateException.class, context::close));
        assertEquals(List.of("first", "middle", "last"), closed);
        assertEquals(List.of(secondError), List.of(firstError.getSuppressed()));
        context.close();
        assertEquals(3, closed.size());
    }

    @Test
    void closedContextsRejectResourceAndSnapshotOperations() {
        var context = new UiApiContext(new UiExtensionRegistry());
        context.close();

        assertThrows(IllegalStateException.class, () -> context.extension("test:missing"));
        assertThrows(IllegalStateException.class, context::extensions);
        assertThrows(IllegalStateException.class, context::preferences);
        assertThrows(IllegalStateException.class, () -> context.frame(null, null, 0));
        assertThrows(IllegalStateException.class, () -> context.manage(() -> {}));
    }

    @Test
    void closingDuringFactoryCreationReleasesItsResultWithoutClosingManagedResourcesTwice() {
        var closed = new ArrayList<String>();
        var registry = new UiExtensionRegistry();
        registry.register("test:new_resource", context -> {
            context.close();
            return new CloseProbe("new", closed, null);
        });
        registry.register("test:managed_resource", context -> {
            var resource = context.manage(new CloseProbe("managed", closed, null));
            context.close();
            return resource;
        });
        try (var first = new UiApiContext(registry); var second = new UiApiContext(registry)) {
            assertThrows(IllegalStateException.class, () -> first.extension("test:new_resource"));
            assertThrows(IllegalStateException.class, () -> second.extension("test:managed_resource"));
        }
        assertEquals(List.of("new", "managed"), closed);
    }

    @Test
    void registrationValidatesIdsAndRejectsConflictsWithoutReplacingTheFactory() {
        var registry = new UiExtensionRegistry();
        for (String invalid : List.of("missing_namespace", "Bad:case", "test:", ":value")) {
            assertThrows(IllegalArgumentException.class, () -> registry.register(invalid, context -> "value"));
        }
        assertThrows(IllegalArgumentException.class, () -> registry.register("test:primitive", int.class, context -> 1));
        var registration = registry.register("test:value", context -> "original");
        assertThrows(IllegalStateException.class, () -> registry.register("test:value", context -> "replacement"));
        List<String> snapshot = registry.ids();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add("test:other"));
        registration.close();
        registration.close();
        assertFalse(registry.contains("test:value"));
        assertEquals(List.of("test:value"), snapshot);
        assertTrue(registry.ids().isEmpty());
    }

    private record CloseProbe(String id, List<String> closed, RuntimeException failure) implements AutoCloseable {
        @Override
        public void close() {
            closed.add(id);
            if (failure != null) throw failure;
        }
    }
}
