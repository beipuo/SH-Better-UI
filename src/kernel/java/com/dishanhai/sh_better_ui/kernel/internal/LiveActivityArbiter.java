// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.Kind;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.Priority;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/** Pure ranking; the central kernel's measure phase owns the current presentation. */
final class LiveActivityArbiter {
    static final long MIN_DWELL = Duration.ofMillis(600).toNanos();
    static final long FAIRNESS_INTERVAL = Duration.ofMillis(500).toNanos();
    private static final long FAIRNESS_POINT = Duration.ofMillis(5).toNanos();

    record Candidate(long instance, Priority priority, Kind kind, long waitingSince, long updatedAt, boolean interruptible) {}

    private LiveActivityArbiter() {}

    static List<Candidate> rank(List<Candidate> candidates, long primary, long primarySince, long pinned,
                                boolean interacting, long now) {
        var order = Comparator.comparingLong((Candidate candidate) -> score(candidate, primary, pinned, now))
                .reversed().thenComparingLong(Candidate::instance);
        var ranked = new java.util.ArrayList<>(candidates);
        ranked.sort(order);
        for (int index = 1; index < ranked.size(); index++) {
            var candidate = ranked.get(index);
            if (candidate.instance != primary) continue;
            var challenger = ranked.get(0);
            if (interacting || challenger.priority != Priority.CRITICAL
                    && (!candidate.interruptible || now - primarySince < MIN_DWELL)) {
                ranked.remove(index);
                ranked.add(0, candidate);
            }
            break;
        }
        return ranked;
    }

    private static long score(Candidate candidate, long primary, long pinned, long now) {
        long tier = candidate.priority.ordinal() * 1_000L;
        long waiting = candidate.instance == primary ? 0
                : Math.min(6_000, Math.max(0, now - candidate.waitingSince) / FAIRNESS_POINT);
        long freshness = now - candidate.updatedAt < FAIRNESS_INTERVAL ? 80 : 0;
        long urgency = candidate.kind == Kind.NOTIFICATION ? 150 : 0;
        long preference = candidate.instance == pinned ? 8_000 : 0;
        if (candidate.priority == Priority.CRITICAL) tier += 16_000;
        return tier + waiting + freshness + urgency + preference;
    }
}
