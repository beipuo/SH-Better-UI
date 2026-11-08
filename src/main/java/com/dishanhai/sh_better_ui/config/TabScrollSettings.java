package com.dishanhai.sh_better_ui.config;

/** 山海：标签栏滚动的输入偏好，步长使用 GUI 单位。 */
public record TabScrollSettings(boolean wheelEnabled, boolean dragEnabled, boolean autoReveal, int scrollStep) {
    public static final int MIN_SCROLL_STEP = 8;
    public static final int MAX_SCROLL_STEP = 128;
    public static final TabScrollSettings DEFAULT = new TabScrollSettings(true, true, true, 32);

    public TabScrollSettings {
        if (scrollStep < MIN_SCROLL_STEP || scrollStep > MAX_SCROLL_STEP) throw new IllegalArgumentException();
    }
}
