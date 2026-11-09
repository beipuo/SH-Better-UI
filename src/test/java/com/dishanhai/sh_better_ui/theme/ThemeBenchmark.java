// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.style.StyleInvalidation;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import com.dishanhai.sh_better_ui.theme.transition.Easing;
import com.dishanhai.sh_better_ui.theme.transition.TransitionSpec;
import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/** Reproducible core-side benchmark. Run with gradle/theme-benchmark.init.gradle. */
public final class ThemeBenchmark {
    private static final int WARMUP = 80;
    private static final int FRAMES = 160;
    private static final java.lang.management.ThreadMXBean CPU = ManagementFactory.getThreadMXBean();
    private static final ThreadMXBean ALLOCATIONS = CPU instanceof ThreadMXBean bean ? bean : null;
    private static final long THREAD = Thread.currentThread().getId();
    private static volatile float blackhole;

    private ThemeBenchmark() {}

    public static void main(String[] args) {
        System.out.printf("Java %s / %s, warmup=%d frames, samples=%d%n", System.getProperty("java.version"),
                System.getProperty("os.name"), WARMUP, FRAMES);
        System.out.println("widgets,case,p50_us,p95_us,p99_us,cpu_us_per_frame,alloc_bytes_per_frame,"
                + "gc_count,gc_ms,invalidated_per_frame,layout_per_frame,read_per_frame,resolutions,evaluated_tokens");
        for (int widgets : new int[]{100, 1000, 10000}) {
            run(widgets, "static", 0, false, false);
            for (int changes : new int[]{1, 10, 100}) run(widgets, "override_" + changes, changes, false, true);
            run(widgets, "animated_retarget", 100, true, false);
        }
    }

    private static void run(int widgets, String label, int changes, boolean animated, boolean nested) {
        var clock = new AtomicLong();
        var totals = new Totals();
        try (var manager = new ThemeManager(clock::get, error -> { throw error; })) {
            var keys = new ArrayList<ThemeToken<Float>>(100);
            for (int i = 0; i < 100; i++) keys.add(manager.registerToken(ThemeTokens.floatToken("benchmark:token/" + i, 0)));
            var screen = manager.global().child("screen");
            var scopes = new ArrayList<ThemeScope>();
            for (int branch = 0; branch < (nested ? 4 : 1); branch++) {
                var panel = screen.child("panel-" + branch);
                if (nested) panel = panel.child("card");
                scopes.add(panel);
            }
            var bindings = new ArrayList<ThemeScope.BoundValue<Float>>(widgets);
            for (int i = 0; i < widgets; i++) {
                var scope = scopes.get((i / keys.size()) % scopes.size());
                var owner = scope.invalidationOwner(types -> {
                    totals.invalidations++;
                    if (types.contains(StyleInvalidation.LAYOUT)) totals.layouts++;
                });
                bindings.add(scope.bind(ThemeBinding.of(keys.get(i % keys.size())), owner,
                        i % 10 == 0 ? StyleInvalidation.LAYOUT : StyleInvalidation.PAINT));
            }
            if (animated) {
                manager.register(variant("benchmark:a", keys, 1));
                manager.register(variant("benchmark:b", keys, 2));
            }
            for (int i = 0; i < WARMUP; i++) frame(manager, clock, scopes.get(0), keys, bindings, i, changes, animated);
            totals.reset();
            var before = manager.inspect(screen);
            var gc = gc();
            long cpu = CPU.getCurrentThreadCpuTime(), allocated = allocated();
            var latency = new long[FRAMES];
            for (int i = 0; i < FRAMES; i++) {
                long start = System.nanoTime();
                frame(manager, clock, scopes.get(0), keys, bindings, i + WARMUP, changes, animated);
                latency[i] = System.nanoTime() - start;
            }
            long cpuUsed = CPU.getCurrentThreadCpuTime() - cpu, bytes = allocated() - allocated;
            var endGc = gc();
            var after = manager.inspect(screen);
            Arrays.sort(latency);
            System.out.printf(Locale.ROOT, "%d,%s,%.2f,%.2f,%.2f,%.2f,%d,%d,%d,%.1f,%.1f,%d,%d,%d%n",
                    widgets, label, percentile(latency, .50), percentile(latency, .95), percentile(latency, .99),
                    cpuUsed / 1000.0 / FRAMES, ALLOCATIONS == null ? -1 : bytes / FRAMES,
                    endGc[0] - gc[0], endGc[1] - gc[1], totals.invalidations / (double) FRAMES,
                    totals.layouts / (double) FRAMES, widgets, after.resolutions() - before.resolutions(),
                    after.evaluatedTokens() - before.evaluatedTokens());
        }
    }

    private static void frame(ThemeManager manager, AtomicLong clock, ThemeScope panel, List<ThemeToken<Float>> keys,
                              List<ThemeScope.BoundValue<Float>> bindings, int index, int changes, boolean animated) {
        clock.addAndGet(16);
        if (animated && index % 3 == 0) {
            manager.transitionTo((index / 3) % 2 == 0 ? "benchmark:a" : "benchmark:b",
                    TransitionSpec.of(240, Easing.STANDARD));
        } else if (!animated && changes > 0) {
            panel.configure(local -> {
                for (int i = 0; i < changes; i++) local.override(keys.get(i), (float) index);
            });
        }
        manager.frame();
        float sum = 0;
        for (var value : bindings) sum += value.value();
        blackhole = sum;
    }

    private static Theme variant(String id, List<ThemeToken<Float>> keys, float value) {
        var builder = Theme.builder(id);
        keys.forEach(key -> builder.token(key, value));
        return builder.build();
    }

    private static double percentile(long[] sorted, double p) { return sorted[(int) Math.ceil(p * sorted.length) - 1] / 1000.0; }

    private static long allocated() {
        return ALLOCATIONS == null ? 0 : ALLOCATIONS.getThreadAllocatedBytes(THREAD);
    }

    private static long[] gc() {
        long[] result = new long[2];
        for (var collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            result[0] += Math.max(0, collector.getCollectionCount());
            result[1] += Math.max(0, collector.getCollectionTime());
        }
        return result;
    }

    private static final class Totals {
        long invalidations;
        long layouts;

        void reset() { invalidations = layouts = 0; }
    }
}
