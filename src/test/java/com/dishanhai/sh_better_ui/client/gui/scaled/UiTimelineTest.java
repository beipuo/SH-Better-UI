package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：验证按帧播放与任意顺序取样，不依赖游戏渲染循环。 */
class UiTimelineTest {
    @Test
    void pausesAndResumesWithoutCountingPausedTime() {
        var timeline = new UiTimeline(30, 120);
        assertEquals(UiTimeline.State.READY, timeline.sample(1000).state());
        timeline.play(1000);
        assertEquals(15, timeline.sample(1500).index());
        timeline.pause(1500);
        assertEquals(15, timeline.sample(9000).index());
        assertEquals(UiTimeline.State.PAUSED, timeline.sample(9000).state());
        timeline.play(9000);
        assertEquals(30, timeline.sample(9500).index());
    }

    @Test
    void seekingIsPausedAndIndependentOfPlaybackHistory() {
        var timeline = new UiTimeline(30, 120);
        timeline.play(0);
        timeline.sample(2000);
        timeline.seek(15);
        var direct = timeline.sample(9000);
        assertEquals(15, direct.index());
        assertEquals(UiTimeline.State.PAUSED, direct.state());
        assertEquals(0.5f, direct.interpolate(0, 30, 0f, 1f));
        timeline.seek(90);
        timeline.seek(15);
        assertEquals(direct, timeline.sample(12000));
        timeline.seek(-20);
        assertEquals(0, timeline.sample(12000).index());
        timeline.seek(Integer.MAX_VALUE);
        assertEquals(119, timeline.sample(12000).index());
        assertEquals(UiTimeline.State.ENDED, timeline.sample(12000).state());
    }

    @Test
    void stopsAtTheLastFrameAndCanReplayAndReset() {
        var timeline = new UiTimeline(30, 120);
        timeline.play(100);
        assertEquals(119, timeline.sample(4100).index());
        assertEquals(UiTimeline.State.ENDED, timeline.sample(5000).state());
        timeline.play(5000);
        assertEquals(0, timeline.sample(5000).index());
        assertEquals(UiTimeline.State.PLAYING, timeline.sample(5000).state());
        timeline.reset();
        assertEquals(0, timeline.sample(9000).index());
        assertEquals(UiTimeline.State.READY, timeline.sample(9000).state());
    }

    @Test
    void repeatedSamplingAndRepeatedPlayDoNotChangeSpeedOrRewind() {
        var timeline = new UiTimeline(30, 120);
        timeline.play(0);
        assertEquals(30, timeline.sample(1000).index());
        assertEquals(30, timeline.sample(1000).index());
        assertEquals(30, timeline.sample(900).index());
        timeline.play(1000);
        assertEquals(60, timeline.sample(2000).index());
    }

    @Test
    void subframeTimeSurvivesPauseAndFpsIsNotRenderRate() {
        var timeline = new UiTimeline(24, 120);
        timeline.play(0);
        timeline.pause(25);
        timeline.play(1000);
        assertEquals(1, timeline.sample(1025).index());
        assertEquals(24, timeline.sample(2000).index());
    }

    @Test
    void interpolationClampsAndSequencesUseExclusiveEndAndLocalFrames() {
        var timeline = new UiTimeline(30, 120);
        var sequence = new UiTimeline.Sequence(30, 30);
        timeline.seek(15);
        var before = timeline.sample(0);
        assertFalse(sequence.contains(before));
        assertEquals(-15, sequence.localFrame(before));
        assertEquals(8f, before.interpolate(30, 60, 8f, -8f));
        timeline.seek(30);
        assertTrue(sequence.contains(timeline.sample(0)));
        assertEquals(0, sequence.localFrame(timeline.sample(0)));
        timeline.seek(45);
        assertEquals(0f, timeline.sample(0).interpolate(30, 60, 8f, -8f));
        timeline.seek(60);
        assertFalse(sequence.contains(timeline.sample(0)));
        assertEquals(-8f, timeline.sample(0).interpolate(30, 60, 8f, -8f));
    }

    @Test
    void validatesConfigurationAndSupportsOneFrame() {
        assertThrows(IllegalArgumentException.class, () -> new UiTimeline(0, 120));
        assertThrows(IllegalArgumentException.class, () -> new UiTimeline(30, 0));
        assertThrows(IllegalArgumentException.class, () -> new UiTimeline.Sequence(-1, 3));
        assertThrows(IllegalArgumentException.class, () -> new UiTimeline.Sequence(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new UiTimeline.Sequence(Integer.MAX_VALUE, 2));
        var timeline = new UiTimeline(30, 1);
        timeline.play(0);
        assertEquals(UiTimeline.State.ENDED, timeline.sample(0).state());
        assertThrows(IllegalArgumentException.class, () -> timeline.sample(0).interpolate(3, 3, 0f, 1f));
    }
}
