// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.palette;

import com.dishanhai.sh_better_ui.theme.Theme;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;

import java.util.Objects;

import static com.dishanhai.sh_better_ui.theme.token.ThemeTokens.*;

/** Immutable coordinated palette ready to publish to a client theme. */
public record ThemePalette(boolean light, ColorValue primary, ColorValue onPrimary, ColorValue secondary,
                           ColorValue accent, ColorValue background, ColorValue surface, ColorValue text) {
    public ThemePalette {
        Objects.requireNonNull(primary);
        Objects.requireNonNull(onPrimary);
        Objects.requireNonNull(secondary);
        Objects.requireNonNull(accent);
        Objects.requireNonNull(background);
        Objects.requireNonNull(surface);
        Objects.requireNonNull(text);
    }

    /** Adds the palette's roles to a theme definition. Other roles still inherit from its parent. */
    public Theme.Builder apply(Theme.Builder theme) {
        return theme.token(LIGHT, light).token(PRIMARY, primary).token(ON_PRIMARY, onPrimary)
                .token(SECONDARY, secondary).token(ACCENT, accent).token(BACKGROUND, background)
                .token(SURFACE, surface).token(TEXT, text);
    }
}
