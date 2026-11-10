// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static com.dishanhai.sh_better_ui.kernel.api.ConfigLayer.*;
import static com.dishanhai.sh_better_ui.kernel.internal.TestModules.*;
import static org.junit.jupiter.api.Assertions.*;

class ValuesContractTest {
    @Test
    void sevenConfigLayersResolveDeterministicallyAcrossTheScopeTree() {
        try (var fixture = new Fixture()) {
            var config = fixture.config();
            var key = number("layered");
            config.define(key);
            var screen = fixture.screen();
            var component = fixture.component(screen);
            var global = fixture.host.rootScope();
            var layers = List.of(new ConfigScope(global, MODULE_DEFAULTS), new ConfigScope(global, RESOURCE_THEME),
                    new ConfigScope(global, USER_GLOBAL), new ConfigScope(screen, SCREEN),
                    new ConfigScope(component, COMPONENT), new ConfigScope(component, RUNTIME_OVERRIDE));
            assertEquals(0, config.get(component, key));
            for (int i = 0; i < layers.size(); i++) {
                config.beginTransaction().set(layers.get(i), key, i + 1).commit();
                assertEquals(i + 1, config.get(component, key));
            }
            assertEquals(3, fixture.publication.current().configs().get(key));
            for (int i = layers.size() - 1; i >= 0; i--) {
                config.beginTransaction().clear(layers.get(i), key).commit();
                assertEquals(i, config.get(component, key));
            }
        }
    }

    @Test
    void configMigrationValidationAndVersionConflictsKeepThePreviousPublication() {
        try (var fixture = new Fixture()) {
            var config = fixture.config();
            var key = new ConfigKey<>(UiId.parse("example:duration"), Integer.class, 10, Set.of(USER_GLOBAL),
                    ConfigKey.Mutability.RUNTIME, ConfigKey.Persistence.USER_GLOBAL, 3, "Duration",
                    value -> value >= 0 && value <= 100, UnaryOperator.identity(),
                    Map.of(1, value -> value + 3, 2, value -> value * 2));
            var other = number("other");
            config.define(key);
            config.define(other);
            var global = new ConfigScope(fixture.host.rootScope(), USER_GLOBAL);
            var stale = config.beginTransaction();
            config.beginTransaction().load(global, key, 1, 7).set(global, other, 20).commit();
            assertEquals(20, config.get(key));
            var before = config.snapshot();
            assertThrows(java.util.ConcurrentModificationException.class, () -> stale.set(global, key, 25).commit());
            assertThrows(IllegalArgumentException.class,
                    () -> config.beginTransaction().set(global, other, 50).load(global, key, 1, 100).commit());
            assertThrows(IllegalArgumentException.class,
                    () -> config.beginTransaction().load(global, key, 4, 20).commit());
            assertEquals(before.version(), config.snapshot().version());
            assertEquals(20, config.get(other));
        }
    }

    @Test
    void configObserverSeesOneEffectiveChangeAndRemountDropsOverrides() {
        try (var fixture = new Fixture()) {
            var config = fixture.config();
            var key = number("observe");
            config.define(key);
            var screen = fixture.screen();
            var changes = new java.util.ArrayList<ValueChange<Integer>>();
            var binding = config.observe(screen, key, changes::add);
            var local = new ConfigScope(screen, SCREEN);
            config.beginTransaction().set(local, key, 1).set(local, key, 2).commit();
            assertEquals(List.of(new ValueChange<>(0, 2, config.snapshot(screen).version())), changes);
            var old = config.snapshot(screen);
            screen.close();
            assertFalse(binding.isActive());
            assertEquals(2, old.get(key));
            var remount = fixture.screen();
            assertEquals(0, config.get(remount, key));
            assertThrows(IllegalStateException.class, () -> config.beginTransaction().set(local, key, 5).commit());
        }
    }

    @Test
    void mutableConfigurationIsDefensivelyCopiedAndReadAtomicallyOffThread() throws Exception {
        try (var fixture = new Fixture()) {
            var config = fixture.config();
            var vector = new ConfigKey<>(UiId.parse("example:vector"), int[].class, new int[]{0}, Set.of(SCREEN),
                    ConfigKey.Mutability.RUNTIME, ConfigKey.Persistence.SESSION, 1, "Vector",
                    value -> value.length == 1, int[]::clone, Map.of());
            var number = number("atomic");
            config.define(vector);
            config.define(number);
            var screen = fixture.screen();
            var local = new ConfigScope(screen, SCREEN);
            var started = new CountDownLatch(1);
            var published = new CountDownLatch(1);
            var reader = CompletableFuture.runAsync(() -> {
                var old = config.snapshot(screen);
                started.countDown();
                await(published);
                var current = config.snapshot(screen);
                assertArrayEquals(new int[]{0}, old.get(vector));
                assertEquals(0, old.get(number));
                assertArrayEquals(new int[]{4}, current.get(vector));
                assertEquals(5, current.get(number));
                current.get(vector)[0] = 30;
                assertArrayEquals(new int[]{4}, current.get(vector));
            });
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                int[] input = {4};
                long version = config.beginTransaction().set(local, vector, input).set(local, number, 5).commit();
                input[0] = 21;
                assertEquals(version, config.beginTransaction().set(local, vector, new int[]{4}).commit());
            } finally {
                published.countDown();
                reader.get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void schemaIdentityRawTypesAndWriteBoundariesAreEnforced() {
        try (var fixture = new Fixture()) {
            var config = fixture.config();
            var key = number("strict");
            config.define(key);
            var alias = number("strict");
            assertThrows(IllegalArgumentException.class, () -> config.get(alias));
            var foreign = fixture.host.rootScope().openChild(ScopeKind.SCREEN, UiId.parse("sh_better_ui:foreign"));
            assertThrows(SecurityException.class,
                    () -> config.beginTransaction().set(new ConfigScope(foreign, SCREEN), key, 2).commit());
            assertThrows(IllegalArgumentException.class,
                    () -> config.beginTransaction().set(new ConfigScope(fixture.screen(), COMPONENT), key, 2).commit());
            assertThrows(SecurityException.class, () -> fixture.publication.current().configs().set(key, 3));
            assertRawTypeRejected(config, fixture.screen(), key);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void assertRawTypeRejected(UIConfigCenter config, ScopeHandle screen, ConfigKey key) {
        assertThrows(IllegalArgumentException.class,
                () -> config.beginTransaction().set(new ConfigScope(screen, SCREEN), key, "wrong").commit());
    }

    @Test
    void stateAndDerivedValuesPublishTogetherWhileAnOffThreadReaderHoldsTheOldVersion() throws Exception {
        try (var fixture = new Fixture()) {
            var state = fixture.state();
            var a = state("a", StateKey.Kind.SCREEN);
            var b = state("b", StateKey.Kind.SCREEN);
            var sum = state("sum", StateKey.Kind.SCREEN);
            state.define(a);
            state.define(b);
            state.define(sum);
            var screen = fixture.screen();
            state.derive(screen, sum, Set.of(a, b), values -> values.get(a) + values.get(b));
            var captured = new CountDownLatch(1);
            var committed = new CountDownLatch(1);
            var reader = CompletableFuture.runAsync(() -> {
                var old = state.snapshot(screen);
                captured.countDown();
                await(committed);
                var current = state.snapshot(screen);
                assertEquals(List.of(0, 0, 0), List.of(old.get(a), old.get(b), old.get(sum)));
                assertEquals(List.of(3, 7, 10), List.of(current.get(a), current.get(b), current.get(sum)));
                assertTrue(current.version() > old.version());
                assertThrows(IllegalStateException.class, () -> state.beginTransaction().set(screen, a, 8).commit());
            });
            try {
                assertTrue(captured.await(5, TimeUnit.SECONDS));
                state.beginTransaction().set(screen, a, 3).set(screen, b, 7).commit();
            } finally {
                committed.countDown();
                reader.get(5, TimeUnit.SECONDS);
            }
            assertThrows(IllegalArgumentException.class,
                    () -> state.beginTransaction().set(screen, a, 5).set(screen, sum, 9).commit());
            assertEquals(3, state.get(screen, a));
            screen.close();
            assertEquals(0, state.get(fixture.screen(), sum));
        }
    }

    @Test
    void derivedCyclesAndReentrantUpdatersFailBeforePublication() {
        try (var fixture = new Fixture()) {
            var state = fixture.state();
            var a = state("first", StateKey.Kind.GLOBAL);
            var b = state("second", StateKey.Kind.GLOBAL);
            state.define(a);
            state.define(b);
            state.derive(fixture.context.get().scope(), b, Set.of(a), values -> values.get(a) * 2);
            var version = state.snapshot().version();
            var failure = assertThrows(IllegalArgumentException.class, () ->
                    state.derive(fixture.context.get().scope(), a, Set.of(b), values -> values.get(b)));
            assertTrue(failure.getMessage().contains("example:first"));
            assertTrue(failure.getMessage().contains("example:second"));
            assertThrows(IllegalStateException.class, () -> state.update(a, value -> {
                state.update(a, old -> 9);
                return 1;
            }));
            assertEquals(version, state.snapshot().version());
        }
    }

    @Test
    void listenerWritesUseSubsequentNotificationsAndDetectCausalCycles() {
        try (var fixture = new Fixture()) {
            var state = fixture.state();
            var a = state("source", StateKey.Kind.GLOBAL);
            var b = state("target", StateKey.Kind.GLOBAL);
            state.define(a);
            state.define(b);
            var scope = fixture.context.get().scope();
            var calls = new java.util.ArrayList<String>();
            state.observe(scope, a, change -> {
                calls.add("A");
                state.update(b, value -> change.current());
                calls.add("A done");
            });
            state.observe(scope, b, change -> {
                calls.add("B");
                state.update(a, value -> 99);
            });
            var independent = new AtomicInteger();
            state.observe(scope, b, change -> independent.incrementAndGet());
            state.update(a, value -> 5);
            assertEquals(List.of("A", "A done", "B"), calls);
            assertEquals(1, independent.get());
            assertEquals(5, state.get(a));
            assertEquals(5, state.get(b));
            assertTrue(fixture.host.lifecycle().snapshot().diagnostics().stream()
                    .anyMatch(message -> message.contains("example:source") && message.contains("example:target")));
        }
    }

    @Test
    void renderCannotCommitConfigurationOrState() {
        try (var fixture = new Fixture()) {
            var config = fixture.config();
            var key = number("render");
            config.define(key);
            var state = fixture.state();
            var value = state("render_state", StateKey.Kind.GLOBAL);
            state.define(value);
            fixture.context.get().engine().scheduler().submit(UITask.ui(fixture.screen(), UIPhase.RENDER,
                    UIPriority.NORMAL, () -> {
                        assertThrows(IllegalStateException.class, () -> config.set(key, 4));
                        assertThrows(IllegalStateException.class, () -> state.update(value, old -> 4));
                    }));
            fixture.host.runFrame();
            assertEquals(0, config.get(key));
            assertEquals(0, state.get(value));
        }
    }

    private static ConfigKey<Integer> number(String path) {
        return ConfigKey.of(new UiId("example", path), Integer.class, 0, EnumSet.complementOf(EnumSet.of(ENGINE_DEFAULTS)),
                value -> value >= 0);
    }

    private static StateKey<Integer> state(String path, StateKey.Kind kind) {
        return StateKey.of(new UiId("example", path), Integer.class, 0, kind);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("timeout");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final AtomicReference<ModuleContext> context = new AtomicReference<>();
        final EnginePublication publication = new EnginePublication();
        final KernelHost host = new KernelHost(ModuleDescriptor.Platform.FORGE_1_20_1, publication,
                () -> 0, runnable -> fail("unexpected worker"), SchedulerBudget.DEFAULT);
        ScopeHandle screen;

        Fixture() {
            var grants = Set.of(UIPermission.MODIFY_GLOBAL_CONFIG, UIPermission.MODIFY_GLOBAL_THEME,
                    UIPermission.REGISTER_RENDER_PASS);
            var descriptor = module("values");
            grants.forEach(descriptor::requestPermission);
            host.start(List.of(candidate(descriptor.build(), grants, false, ctx -> {}, context::set, ctx -> {})));
        }

        UIConfigCenter config() {
            return context.get().engine().configs();
        }

        UIStateStore state() {
            return context.get().engine().states();
        }

        ScopeHandle screen() {
            if (screen == null || !screen.isOpen()) {
                screen = context.get().scope().openChild(ScopeKind.SCREEN, UiId.parse("example:values_screen"));
            }
            return screen;
        }

        ScopeHandle component(ScopeHandle parent) {
            return parent.openChild(ScopeKind.UI_TREE, UiId.parse("example:tree"))
                    .openChild(ScopeKind.COMPONENT, UiId.parse("example:control"));
        }

        @Override
        public void close() {
            host.close();
        }
    }
}
