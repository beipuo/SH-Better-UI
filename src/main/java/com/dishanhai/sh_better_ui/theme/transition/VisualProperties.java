// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.transition;

import com.dishanhai.sh_better_ui.theme.token.ColorValue;

import java.util.Objects;

/** Immutable, platform-neutral values that renderers can map to their own drawing APIs. */
public final class VisualProperties {
    private VisualProperties() {}

    /** A shadow defined in GUI units. */
    public record Shadow(float x, float y, float radius, ColorValue color) {
        public Shadow {
            finite(x, y, radius);
            Objects.requireNonNull(color);
            if (radius < 0) throw new IllegalArgumentException("Negative shadow radius");
        }

        public static Shadow interpolate(Shadow a, Shadow b, double t) {
            return new Shadow(lerp(a.x, b.x, t), lerp(a.y, b.y, t), lerp(a.radius, b.radius, t),
                    ColorValue.interpolateOklab(a.color, b.color, t));
        }
    }

    /** Translation, scale and rotation. Rotation follows the shortest path in degrees. */
    public record Transform(float x, float y, float scaleX, float scaleY, float rotation) {
        public static final Transform IDENTITY = new Transform(0, 0, 1, 1, 0);

        public Transform { finite(x, y, scaleX, scaleY, rotation); }

        public static Transform interpolate(Transform a, Transform b, double t) {
            double delta = Math.IEEEremainder((double) b.rotation - a.rotation, 360);
            return new Transform(lerp(a.x, b.x, t), lerp(a.y, b.y, t), lerp(a.scaleX, b.scaleX, t),
                    lerp(a.scaleY, b.scaleY, t), (float) (a.rotation + delta * t));
        }
    }

    private static void finite(float... values) {
        for (float value : values) if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite visual value");
    }

    private static float lerp(float a, float b, double t) { return (float) (a * (1 - t) + b * t); }
}
