package com.dishanhai.sh_better_ui.config;

import java.util.Objects;

/** 山海：全库客户端主题。自定义颜色采用六位 RGB，空字符串继承基底。 */
public record ThemeSettings(Mode mode, Base customBase, String background, String surface,
                            String border, String text, String accent, ThemeOverrides effects) {
    public enum Mode { DARK, LIGHT, CUSTOM }
    public enum Base { DARK, LIGHT }

    public static final ThemeSettings DEFAULT = new ThemeSettings(Mode.DARK, Base.DARK, "", "", "", "", "");

    public ThemeSettings {
        Objects.requireNonNull(effects);
        Objects.requireNonNull(mode);
        Objects.requireNonNull(customBase);
        background = normalize(background);
        surface = normalize(surface);
        border = normalize(border);
        text = normalize(text);
        accent = normalize(accent);
    }

    public ThemeSettings(Mode mode, Base customBase, String background, String surface,
                         String border, String text, String accent) {
        this(mode, customBase, background, surface, border, text, accent, ThemeOverrides.EMPTY);
    }

    public ThemeSettings withEffects(ThemeOverrides next) {
        return new ThemeSettings(mode, customBase, background, surface, border, text, accent, next);
    }

    public ThemeSettings withMode(Mode next) {
        return new ThemeSettings(next, customBase, background, surface, border, text, accent, effects);
    }

    public boolean light() {
        return mode == Mode.LIGHT || mode == Mode.CUSTOM && customBase == Base.LIGHT;
    }

    public static boolean validColor(Object value) {
        return value instanceof String color && (color.isEmpty() || color.matches("#?[0-9a-fA-F]{6}"));
    }

    private static String normalize(String value) {
        if (!validColor(value)) throw new IllegalArgumentException("Expected an empty value or a six-digit RGB color");
        if (value.isEmpty()) return value;
        return "#" + (value.startsWith("#") ? value.substring(1) : value).toUpperCase(java.util.Locale.ROOT);
    }
}
