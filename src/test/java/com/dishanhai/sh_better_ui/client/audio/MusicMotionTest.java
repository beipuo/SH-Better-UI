package com.dishanhai.sh_better_ui.client.audio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 山海：平滑使用时间差，尊重减少动效并复用标量动画。 */
class MusicMotionTest {
    private static final AudioFrame LOUD = new AudioFrame(0, .6f, .9f, 1, .3f, .2f, 1, 1);

    @Test
    void mapsSelectedBandAndReducedMotionReturnsRestingValue() {
        var motion = new MusicMotion(AudioBand.BASS, 35, 180);
        assertEquals(0, motion.sample(LOUD, 0, false));
        assertTrue(motion.sample(LOUD, 100, false) > .9);
        assertEquals(1, motion.value(LOUD, 100, true, 1, 1.2f));
        assertEquals(0, motion.sample(LOUD, 100, false));
        assertThrows(IllegalArgumentException.class, () -> new MusicMotion(AudioBand.BASS, Float.NaN, 1));
    }

    @Test
    void smoothingIsIndependentOfRenderRateAndRepeatedSampling() {
        var fast = new MusicMotion();
        var slow = new MusicMotion();
        fast.sample(LOUD, 0, false);
        slow.sample(LOUD, 0, false);
        for (int time = 10; time <= 100; time += 10) fast.sample(LOUD, time, false);
        assertEquals(fast.sample(LOUD, 100, false), slow.sample(LOUD, 100, false), .00001);
        float previous = slow.sample(LOUD, 100, false);
        assertEquals(previous, slow.sample(LOUD, 100, false));
        assertTrue(slow.sample(AudioFrame.silence(200), 200, false) < previous);
        assertEquals(0, slow.sample(AudioFrame.silence(10000), 10000, false), .00001);
    }
}
