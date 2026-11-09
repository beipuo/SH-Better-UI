package com.dishanhai.sh_better_ui.client.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：多频段分离、同帧共享、节拍重叠、静音和减少动效。 */
class MusicRhythmTest {
    @Test
    void frequencyBandsMoveDifferentLayersAndSilenceReleasesThem() {
        var low = new MusicRhythm();
        low.sample(AudioFrame.silence(0), 0, false);
        var bass = low.sample(new AudioFrame(40, .3f, .4f, .5f, 0, 0, 0, 0), 40, false);
        assertTrue(bass.bass() > .6f);
        assertEquals(0, bass.wave(1));
        assertEquals(0, bass.sparkle(1));

        var middleAndHigh = new AudioFrame(80, .35f, .5f, 0, .3f, .2f, 0, 0);
        var active = low.sample(middleAndHigh, 80, false);
        assertNotEquals(active.wave(.8f), active.wave(2.8f));
        assertNotEquals(active.sparkle(.8f), active.sparkle(2.8f));
        assertTrue(active.flowPhase() > bass.flowPhase());
        assertSame(active, low.sample(AudioFrame.silence(80), 80, false));
        assertSame(active, low.sample(AudioFrame.silence(70), 70, false));

        var fading = low.sample(AudioFrame.silence(150), 150, false);
        assertTrue(fading.bass() > 0 && fading.bass() < active.bass());
        assertTrue(fading.treble() > 0 && fading.treble() < active.treble());
        var silent = low.sample(AudioFrame.silence(8000), 8000, false);
        assertTrue(silent.energy() < .001f && silent.mid() < .001f && silent.treble() < .001f);
    }

    @Test
    void overlappingBeatsExpandIndependentlyWithoutReplayingBacklog() {
        var motion = new MusicRhythm();
        motion.sample(beat(0, 10, 1), 0, false);
        assertTrue(motion.sample(beat(16, 10, .9f), 16, false).pulses().isEmpty());
        motion.sample(beat(200, 11, 1), 200, false);
        var strike = motion.sample(beat(260, 11, .6f), 260, false);
        assertEquals(1, strike.pulses().size());
        assertTrue(strike.pulses().getFirst().opacity() > 0);
        assertTrue(strike.impact() > 0);
        assertTrue(motion.sample(beat(440, 11, .1f), 440, false).impact() < 0);
        motion.sample(beat(460, 14, .8f), 460, false);
        var overlap = motion.sample(beat(500, 14, .6f), 500, false);
        assertEquals(2, overlap.pulses().size());
        assertTrue(overlap.pulses().getFirst().progress() > overlap.pulses().getLast().progress());
        assertThrows(UnsupportedOperationException.class, overlap.pulses()::clear);
        assertTrue(motion.sample(beat(1100, 18, .05f), 1100, false).pulses().isEmpty());
    }

    @Test
    void staticModeAndSourceResetsDiscardVisualHistory() {
        var motion = new MusicRhythm();
        motion.sample(AudioFrame.silence(0), 0, false);
        assertEquals(1, motion.sample(beat(30, 1, 1), 30, false).pulses().size());
        assertSame(MusicRhythm.Frame.STILL, motion.sample(beat(30, 1, 1), 30, true));
        motion.sample(beat(700, 4, 1), 700, true);
        var resumed = motion.sample(beat(720, 4, .8f), 720, false);
        assertEquals(0, resumed.lift(0));
        assertTrue(resumed.pulses().isEmpty());
        assertEquals(1, motion.sample(beat(1000, 5, 1), 1000, false).pulses().size());
        assertTrue(motion.sample(AudioFrame.silence(1010), 1010, false).pulses().isEmpty());
        motion.reset();
        assertTrue(motion.sample(beat(0, 20, 1), 0, false).pulses().isEmpty());
    }

    private static AudioFrame beat(long time, long count, float pulse) {
        return new AudioFrame(time, .35f, .5f, .4f, .2f, .1f, pulse, count);
    }
}
