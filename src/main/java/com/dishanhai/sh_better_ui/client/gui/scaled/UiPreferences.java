package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.ControlFeedbackSettings;
import com.dishanhai.sh_better_ui.theme.ThemeScope;

import java.util.Objects;

/** 山海：由画面传入的显示偏好，供绘制与动画共用。 */
public record UiPreferences(boolean highContrast, boolean reducedMotion, boolean backgroundBlur,
                             ControlFeedbackSettings feedback) {
    public static final UiPreferences DEFAULT = new UiPreferences(false, false, true);
    private static final UiPreferences[] CONFIGURED = {
            new UiPreferences(false, false, false), DEFAULT,
            new UiPreferences(false, true, false), new UiPreferences(false, true, true),
            new UiPreferences(true, false, false), new UiPreferences(true, false, true),
            new UiPreferences(true, true, false), new UiPreferences(true, true, true)
    };

    public UiPreferences {
        Objects.requireNonNull(feedback);
    }

    public UiPreferences(boolean highContrast, boolean reducedMotion, boolean backgroundBlur) {
        this(highContrast, reducedMotion, backgroundBlur, ControlFeedbackSettings.DEFAULT);
    }

    /** 读取客户端配置中的默认显示偏好。 */
    public static UiPreferences configured() {
        return fromSettings(ThemeEffects.settings());
    }

    /** 使用指定快照的控件偏好，适用于尚未提交的设置预览。 */
    public static UiPreferences fromSettings(ClientUiSettings settings) {
        var feedback = ThemeEffects.profile(settings.theme(), "feedback", settings.feedback());
        if (!ControlFeedbackSettings.DEFAULT.equals(feedback)) {
            return new UiPreferences(settings.highContrast(), settings.reducedMotion(), settings.widgetBackdropBlur(),
                    feedback);
        }
        int index = (settings.highContrast() ? 4 : 0) | (settings.reducedMotion() ? 2 : 0)
                | (settings.widgetBackdropBlur() ? 1 : 0);
        return CONFIGURED[index];
    }

    public boolean useBackdropBlur() {
        return backgroundBlur && !highContrast;
    }

    /** Combines this preference snapshot with an opt-in dynamic theme scope. */
    public UiPreferences withTheme(ThemeScope scope) {
        if (scope == null || scope.isClosed()) return this;
        var dynamic = scope.preferences();
        return new UiPreferences(highContrast || dynamic.highContrast(), reducedMotion || dynamic.reducedMotion(),
                backgroundBlur && !dynamic.lowPerformance(), feedback);
    }
}
