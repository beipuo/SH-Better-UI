// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.glass;

import java.util.Objects;

/**
 * Immutable optical parameters. Refraction, thickness and blur radius use GUI units (0 to 32).
 * The remaining parameters are unitless amounts (0 to 1).
 */
public record GlassOptics(float transmission, float refraction, float lensing, float scattering,
                          float edgeHighlight, float thickness, float blurRadius) {
    public GlassOptics {
        check("transmission", transmission, 1);
        check("refraction", refraction, 32);
        check("lensing", lensing, 1);
        check("scattering", scattering, 1);
        check("edgeHighlight", edgeHighlight, 1);
        check("thickness", thickness, 32);
        check("blurRadius", blurRadius, 32);
    }

    public GlassOptics interpolate(GlassOptics target, float fraction) {
        Objects.requireNonNull(target, "target");
        check("fraction", fraction, 1);
        if (fraction == 0) return this;
        if (fraction == 1) return target;
        return new GlassOptics(
                mix(transmission, target.transmission, fraction), mix(refraction, target.refraction, fraction),
                mix(lensing, target.lensing, fraction), mix(scattering, target.scattering, fraction),
                mix(edgeHighlight, target.edgeHighlight, fraction), mix(thickness, target.thickness, fraction),
                mix(blurRadius, target.blurRadius, fraction));
    }

    static float mix(float from, float to, float fraction) {
        return (float) (from + ((double) to - from) * fraction);
    }

    private static void check(String name, float value, float max) {
        if (!Float.isFinite(value) || value < 0 || value > max) {
            throw new IllegalArgumentException(name + " must be finite and in [0, " + max + "]");
        }
    }
}
