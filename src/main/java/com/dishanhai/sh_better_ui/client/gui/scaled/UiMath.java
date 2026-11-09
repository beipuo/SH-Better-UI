// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.gui.scaled;

/** Math operations shared by the Java 17 and Java 21 client builds. */
public final class UiMath {
    public static final double TAU = 2.0 * Math.PI;

    private UiMath() {}

    public static int clamp(long value, int min, int max) {
        return (int) clamp(value, (long) min, max);
    }

    public static long clamp(long value, long min, long max) {
        if (min > max) throw new IllegalArgumentException("min > max");
        return Math.max(min, Math.min(value, max));
    }

    public static float clamp(float value, float min, float max) {
        if (Float.isNaN(min) || Float.isNaN(max) || Float.compare(min, max) > 0)
            throw new IllegalArgumentException("invalid bounds");
        return Math.max(min, Math.min(value, max));
    }

    public static double clamp(double value, double min, double max) {
        if (Double.isNaN(min) || Double.isNaN(max) || Double.compare(min, max) > 0)
            throw new IllegalArgumentException("invalid bounds");
        return Math.max(min, Math.min(value, max));
    }

    public static int ceilDiv(int value, int divisor) {
        int quotient = value / divisor;
        return (value ^ divisor) >= 0 && value % divisor != 0 ? quotient + 1 : quotient;
    }
}
