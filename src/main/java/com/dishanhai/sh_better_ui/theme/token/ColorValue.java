// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.token;

/** Immutable sRGB value in packed ARGB format. */
public record ColorValue(int argb) {
    private static final double[] LINEAR = linearChannels();
    public static ColorValue argb(int argb) { return new ColorValue(argb); }

    public static ColorValue rgb(int r, int g, int b) {
        if ((r | g | b) < 0 || r > 255 || g > 255 || b > 255) {
            throw new IllegalArgumentException("RGB channels must be in [0,255]");
        }
        return new ColorValue(0xff000000 | r << 16 | g << 8 | b);
    }

    /** Parses #RRGGBB (opaque) or #AARRGGBB. */
    public static ColorValue parse(String text) {
        if (!text.matches("#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})")) {
            throw new IllegalArgumentException("Expected #RRGGBB or #AARRGGBB: " + text);
        }
        long parsed = Long.parseLong(text.substring(1), 16);
        return new ColorValue((int) parsed | (text.length() == 7 ? 0xff000000 : 0));
    }

    /** OKLab interpolation with alpha-weighted coordinates and exact packed endpoints. */
    public static ColorValue interpolateOklab(ColorValue from, ColorValue to, double progress) {
        if (!Double.isFinite(progress)) throw new IllegalArgumentException("Non-finite color progress");
        if (progress <= 0 || from.equals(to)) return from;
        if (progress >= 1) return to;
        double weightFrom = (from.argb >>> 24) * (1 - progress);
        double weightTo = (to.argb >>> 24) * progress;
        double alpha = weightFrom + weightTo;
        if (alpha < 0.5) return new ColorValue(0);
        Lab start = lab(from.argb);
        Lab end = lab(to.argb);
        double t = weightTo / alpha;
        double l = lerp(start.l, end.l, t);
        double a = lerp(start.a, end.a, t);
        double b = lerp(start.b, end.b, t);
        double ll = cube(l + 0.3963377774 * a + 0.2158037573 * b);
        double mm = cube(l - 0.1055613458 * a - 0.0638541728 * b);
        double ss = cube(l - 0.0894841775 * a - 1.2914855480 * b);
        int red = channel(4.0767416621 * ll - 3.3077115913 * mm + 0.2309699292 * ss);
        int green = channel(-1.2684380046 * ll + 2.6097574011 * mm - 0.3413193965 * ss);
        int blue = channel(-0.0041960863 * ll - 0.7034186147 * mm + 1.7076147010 * ss);
        return new ColorValue((int) Math.round(alpha) << 24 | red << 16 | green << 8 | blue);
    }

    private static Lab lab(int color) {
        double r = linear((color >>> 16) & 255), g = linear((color >>> 8) & 255), b = linear(color & 255);
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new Lab(0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s);
    }

    /** Converts an eight-bit sRGB channel to linear light. */
    public static double linear(int channel) {
        return LINEAR[channel];
    }

    private static double[] linearChannels() {
        double[] result = new double[256];
        for (int channel = 0; channel < result.length; channel++) {
            double x = channel / 255.0;
            result[channel] = x <= 0.04045 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
        }
        return result;
    }

    private static int channel(double linear) {
        double x = Math.max(0, Math.min(1, linear));
        return (int) Math.round(255 * (x <= 0.0031308 ? 12.92 * x : 1.055 * Math.pow(x, 1 / 2.4) - 0.055));
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static double cube(double x) { return x * x * x; }

    private record Lab(double l, double a, double b) {}

    @Override
    public String toString() { return "#%08X".formatted(argb); }
}
