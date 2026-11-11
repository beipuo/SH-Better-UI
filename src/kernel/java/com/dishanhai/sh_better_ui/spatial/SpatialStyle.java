// Author: dishanhai
package com.dishanhai.sh_better_ui.spatial;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;

import static com.dishanhai.sh_better_ui.spatial.SpatialGeometry.range;

/** Validated values shared by themes, scene nodes and renderer. Colors are straight ARGB. */
public final class SpatialStyle {
    private SpatialStyle() {}

    private static double lerp(double from, double to, double progress) {
        return from + (to - from) * progress;
    }

    private static int color(int from, int to, double progress) {
        if (progress == 0) return from;
        if (progress == 1) return to;
        double a = (from >>> 24) * (1 - progress), b = (to >>> 24) * progress;
        double sum = a + b;
        if (sum < .5) return 0;
        int result = (int) Math.round(sum) << 24;
        for (int shift = 0; shift <= 16; shift += 8) {
            int channel = (int) Math.round(((from >> shift & 255) * a + (to >> shift & 255) * b) / sum);
            result |= channel << shift;
        }
        return result;
    }

    public enum Elevation {
        SURFACE(0), RAISED(2), FLOATING(4), OVERLAY(8), MODAL(16), DRAGGING(24);

        private final double height;
        Elevation(double height) { this.height = height; }
        public double height() { return height; }
    }

    public enum ShadowType { DROP, INNER, AMBIENT, CONTACT, DIRECTIONAL, GLOW, SURFACE }

    public enum ShadowClip { NODE, PARENT, WINDOW, OVERFLOW }

    public record Shadow(ShadowType type, double x, double y, double blur, double spread,
                         double opacity, int color, boolean soft) {
        public Shadow {
            Objects.requireNonNull(type);
            if (type == ShadowType.SURFACE) throw new IllegalArgumentException("SURFACE is a render pass");
            range(x, -512, 512, "shadow x");
            range(y, -512, 512, "shadow y");
            range(blur, 0, 128, "shadow blur");
            range(spread, -128, 128, "shadow spread");
            range(opacity, 0, 1, "shadow opacity");
        }

        public Shadow interpolate(Shadow target, double progress) {
            range(progress, 0, 1, "interpolation");
            if (type != target.type) throw new IllegalArgumentException("Shadow types must match");
            return new Shadow(type, lerp(x, target.x, progress), lerp(y, target.y, progress),
                    lerp(blur, target.blur, progress), lerp(spread, target.spread, progress),
                    lerp(opacity, target.opacity, progress), SpatialStyle.color(color, target.color, progress),
                    progress < .5 ? soft : target.soft);
        }
    }

    public record Material(int color, double opacity, double roughness, double highlight,
                           double border, double depth, double blur) {
        public static final Material FLAT = new Material(0xff283244, 1, 1, 0, 0, 0, 0);
        public static final Material SOFT_RAISED = new Material(0xff313c50, 1, .65, .26, .28, .45, 0);
        public static final Material SOFT_INSET = new Material(0xff191f2c, 1, .8, .08, .1, -.6, 0);
        public static final Material GLASS = new Material(0x987c9cbc, 1, .35, .28, .6, .32, 5);
        public static final Material METALLIC = new Material(0xff616c7f, 1, .16, .66, .4, .7, 0);
        public static final Material HOLOGRAPHIC = new Material(0xcb5765ae, 1, .22, .7, .6, .5, 0);

        public Material {
            range(opacity, 0, 1, "material opacity");
            range(roughness, 0, 1, "roughness");
            range(highlight, 0, 1, "highlight");
            range(border, 0, 1, "border");
            range(depth, -1, 1, "surface depth");
            range(blur, 0, 32, "backdrop blur");
        }
    }

    @FunctionalInterface
    public interface ShadowPreset {
        List<Shadow> resolve(double height);
    }

    public record Theme(Map<Elevation, Double> heights, DoubleUnaryOperator curve,
                        ShadowPreset shadows, Material material) {
        public static final Theme DEFAULT = new Theme(Map.of(), value -> value, Theme::elevationShadows, Material.FLAT);

        public Theme {
            heights = Map.copyOf(heights);
            heights.values().forEach(height -> range(height, 0, 64, "theme height"));
            Objects.requireNonNull(curve);
            Objects.requireNonNull(shadows);
            Objects.requireNonNull(material);
        }

        public double elevation(Elevation token) { return heights.getOrDefault(token, token.height()); }

        public List<Shadow> shadows(double height) {
            var result = List.copyOf(shadows.resolve(range(curve.applyAsDouble(height), 0, 64, "shadow curve")));
            if (result.size() > 8) throw new IllegalArgumentException("Too many themed shadow layers");
            return result;
        }

        /** Default elevation curve using the current theme's surface and shadow colors. */
        public static Theme palette(int surface, int shadow) {
            return new Theme(Map.of(), height -> height, height -> elevationShadows(height, shadow),
                    new Material(surface, 1, 1, 0, 0, 0, 0));
        }

        private static List<Shadow> elevationShadows(double height) {
            return elevationShadows(height, 0xff080c16);
        }

        private static List<Shadow> elevationShadows(double height, int color) {
            if (height == 0) return List.of();
            return List.of(
                    new Shadow(ShadowType.AMBIENT, 0, 0, 2 + height * .65, 0, .24, color, true),
                    new Shadow(ShadowType.DIRECTIONAL, 0, height * .55, 1 + height * .5, 0, .29, color, true),
                    new Shadow(ShadowType.CONTACT, 0, .75, 1.5, 0, .25 / (1 + height * .18), color, false));
        }
    }

    public enum Quality {
        LOW(1, 1), MEDIUM(3, 4), HIGH(6, 8), ULTRA(8, 12), AUTO(6, 8);

        private final int shadowLimit;
        private final int lightLimit;
        Quality(int shadowLimit, int lightLimit) {
            this.shadowLimit = shadowLimit;
            this.lightLimit = lightLimit;
        }

        public int shadowLimit() { return shadowLimit; }
        public int lightLimit() { return lightLimit; }
    }

    public record Settings(boolean enabled, boolean shadows, boolean lighting, boolean blur, Quality quality,
                           boolean profiling) {
        public static final Settings DEFAULT = new Settings(true, true, true, true, Quality.AUTO);
        public Settings { Objects.requireNonNull(quality); }

        public Settings(boolean enabled, boolean shadows, boolean lighting, boolean blur, Quality quality) {
            this(enabled, shadows, lighting, blur, quality, false);
        }
    }

    public enum LightType { AMBIENT, DIRECTIONAL, POINT, SPOT }

    /** Direction in screen radians, cone half-angle in radians. Local lights use GUI units. */
    public record Light(LightType type, double x, double y, double height, double intensity,
                        double radius, int color, double direction, double cone) {
        public Light {
            Objects.requireNonNull(type);
            range(x, -32768, 32768, "light x");
            range(y, -32768, 32768, "light y");
            range(height, 0, 4096, "light height");
            range(intensity, 0, 4, "light intensity");
            range(radius, .001, 4096, "light radius");
            range(direction, -1e6, 1e6, "light direction");
            range(cone, .001, Math.PI, "light cone");
        }

        public Light interpolate(Light target, double progress) {
            range(progress, 0, 1, "interpolation");
            if (type != target.type) throw new IllegalArgumentException("Light types must match");
            return new Light(type, lerp(x, target.x, progress), lerp(y, target.y, progress),
                    lerp(height, target.height, progress), lerp(intensity, target.intensity, progress),
                    lerp(radius, target.radius, progress), SpatialStyle.color(color, target.color, progress),
                    lerp(direction, target.direction, progress), lerp(cone, target.cone, progress));
        }
    }
}
