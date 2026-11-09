package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.ThemeSettings;

/** 山海：解析共用材质色，并为设置界面提供作用域内的草稿预览。 */
public record UiTheme(boolean light, int background, int surface, int border, int text, int accent,
                      int overrides) {
    private static final int BACKGROUND = 1, SURFACE = 2, BORDER = 4, TEXT = 8, ACCENT = 16;
    private static final ThreadLocal<UiTheme> PREVIEW = new ThreadLocal<>();
    private static final UiTheme DARK = new UiTheme(false, DarkModeTheme.BACKGROUND, DarkModeTheme.SURFACE,
            DarkModeTheme.BORDER, DarkModeTheme.TEXT, DarkModeTheme.ACCENT, 0);
    private static final UiTheme LIGHT = new UiTheme(true, 0xFFE8EEF5, 0xFFF8FAFC,
            0xFF64748B, 0xFF172334, 0xFF1755AA, 0);
    private static ThemeSettings cachedSettings;
    private static UiTheme cachedTheme;

    public static UiTheme current() {
        UiTheme preview = PREVIEW.get();
        return preview == null ? configured() : preview;
    }

    private static UiTheme configured() {
        var settings = ClientUiSettings.current().theme();
        if (settings.mode() != ThemeSettings.Mode.CUSTOM) return settings.light() ? LIGHT : DARK;
        // 绘制只在客户端渲染线程进行。配置线程通过不可变快照发布变更。
        return resolveCustom(settings);
    }

    private static synchronized UiTheme resolveCustom(ThemeSettings settings) {
        if (!settings.equals(cachedSettings)) {
            cachedTheme = resolve(settings);
            cachedSettings = settings;
        }
        return cachedTheme;
    }

    public static UiTheme resolve(ThemeSettings settings) {
        UiTheme base = settings.light() ? LIGHT : DARK;
        if (settings.mode() != ThemeSettings.Mode.CUSTOM) return base;
        int mask = (settings.background().isEmpty() ? 0 : BACKGROUND)
                | (settings.surface().isEmpty() ? 0 : SURFACE)
                | (settings.border().isEmpty() ? 0 : BORDER)
                | (settings.text().isEmpty() ? 0 : TEXT)
                | (settings.accent().isEmpty() ? 0 : ACCENT);
        return new UiTheme(base.light, rgb(settings.background(), base.background), rgb(settings.surface(), base.surface),
                rgb(settings.border(), base.border), rgb(settings.text(), base.text), rgb(settings.accent(), base.accent),
                mask);
    }

    public static void preview(UiTheme theme, Runnable render) {
        UiTheme previous = PREVIEW.get();
        PREVIEW.set(theme);
        try {
            render.run();
        } finally {
            if (previous == null) PREVIEW.remove();
            else PREVIEW.set(previous);
        }
    }

    public static GraphicIcon icon(ThemeSettings.Mode mode) {
        return switch (mode) {
            case DARK -> GraphicIcon.DARK_MODE;
            case LIGHT -> GraphicIcon.LIGHT_MODE;
            case CUSTOM -> GraphicIcon.CUSTOM_THEME;
        };
    }

    public int background(int dark) { return tint(dark, background, BACKGROUND); }
    public int surface(int dark) { return tint(dark, surface, SURFACE); }
    public int border(int dark) { return tint(dark, border, BORDER); }
    public int text(int dark) { return tint(dark, text, TEXT); }
    public int accent(int dark) { return tint(dark, accent, ACCENT); }

    public int secondaryText(int dark, float muted) {
        return light || (overrides & (TEXT | SURFACE)) != 0
                ? GuiMotion.lerpColor(text, surface, muted) : dark;
    }

    public int controlFill(int dark, float emphasis) {
        if (!light && (overrides & (SURFACE | ACCENT)) == 0) return dark;
        int tint = GuiMotion.lerpColor(surface, accent, emphasis);
        // 浅色文字与底板需要稳定的对比度，同时保留玻璃采样。
        int alpha = light ? Math.max(dark >>> 24, 0xCC) : dark >>> 24;
        return alpha << 24 | (tint & 0xFFFFFF);
    }

    private int tint(int dark, int color, int role) {
        if (!light && (overrides & role) == 0) return dark;
        return (dark & 0xFF000000) | (color & 0xFFFFFF);
    }

    private static int rgb(String value, int fallback) {
        return value.isEmpty() ? fallback : 0xFF000000 | Integer.parseInt(value.substring(1), 16);
    }
}
