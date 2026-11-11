// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.Platform;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.UIPhase;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.Content;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.Kind;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.Lifecycle;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.Priority;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.Text;
import com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceService.Plan;
import com.dishanhai.sh_better_ui.kernel.api.live.NotificationService;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfaceGeometry;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfacePlacement;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfacePlacement.Candidate;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfacePlacement.Rect;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfacePresentation;
import com.dishanhai.sh_better_ui.kernel.benchmark.BenchmarkSamples;
import com.dishanhai.sh_better_ui.kernel.internal.KernelBenchmarkWorkloads.Fixture;

import java.io.IOException;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Function;

/** Headless workloads for the production live surface runtime, scheduler and placement. */
public final class LiveSurfaceBenchmark {
    private static final int UPDATES = 1_000;
    private static final int BURST_ADMITTED = 8;
    private static final int BURST_REJECTED = 8;
    private static final int BURST_MERGE_ROUNDS = 2;
    private static final Rect VIEWPORT = new Rect(0, 0, 320, 180);
    private static final Content INITIAL = label("Initial activity");
    private static final Content PENDING = label("Pending on scope close");

    private LiveSurfaceBenchmark() {}

    public static void main(String[] args) throws IOException {
        Options options = Options.parse(args);
        List<Scenario> scenarios = scenarios();
        List<Scenario> selected = scenarios.stream()
                .filter(scenario -> options.scenario.equals("all") || options.scenario.equals(scenario.name)).toList();
        require(!selected.isEmpty(), "Unknown scenario " + options.scenario + ". Available: "
                + scenarios.stream().map(Scenario::name).toList());

        Files.createDirectories(options.output);
        Path metadata = options.output.resolve("environment.properties");
        Files.deleteIfExists(metadata);
        try (var raw = Files.newBufferedWriter(options.output.resolve("raw.csv"));
             var summary = Files.newBufferedWriter(options.output.resolve("summary.csv"));
             var work = Files.newBufferedWriter(options.output.resolve("work.csv"))) {
            raw.write(BenchmarkSamples.RAW_HEADER);
            summary.write(BenchmarkSamples.MEDIAN_SUMMARY_HEADER);
            work.write("scenario,sample_unit,completed_unit,samples,attempted,completed,coalesced,rejected,"
                    + "stable_plan_rebuilds,scheduler_succeeded,scheduler_cancelled,queued_at_end\n");
            for (Scenario scenario : selected) run(options, scenario, raw, summary, work);
        }
        writeEnvironment(options, selected, metadata);
        System.out.println("Wrote live surface benchmark " + options.output.toAbsolutePath());
    }

    private static List<Scenario> scenarios() {
        var scenarios = new ArrayList<Scenario>();
        for (int count : new int[] {1, 10, 100}) {
            scenarios.add(new Scenario("stable-" + count, "frame", "validated_frames", KernelBenchmarkWorkloads.STEP_NANOS,
                    0, 0, context -> stable(context, count)));
        }
        scenarios.add(new Scenario("coalesced-1000", "1000_submissions_and_frame", "committed_revisions",
                KernelBenchmarkWorkloads.STEP_NANOS, 1, 0, LiveSurfaceBenchmark::updates));
        scenarios.add(new Scenario("notification-burst-32", "32_publications_and_frame", "new_notifications",
                Duration.ofSeconds(1).toNanos(), 0, 0, LiveSurfaceBenchmark::notificationBurst));
        scenarios.add(new Scenario("placement-8-obstacles", "one_placement", "safe_placements",
                KernelBenchmarkWorkloads.STEP_NANOS, 0, 0, ignored -> placement()));
        scenarios.add(new Scenario("scope-remount", "open_publish_close_and_two_frames", "closed_activities",
                KernelBenchmarkWorkloads.STEP_NANOS, 0, 1, LiveSurfaceBenchmark::scopeRemount));
        return scenarios;
    }

    private static void run(Options options, Scenario scenario, Writer raw, Writer summary, Writer work) throws IOException {
        try (var kernel = new Fixture(options.platform)) {
            var context = new Context(kernel);
            var operation = scenario.setup.apply(context);
            var warm = new Counters();
            for (int i = 0; i < options.warmup; i++) sample(kernel, scenario, operation, warm, null);

            var before = kernel.engine.scheduler().snapshot();
            var measured = new Counters();
            var sampler = new BenchmarkSamples(options.samples);
            for (int i = 0; i < options.samples; i++) sample(kernel, scenario, operation, measured, sampler);
            var after = kernel.engine.scheduler().snapshot();
            long succeeded = after.succeeded() - before.succeeded();
            long cancelled = after.cancelled() - before.cancelled();
            require(succeeded == (long) options.samples * scenario.succeededPerSample
                    && cancelled == (long) options.samples * scenario.cancelledPerSample
                    && after.queued() == 0 && after.backgroundInFlight() == 0 && after.failed() == 0
                    && after.timedOut() == 0 && after.deferred() == 0 && after.rejected() == 0,
                    scenario.name + " did not complete expected scheduler work: " + after);

            sampler.writeRaw(raw, scenario.name);
            sampler.writeSummaryWithMedian(summary, scenario.name);
            String rebuilds = scenario.name.startsWith("stable-") ? Long.toString(measured.planRebuilds) : "NA";
            work.write(scenario.name + "," + scenario.sampleUnit + "," + scenario.completedUnit + "," + options.samples
                    + "," + measured.attempted + "," + measured.completed + "," + measured.coalesced
                    + "," + measured.rejected + "," + rebuilds + "," + succeeded + "," + cancelled + "," + after.queued() + "\n");
        }
    }

    private static void sample(Fixture kernel, Scenario scenario, Workload operation, Counters counters,
                               BenchmarkSamples sampler) {
        kernel.stepClock(scenario.stepNanos);
        operation.prepare();
        if (sampler != null) sampler.begin();
        operation.measure();
        if (sampler != null) sampler.end();
        operation.check(counters);
    }

    private static Workload stable(Context context, int count) {
        for (int i = 0; i < count; i++) context.publish(context.moduleScope(), activity("stable/" + i));
        context.frame();
        return new Workload() {
            private Plan previousPlan = context.live.plan();

            @Override public void measure() { context.frame(); }

            @Override public void check(Counters counters) {
                var plan = context.live.plan();
                require(plan.primary() != null && (plan.secondary() != null) == (count > 1)
                                && plan.waiting().size() == Math.max(0, count - 2),
                        "Stable frame lost its active population");
                if (plan != previousPlan) counters.planRebuilds++;
                previousPlan = plan;
                counters.attempted++;
                counters.completed++;
            }
        };
    }

    private static Workload updates(Context context) {
        var handle = context.publish(context.moduleScope(), activity("updates"));
        var fillers = new Content[UPDATES - 1];
        for (int i = 0; i < fillers.length; i++) fillers[i] = label("Update " + i);
        var ends = new Content[] {label("Final A"), label("Final B")};
        long[] observations = {0};
        handle.observe(ignored -> observations[0]++);
        return new Workload() {
            private int batch;
            private long revision, observed, coalesced, rejected;
            private Content expected;

            @Override public void prepare() {
                expected = ends[batch++ & 1];
                revision = handle.snapshot().revision();
                observed = observations[0];
                var diagnostics = context.live.diagnostics();
                coalesced = diagnostics.coalescedUpdates();
                rejected = diagnostics.rejectedUpdates();
            }

            @Override public void measure() {
                for (Content filler : fillers) handle.update(filler);
                handle.update(expected);
                context.frame();
            }

            @Override public void check(Counters counters) {
                var diagnostics = context.live.diagnostics();
                long committed = handle.snapshot().revision() - revision;
                long merged = diagnostics.coalescedUpdates() - coalesced;
                long dropped = diagnostics.rejectedUpdates() - rejected;
                var primary = context.live.plan().primary();
                require(committed == 1 && merged == UPDATES - 1 && dropped == 0 && observations[0] - observed == 1
                                && primary != null && primary.content() == expected && handle.snapshot().content() == expected,
                        "Update batch did not commit and observe its last submission once");
                counters.attempted += UPDATES;
                counters.completed += committed;
                counters.coalesced += merged;
                counters.rejected += dropped;
            }
        };
    }

    private static Workload notificationBurst(Context context) {
        var requests = new NotificationService.Request[BURST_ADMITTED + BURST_REJECTED];
        for (int i = 0; i < requests.length; i++) {
            var id = id("notification/" + i);
            requests[i] = new NotificationService.Request(id, INITIAL, Priority.NORMAL, Duration.ZERO,
                    id, NotificationService.Merge.COUNT, null);
        }
        return new Workload() {
            private final NotificationService.Handle[] admitted = new NotificationService.Handle[BURST_ADMITTED];
            private ScopeHandle scope;
            private int merged, rejected;

            @Override public void prepare() {
                scope = context.session("burst");
                merged = rejected = 0;
            }

            @Override public void measure() {
                for (int i = 0; i < admitted.length; i++) admitted[i] = context.publish(scope, requests[i]);
                for (int round = 0; round < BURST_MERGE_ROUNDS; round++) {
                    for (int i = 0; i < admitted.length; i++) {
                        if (context.publish(scope, requests[i]) == admitted[i]) merged++;
                    }
                }
                for (int i = admitted.length; i < requests.length; i++) {
                    try {
                        context.publish(scope, requests[i]);
                    } catch (IllegalStateException limit) {
                        String reason = limit.getMessage();
                        if (reason == null || !reason.startsWith("Live notification rate exceeded")) throw limit;
                        rejected++;
                    }
                }
                context.frame();
            }

            @Override public void check(Counters counters) {
                int published = context.live.diagnostics().notifications();
                var primary = context.live.plan().primary();
                require(published == BURST_ADMITTED && merged == BURST_ADMITTED * BURST_MERGE_ROUNDS
                                && rejected == BURST_REJECTED && primary != null && primary.kind() == Kind.NOTIFICATION,
                        "Notification burst did not exercise the expected admission, merge and rate limits");
                for (var handle : admitted) {
                    require(handle.snapshot().occurrences() == 1 + BURST_MERGE_ROUNDS, "Invalid COUNT merge total");
                }
                scope.close();
                require(context.live.diagnostics().notifications() == 0, "Notifications escaped their scope");
                counters.attempted += requests.length + (long) BURST_ADMITTED * BURST_MERGE_ROUNDS;
                counters.completed += published;
                counters.coalesced += merged;
                counters.rejected += rejected;
            }
        };
    }

    private static Workload placement() {
        var candidates = List.of(new Candidate(SurfacePresentation.COMPACT, new SurfaceGeometry(80, 4, 160, 26, 9, 1), 136, 24),
                new Candidate(SurfacePresentation.MINIMAL, new SurfaceGeometry(112, 4, 96, 19, 8, 1)));
        var fixed = List.of(new Rect(280, 0, 40, 52), new Rect(0, 156, 140, 24), new Rect(184, 156, 136, 24),
                new Rect(4, 62, 16, 42), new Rect(290, 80, 24, 28), new Rect(70, 120, 24, 18), new Rect(244, 120, 24, 18));
        var obstacles = new ArrayList<List<Rect>>();
        for (int width : new int[] {72, 88, 104, 134}) {
            var regions = new ArrayList<>(fixed);
            regions.add(new Rect(0, 0, width, 36));
            obstacles.add(List.copyOf(regions));
        }
        return new Workload() {
            private int cursor;
            private List<Rect> occupied;
            private SurfacePlacement.Result previous;

            @Override public void prepare() {
                occupied = obstacles.get(cursor);
                cursor = (cursor + 1) % obstacles.size();
            }

            @Override public void measure() {
                previous = SurfacePlacement.place(VIEWPORT, candidates, occupied, SurfacePlacement.CollisionPolicy.AVOID, previous);
            }

            @Override public void check(Counters counters) {
                SurfaceGeometry geometry = previous.geometry();
                require(geometry.visible() && SurfacePlacement.fit(geometry, VIEWPORT).equals(geometry), "Placement escaped viewport");
                var bounds = new Rect(geometry.x(), geometry.y(), geometry.width(), geometry.height());
                for (var rect : occupied) require(!bounds.intersects(rect), "Placement overlapped occupied space");
                counters.attempted++;
                counters.completed++;
            }
        };
    }

    private static Workload scopeRemount(Context context) {
        var request = activity("remount/activity");
        var reservationId = id("remount/reservation");
        var rect = new Rect(0, 0, 64, 28);
        return new Workload() {
            private ActivityService.Handle handle;
            private long lastInstance;
            private boolean mounted;

            @Override public void measure() {
                try (var scope = context.session("remount")) {
                    handle = context.publish(scope, request);
                    context.live.reserve(context.kernel.moduleContext(), scope, reservationId, rect);
                    context.frame();
                    var primary = context.live.plan().primary();
                    mounted = primary != null && primary.instance() == handle.snapshot().instance();
                    handle.update(PENDING);
                }
                context.frame();
            }

            @Override public void check(Counters counters) {
                var snapshot = handle.snapshot();
                require(mounted && snapshot.instance() > lastInstance && snapshot.lifecycle() == Lifecycle.CANCELLED
                                && snapshot.content() == INITIAL && context.live.diagnostics().activities() == 0
                                && context.live.plan().primary() == null && context.live.occupied().isEmpty(),
                        "Closing the remounted scope retained pending work, activity state or reservations");
                lastInstance = snapshot.instance();
                counters.attempted++;
                counters.completed++;
            }
        };
    }

    private static void writeEnvironment(Options options, List<Scenario> selected, Path metadata) throws IOException {
        var properties = new Properties();
        for (String key : List.of("java.runtime.version", "java.vendor", "java.vm.name", "os.name", "os.version", "os.arch")) {
            properties.setProperty(key, System.getProperty(key, "unknown"));
        }
        properties.setProperty("cpu.availableProcessors", Integer.toString(Runtime.getRuntime().availableProcessors()));
        properties.setProperty("cpu.reportedModel", System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "unavailable"));
        properties.setProperty("jvm.arguments", String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments()));
        properties.setProperty("benchmark.platformSelector", options.platform.name());
        properties.setProperty("benchmark.warmupPerScenario", Integer.toString(options.warmup));
        properties.setProperty("benchmark.samplesPerScenario", Integer.toString(options.samples));
        properties.setProperty("benchmark.allocation", new BenchmarkSamples(1).allocationAvailable()
                ? "ThreadMXBean current-thread bytes" : "unavailable");
        properties.setProperty("benchmark.percentile", "nearest rank, ceil(n*p)");
        properties.setProperty("benchmark.schedulerBudget", KernelBenchmarkWorkloads.BUDGET.toString());
        for (Scenario scenario : selected) {
            properties.setProperty("benchmark.stepNanos." + scenario.name, Long.toString(scenario.stepNanos));
        }
        properties.setProperty("kernel.codeSource", KernelHost.class.getProtectionDomain().getCodeSource().getLocation().toString());
        try (var out = Files.newBufferedWriter(metadata)) {
            properties.store(out, "Headless adaptive live surface benchmark");
        }
    }

    private static ActivityService.Request activity(String path) {
        return new ActivityService.Request(id(path), INITIAL, Priority.NORMAL, Duration.ZERO, true, null);
    }

    private static UiId id(String path) { return new UiId("kernel_benchmark", path); }

    private static Content label(String title) { return Content.label(Text.literal(title)); }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException(detail);
    }

    private record Scenario(String name, String sampleUnit, String completedUnit, long stepNanos,
                            int succeededPerSample, int cancelledPerSample, Function<Context, Workload> setup) {}

    private interface Workload {
        default void prepare() {}
        void measure();
        void check(Counters counters);
    }

    private static final class Counters {
        long attempted, completed, coalesced, rejected, planRebuilds;
    }

    private static final class Context {
        private final Fixture kernel;
        private final LiveSurfaceRuntime live;
        private final Consumer<UIPhase> phases;

        private Context(Fixture kernel) {
            this.kernel = kernel;
            live = new LiveSurfaceRuntime(kernel.host);
            phases = phase -> {
                if (phase == UIPhase.MEASURE) live.advance(false, false);
            };
        }

        private ScopeHandle moduleScope() { return kernel.moduleContext().scope(); }

        private ScopeHandle session(String name) { return moduleScope().openChild(ScopeKind.CLIENT_SESSION, id(name)); }

        private ActivityService.Handle publish(ScopeHandle scope, ActivityService.Request request) {
            return live.publish(kernel.moduleContext(), scope, request);
        }

        private NotificationService.Handle publish(ScopeHandle scope, NotificationService.Request request) {
            return live.publish(kernel.moduleContext(), scope, request);
        }

        private void frame() {
            kernel.host.runFrame(phases);
            var primary = live.plan().primary();
            if (primary != null && primary.kind() == Kind.NOTIFICATION) live.markPainted(primary);
        }
    }

    private record Options(Path output, Platform platform, int warmup, int samples, String scenario) {
        private static Options parse(String[] args) {
            Path output = Path.of("build", "reports", "live-surface-benchmark");
            Platform platform = Platform.FORGE_1_20_1;
            int warmup = 2_000;
            int samples = 2_000;
            String scenario = "all";
            if ((args.length & 1) != 0) throw new IllegalArgumentException("Expected benchmark option/value pairs");
            for (int i = 0; i < args.length; i += 2) {
                String value = args[i + 1];
                switch (args[i]) {
                    case "--output" -> output = Path.of(value);
                    case "--platform" -> platform = Platform.valueOf(value);
                    case "--warmup" -> warmup = Integer.parseInt(value);
                    case "--samples" -> samples = Integer.parseInt(value);
                    case "--scenario" -> scenario = value;
                    default -> throw new IllegalArgumentException("Unknown benchmark option " + args[i]);
                }
            }
            if (warmup < 0 || samples < 1) throw new IllegalArgumentException("Require warmup >= 0 and samples >= 1");
            return new Options(output, platform, warmup, samples, scenario);
        }
    }
}
