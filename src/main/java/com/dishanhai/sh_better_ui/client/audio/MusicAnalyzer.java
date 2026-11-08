package com.dishanhai.sh_better_ui.client.audio;

import java.util.Arrays;
import java.util.Objects;

/**
 * 山海：PCM 的流式分析，独立于设备和 Minecraft；逐声道合并功率，避免立体声相消。
 * 2048 采样 Hann 窗 + FFT；频段为 20–250、250–4000、4000 Hz–Nyquist。
 * 节拍是能量上升沿提示，不代表稳定 BPM。不会保存或传输原始声音。
 * accept/reset 由一个生产线程调用；sample 可从其他线程读取。
 */
public final class MusicAnalyzer {
    public static final int WINDOW_SIZE = 2048;
    private float[][] samples = new float[1][WINDOW_SIZE];
    private final double[] real = new double[WINDOW_SIZE];
    private final double[] imaginary = new double[WINDOW_SIZE];
    private final double[] window = new double[WINDOW_SIZE];
    private final double windowPower;
    private int count, sampleRate, channels = 1;
    private float averageEnergy, previousEnergy;
    private long beats, lastBeatMs;
    private long lastInputMs;
    private boolean hasInput;
    private volatile Snapshot latest = new Snapshot(AudioFrame.silence(0), 0, false);

    private record Snapshot(AudioFrame frame, long lastBeatMs, boolean hasAudio) {}

    public MusicAnalyzer() {
        double sum = 0;
        for (int i = 0; i < WINDOW_SIZE; i++) {
            window[i] = .5 - .5 * Math.cos(Math.TAU * i / (WINDOW_SIZE - 1));
            sum += window[i] * window[i];
        }
        windowPower = sum / WINDOW_SIZE;
    }

    /** 输入归一化单声道采样，可任意分包。非有限值视作静音，超幅值截断。 */
    public void accept(float[] mono, int rate, long nowMs) {
        acceptInterleaved(mono, 1, rate, nowMs);
    }

    /** 多声道输入按帧交错，每包须包含完整帧；合并各声道功率，不先混成单声道。 */
    public void acceptInterleaved(float[] interleaved, int channelCount, int rate, long nowMs) {
        Objects.requireNonNull(interleaved);
        if (rate < 8000 || rate > 384000) throw new IllegalArgumentException();
        if (channelCount < 1 || channelCount > 32 || interleaved.length % channelCount != 0) {
            throw new IllegalArgumentException();
        }
        if (sampleRate != rate || channels != channelCount) {
            reset();
            sampleRate = rate;
            channels = channelCount;
            samples = new float[channels][WINDOW_SIZE];
        }
        if (interleaved.length == 0) return;
        if (hasInput && (double) nowMs - lastInputMs > 250) {
            count = 0;
            averageEnergy = previousEnergy = 0;
        }
        lastInputMs = nowMs;
        hasInput = true;
        for (int offset = 0; offset < interleaved.length; offset += channels) {
            for (int channel = 0; channel < channels; channel++) {
                float value = interleaved[offset + channel];
                samples[channel][count] = Float.isFinite(value) ? Math.clamp(value, -1f, 1f) : 0;
            }
            count++;
            if (count == WINDOW_SIZE) {
                analyze(nowMs);
                count = 0;
            }
        }
    }

    /** 没有新包时，超过 100ms 的音量以 150ms 时间常数衰减；节拍以 120ms 衰减。 */
    public AudioFrame sample(long nowMs) {
        var snapshot = latest;
        if (!snapshot.hasAudio) return AudioFrame.silence(nowMs);
        var frame = snapshot.frame;
        double age = Math.max(0d, (double) nowMs - frame.timestampMs());
        float fade = decay(Math.max(0, age - 100) / 150);
        float beat = frame.beatCount() == 0 ? 0 : decay(Math.max(0d, (double) nowMs - snapshot.lastBeatMs) / 120);
        return new AudioFrame(nowMs, frame.rms() * fade, frame.peak() * fade, frame.bass() * fade,
                frame.mid() * fade, frame.treble() * fade, beat, frame.beatCount());
    }

    /** 设备格式变更、数据中断或停止时，丢弃旧窗口及节拍历史。 */
    public void reset() {
        count = sampleRate = 0;
        averageEnergy = previousEnergy = 0;
        beats = lastBeatMs = 0;
        lastInputMs = 0;
        hasInput = false;
        for (float[] channel : samples) Arrays.fill(channel, 0);
        latest = new Snapshot(AudioFrame.silence(0), 0, false);
    }

    private void analyze(long nowMs) {
        double squares = 0;
        float peak = 0;
        double bass = 0, mid = 0, treble = 0;
        for (float[] channel : samples) {
            for (int i = 0; i < WINDOW_SIZE; i++) {
                float value = channel[i];
                squares += value * (double) value;
                peak = Math.max(peak, Math.abs(value));
                real[i] = value * window[i];
                imaginary[i] = 0;
            }
            fft();
            for (int i = 1; i <= WINDOW_SIZE / 2; i++) {
                double frequency = i * (double) sampleRate / WINDOW_SIZE;
                double power = real[i] * real[i] + imaginary[i] * imaginary[i];
                if (i == WINDOW_SIZE / 2) power *= .5;
                if (frequency >= 20 && frequency < 250) bass += power;
                else if (frequency >= 250 && frequency < 4000) mid += power;
                else if (frequency >= 4000) treble += power;
            }
        }
        float rms = (float) Math.sqrt(squares / (WINDOW_SIZE * (double) channels));
        double normalization = 2d / (WINDOW_SIZE * (double) WINDOW_SIZE * windowPower * channels);
        float low = (float) Math.sqrt(bass * normalization);
        float middle = (float) Math.sqrt(mid * normalization);
        float high = (float) Math.sqrt(treble * normalization);
        float energy = .65f * low + .35f * rms;
        if (energy > Math.max(.015f, averageEnergy * 1.45f)
                && energy - previousEnergy > Math.max(.008f, averageEnergy * .15f)
                && (beats == 0 || (double) nowMs - lastBeatMs >= 180)) {
            beats++;
            lastBeatMs = nowMs;
        }
        double weight = 1 - Math.exp(-WINDOW_SIZE / (sampleRate * .8));
        averageEnergy += (float) ((energy - averageEnergy) * weight);
        previousEnergy = energy;
        latest = new Snapshot(new AudioFrame(nowMs, rms, peak, low, middle, high, 0, beats), lastBeatMs, true);
    }

    private void fft() {
        for (int i = 1, j = 0; i < WINDOW_SIZE; i++) {
            int bit = WINDOW_SIZE >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double value = real[i];
                real[i] = real[j];
                real[j] = value;
            }
        }
        for (int length = 2; length <= WINDOW_SIZE; length <<= 1) {
            double angle = -Math.TAU / length;
            double stepReal = Math.cos(angle), stepImaginary = Math.sin(angle);
            for (int start = 0; start < WINDOW_SIZE; start += length) {
                double factorReal = 1, factorImaginary = 0;
                for (int offset = 0; offset < length / 2; offset++) {
                    int a = start + offset, b = a + length / 2;
                    double valueReal = real[b] * factorReal - imaginary[b] * factorImaginary;
                    double valueImaginary = real[b] * factorImaginary + imaginary[b] * factorReal;
                    real[b] = real[a] - valueReal;
                    imaginary[b] = imaginary[a] - valueImaginary;
                    real[a] += valueReal;
                    imaginary[a] += valueImaginary;
                    double nextReal = factorReal * stepReal - factorImaginary * stepImaginary;
                    factorImaginary = factorReal * stepImaginary + factorImaginary * stepReal;
                    factorReal = nextReal;
                }
            }
        }
    }

    private static float decay(double exponent) {
        float value = (float) Math.exp(-exponent);
        return value < .0001f ? 0 : value;
    }
}
