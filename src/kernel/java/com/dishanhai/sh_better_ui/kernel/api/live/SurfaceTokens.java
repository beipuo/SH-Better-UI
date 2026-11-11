// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.live;

import com.dishanhai.sh_better_ui.spatial.SpatialStyle;

import java.util.Objects;

/** Replaces the appearance of a live surface. Colors use straight ARGB and sizes use native GUI units. */
public record SurfaceTokens(int surface, int title, int detail, int accent, int progressTrack,
                            int button, int buttonPressed, int focus, int shadow, boolean highContrast, Dimensions dimensions) {
    public static final SurfaceTokens DARK = new SurfaceTokens(0xf0171c26, 0xfff4f7fc, 0xffb9c5d6,
            0xff81d9df, 0xff45536a, 0xff2b3648, 0xff415c72, 0xffbcecff, 0xff080c16, false);
    public static final SurfaceTokens HIGH_CONTRAST = new SurfaceTokens(0xff080a0e, 0xffffffff, 0xfff4f7fc,
            0xffa8f7ff, 0xff545f73, 0xff222935, 0xff3f5468, 0xffffffff, 0xff000000, true);

    public SurfaceTokens { Objects.requireNonNull(dimensions, "dimensions"); }

    /** Changes only the palette and uses the built-in sizing and elevation. */
    public SurfaceTokens(int surface, int title, int detail, int accent, int progressTrack,
                         int button, int buttonPressed, int focus, int shadow, boolean highContrast) {
        this(surface, title, detail, accent, progressTrack, button, buttonPressed, focus, shadow, highContrast, Dimensions.DEFAULT);
    }

    /** Measured bounds, padding, icon and corner radius for a visible presentation. Radius -1 follows the short edge. */
    public record Sizing(int minWidth, int maxWidth, int minHeight, int padding, int iconSize, double radius) {
        public Sizing {
            if (minWidth < 8 || maxWidth < minWidth || maxWidth > 640 || minHeight < 8 || minHeight > 192
                    || padding < 0 || padding > 24 || iconSize < 4 || iconSize > 48
                    || !Double.isFinite(radius) || radius != -1 && (radius < 0 || radius > 64)) {
                throw new IllegalArgumentException("Invalid live surface sizing");
            }
        }
    }

    /** Presentation-specific geometry and one spacing/elevation scheme. */
    public record Dimensions(Sizing minimal, Sizing compact, Sizing split, Sizing expanded, int gap,
                             SpatialStyle.Elevation elevation) {
        public static final Dimensions DEFAULT = new Dimensions(new Sizing(14, 14, 14, 3, 8, -1),
                new Sizing(54, 214, 20, 6, 14, -1), new Sizing(48, 150, 20, 5, 12, -1),
                new Sizing(112, 268, 28, 8, 16, 13), 4, SpatialStyle.Elevation.RAISED);

        public Dimensions {
            Objects.requireNonNull(minimal, "minimal");
            Objects.requireNonNull(compact, "compact");
            Objects.requireNonNull(split, "split");
            Objects.requireNonNull(expanded, "expanded");
            Objects.requireNonNull(elevation, "elevation");
            if (gap < 0 || gap > 24) throw new IllegalArgumentException("Invalid live surface content gap");
        }

        /** Hidden and suspended use the compact fallback sizing. */
        public Sizing forPresentation(SurfacePresentation presentation) {
            return switch (presentation) {
                case MINIMAL -> minimal;
                case SPLIT -> split;
                case EXPANDED, FLOATING -> expanded;
                default -> compact;
            };
        }
    }
}
