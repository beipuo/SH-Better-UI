// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.glass;

import java.util.Objects;

import static com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics.mix;

/** Present geometry in GUI units. The shadow lies outside the interactive region. */
public record GlassRegion(float x, float y, float width, float height, float radius, Shape shape) {
    public GlassRegion {
        Objects.requireNonNull(shape, "shape");
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(x + width) || !Float.isFinite(y + height)
                || !Float.isFinite(radius) || width <= 0 || height <= 0 || radius < 0) {
            throw new IllegalArgumentException("Invalid Liquid Glass region");
        }
        float maxRadius = Math.min(width, height) * .5f;
        radius = switch (shape) {
            case CAPSULE -> maxRadius;
            case ELLIPSE -> 0;
            case ROUNDED, CONTINUOUS -> Math.min(radius, maxRadius);
        };
    }

    public enum Shape { ROUNDED, CAPSULE, ELLIPSE, CONTINUOUS }

    public static GlassRegion rounded(float x, float y, float width, float height, float radius) {
        return new GlassRegion(x, y, width, height, radius, Shape.ROUNDED);
    }

    public static GlassRegion capsule(float x, float y, float width, float height) {
        return new GlassRegion(x, y, width, height, 0, Shape.CAPSULE);
    }

    public static GlassRegion ellipse(float x, float y, float width, float height) {
        return new GlassRegion(x, y, width, height, 0, Shape.ELLIPSE);
    }

    public static GlassRegion continuous(float x, float y, float width, float height, float radius) {
        return new GlassRegion(x, y, width, height, radius, Shape.CONTINUOUS);
    }

    /** Interpolates one shape family. Rounded rectangles and capsules share a family. */
    public GlassRegion interpolate(GlassRegion target, float fraction) {
        Objects.requireNonNull(target, "target");
        if (!(fraction >= 0 && fraction <= 1)) throw new IllegalArgumentException("Invalid morph fraction");
        Shape family = shape == Shape.CAPSULE ? Shape.ROUNDED : shape;
        Shape targetFamily = target.shape == Shape.CAPSULE ? Shape.ROUNDED : target.shape;
        if (family != targetFamily) throw new IllegalArgumentException("Incompatible Liquid Glass shape families");
        if (fraction == 0) return this;
        if (fraction == 1) return target;
        return new GlassRegion(mix(x, target.x, fraction), mix(y, target.y, fraction),
                mix(width, target.width, fraction), mix(height, target.height, fraction),
                mix(radius, target.radius, fraction), family);
    }

    public boolean contains(double mouseX, double mouseY) {
        double half = halfWidthAt(mouseY);
        return half >= 0 && Math.abs(mouseX - (x + width * .5)) <= half;
    }

    /** Horizontal half extent of the fill at the given GUI coordinate, or -1 outside the height. */
    public double halfWidthAt(double sampleY) {
        double halfHeight = height * .5;
        double halfWidth = width * .5;
        double dy = Math.abs(sampleY - (y + halfHeight));
        if (!(dy <= halfHeight)) return -1;
        if (shape == Shape.ELLIPSE) {
            double t = dy / halfHeight;
            return halfWidth * Math.sqrt(Math.max(0, 1 - t * t));
        }
        if (radius == 0 || dy <= halfHeight - radius) return halfWidth;
        double t = (dy - halfHeight + radius) / radius;
        double arc = shape == Shape.CONTINUOUS
                ? Math.sqrt(Math.sqrt(Math.max(0, 1 - t * t * t * t)))
                : Math.sqrt(Math.max(0, 1 - t * t));
        return halfWidth - radius + radius * arc;
    }
}
