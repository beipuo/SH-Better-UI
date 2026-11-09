package com.dishanhai.sh_better_ui.client.gui.scaled;

import java.util.List;
import java.util.Objects;

/** 山海：反重力粒子的 Oklab 色板；循环色环与径向色带共用同一插值。 */
final class AntigravityPalette {
    static final int SIZE = 256;

    private AntigravityPalette() {}

    static int[] build(List<Integer> argbColors, boolean lightMode, boolean cyclic) {
        Objects.requireNonNull(argbColors);
        if (argbColors.isEmpty()) throw new IllegalArgumentException("Empty antigravity palette");
        var colors = List.copyOf(argbColors);
        double[][] stops = new double[colors.size()][];
        for (int i = 0; i < colors.size(); i++) stops[i] = toOklab(colors.get(i), lightMode);
        int[] result = new int[SIZE];
        for (int x = 0; x < SIZE; x++) {
            double position = cyclic ? (double) x / SIZE * stops.length
                    : (double) x / (SIZE - 1) * Math.max(0, stops.length - 1);
            int from = Math.min((int) position, stops.length - 1);
            int to = cyclic ? (from + 1) % stops.length : Math.min(from + 1, stops.length - 1);
            double k = position - from;
            double eased = k * k * (3 - 2 * k);
            double[] start = stops[from];
            double[] end = stops[to];
            double chroma = mix(Math.hypot(start[1], start[2]), Math.hypot(end[1], end[2]), eased);
            double a = mix(start[1], end[1], eased);
            double b = mix(start[2], end[2], eased);
            double length = Math.hypot(a, b);
            double scale = length > 1e-4 ? chroma / length : 0;
            int alpha = (int) Math.round(mix(colors.get(from) >>> 24, colors.get(to) >>> 24, eased));
            result[x] = fromOklab(mix(start[0], end[0], eased), a * scale, b * scale, alpha);
            // 未调整明度的色标保持原始 ARGB，避免颜色空间往返舍入影响端点。
            if (!lightMode && (eased == 0 || from == to)) result[x] = colors.get(from);
        }
        return result;
    }

    /** 与线性纹理采样一致，循环时最后一项与第一项相接。 */
    static int sample(int[] lut, float u, boolean cyclic) {
        Objects.requireNonNull(lut);
        if (lut.length != SIZE || !Float.isFinite(u)) {
            throw new IllegalArgumentException("Invalid antigravity palette sample");
        }
        double value = cyclic ? u - Math.floor(u) : clamp(u);
        double position = value * (cyclic ? SIZE : SIZE - 1);
        int from = Math.min((int) position, SIZE - 1);
        int to = cyclic ? (from + 1) % SIZE : Math.min(from + 1, SIZE - 1);
        double amount = position - from;
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int channel = (int) Math.round(mix(lut[from] >>> shift & 255, lut[to] >>> shift & 255, amount));
            result |= channel << shift;
        }
        return result;
    }

    private static double[] toOklab(int argb, boolean lightMode) {
        double r = toLinear((argb >> 16 & 255) / 255.0);
        double g = toLinear((argb >> 8 & 255) / 255.0);
        double b = toLinear((argb & 255) / 255.0);
        double l = Math.cbrt(.4122214708 * r + .5363325363 * g + .0514459929 * b);
        double m = Math.cbrt(.2119034982 * r + .6806995451 * g + .1073969566 * b);
        double s = Math.cbrt(.0883024619 * r + .2817188376 * g + .6299787005 * b);
        double light = .2104542553 * l + .7936177850 * m - .0040720468 * s;
        double a = 1.9779984951 * l - 2.4285922050 * m + .4505937099 * s;
        double blue = .0259040371 * l + .7827717662 * m - .8086757660 * s;
        if (lightMode) {
            double hue = Math.toDegrees(Math.atan2(blue, a));
            double warmth = clamp(1 - Math.abs(hue - 85) / 40);
            double ceiling = .62 + .14 * warmth * warmth * (3 - 2 * warmth);
            if (light > ceiling) {
                double boost = 1 + (light - ceiling) * 1.6;
                light = ceiling;
                a *= boost;
                blue *= boost;
            }
        }
        return new double[] {light, a, blue};
    }

    private static int fromOklab(double light, double a, double b, int alpha) {
        double l = cube(light + .3963377774 * a + .2158037573 * b);
        double m = cube(light - .1055613458 * a - .0638541728 * b);
        double s = cube(light - .0894841775 * a - 1.2914855480 * b);
        int r = channel(4.0767416621 * l - 3.3077115913 * m + .2309699292 * s);
        int g = channel(-1.2684380046 * l + 2.6097574011 * m - .3413193965 * s);
        int blue = channel(-.0041960863 * l - .7034186147 * m + 1.7076147010 * s);
        return alpha << 24 | r << 16 | g << 8 | blue;
    }

    private static double toLinear(double channel) {
        return channel <= .04045 ? channel / 12.92 : Math.pow((channel + .055) / 1.055, 2.4);
    }

    private static int channel(double linear) {
        double srgb = linear <= .0031308 ? linear * 12.92
                : 1.055 * Math.pow(Math.max(linear, 0), 1 / 2.4) - .055;
        return (int) Math.round(clamp(srgb) * 255);
    }

    private static double cube(double value) { return value * value * value; }
    private static double clamp(double value) { return UiMath.clamp(value, 0, 1); }
    private static double mix(double from, double to, double amount) { return from + (to - from) * amount; }
}
