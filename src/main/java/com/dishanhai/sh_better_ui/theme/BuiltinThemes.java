// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.token.ColorValue;

import java.util.Set;

import static com.dishanhai.sh_better_ui.theme.token.ThemeTokens.*;

/** Built-in, resource-independent themes used even when a pack fails to load. */
public final class BuiltinThemes {
    public static final String DARK = "sh_better_ui:dark";
    public static final String LIGHT_ID = "sh_better_ui:light";
    public static final String NETHER = "sh_better_ui:nether";
    public static final String END = "sh_better_ui:end";
    static final Set<String> IDS = Set.of(DARK, LIGHT_ID, NETHER, END);

    private BuiltinThemes() {}

    static ThemeRegistry registry() {
        return new ThemeRegistry()
                .with(theme(DARK).build())
                .with(theme(LIGHT_ID).parent(DARK).token(LIGHT, true)
                        .token(BACKGROUND, color(0xffe8eef5)).token(SURFACE, color(0xfff8fafc))
                        .token(SURFACE_SECONDARY, color(0xffe0e8f3)).token(SURFACE_ELEVATED, color(0xffffffff))
                        .token(SURFACE_OVERLAY, color(0xfff8fafc)).token(TEXT, color(0xff172334))
                        .token(TEXT_SECONDARY, color(0xff38495e)).token(TEXT_DISABLED, color(0xff52637a))
                        .token(BORDER, color(0xff64748b)).token(BORDER_FOCUSED, color(0xff1755aa))
                        .token(PRIMARY, color(0xff1755aa)).token(ON_PRIMARY, color(0xffffffff))
                        .token(ACCENT, color(0xff1755aa)).token(HOVER, color(0x181755aa))
                        .token(PRESSED, color(0x321755aa)).build())
                .with(theme(NETHER).parent(DARK).token(BACKGROUND, color(0xff211419))
                        .token(SURFACE, color(0xff38252c)).token(SURFACE_ELEVATED, color(0xff483036))
                        .token(PRIMARY, color(0xffff9f87)).token(ACCENT, color(0xfffa846e)).build())
                .with(theme(END).parent(DARK).token(BACKGROUND, color(0xff171329))
                        .token(SURFACE, color(0xff29243c)).token(SURFACE_ELEVATED, color(0xff3b3154))
                        .token(PRIMARY, color(0xffd7baf8)).token(ACCENT, color(0xffc09df0)).build());
    }

    private static Theme.Builder theme(String id) {
        return Theme.builder(id).nameKey("sh_better_ui.theme.dynamic." + id.substring(id.indexOf(':') + 1))
                .source("built-in");
    }

    private static ColorValue color(int value) { return ColorValue.argb(value); }
}
