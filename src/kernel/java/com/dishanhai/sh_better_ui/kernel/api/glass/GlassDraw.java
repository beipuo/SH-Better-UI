// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.glass;

import java.util.Objects;

import static com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics.mix;

/** A surface and optional local highlight. Coordinates run from -1 to 1, with Y increasing downwards. */
public record GlassDraw(GlassRegion region, GlassMaterial material, float lightX, float lightY, float lightStrength) {
    public GlassDraw {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(material, "material");
        if (!Float.isFinite(lightX) || Math.abs(lightX) > 1 || !Float.isFinite(lightY) || Math.abs(lightY) > 1
                || !Float.isFinite(lightStrength) || lightStrength < 0 || lightStrength > 1) {
            throw new IllegalArgumentException("Invalid Liquid Glass highlight");
        }
    }

    public GlassDraw(GlassRegion region, GlassMaterial material) {
        this(region, material, 0, 0, 0);
    }

    /** Uses one progress value for the visible region, optics and local highlight. */
    public GlassDraw interpolate(GlassDraw target, float fraction) {
        Objects.requireNonNull(target, "target");
        if (material.variant() != target.material.variant() || material.quality() != target.material.quality()
                || material.foreground() != target.material.foreground()) {
            throw new IllegalArgumentException("Liquid Glass interpolation requires the same material semantics");
        }
        var geometry = region.interpolate(target.region, fraction);
        if (fraction == 0) return this;
        if (fraction == 1) return target;
        var optics = material.optics().interpolate(target.material.optics(), fraction);
        return new GlassDraw(geometry, new GlassMaterial(material.variant(), optics, material.quality(), material.foreground()),
                mix(lightX, target.lightX, fraction), mix(lightY, target.lightY, fraction),
                mix(lightStrength, target.lightStrength, fraction));
    }
}
