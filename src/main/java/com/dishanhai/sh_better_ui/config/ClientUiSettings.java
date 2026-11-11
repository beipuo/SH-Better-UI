package com.dishanhai.sh_better_ui.config;

import com.dishanhai.sh_better_ui.spatial.SpatialStyle.Settings;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** 山海：两个加载器共用的客户端界面设置快照。 */
public record ClientUiSettings(GlassBackdropSettings backdrop, boolean highContrast, boolean reducedMotion,
                               boolean widgetBackdropBlur, ControlFeedbackSettings feedback, TabScrollSettings tabs,
                               ThemeSettings theme, DynamicThemeSettings dynamicTheme, Settings spatial) {
    public static final ClientUiSettings DEFAULT = new ClientUiSettings(GlassBackdropSettings.DEFAULT, false, false, true,
            ControlFeedbackSettings.DEFAULT, TabScrollSettings.DEFAULT);
    private static final AtomicReference<ClientUiSettings> CURRENT = new AtomicReference<>(DEFAULT);

    public ClientUiSettings {
        Objects.requireNonNull(backdrop);
        Objects.requireNonNull(feedback);
        Objects.requireNonNull(tabs);
        Objects.requireNonNull(theme);
        Objects.requireNonNull(dynamicTheme);
        Objects.requireNonNull(spatial);
    }

    public ClientUiSettings(GlassBackdropSettings backdrop, boolean highContrast, boolean reducedMotion,
                            boolean widgetBackdropBlur, ControlFeedbackSettings feedback, TabScrollSettings tabs,
                            ThemeSettings theme, DynamicThemeSettings dynamicTheme) {
        this(backdrop, highContrast, reducedMotion, widgetBackdropBlur, feedback, tabs, theme, dynamicTheme,
                Settings.DEFAULT);
    }

    public ClientUiSettings(GlassBackdropSettings backdrop, boolean highContrast, boolean reducedMotion,
                            boolean widgetBackdropBlur, ControlFeedbackSettings feedback, TabScrollSettings tabs,
                            ThemeSettings theme) {
        this(backdrop, highContrast, reducedMotion, widgetBackdropBlur, feedback, tabs, theme, DynamicThemeSettings.DEFAULT);
    }

    public ClientUiSettings(GlassBackdropSettings backdrop, boolean highContrast, boolean reducedMotion,
                            boolean widgetBackdropBlur, ControlFeedbackSettings feedback, TabScrollSettings tabs) {
        this(backdrop, highContrast, reducedMotion, widgetBackdropBlur, feedback, tabs, ThemeSettings.DEFAULT);
    }

    public ClientUiSettings(GlassBackdropSettings backdrop, boolean highContrast, boolean reducedMotion,
                            boolean widgetBackdropBlur) {
        this(backdrop, highContrast, reducedMotion, widgetBackdropBlur, ControlFeedbackSettings.DEFAULT,
                TabScrollSettings.DEFAULT);
    }

    public ClientUiSettings(GlassBackdropSettings backdrop, boolean highContrast, boolean reducedMotion) {
        this(backdrop, highContrast, reducedMotion, DEFAULT.widgetBackdropBlur());
    }

    public static ClientUiSettings current() {
        return CURRENT.get();
    }

    public static void update(ClientUiSettings settings) {
        CURRENT.set(Objects.requireNonNull(settings));
    }

    static void updateBackdrop(GlassBackdropSettings backdrop) {
        CURRENT.updateAndGet(settings -> settings.withBackdrop(backdrop));
    }

    public ClientUiSettings withBackdrop(GlassBackdropSettings settings) {
        return new ClientUiSettings(settings, highContrast, reducedMotion, widgetBackdropBlur, feedback, tabs, theme,
                dynamicTheme, spatial);
    }

    public ClientUiSettings withDisplay(boolean highContrast, boolean reducedMotion, boolean widgetBackdropBlur) {
        return new ClientUiSettings(backdrop, highContrast, reducedMotion, widgetBackdropBlur, feedback, tabs, theme,
                dynamicTheme, spatial);
    }

    public ClientUiSettings withFeedback(ControlFeedbackSettings settings) {
        return new ClientUiSettings(backdrop, highContrast, reducedMotion, widgetBackdropBlur, settings, tabs, theme,
                dynamicTheme, spatial);
    }

    public ClientUiSettings withTabs(TabScrollSettings settings) {
        return new ClientUiSettings(backdrop, highContrast, reducedMotion, widgetBackdropBlur, feedback, settings, theme,
                dynamicTheme, spatial);
    }

    public ClientUiSettings withTheme(ThemeSettings settings) {
        return new ClientUiSettings(backdrop, highContrast, reducedMotion, widgetBackdropBlur, feedback, tabs, settings,
                dynamicTheme, spatial);
    }

    public ClientUiSettings withDynamicTheme(DynamicThemeSettings settings) {
        return new ClientUiSettings(backdrop, highContrast, reducedMotion, widgetBackdropBlur, feedback, tabs, theme,
                settings, spatial);
    }

    public ClientUiSettings withSpatial(Settings settings) {
        return new ClientUiSettings(backdrop, highContrast, reducedMotion, widgetBackdropBlur, feedback, tabs, theme,
                dynamicTheme, settings);
    }
}
