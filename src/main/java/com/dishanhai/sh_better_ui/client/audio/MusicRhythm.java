package com.dishanhai.sh_better_ui.client.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 山海：每个画面独立持有的律动状态。只读音频快照，不采集音频或操作控件。 */
public final class MusicRhythm {
    private static final int PULSE_MS = 540;
    private static final int MIN_PULSE_GAP_MS = 180;
    private static final float TAU = (float) (2 * Math.PI);

    private final MusicMotion bass = new MusicMotion(AudioBand.BASS, 42, 250);
    private final MusicMotion mid = new MusicMotion(AudioBand.MID, 85, 285);
    private final MusicMotion treble = new MusicMotion(AudioBand.TREBLE, 32, 165);
    private final MusicMotion loudness = new MusicMotion(AudioBand.RMS, 410, 890);
    private final List<Onset> onsets = new ArrayList<>(3);
    private long previousMs, beatCount;
    private boolean initialized, wasStatic;
    private float flowPhase, shimmerPhase;
    private Frame current = Frame.STILL;

    /**
     * 按渲染时钟更新；同一时刻可复用返回值。首次取样、恢复动效或节拍计数回退时不补播历史。
     * staticMode 用于减少动效或高对比模式：立即返回静态状态，恢复时重新淡入。
     */
    public Frame sample(AudioFrame audio, long nowMs, boolean staticMode) {
        Objects.requireNonNull(audio);
        if (initialized && nowMs < previousMs && !staticMode) return current;
        if (staticMode) {
            if (!wasStatic) reset();
            initialized = wasStatic = true;
            previousMs = nowMs;
            beatCount = audio.beatCount();
            return current;
        }
        if (wasStatic) reset();
        if (initialized && nowMs == previousMs) return current;

        double elapsed = initialized ? (double) nowMs - previousMs : 0;
        boolean newBeat = initialized && audio.beatCount() > beatCount;
        if (audio.beatCount() < beatCount) onsets.clear();
        onsets.removeIf(onset -> (double) nowMs - onset.timeMs() >= PULSE_MS);
        // A count can advance during a dropped frame. Draw one fresh pulse, never a backlog.
        float accent = intensity(.65f * audio.bass() + .35f * audio.rms(), 5f);
        if (newBeat && audio.beat() >= .32f && accent >= .24f
                && (onsets.isEmpty() || (double) nowMs - onsets.getLast().timeMs() >= MIN_PULSE_GAP_MS)) {
            onsets.add(new Onset(nowMs, accent * audio.beat()));
        }
        initialized = true;
        previousMs = nowMs;
        beatCount = audio.beatCount();

        float low = intensity(bass.sample(audio, nowMs, false), 3.8f);
        float middle = intensity(mid.sample(audio, nowMs, false), 4.8f);
        float high = intensity(treble.sample(audio, nowMs, false), 6.4f);
        float energy = intensity(loudness.sample(audio, nowMs, false), 3.2f);
        // Integrate speed so a change of intensity cannot jump the phase. Ignore offscreen time.
        double seconds = Math.min(100, elapsed) / 1000;
        flowPhase = advance(flowPhase, seconds, .4f * energy + 1.8f * middle);
        shimmerPhase = advance(shimmerPhase, seconds, .6f * energy + 2.9f * high);

        float impact = 0;
        List<Pulse> pulses = new ArrayList<>(onsets.size());
        for (Onset onset : onsets) {
            float progress = (float) (((double) nowMs - onset.timeMs()) / PULSE_MS);
            float attack = Math.min(1, progress * 42);
            float fade = 1 - progress;
            pulses.add(new Pulse(progress, onset.strength() * attack * fade * fade));
            impact += onset.strength() * attack * (float) (Math.sin(progress * 3 * Math.PI)
                    * Math.exp(-progress * 7) * fade);
        }
        current = new Frame(low, middle, high, energy, flowPhase, shimmerPhase, impact, pulses);
        return current;
    }

    /** 换音源、关闭画面或跳转播放位置时丢弃旧时钟和脉冲。 */
    public void reset() {
        bass.reset();
        mid.reset();
        treble.reset();
        loudness.reset();
        onsets.clear();
        initialized = wasStatic = false;
        previousMs = beatCount = 0;
        flowPhase = shimmerPhase = 0;
        current = Frame.STILL;
    }

    private static float intensity(float value, float gain) {
        return (float) -Math.expm1(-value * gain);
    }

    private static float advance(float phase, double seconds, float speed) {
        return (float) ((phase + seconds * speed) % TAU);
    }

    private record Onset(long timeMs, float strength) {}

    /** 由节拍产生的扩散进度和可见强度，均为 0..1。 */
    public record Pulse(float progress, float opacity) {}

    /** 帧快照可供任意多个绘制区域复用。频段及能量为 0..1，impact 为带回弹的有符号位移系数。 */
    public record Frame(float bass, float mid, float treble, float energy, float flowPhase,
                        float shimmerPhase, float impact, List<Pulse> pulses) {
        public static final Frame STILL = new Frame(0, 0, 0, 0, 0, 0, 0, List.of());

        public Frame {
            pulses = List.copyOf(pulses);
        }

        /** 用不同相位偏移（弧度）错开中频轨道、装饰线条或图标。 */
        public float wave(float offset) {
            double phase = flowPhase + offset;
            return mid * (float) (.72 * Math.sin(phase) + .28 * Math.sin(2 * phase - .85));
        }

        /** 用不同偏移给每个像素独立的高频亮度，静音时为零。 */
        public float sparkle(float offset) {
            double wave = .5 + .5 * Math.sin(shimmerPhase + offset);
            return treble * (float) (wave * wave * wave * wave);
        }

        /** 按钮图标等装饰元素的轻微抬升；单位由调用者决定，控件热区保持原位。 */
        public float lift(float offset) {
            return .5f * bass + .3f * wave(offset) + impact;
        }
    }
}
