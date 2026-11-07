package com.dishanhai.sh_better_ui.client.gui.scaled;

/** 山海：由画面传入的显示偏好，供绘制与动画共用。 */
public record UiPreferences(boolean highContrast, boolean reducedMotion, boolean backgroundBlur) {
    public static final UiPreferences DEFAULT = new UiPreferences(false, false, true);

    public boolean useBackdropBlur() {
        return backgroundBlur && !highContrast;
    }
}
