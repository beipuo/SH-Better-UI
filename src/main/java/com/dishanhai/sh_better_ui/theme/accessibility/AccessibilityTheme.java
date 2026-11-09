// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.accessibility;

import com.dishanhai.sh_better_ui.theme.ThemeSnapshot;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;
import com.dishanhai.sh_better_ui.theme.transition.VisualProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.dishanhai.sh_better_ui.theme.token.ThemeTokens.*;

/** Resolves mandatory accessibility overrides after the widget style cascade. */
public final class AccessibilityTheme {
    private static final ColorValue BLACK = ColorValue.argb(0xff000000);
    private static final ColorValue WHITE = ColorValue.argb(0xffffffff);
    private static final List<ThemeToken<ColorValue>> SURFACES = List.of(BACKGROUND, SURFACE, SURFACE_SECONDARY,
            SURFACE_ELEVATED, SURFACE_OVERLAY);
    private static final List<ThemeToken<ColorValue>> TEXTS = List.of(TEXT, TEXT_SECONDARY, TEXT_DISABLED);

    private AccessibilityTheme() {}

    /** Returns only properties forced by the current accessibility settings. */
    public static Map<ThemeToken<?>, Object> forced(ThemeSnapshot theme, ThemePreferences preferences) {
        if (!preferences.highContrast() && !preferences.lowPerformance() && !preferences.reducedMotion()) return Map.of();
        var values = new LinkedHashMap<ThemeToken<?>, Object>();
        if (preferences.highContrast()) {
            boolean light = theme.resolve(LIGHT);
            ColorValue surface = light ? WHITE : BLACK;
            ColorValue text = light ? BLACK : WHITE;
            ColorValue primary = ColorValue.argb(light ? 0xff123974 : 0xffb6daff);
            for (var token : SURFACES) values.put(token, surface);
            for (var token : TEXTS) values.put(token, text);
            values.put(PRIMARY, primary);
            values.put(ON_PRIMARY, surface);
            values.put(ACCENT, primary);
            values.put(BORDER, text);
            values.put(BORDER_FOCUSED, primary);
            values.put(BORDER_WIDTH, 2f);
            values.put(OPACITY, 1f);
        }
        if (preferences.highContrast() || preferences.lowPerformance()) {
            values.put(BLUR, 0f);
            values.put(BACKDROP, false);
            values.put(SHADOW, new VisualProperties.Shadow(0, 0, 0, ColorValue.argb(0)));
        }
        if (preferences.highContrast() || preferences.lowPerformance() || preferences.reducedMotion()) {
            values.put(GLOW, 0f);
            values.put(PULSE, 0f);
        }
        return Map.copyOf(values);
    }
}
