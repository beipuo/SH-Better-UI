// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.benchmark;

import com.sun.management.ThreadMXBean;

import java.io.IOException;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;

/** Measures one operation at a time on the constructing thread. Writes only after sampling. */
public final class BenchmarkSamples {
    public static final String RAW_HEADER = "scenario,sample,elapsed_ns,allocated_bytes\n";
    public static final String SUMMARY_HEADER = "scenario,samples,mean_ns,p95_ns,p99_ns,"
            + "mean_allocated_bytes,p95_allocated_bytes,p99_allocated_bytes\n";

    private final ThreadMXBean allocations = allocationBean();
    private final long ownerId = Thread.currentThread().getId();
    private final long[] elapsed;
    private final long[] allocated;
    private long started;
    private long beforeBytes;
    private int count;

    public BenchmarkSamples(int sampleCount) {
        if (sampleCount < 1) throw new IllegalArgumentException("Sample count must be positive");
        elapsed = new long[sampleCount];
        allocated = new long[sampleCount];
    }

    public boolean allocationAvailable() {
        return allocations != null;
    }

    public void begin() {
        beforeBytes = allocatedBytes();
        started = System.nanoTime();
    }

    public void end() {
        long duration = System.nanoTime() - started;
        long afterBytes = allocatedBytes();
        elapsed[count] = duration;
        allocated[count] = beforeBytes < 0 || afterBytes < beforeBytes ? -1 : afterBytes - beforeBytes;
        count++;
    }

    public void writeRaw(Writer out, String scenario) throws IOException {
        requireComplete();
        for (int index = 0; index < count; index++) {
            out.write(scenario + "," + (index + 1) + "," + elapsed[index] + "," + allocated[index] + "\n");
        }
    }

    public void writeSummary(Writer out, String scenario) throws IOException {
        requireComplete();
        out.write(scenario + "," + count + "," + statistics(elapsed) + "," + statistics(allocated) + "\n");
    }

    private void requireComplete() {
        if (count != elapsed.length) throw new IllegalStateException("Incomplete benchmark samples");
    }

    private long allocatedBytes() {
        if (Thread.currentThread().getId() != ownerId) {
            throw new IllegalStateException("Benchmark samples must run on the constructing thread");
        }
        return allocations == null ? -1 : allocations.getThreadAllocatedBytes(ownerId);
    }

    private static ThreadMXBean allocationBean() {
        if (!(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean)
                || !bean.isThreadAllocatedMemorySupported()) return null;
        try {
            if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
            return bean;
        } catch (UnsupportedOperationException | SecurityException denied) {
            return null;
        }
    }

    private static String statistics(long[] values) {
        double sum = 0;
        for (long value : values) {
            if (value < 0) return "NA,NA,NA";
            sum += value;
        }
        var ordered = values.clone();
        Arrays.sort(ordered);
        return String.format(Locale.ROOT, "%.2f,%d,%d", sum / ordered.length,
                percentile(ordered, 0.95), percentile(ordered, 0.99));
    }

    private static long percentile(long[] sorted, double fraction) {
        return sorted[(int) Math.ceil(sorted.length * fraction) - 1];
    }
}
