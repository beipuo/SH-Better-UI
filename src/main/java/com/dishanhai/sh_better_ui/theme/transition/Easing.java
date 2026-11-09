// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.transition;

import java.util.function.DoubleUnaryOperator;

/** Built-in normalized timing curves. SPRING is critically damped and bounded. */
public enum Easing implements DoubleUnaryOperator {
    LINEAR,
    STANDARD,
    EMPHASIZED,
    SPRING;

    @Override
    public double applyAsDouble(double t) {
        return switch (this) {
            case LINEAR -> t;
            case STANDARD -> t * t * (3 - 2 * t);
            case EMPHASIZED -> t < 0.5 ? 4 * t * t * t : 1 - Math.pow(2 - 2 * t, 3) / 2;
            case SPRING -> (1 - (1 + 8 * t) * Math.exp(-8 * t)) / (1 - 9 * Math.exp(-8));
        };
    }
}
