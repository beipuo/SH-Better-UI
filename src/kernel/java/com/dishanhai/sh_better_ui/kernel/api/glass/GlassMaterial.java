// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.glass;

import java.util.Objects;

/** A reusable optical preset. Draw text using the chosen foreground after drawing the material. */
public record GlassMaterial(GlassVariant variant, GlassOptics optics, GlassQuality quality, Foreground foreground) {
    private static final GlassMaterial REGULAR = new GlassMaterial(GlassVariant.REGULAR,
            new GlassOptics(.85f, 3.3f, .30f, .66f, .62f, 3.2f, 3.6f), GlassQuality.BALANCED, Foreground.LIGHT);
    private static final GlassMaterial CLEAR = new GlassMaterial(GlassVariant.CLEAR,
            new GlassOptics(.97f, 4.6f, .42f, .12f, .78f, 4.0f, 1.2f), GlassQuality.BALANCED, Foreground.LIGHT);

    public GlassMaterial {
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(optics, "optics");
        Objects.requireNonNull(quality, "quality");
        Objects.requireNonNull(foreground, "foreground");
    }

    public static GlassMaterial preset(GlassVariant variant) {
        return switch (variant) {
            case REGULAR -> REGULAR;
            case CLEAR -> CLEAR;
        };
    }

    public enum Foreground {
        LIGHT(0xffffffff, 0xff20252c),
        DARK(0xff000000, 0xffe4e8ee);

        private final int argb;
        private final int fallbackArgb;

        Foreground(int argb, int fallbackArgb) {
            this.argb = argb;
            this.fallbackArgb = fallbackArgb;
        }

        public int argb() { return argb; }
        public int fallbackArgb() { return fallbackArgb; }
    }
}
