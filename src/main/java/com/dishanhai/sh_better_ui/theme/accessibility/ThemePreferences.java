// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.accessibility;

import com.dishanhai.sh_better_ui.theme.ThemeId;

import java.util.Objects;

/** Local user choices. These values take precedence over environmental theme rules. */
public record ThemePreferences(Mode mode, String fixedThemeId, boolean highContrast, boolean reducedMotion,
                               boolean musicReactive, double animationSpeed, boolean lowPerformance) {
    public static final ThemePreferences DEFAULT = new ThemePreferences(
            Mode.FOLLOW_ENVIRONMENT, null, false, false, true, 1, false);

    public enum Mode { FOLLOW_ENVIRONMENT, FIXED, DARK, LIGHT }

    public ThemePreferences {
        Objects.requireNonNull(mode);
        if (mode == Mode.FIXED) ThemeId.require(fixedThemeId);
        else if (fixedThemeId != null) throw new IllegalArgumentException("Fixed theme ID requires FIXED mode");
        if (!Double.isFinite(animationSpeed) || animationSpeed < 0 || animationSpeed > 8) {
            throw new IllegalArgumentException("Animation speed must be in [0, 8]");
        }
    }

    public ThemePreferences withMode(Mode mode) {
        return new ThemePreferences(mode, mode == Mode.FIXED ? fixedThemeId : null, highContrast, reducedMotion,
                musicReactive, animationSpeed, lowPerformance);
    }

    public ThemePreferences withFixedTheme(String id) {
        return new ThemePreferences(Mode.FIXED, id, highContrast, reducedMotion, musicReactive, animationSpeed, lowPerformance);
    }

    public ThemePreferences withReducedMotion(boolean enabled) {
        return new ThemePreferences(mode, fixedThemeId, highContrast, enabled, musicReactive, animationSpeed, lowPerformance);
    }

    public ThemePreferences withHighContrast(boolean enabled) {
        return new ThemePreferences(mode, fixedThemeId, enabled, reducedMotion, musicReactive, animationSpeed, lowPerformance);
    }

    /** Reduced motion, low performance mode, or a zero speed disable theme transitions. */
    public boolean animate() { return !reducedMotion && !lowPerformance && animationSpeed > 0; }
}
