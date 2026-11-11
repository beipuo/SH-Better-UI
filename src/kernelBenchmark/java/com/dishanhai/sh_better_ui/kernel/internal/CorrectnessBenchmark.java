// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.Platform;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.CorrectionPlan;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.UICorrectnessEngine;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.UIElement;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.UISnapshot;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.ValidationReport;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence;
import com.dishanhai.sh_better_ui.kernel.benchmark.BenchmarkSamples;
import com.dishanhai.sh_better_ui.kernel.internal.KernelBenchmarkWorkloads.Fixture;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Shape;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Transform;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import static com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.*;

/** Headless CPU/allocation measurements. No result represents Minecraft rendering or GPU time. */
public final class CorrectnessBenchmark {
    private static final long SEED = 20261110L;
    private static final long LAYOUT_BUDGET_NANOS = 4_000_000;
    private static final long PREDICTION_BUDGET_NANOS = 1_000_000;
    private static final Rect VIEW = new Rect(0, 0, 1280, 720, Space.GUI);
    private static final List<UISnapshot.ReservedArea> SAFE = List.of(new UISnapshot.ReservedArea(
            new UiId("minecraft", "synthetic_boss"), new Rect(440, 0, 400, 40, Space.GUI), ValidationReport.Evidence.MODEL, 0));
    private static final String WORK_HEADER = "scenario,input_nodes,samples,completed_samples,valid_results,safe_degraded_results,"
            + "renderable_nodes_sum,hard_issues_sum,budget_exhausted_samples,deferred_publications,solver_iterations_sum,model_predictions\n";

    private CorrectnessBenchmark() {}

    public static void main(String[] args) throws IOException {
        var options = Options.parse(args);
        var selected = scenarios().stream().filter(s -> options.scenario.equals("all") || options.scenario.equals(s.name)).toList();
        require(!selected.isEmpty(), "Unknown correctness scenario " + options.scenario);
        Files.createDirectories(options.output);
        var summaries = new ArrayList<String>();
        var workRecords = new ArrayList<String>();
        boolean allocationAvailable = new BenchmarkSamples(1).allocationAvailable();
        try (var raw = Files.newBufferedWriter(options.output.resolve("raw.csv"));
             var summary = Files.newBufferedWriter(options.output.resolve("summary.csv"));
             var work = Files.newBufferedWriter(options.output.resolve("work.csv"))) {
            raw.write(BenchmarkSamples.RAW_HEADER);
            summary.write(BenchmarkSamples.MEDIAN_SUMMARY_HEADER);
            work.write(WORK_HEADER);
            for (var scenario : selected) {
                try (var fixture = new Fixture(options.platform)) {
                    var workload = scenario.setup.apply(fixture);
                    for (int i = 0; i < options.warmup; i++) { workload.measure(); workload.inspect(); }
                    var samples = new BenchmarkSamples(options.samples);
                    var counts = new Counts();
                    for (int i = 0; i < options.samples; i++) {
                        samples.begin();
                        workload.measure();
                        samples.end();
                        counts.add(workload.inspect());
                    }
                    samples.writeRaw(raw, scenario.name);
                    var row = new StringWriter();
                    samples.writeSummaryWithMedian(row, scenario.name);
                    summary.write(row.toString());
                    summaries.add(csvObject(BenchmarkSamples.MEDIAN_SUMMARY_HEADER, row.toString()));
                    String workRow = scenario.name + "," + scenario.nodes + "," + options.samples + "," + counts.completed
                            + "," + counts.valid + "," + counts.degraded + "," + counts.renderable + "," + counts.hard
                            + "," + counts.budget + "," + counts.deferred + "," + counts.iterations + "," + counts.predicted + "\n";
                    work.write(workRow);
                    workRecords.add(csvObject(WORK_HEADER, workRow));
                    System.out.print(row.toString());
                }
            }
        }
        String environment = environment(options, selected, allocationAvailable);
        Files.writeString(options.output.resolve("environment.json"), environment + "\n");
        Files.writeString(options.output.resolve("summary.json"), "{\"environment\":" + environment
                + ",\"measurements\":[" + String.join(",", summaries) + "],\"work\":[" + String.join(",", workRecords) + "]}\n");
        System.out.println("Wrote correctness CPU benchmark " + options.output.toAbsolutePath());
    }

    private static List<Scenario> scenarios() {
        var result = new ArrayList<Scenario>();
        for (int count : new int[] {1, 32, 128}) {
            result.add(new Scenario("idle-committed-" + count, count, "one_unchanged_runtime_prepare",
                    fixture -> runtime(fixture, count, false)));
        }
        for (int count : new int[] {32, 128}) {
            result.add(new Scenario("dense-layout-" + count, count, "all_declaration_updates_and_runtime_prepare",
                    fixture -> runtime(fixture, count, true)));
        }
        result.add(new Scenario("rotation-prediction-64", 1, "one_64_sample_rotation_prediction", ignored -> rotation()));
        result.add(new Scenario("spring-projection-32", 1, "fresh_32_keyframe_spring_and_prediction", ignored -> spring()));
        result.add(new Scenario("visual-evaluate-256", 1, "evaluate_256_synthetic_background_pixels", ignored -> visual(false)));
        result.add(new Scenario("adaptive-material-256", 1, "resolve_256_synthetic_background_pixels", ignored -> visual(true)));
        result.add(new Scenario("bounded-correction-32", 32, "validate_and_resolve_detached_degraded_32_nodes", CorrectnessBenchmark::correction));
        return result;
    }

    private static Workload runtime(Fixture fixture, int count, boolean dense) {
        var engine = new CorrectnessRuntime(fixture.host);
        // Idle samples measure only reuse. Give their untimed initialization an explicit larger
        // budget so a cold-JIT partial publication cannot masquerade as a fully valid idle scene.
        engine.budget(256, 256, dense ? LAYOUT_BUDGET_NANOS : 100_000_000);
        var bindings = new ArrayList<UICorrectnessEngine.Binding>(count);
        var variants = new UIElement[2][count];
        var random = new Random(SEED + count);
        for (int i = 0; i < count; i++) {
            double x = dense ? 240 + random.nextInt(400) : 8 + (i % 8) * 150;
            double y = dense ? 80 + random.nextInt(220) : 56 + (i / 8) * 40;
            for (int phase = 0; phase < 2; phase++) {
                variants[phase][i] = UIElement.builder(id("layout/" + i).toString())
                        .anchor(Anchor.ABSOLUTE).margin(0).preferredSize(120, 32).minSize(60, 24).maxSize(160, 48)
                        .priority(80 - i % 8).preferredBounds(new Rect(x + phase * 8, y + phase * 3, 120, 32, Space.GUI)).build();
            }
            bindings.add(engine.register(fixture.moduleContext(), fixture.moduleContext().scope(), variants[0][i]));
        }
        engine.prepare(VIEW, SAFE, 1, KernelBenchmarkWorkloads.STEP_NANOS, 1, 1, false);
        return new Workload() {
            private long frame = 1;
            private int phase;
            private final long initialVersion = engine.snapshot(fixture.moduleContext(), fixture.moduleContext().scope()).version();
            private long previousVersion = initialVersion;
            @Override public void measure() {
                fixture.stepClock();
                if (dense) {
                    phase ^= 1;
                    for (int i = 0; i < bindings.size(); i++) {
                        var binding = bindings.get(i);
                        require(binding.update(binding.revision(), variants[phase][i]), "Benchmark CAS declaration update failed");
                    }
                }
                engine.prepare(VIEW, SAFE, ++frame, frame * KernelBenchmarkWorkloads.STEP_NANOS, 1, 1, false);
            }
            @Override public Outcome inspect() {
                var snapshot = engine.snapshot(fixture.moduleContext(), fixture.moduleContext().scope());
                require(snapshot.nodes().size() == count, "Runtime lost benchmark nodes");
                if (!dense) {
                    require(snapshot.version() == initialVersion, "Idle runtime rebuilt its immutable committed snapshot");
                    require(snapshot.status() == UISnapshot.Status.VALID && snapshot.nodes().values().stream().allMatch(UISnapshot.Node::renderable),
                            "Idle initialization did not produce a fully valid committed population");
                }
                boolean deferred = false;
                for (var binding : bindings) {
                    var node = snapshot.nodes().get(binding.declaration().id());
                    deferred |= node.revision() != binding.revision();
                }
                var observed = observed(snapshot, engine.metrics().lastIterations(), deferred);
                boolean rebuilt = snapshot.version() != previousVersion;
                previousVersion = snapshot.version();
                return new Outcome(observed.valid, observed.renderable, observed.hard, rebuilt && observed.budget,
                        deferred, rebuilt ? observed.iterations : 0, observed.predicted);
            }
        };
    }

    private static Workload rotation() {
        var shape = Shape.rectangle(120, 40);
        var trajectory = new Trajectory(List.of(new Keyframe(0, new MotionState(shape, new Transform(200, 100, 1, 1, 0, 60, 20))),
                new Keyframe(1, new MotionState(shape, new Transform(280, 140, 1.4, .8, Math.PI * 2, 60, 20)))),
                "Declared rotating/scaling trajectory; synthetic geometry, no rendered observation");
        return new Workload() {
            private Prediction prediction;
            @Override public void measure() { prediction = SpatialIntelligence.predict(id("rotation"), trajectory, 1, 64, PREDICTION_BUDGET_NANOS); }
            @Override public Outcome inspect() {
                require(prediction.conservative() && prediction.sweptBounds().width() > 120, "Rotation envelope lost intermediate coverage");
                return new Outcome(true, 1, 0, prediction.budgetExceeded(), false, 0, true);
            }
        };
    }

    private static Workload spring() {
        var from = new MotionState(Shape.rectangle(120, 40), new Transform(200, 100, 1, 1, 0, 60, 20));
        var to = new MotionState(Shape.rectangle(180, 80), new Transform(500, 180, 1.2, 1.1, .3, 60, 20));
        var velocity = new Velocity(60, -20, .1, 0, .2, 12, 8);
        return new Workload() {
            private Prediction prediction;
            @Override public void measure() {
                var trajectory = SpatialIntelligence.spring(from, to, velocity, .2, .92, .5, 32);
                prediction = SpatialIntelligence.predict(id("spring"), trajectory, .5, 32, PREDICTION_BUDGET_NANOS);
            }
            @Override public Outcome inspect() {
                require(prediction.trajectory().sampledModel() && prediction.spatialError() == -1, "A sampled spring claimed an exact live-model bound");
                return new Outcome(true, 1, 0, prediction.budgetExceeded(), false, 0, true);
            }
        };
    }

    private static Workload visual(boolean resolve) {
        var random = new Random(SEED);
        var pixels = new int[256];
        for (int i = 0; i < pixels.length; i++) pixels[i] = 0xff000000 | random.nextInt(0x1000000);
        var background = VisualIntelligence.BackgroundSample.pixels(new VisualIntelligence.PixelRegion(20, 20, 160, 48),
                16, 16, pixels, VisualIntelligence.Stage.BACKDROP_PRE_MATERIAL, VisualIntelligence.Source.CPU_PIXELS, 1, 0, 1);
        var text = VisualIntelligence.TextStyle.of(0xffeeeeee);
        var material = VisualIntelligence.MaterialSnapshot.of(GlassMaterial.preset(GlassVariant.REGULAR));
        return new Workload() {
            private VisualIntelligence.LegibilityReport report;
            @Override public void measure() {
                report = resolve ? VisualIntelligence.resolve(text, background, material, 4.5).after()
                        : VisualIntelligence.evaluate(text, background, material);
            }
            @Override public Outcome inspect() {
                require(report.valid() && !report.pixelObserved(), "Pre-material model result was mistaken for final native pixels");
                return new Outcome(report.worstRatio() >= 4.5, 1, 0, false, false, 0, true);
            }
        };
    }

    private static Workload correction(Fixture fixture) {
        var engine = new CorrectnessRuntime(fixture.host);
        engine.budget(256, 256, LAYOUT_BUDGET_NANOS);
        for (int i = 0; i < 32; i++) {
            double width = i == 31 ? 2000 : 100;
            var declaration = UIElement.builder(id("correction/" + i).toString()).anchor(Anchor.ABSOLUTE).margin(0)
                    .preferredSize(width, 30).minSize(width, 30).maxSize(3000, 100)
                    .preferredBounds(new Rect(8 + (i % 8) * 150, 56 + (i / 8) * 40, width, 30, Space.GUI)).build();
            engine.register(fixture.moduleContext(), fixture.moduleContext().scope(), declaration);
        }
        engine.prepare(VIEW, SAFE, 1, 0, 1, 1, false);
        var source = engine.snapshot(fixture.moduleContext(), fixture.moduleContext().scope());
        return new Workload() {
            private CorrectionPlan plan;
            @Override public void measure() { plan = engine.resolve(source, engine.validate(source)); }
            @Override public Outcome inspect() {
                require(!plan.proposed().report().unresolvedHard().isEmpty(), "Impossible minimum was silently treated as fixed");
                require(plan.proposed().nodes().values().stream().filter(n -> n.id().path().endsWith("/31")).noneMatch(UISnapshot.Node::renderable),
                        "Bounded correction left the known unsafe control renderable");
                return observed(plan.proposed(), plan.proposed().report().revalidationCount(), false);
            }
        };
    }

    private static Outcome observed(UISnapshot snapshot, int iterations, boolean deferred) {
        boolean budget = snapshot.report().issues().stream().anyMatch(i -> i.code().contains("BUDGET"));
        int renderable = (int) snapshot.nodes().values().stream().filter(UISnapshot.Node::renderable).count();
        return new Outcome(snapshot.status() == UISnapshot.Status.VALID, renderable, snapshot.report().unresolvedHard().size(),
                budget, deferred, iterations, false);
    }

    private record Scenario(String name, int nodes, String sampleUnit, Function<Fixture, Workload> setup) {}
    private interface Workload { void measure(); Outcome inspect(); }
    private record Outcome(boolean valid, int renderable, int hard, boolean budget, boolean deferred, int iterations, boolean predicted) {}
    private static final class Counts {
        long completed, valid, degraded, renderable, hard, budget, deferred, iterations, predicted;
        void add(Outcome result) {
            completed++; if (result.valid) valid++; else degraded++;
            renderable += result.renderable; hard += result.hard;
            if (result.budget) budget++; if (result.deferred) deferred++;
            iterations += result.iterations; if (result.predicted) predicted++;
        }
    }

    private static String environment(Options options, List<Scenario> selected, boolean allocationAvailable) {
        return "{\"java_version\":" + json(System.getProperty("java.version"))
                + ",\"java_vendor\":" + json(System.getProperty("java.vendor"))
                + ",\"vm\":" + json(System.getProperty("java.vm.name"))
                + ",\"jvm_arguments\":" + json(ManagementFactory.getRuntimeMXBean().getInputArguments().toString())
                + ",\"os\":" + json(System.getProperty("os.name") + " " + System.getProperty("os.version"))
                + ",\"arch\":" + json(System.getProperty("os.arch"))
                + ",\"available_processors\":" + Runtime.getRuntime().availableProcessors()
                + ",\"kernel_platform_label\":" + json(options.platform.name())
                + ",\"warmup_per_scenario\":" + options.warmup + ",\"samples_per_scenario\":" + options.samples
                + ",\"input_seed\":" + SEED + ",\"thread_allocations_available\":" + allocationAvailable
                + ",\"layout_cooperative_budget_ns\":" + LAYOUT_BUDGET_NANOS
                + ",\"idle_untimed_initialization_budget_ns\":100000000"
                + ",\"placement_limit\":256,\"prediction_cooperative_budget_ns\":" + PREDICTION_BUDGET_NANOS
                + ",\"measurement\":\"headless Java 17 CPU and calling-thread allocations; no Minecraft client, framebuffer or GPU measurement\""
                + ",\"visual_input\":\"seeded synthetic CPU pixels; pre-material compositing remains a model\""
                + ",\"scenarios\":[" + String.join(",", selected.stream().map(s -> "{\"name\":" + json(s.name)
                        + ",\"input_nodes\":" + s.nodes + ",\"sample_unit\":" + json(s.sampleUnit) + "}").toList()) + "]}";
    }

    private static String csvObject(String header, String row) {
        var keys = header.strip().split(","); var values = row.strip().split(",");
        require(keys.length == values.length, "Benchmark CSV schema mismatch");
        var fields = new ArrayList<String>(keys.length);
        for (int i = 0; i < keys.length; i++) {
            fields.add(json(keys[i]) + ":" + (i == 0 ? json(values[i]) : values[i].equals("NA") ? "null" : values[i]));
        }
        return "{" + String.join(",", fields) + "}";
    }

    private static String json(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }
    private static UiId id(String path) { return new UiId("kernel_benchmark", "correctness/" + path); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private record Options(Path output, Platform platform, int warmup, int samples, String scenario) {
        static Options parse(String[] args) {
            Path output = Path.of("build", "reports", "correctness-benchmark");
            Platform platform = Platform.FORGE_1_20_1;
            int warmup = 2000, samples = 2000; String scenario = "all";
            if ((args.length & 1) != 0) throw new IllegalArgumentException("Expected option/value pairs");
            for (int i = 0; i < args.length; i += 2) {
                switch (args[i]) {
                    case "--output" -> output = Path.of(args[i + 1]);
                    case "--platform" -> platform = Platform.valueOf(args[i + 1]);
                    case "--warmup" -> warmup = Integer.parseInt(args[i + 1]);
                    case "--samples" -> samples = Integer.parseInt(args[i + 1]);
                    case "--scenario" -> scenario = args[i + 1];
                    default -> throw new IllegalArgumentException("Unknown correctness benchmark option " + args[i]);
                }
            }
            if (warmup < 0 || warmup > 1_000_000 || samples < 1 || samples > 1_000_000) {
                throw new IllegalArgumentException("Invalid fixed warmup/sample counts");
            }
            return new Options(output, platform, warmup, samples, scenario);
        }
    }
}
