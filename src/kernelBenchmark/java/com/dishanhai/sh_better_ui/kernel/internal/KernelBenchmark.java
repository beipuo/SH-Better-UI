// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.Platform;
import com.dishanhai.sh_better_ui.kernel.api.SchedulerSnapshot;
import com.dishanhai.sh_better_ui.kernel.benchmark.BenchmarkSamples;
import com.dishanhai.sh_better_ui.kernel.internal.KernelBenchmarkWorkloads.Fixture;
import com.dishanhai.sh_better_ui.kernel.internal.KernelBenchmarkWorkloads.Scenario;

import java.io.IOException;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/** Java 17 benchmark entry point. Run each baseline and candidate in a fresh JVM. */
public final class KernelBenchmark {
    private KernelBenchmark() {}

    public static void main(String[] args) throws IOException {
        var options = Options.parse(args);
        var scenarios = KernelBenchmarkWorkloads.scenarios();
        var selected = options.scenario.equals("all") ? scenarios : scenarios.stream()
                .filter(scenario -> scenario.name().equals(options.scenario)).toList();
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("Unknown benchmark scenario " + options.scenario + ". Available: "
                    + scenarios.stream().map(Scenario::name).toList());
        }

        Files.createDirectories(options.output);
        var metadata = options.output.resolve("environment.properties");
        // Metadata is written only after all scenarios and counters validate.
        Files.deleteIfExists(metadata);
        try (var raw = Files.newBufferedWriter(options.output.resolve("raw.csv"));
             var summary = Files.newBufferedWriter(options.output.resolve("summary.csv"));
             var work = Files.newBufferedWriter(options.output.resolve("work.csv"))) {
            raw.write(BenchmarkSamples.RAW_HEADER);
            summary.write(BenchmarkSamples.SUMMARY_HEADER);
            work.write("scenario,components,sample_unit,work_unit,expected_work_per_sample,measured_work_total,"
                    + "succeeded,cancelled,deferred,rejected,queued_at_end\n");
            for (var scenario : selected) run(options, scenario, raw, summary, work);
        }
        writeEnvironment(options, selected, metadata);
        System.out.println("Wrote kernel benchmark " + options.output.toAbsolutePath());
    }

    private static void run(Options options, Scenario scenario, Writer raw, Writer summary, Writer work)
            throws IOException {
        try (var fixture = new Fixture(options.platform)) {
            var workload = scenario.create().apply(fixture);
            var sampler = new BenchmarkSamples(options.samples);
            var scheduler = fixture.engine.scheduler();
            for (int i = 0; i < options.warmup; i++) {
                fixture.stepClock();
                workload.operation().run();
            }
            long beforeWork = workload.totalWork().getAsLong();
            var before = scheduler.snapshot();
            for (int i = 0; i < options.samples; i++) {
                fixture.stepClock();
                sampler.begin();
                workload.operation().run();
                sampler.end();
            }
            long measuredWork = workload.totalWork().getAsLong() - beforeWork;
            var after = scheduler.snapshot();
            validate(scenario, options, before, after, measuredWork, workload.expectedWorkPerSample());
            sampler.writeRaw(raw, scenario.name());
            sampler.writeSummary(summary, scenario.name());
            work.write(scenario.name() + "," + scenario.components() + "," + scenario.sampleUnit() + ","
                    + scenario.workUnit() + "," + workload.expectedWorkPerSample() + "," + measuredWork + ","
                    + (after.succeeded() - before.succeeded()) + "," + (after.cancelled() - before.cancelled()) + ","
                    + (after.deferred() - before.deferred()) + "," + (after.rejected() - before.rejected()) + ","
                    + after.queued() + "\n");
        }
    }

    private static void validate(Scenario scenario, Options options, SchedulerSnapshot before, SchedulerSnapshot after,
                                 long measuredWork, long expectedPerSample) {
        if (measuredWork != expectedPerSample * options.samples) {
            throw new IllegalStateException(scenario.name() + " did unexpected work: " + measuredWork);
        }
        if (before.queued() != 0 || after.queued() != 0 || after.backgroundInFlight() != 0
                || after.failed() != 0 || after.timedOut() != 0 || after.rejected() != 0 || after.deferred() != 0) {
            throw new IllegalStateException(scenario.name() + " did not drain expected tasks: " + after);
        }
    }

    private static void writeEnvironment(Options options, List<Scenario> scenarios, Path metadata) throws IOException {
        var props = new Properties();
        for (String key : List.of("java.runtime.version", "java.vendor", "java.vm.name", "os.name", "os.version", "os.arch")) {
            props.setProperty(key, System.getProperty(key, "unknown"));
        }
        props.setProperty("cpu.availableProcessors", Integer.toString(Runtime.getRuntime().availableProcessors()));
        props.setProperty("cpu.reportedModel", System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "unavailable"));
        props.setProperty("jvm.arguments", String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments()));
        props.setProperty("benchmark.allocation", new BenchmarkSamples(1).allocationAvailable()
                ? "ThreadMXBean current-thread bytes" : "unavailable");
        props.setProperty("benchmark.platformSelector", options.platform.name());
        props.setProperty("benchmark.warmupPerScenario", Integer.toString(options.warmup));
        props.setProperty("benchmark.samplesPerScenario", Integer.toString(options.samples));
        props.setProperty("benchmark.scenarios", String.join(",", scenarios.stream().map(Scenario::name).toList()));
        props.setProperty("benchmark.seed", Long.toString(KernelBenchmarkWorkloads.SEED));
        props.setProperty("benchmark.simulatedStepNs", Long.toString(KernelBenchmarkWorkloads.STEP_NANOS));
        props.setProperty("benchmark.schedulerBudget", KernelBenchmarkWorkloads.BUDGET.toString());
        props.setProperty("benchmark.percentile", "nearest rank, ceil(n*p)");
        props.setProperty("kernel.codeSource", KernelHost.class.getProtectionDomain().getCodeSource().getLocation().toString());
        try (var out = Files.newBufferedWriter(metadata)) {
            props.store(out, "Shared UI kernel benchmark");
        }
    }

    private record Options(Path output, Platform platform, int warmup, int samples, String scenario) {
        static Options parse(String[] args) {
            Path output = Path.of("build", "reports", "kernel-benchmark");
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
