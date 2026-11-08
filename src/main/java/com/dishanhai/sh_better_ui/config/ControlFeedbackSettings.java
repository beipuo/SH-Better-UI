package com.dishanhai.sh_better_ui.config;

/** 山海：控件动效与提示延迟；关闭动效仍保留静态状态反馈。 */
public record ControlFeedbackSettings(boolean hoverAnimation, boolean pressAnimation, boolean selectionPulse,
                                      int tooltipDelayMs) {
    public static final int MAX_TOOLTIP_DELAY_MS = 2000;
    public static final ControlFeedbackSettings DEFAULT = new ControlFeedbackSettings(true, true, true, 0);

    public ControlFeedbackSettings {
        if (tooltipDelayMs < 0 || tooltipDelayMs > MAX_TOOLTIP_DELAY_MS) throw new IllegalArgumentException();
    }
}
