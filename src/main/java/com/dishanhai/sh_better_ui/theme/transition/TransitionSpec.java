// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.transition;

import java.util.Objects;
import java.util.function.DoubleUnaryOperator;

/** Duration in real milliseconds, normalized timing curve, and interruption policy. */
public record TransitionSpec(long durationMillis, DoubleUnaryOperator easing, InterruptPolicy policy) {
    private static final long MAX_DURATION = 86_400_000;
    private static final TransitionSpec IMMEDIATE = of(0, Easing.LINEAR);

    public TransitionSpec {
        if (durationMillis < 0 || durationMillis > MAX_DURATION) throw new IllegalArgumentException("Invalid theme duration");
        Objects.requireNonNull(easing);
        Objects.requireNonNull(policy);
    }

    /** Creates a timing curve using the default continuous RETARGET policy. */
    public static TransitionSpec of(long durationMillis, DoubleUnaryOperator easing) {
        return new TransitionSpec(durationMillis, easing, InterruptPolicy.RETARGET);
    }

    public static TransitionSpec immediate() { return IMMEDIATE; }

    public TransitionSpec withPolicy(InterruptPolicy policy) {
        return new TransitionSpec(durationMillis, easing, policy);
    }

    /** Adjusts timing by a positive user speed factor. Zero disables animation. */
    public TransitionSpec atSpeed(double speed) {
        if (!Double.isFinite(speed) || speed < 0) throw new IllegalArgumentException("Invalid animation speed");
        if (speed == 0) return IMMEDIATE;
        return new TransitionSpec(Math.min(MAX_DURATION, Math.round(durationMillis / speed)), easing, policy);
    }

    double eased(double progress) {
        if (progress <= 0 || progress >= 1) return progress;
        double result = easing.applyAsDouble(progress);
        return Double.isFinite(result) ? Math.max(0, Math.min(1, result)) : progress;
    }
}
