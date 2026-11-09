// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.palette;

import com.dishanhai.sh_better_ui.theme.accessibility.ThemePreferences;

/** Throttled local effects. Sampling audio never mutates a theme registry or scope tree. */
public final class AudioReactiveTheme {
    private final float smoothing;
    private final long periodMillis;
    private final float maxGlow;
    private final float maxPulse;
    private long lastSample;
    private boolean sampled;
    private Effects effects = Effects.NONE;

    public AudioReactiveTheme(float smoothing, long periodMillis, float maxGlow, float maxPulse) {
        if (!Float.isFinite(smoothing) || smoothing < 0 || smoothing >= 1 || periodMillis < 16 || periodMillis > 1000) {
            throw new IllegalArgumentException("Invalid audio smoothing or sample period");
        }
        this.smoothing = smoothing;
        this.periodMillis = periodMillis;
        this.maxGlow = level(maxGlow);
        this.maxPulse = level(maxPulse);
    }

    /** Samples normalized bands using the caller's monotonic frame clock. Stops immediately when disabled. */
    public Effects sample(boolean playing, float bass, float mid, ThemePreferences prefs, long nowMillis) {
        if (!playing || !prefs.musicReactive() || prefs.reducedMotion() || prefs.lowPerformance() || prefs.highContrast()) {
            reset();
            return effects;
        }
        if (sampled && nowMillis - lastSample < periodMillis) return effects;
        long steps = sampled ? Math.max(1, (nowMillis - lastSample) / periodMillis) : 1;
        float retain = (float) Math.pow(smoothing, steps);
        lastSample = nowMillis;
        sampled = true;
        effects = new Effects(effects.glow * retain + maxGlow * level(bass) * (1 - retain),
                effects.pulse * retain + maxPulse * level(mid) * (1 - retain));
        return effects;
    }

    public void reset() {
        sampled = false;
        effects = Effects.NONE;
    }

    private static float level(float value) { return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0; }

    public record Effects(float glow, float pulse) {
        public static final Effects NONE = new Effects(0, 0);
    }
}
