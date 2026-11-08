package com.dishanhai.sh_better_ui.client.audio;

import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;

/** 山海：可跨线程读取的不可变快照；强度为 0..1，时间使用单调毫秒。 */
public record AudioFrame(long timestampMs, float rms, float peak, float bass, float mid, float treble,
                         float beat, long beatCount) {
    public AudioFrame {
        rms = finiteLevel(rms);
        peak = finiteLevel(peak);
        bass = finiteLevel(bass);
        mid = finiteLevel(mid);
        treble = finiteLevel(treble);
        beat = finiteLevel(beat);
        if (beatCount < 0) throw new IllegalArgumentException();
    }

    public static AudioFrame silence(long nowMs) {
        return new AudioFrame(nowMs, 0, 0, 0, 0, 0, 0, 0);
    }

    public float level(AudioBand band) {
        return switch (band) {
            case RMS -> rms;
            case PEAK -> peak;
            case BASS -> bass;
            case MID -> mid;
            case TREBLE -> treble;
            case BEAT -> beat;
        };
    }

    static float finiteLevel(float value) {
        return Float.isFinite(value) ? GuiMotion.clamp01(value) : 0;
    }
}
