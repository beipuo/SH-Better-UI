package com.dishanhai.sh_better_ui.client.audio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 山海：用确定的合成信号验证频段与瞬态，不依赖设备。 */
class MusicAnalyzerTest {
    @Test
    void silenceAndInvalidSamplesStayFinite() {
        var analyzer = new MusicAnalyzer();
        analyzer.accept(new float[2048], 48000, 0);
        assertEquals(0, analyzer.sample(0).rms());
        analyzer.accept(new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}, 48000, 1);
        analyzer.accept(new float[2045], 48000, 2);
        var frame = analyzer.sample(2);
        assertEquals(0, frame.peak());
        for (var band : AudioBand.values()) assertTrue(Float.isFinite(frame.level(band)));
        assertThrows(IllegalArgumentException.class, () -> analyzer.accept(new float[1], 0, 0));
    }

    @Test
    void separatesLowMidAndHighSineWavesAtBothSampleRates() {
        for (int rate : new int[]{44100, 48000}) {
            for (int frequency : new int[]{100, 1000, 8000}) {
                var analyzer = new MusicAnalyzer();
                analyzer.accept(sine(rate, frequency, 2048, .5f), rate, 100);
                var frame = analyzer.sample(100);
                assertEquals(.3535, frame.rms(), .01);
                assertEquals(rate == 48000 && frequency == 8000 ? .4330127 : .5, frame.peak(), .002);
                var expected = frequency == 100 ? AudioBand.BASS : frequency == 1000 ? AudioBand.MID : AudioBand.TREBLE;
                assertTrue(frame.level(expected) > .30, expected.toString());
                for (var other : new AudioBand[]{AudioBand.BASS, AudioBand.MID, AudioBand.TREBLE}) {
                    if (other != expected) assertTrue(frame.level(other) < .02, other.toString());
                }
            }
        }
    }

    @Test
    void packetBoundariesDoNotChangeTheSpectrum() {
        float[] signal = sine(48000, 1000, 2048, .6f);
        var whole = new MusicAnalyzer();
        var packets = new MusicAnalyzer();
        whole.accept(signal, 48000, 200);
        for (int offset = 0; offset < signal.length; offset += 73) {
            packets.accept(java.util.Arrays.copyOfRange(signal, offset, Math.min(offset + 73, signal.length)), 48000, 200);
        }
        assertEquals(whole.sample(200), packets.sample(200));
    }

    @Test
    void onsetsHaveCooldownAndStaleAudioFadesToSilence() {
        var analyzer = new MusicAnalyzer();
        var loud = sine(48000, 100, 2048, .8f);
        analyzer.accept(new float[2048], 48000, 0);
        analyzer.accept(loud, 48000, 50);
        var onset = analyzer.sample(50);
        assertEquals(1, onset.beatCount());
        assertEquals(1, onset.beat());
        analyzer.accept(new float[2048], 48000, 80);
        analyzer.accept(loud, 48000, 100);
        assertEquals(1, analyzer.sample(100).beatCount());
        analyzer.accept(new float[2048], 48000, 250);
        analyzer.accept(loud, 48000, 300);
        assertEquals(2, analyzer.sample(300).beatCount());
        assertTrue(analyzer.sample(600).rms() < .2f);
        assertEquals(0, analyzer.sample(5000).rms());
        assertEquals(0, analyzer.sample(5000).beat());
        assertEquals(2, analyzer.sample(5000).beatCount());
    }

    @Test
    void rateChangeAndResetDiscardPartialWindows() {
        var analyzer = new MusicAnalyzer();
        analyzer.accept(sine(48000, 100, 1024, .8f), 48000, 0);
        analyzer.accept(new float[2048], 44100, 100);
        assertEquals(0, analyzer.sample(100).rms());
        analyzer.reset();
        assertEquals(AudioFrame.silence(200), analyzer.sample(200));
    }

    @Test
    void resumingAfterNoPacketsStartsANewOnsetWithoutJoiningOldSamples() {
        var analyzer = new MusicAnalyzer();
        var loud = sine(48000, 100, 2048, .8f);
        analyzer.accept(loud, 48000, 0);
        analyzer.accept(new float[]{1, 1, 1}, 48000, 5);
        assertEquals(1, analyzer.sample(2000).beatCount());
        analyzer.accept(loud, 48000, 2000);
        assertEquals(2, analyzer.sample(2000).beatCount());
        assertEquals(.8 / Math.sqrt(2), analyzer.sample(2000).rms(), .01);
    }

    @Test
    void stereoPhaseInversionPreservesEnergyAndFrequencies() {
        float[] mono = sine(48000, 1000, 2048, .5f);
        float[] stereo = new float[mono.length * 2];
        for (int i = 0; i < mono.length; i++) {
            stereo[2 * i] = mono[i];
            stereo[2 * i + 1] = -mono[i];
        }
        var left = new MusicAnalyzer();
        var combined = new MusicAnalyzer();
        left.accept(mono, 48000, 100);
        combined.acceptInterleaved(stereo, 2, 48000, 100);
        assertEquals(left.sample(100).rms(), combined.sample(100).rms(), .00001);
        assertEquals(left.sample(100).mid(), combined.sample(100).mid(), .00001);
        assertTrue(combined.sample(100).treble() < .001);
        assertThrows(IllegalArgumentException.class,
                () -> combined.acceptInterleaved(new float[3], 2, 48000, 100));
    }

    static float[] sine(int rate, int hz, int size, float amplitude) {
        float[] signal = new float[size];
        for (int i = 0; i < size; i++) signal[i] = amplitude * (float) Math.sin(Math.TAU * hz * i / rate);
        return signal;
    }
}
