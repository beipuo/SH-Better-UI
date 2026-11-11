// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.Platform;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceClaim;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

final class KernelBenchmarkWorkloads {
    static final long SEED = 20261110L;
    static final long STEP_NANOS = 16_666_667L;
    static final SchedulerBudget BUDGET = new SchedulerBudget(512, 128, 2, 256, 64, 32, 1_000_000L);
    private static final int REPLAY_LENGTH = 4096;
    private static final int POINTERS_PER_SAMPLE = 64;
    private static final int TASKS_PER_FRAME = 32;
    private static final int BATCH_SIZE = 256;
    private static final int CHURN_SIZE = 8;
    private static final UiId SCREEN_ID = new UiId("sh_better_ui", "benchmark/screen");
    private static final UiId TREE_ID = id("tree");
    private static final UiId RELOAD_ID = id("reload");
    private static final UiId[] CHILDREN = new UiId[CHURN_SIZE];
    private static final List<ResourceClaim> TEXTURE_CLAIMS = new ArrayList<>();
    private static final UIEventType<NativeInput.Pointer> POINTER =
            new UIEventType<>(id("pointer_move"), NativeInput.Pointer.class, false);
    private static final Set<UIDirtyFlag> LOCAL_FLAGS = Set.of(UIDirtyFlag.MEASURE, UIDirtyFlag.LAYOUT);
    private static final Set<UIPermission> GRANTS = Set.of(UIPermission.MODIFY_GLOBAL_CONFIG,
            UIPermission.MODIFY_GLOBAL_THEME);
    private static final Runnable MUST_NOT_RUN = () -> {
        throw new AssertionError("Work survived a closed benchmark screen");
    };

    static {
        for (int i = 0; i < CHURN_SIZE; i++) {
            CHILDREN[i] = id("child/" + i);
            TEXTURE_CLAIMS.add(new ResourceClaim(ResourceKind.TEXTURE_RESOURCE, id("texture/" + i)));
        }
    }

    private KernelBenchmarkWorkloads() {}

    static List<Scenario> scenarios() {
        var scenarios = new ArrayList<Scenario>();
        for (int count : new int[] {100, 1_000, 10_000}) {
            scenarios.add(new Scenario("idle-" + count, count, "frame", "dirty_entries", f -> layout(f, count, false)));
            scenarios.add(new Scenario("local-dirty-" + count, count, "frame", "dirty_entries",
                    f -> layout(f, count, true)));
        }
        scenarios.add(new Scenario("animation-phase-32", 32, "frame", "callbacks", KernelBenchmarkWorkloads::animation));
        scenarios.add(new Scenario("pointer-route-single-1000", 1_000, "64_moves", "listener_deliveries",
                f -> pointer(f, 1)));
        scenarios.add(new Scenario("pointer-route-prioritized-1000", 1_000, "64_moves", "listener_deliveries",
                f -> pointer(f, 8)));
        scenarios.add(new Scenario("screen-config-batch-256", 0, "256_config_writes", "observed_changes",
                f -> config(f, ConfigLayer.SCREEN)));
        scenarios.add(new Scenario("resource-theme-config-batch-256", 0, "256_config_writes", "observed_changes",
                f -> config(f, ConfigLayer.RESOURCE_THEME)));
        scenarios.add(new Scenario("screen-state-batch-256", 0, "256_state_writes", "observed_changes",
                KernelBenchmarkWorkloads::state));
        scenarios.add(new Scenario("screen-scope-churn-8", CHURN_SIZE, "open_close", "resource_releases",
                KernelBenchmarkWorkloads::screenChurn));
        scenarios.add(new Scenario("reload-scope-churn-8", 0, "open_close", "lease_releases",
                KernelBenchmarkWorkloads::reloadChurn));
        return List.copyOf(scenarios);
    }

    private static Workload layout(Fixture f, int count, boolean dirty) {
        var screen = f.screen();
        var targets = replayTargets(tree(f, screen, count));
        var invalidations = f.engine.invalidations();
        var counts = new DirtyCounts(f.host.dirty());
        Consumer<UIPhase> drain = counts::drainPhase;
        var operation = new Runnable() {
            private int cursor;

            @Override
            public void run() {
                if (dirty) {
                    var leaf = targets[cursor++ & (REPLAY_LENGTH - 1)];
                    // Duplicate invalidations must coalesce in the same frame.
                    invalidations.invalidate(leaf, LOCAL_FLAGS);
                    invalidations.invalidate(leaf, LOCAL_FLAGS);
                }
                f.host.runFrame(drain);
            }
        };
        // Measure: leaf + branch + tree. Layout, transform and hit-test: leaf each. Paint: leaf + branch + tree.
        return new Workload(operation, counts::total, dirty ? 9 : 0);
    }

    private static Workload animation(Fixture f) {
        var leaves = tree(f, f.screen(), TASKS_PER_FRAME, 0);
        var scheduler = f.engine.scheduler();
        var position = new double[TASKS_PER_FRAME];
        var count = new Counter();
        var plans = new UITask[TASKS_PER_FRAME];
        for (int i = 0; i < plans.length; i++) {
            final int index = i;
            plans[i] = UITask.ui(leaves.get(i), UIPhase.ANIMATION, UIPriority.ANIMATION, () -> {
                position[index] = (scheduler.nowNanos() % 1_000_000_000L) / 1_000_000_000.0;
                count.increment();
            });
        }
        return new Workload(() -> {
            for (var plan : plans) scheduler.submit(plan);
            f.host.runFrame();
            double expected = (f.clockNanos % 1_000_000_000L) / 1_000_000_000.0;
            if (position[position.length - 1] != expected) {
                throw new IllegalStateException("Animation-phase callbacks did not use the replay clock");
            }
        }, count::get, TASKS_PER_FRAME);
    }

    private static Workload pointer(Fixture f, int fanout) {
        var screen = f.screen();
        var leaves = tree(f, screen, 1_000);
        var events = f.engine.events();
        var count = new Counter();
        Consumer<UIEvent<NativeInput.Pointer>> receive = event -> count.increment();
        for (var leaf : leaves) {
            events.subscribe(POINTER, leaf, UIEventPhase.TARGET, 0, event -> {
                count.increment();
                event.markHandled();
            });
            if (fanout > 1) {
                for (int i = 1; i <= fanout - 3; i++) {
                    events.subscribe(POINTER, leaf, UIEventPhase.TARGET, i % 3 - 1, receive);
                }
            }
        }
        var root = f.host.ownedNode(f.module.context, leaves.get(0)).parent().parent();
        if (fanout > 1) {
            events.subscribe(POINTER, root, UIEventPhase.CAPTURE, 0, receive);
            events.subscribe(POINTER, root, UIEventPhase.BUBBLE, 0, receive);
        }
        var targets = replayTargets(leaves);
        var random = new Random(SEED);
        var payloads = new NativeInput.Pointer[REPLAY_LENGTH];
        for (int i = 0; i < payloads.length; i++) {
            payloads[i] = new NativeInput.Pointer(NativeInput.PointerAction.MOVE,
                    random.nextDouble() * 960, random.nextDouble() * 540, -1, 0, 0);
        }
        return new Workload(new Runnable() {
            private int cursor;

            @Override
            public void run() {
                for (int event = 0; event < POINTERS_PER_SAMPLE; event++) {
                    int index = cursor++ & (REPLAY_LENGTH - 1);
                    var result = f.host.nativeInput(() -> f.host.routeInput(POINTER, screen, targets[index], payloads[index]));
                    if (!result.handled()) throw new IllegalStateException("Pointer event missed its target");
                }
            }
        }, count::get, POINTERS_PER_SAMPLE * fanout);
    }

    private static Workload config(Fixture f, ConfigLayer layer) {
        var config = f.engine.configs();
        var screen = f.module.context.scope().openChild(ScopeKind.SCREEN, id("config/screen"));
        var target = new ConfigScope(layer == ConfigLayer.SCREEN ? screen : f.host.rootScope(), layer);
        var keys = new ArrayList<ConfigKey<Integer>>(BATCH_SIZE);
        var count = new Counter();
        for (int i = 0; i < BATCH_SIZE; i++) {
            var key = ConfigKey.of(id("config/" + i), Integer.class, 0, Set.of(layer), value -> value >= 0 && value <= 2);
            keys.add(key);
            config.define(key);
            config.observe(screen, key, change -> count.increment());
        }
        return new Workload(new Runnable() {
            private int value = 2;

            @Override
            public void run() {
                value = 3 - value;
                var transaction = config.beginTransaction();
                for (var key : keys) transaction.set(target, key, value);
                transaction.commit();
            }
        }, count::get, BATCH_SIZE);
    }

    private static Workload state(Fixture f) {
        var state = f.engine.states();
        var screen = f.module.context.scope().openChild(ScopeKind.SCREEN, id("state/screen"));
        var keys = new ArrayList<StateKey<Integer>>(BATCH_SIZE);
        var count = new Counter();
        for (int i = 0; i < BATCH_SIZE; i++) {
            var key = StateKey.of(id("state/" + i), Integer.class, 0, StateKey.Kind.SCREEN);
            keys.add(key);
            state.define(key);
            state.observe(screen, key, change -> count.increment());
        }
        return new Workload(new Runnable() {
            private int value = 2;

            @Override
            public void run() {
                value = 3 - value;
                var transaction = state.beginTransaction();
                for (var key : keys) transaction.set(screen, key, value);
                transaction.commit();
            }
        }, count::get, BATCH_SIZE);
    }

    private static Workload screenChurn(Fixture f) {
        var count = new Counter();
        var releases = releaseCallbacks(count);
        Consumer<UIEvent<NativeInput.Pointer>> listener = event -> event.markHandled();
        var scheduler = f.engine.scheduler();
        var events = f.engine.events();
        return new Workload(() -> {
            try (var screen = f.screen()) {
                var tree = f.host.mountTree(f.module.context, screen, TREE_ID);
                for (int i = 0; i < CHILDREN.length; i++) {
                    var child = tree.openChild(ScopeKind.COMPONENT, CHILDREN[i]);
                    child.track(releases[i]);
                    events.subscribe(POINTER, child, UIEventPhase.TARGET, 0, listener);
                    scheduler.submit(UITask.ui(child, UIPhase.STATE_MUTATION, UIPriority.NORMAL, MUST_NOT_RUN));
                }
            }
        }, count::get, CHURN_SIZE);
    }

    private static Workload reloadChurn(Fixture f) {
        var count = new Counter();
        var releases = releaseCallbacks(count);
        return new Workload(() -> {
            try (var scope = f.module.context.scope().openChild(ScopeKind.RESOURCE_RELOAD, RELOAD_ID)) {
                for (int i = 0; i < releases.length; i++) {
                    f.engine.ownership().acquire(new ResourceRequest(TEXTURE_CLAIMS.get(i), scope, 0, 0,
                            ConflictPolicy.REJECT), releases[i]);
                }
            }
        }, count::get, CHURN_SIZE);
    }

    private static AutoCloseable[] releaseCallbacks(Counter count) {
        var releases = new AutoCloseable[CHURN_SIZE];
        for (int i = 0; i < releases.length; i++) {
            // Each slot needs an independent identity while its lifetime is open.
            releases[i] = new AutoCloseable() {
                @Override public void close() { count.increment(); }
            };
        }
        return releases;
    }

    private static List<ScopeHandle> tree(Fixture f, ScopeHandle screen, int count) {
        return tree(f, screen, count, 10);
    }

    private static List<ScopeHandle> tree(Fixture f, ScopeHandle screen, int count, int branches) {
        var root = f.host.mountTree(f.module.context, screen, TREE_ID);
        var parents = new ArrayList<ScopeHandle>(branches);
        for (int i = 0; i < branches; i++) {
            parents.add(root.openChild(ScopeKind.COMPONENT, id("branch/" + i)));
        }
        var leaves = new ArrayList<ScopeHandle>(count - branches);
        for (int i = 0; i < count - branches; i++) {
            var parent = branches == 0 ? root : parents.get(i % branches);
            leaves.add(parent.openChild(ScopeKind.COMPONENT, id("leaf/" + i)));
        }
        return leaves;
    }

    private static ScopeHandle[] replayTargets(List<ScopeHandle> leaves) {
        var random = new Random(SEED);
        var targets = new ScopeHandle[REPLAY_LENGTH];
        for (int i = 0; i < targets.length; i++) {
            targets[i] = leaves.get(random.nextInt(leaves.size()));
        }
        return targets;
    }

    private static UiId id(String path) {
        return new UiId("kernel_benchmark", path);
    }

    record Scenario(String name, int components, String sampleUnit, String workUnit, Function<Fixture, Workload> create) {}

    record Workload(Runnable operation, LongSupplier totalWork, long expectedWorkPerSample) {}

    static final class Fixture implements AutoCloseable {
        private final BenchModule module = new BenchModule();
        private long clockNanos;
        final KernelHost host;
        final UIEngine engine;

        Fixture(Platform platform) {
            host = new KernelHost(platform, new EnginePublication(), () -> clockNanos, null, BUDGET);
            host.start(List.of(new ModuleDiscovery.Candidate("kernel_benchmark", "kernel-benchmark", module, GRANTS, false)));
            engine = module.context.engine();
        }

        ScopeHandle screen() {
            return host.rootScope().openChild(ScopeKind.SCREEN, SCREEN_ID);
        }

        ModuleContext moduleContext() {
            return module.context;
        }

        void stepClock() {
            stepClock(STEP_NANOS);
        }

        void stepClock(long nanos) {
            clockNanos += nanos;
        }

        @Override
        public void close() {
            host.close();
        }
    }

    private static final class BenchModule implements UIModule {
        private ModuleContext context;

        @Override
        public ModuleDescriptor descriptor() {
            var builder = ModuleDescriptor.builder().id(id("workload")).version("1.0.0");
            GRANTS.forEach(builder::requestPermission);
            TEXTURE_CLAIMS.forEach(builder::claimResource);
            return builder.build();
        }

        @Override public void register(ModuleContext module) {}
        @Override public void start(ModuleContext module) { context = module; }
        @Override public void stop(ModuleContext module) {}
    }

    private static final class Counter {
        private long value;

        void increment() { value++; }
        long get() { return value; }
    }

    private static final class DirtyCounts {
        private final DirtyWork work;
        private long total;

        DirtyCounts(DirtyWork work) {
            this.work = work;
        }

        void drainPhase(UIPhase phase) {
            switch (phase) {
                case MEASURE -> drain(UIDirtyFlag.MEASURE);
                case LAYOUT -> drain(UIDirtyFlag.LAYOUT);
                case GEOMETRY_UPDATE -> {
                    drain(UIDirtyFlag.TRANSFORM);
                    drain(UIDirtyFlag.HIT_TEST);
                }
                case RENDER_PREPARATION -> drain(UIDirtyFlag.PAINT);
                default -> {}
            }
        }

        private void drain(UIDirtyFlag flag) {
            total += work.drain(flag).size();
        }

        long total() { return total; }
    }
}
