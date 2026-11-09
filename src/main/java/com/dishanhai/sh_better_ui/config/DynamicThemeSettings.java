// Author: dishanhai
package com.dishanhai.sh_better_ui.config;

import com.dishanhai.sh_better_ui.theme.ThemeId;
import com.dishanhai.sh_better_ui.theme.accessibility.ThemePreferences;

/** Persisted local options for screens that opt into the dynamic theme engine. */
public record DynamicThemeSettings(String fixedTheme, int animationPercent, boolean lowPerformance) {
    public static final int MAX_ANIMATION_PERCENT = 800;
    public static final DynamicThemeSettings DEFAULT = new DynamicThemeSettings("", 100, false);

    public DynamicThemeSettings {
        if (!validFixedTheme(fixedTheme)) throw new IllegalArgumentException("Invalid dynamic theme ID: " + fixedTheme);
        if (animationPercent < 0 || animationPercent > MAX_ANIMATION_PERCENT) {
            throw new IllegalArgumentException("Dynamic theme animation percent out of range: " + animationPercent);
        }
    }

    public static boolean validFixedTheme(Object value) {
        if (!(value instanceof String id)) return false;
        if (id.isEmpty()) return true;
        try {
            ThemeId.require(id);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public ThemePreferences applyTo(ThemePreferences previous) {
        boolean follows = fixedTheme.isEmpty();
        return new ThemePreferences(follows ? ThemePreferences.Mode.FOLLOW_ENVIRONMENT : ThemePreferences.Mode.FIXED,
                follows ? null : fixedTheme, previous.highContrast(), previous.reducedMotion(), previous.musicReactive(),
                animationPercent / 100.0, lowPerformance);
    }
}
