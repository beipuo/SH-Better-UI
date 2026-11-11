// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.accessibility;

import com.dishanhai.sh_better_ui.theme.token.ColorValue;

/** WCAG relative luminance and contrast for sRGB. Supply the actual opaque backdrop for translucent layers. */
public final class ContrastPolicy {
    public static final double TEXT_RATIO = 4.5;
    public static final double HIGH_CONTRAST_RATIO = 7;

    private ContrastPolicy() {}

    /** Composites an ARGB layer over an opaque ARGB backdrop. */
    public static int composite(int foreground, int backdrop) {
        if ((backdrop >>> 24) != 255) throw new IllegalArgumentException("Contrast requires an opaque backdrop");
        int alpha = foreground >>> 24;
        if (alpha == 255) return foreground;
        if (alpha == 0) return backdrop;
        int color = 0xff000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int front = (foreground >>> shift) & 255;
            int back = (backdrop >>> shift) & 255;
            color |= ((front * alpha + back * (255 - alpha) + 127) / 255) << shift;
        }
        return color;
    }

    /** Computes foreground contrast after compositing both layers over the supplied backdrop. */
    public static double ratio(int foreground, int background, int backdrop) {
        int base = composite(background, backdrop);
        double a = luminance(composite(foreground, base));
        double b = luminance(base);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    /** Keeps a readable foreground or selects the stronger of opaque black and white. */
    public static int readable(int foreground, int background, int backdrop, double minimum) {
        if (!Double.isFinite(minimum) || minimum < 1 || minimum > 21) {
            throw new IllegalArgumentException("Contrast ratio must be in [1, 21]");
        }
        if (ratio(foreground, background, backdrop) >= minimum) return foreground;
        return ratio(0xff000000, background, backdrop) >= ratio(0xffffffff, background, backdrop)
                ? 0xff000000 : 0xffffffff;
    }

    private static double luminance(int color) {
        return 0.2126 * ColorValue.linear((color >>> 16) & 255)
                + 0.7152 * ColorValue.linear((color >>> 8) & 255) + 0.0722 * ColorValue.linear(color & 255);
    }
}
