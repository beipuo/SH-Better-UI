package com.dishanhai.sh_better_ui.client.gui.scaled;

/** 山海：以 GUI 单位指定最大半径，以矩形宽度的比例指定清晰度过渡。 */
public record RevealProfile(float maxRadius, float fullBlurUntil, float sharpFrom) {
    public static final RevealProfile DEFAULT = new RevealProfile(12f, 0.2f, 0.8f);
    private static final float MAX_RADIUS = 32f;

    public RevealProfile {
        if (!Float.isFinite(maxRadius) || maxRadius < 0f || maxRadius > MAX_RADIUS) {
            throw new IllegalArgumentException("maxRadius must be in [0, 32]");
        }
        if (!(fullBlurUntil >= 0f && fullBlurUntil < sharpFrom && sharpFrom <= 1f)) {
            throw new IllegalArgumentException("Expected 0 <= fullBlurUntil < sharpFrom <= 1");
        }
    }
}
