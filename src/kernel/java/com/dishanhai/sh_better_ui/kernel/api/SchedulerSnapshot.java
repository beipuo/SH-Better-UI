// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record SchedulerSnapshot(long frame, long frameTimeNanos, int queued, int backgroundInFlight,
                                long succeeded, long cancelled, long timedOut, long failed,
                                long rejected, long deferred, Map<UIPhase, PhaseMetrics> phases) {
    public SchedulerSnapshot {
        var copy = new EnumMap<UIPhase, PhaseMetrics>(UIPhase.class);
        copy.putAll(phases);
        phases = Collections.unmodifiableMap(copy);
    }

    public record PhaseMetrics(long callbacks, long elapsedNanos, long overBudget) {}
}
