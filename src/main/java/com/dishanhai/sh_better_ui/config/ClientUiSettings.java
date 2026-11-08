package com.dishanhai.sh_better_ui.config;

import java.util.concurrent.atomic.AtomicReference;

/** 山海：两个加载器共用的客户端界面设置快照。 */
public record ClientUiSettings(GlassBackdropSettings backdrop, boolean highContrast, boolean reducedMotion,
                               boolean widgetBackdropBlur) {
    public static final ClientUiSettings DEFAULT = new ClientUiSettings(GlassBackdropSettings.DEFAULT, false, false, true);
    private static final AtomicReference<ClientUiSettings> CURRENT = new AtomicReference<>(DEFAULT);

    public ClientUiSettings(GlassBackdropSettings backdrop, boolean highContrast, boolean reducedMotion) {
        this(backdrop, highContrast, reducedMotion, DEFAULT.widgetBackdropBlur());
    }

    public static ClientUiSettings current() {
        return CURRENT.get();
    }

    public static void update(ClientUiSettings settings) {
        CURRENT.set(settings);
    }

    static void updateBackdrop(GlassBackdropSettings backdrop) {
        CURRENT.updateAndGet(settings -> settings.withBackdrop(backdrop));
    }

    public ClientUiSettings withBackdrop(GlassBackdropSettings settings) {
        return new ClientUiSettings(settings, highContrast, reducedMotion, widgetBackdropBlur);
    }
}
