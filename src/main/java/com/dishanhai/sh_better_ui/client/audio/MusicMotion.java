package com.dishanhai.sh_better_ui.client.audio;

import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import java.util.Objects;

/** 山海：选择音频信号并以独立起落时间平滑，返回值可驱动缩放、位置、透明度或已有动画目标。 */
public final class MusicMotion {
    private final AudioBand band;
    private final float attackMs, releaseMs;
    private float intensity;
    private long previousMs;
    private boolean initialized;

    public MusicMotion() { this(AudioBand.BASS, 35, 180); }

    public MusicMotion(AudioBand band, float attackMs, float releaseMs) {
        this.band = Objects.requireNonNull(band);
        if (!Float.isFinite(attackMs) || !Float.isFinite(releaseMs) || attackMs <= 0 || releaseMs <= 0) {
            throw new IllegalArgumentException();
        }
        this.attackMs = attackMs;
        this.releaseMs = releaseMs;
    }

    /** 每个控件持有一个实例；同一时刻重复取样不推进，减少动效立即归零。 */
    public float sample(AudioFrame frame, long nowMs, boolean reducedMotion) {
        Objects.requireNonNull(frame);
        if (!initialized || reducedMotion) {
            initialized = true;
            previousMs = nowMs;
            intensity = 0;
            return 0;
        }
        double elapsed = Math.max(0d, (double) nowMs - previousMs);
        previousMs = Math.max(previousMs, nowMs);
        float target = frame.level(band);
        float time = target > intensity ? attackMs : releaseMs;
        intensity += (float) ((target - intensity) * (1 - Math.exp(-elapsed / time)));
        return GuiMotion.clamp01(intensity);
    }

    /** 映射到任意有限标量区间；减少动效时返回 resting。 */
    public float value(AudioFrame frame, long nowMs, boolean reducedMotion, float resting, float maximum) {
        if (!Float.isFinite(resting) || !Float.isFinite(maximum)) throw new IllegalArgumentException();
        float amount = sample(frame, nowMs, reducedMotion);
        return (float) ((double) resting + ((double) maximum - resting) * amount);
    }

    public void reset() {
        intensity = 0;
        initialized = false;
    }
}
