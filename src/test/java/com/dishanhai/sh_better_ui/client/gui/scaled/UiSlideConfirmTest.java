package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：核对滑动确认的最终落点、回弹和重复触发边界。 */
class UiSlideConfirmTest {
    private static final UiSlideConfirm.Track TRACK = new UiSlideConfirm.Track(10, 20, 160, 24);
    private static final UiPreferences MOTION = UiPreferences.DEFAULT;
    private static final UiPreferences REDUCED_MOTION = new UiPreferences(false, true, false);
    private static final int GRAB_X = 16;
    private static final int GRAB_Y = 32;

    @Test
    void onlyTheRoundKnobStartsTheGestureAndKeepsItsGrabOffset() {
        var gesture = new UiSlideConfirm.Gesture();
        assertFalse(gesture.begin(TRACK, 140, GRAB_Y, 0, MOTION));
        assertFalse(gesture.begin(TRACK, 13, 23, 0, MOTION));
        assertTrue(gesture.begin(TRACK, GRAB_X, GRAB_Y, 0, MOTION));

        gesture.drag(TRACK, GRAB_X + TRACK.travel() / 2.0, -40);
        float progress = gesture.sample(16, MOTION);
        assertEquals(0.5f, progress);
        assertEquals(80f, TRACK.thumbX(progress));
        assertFalse(gesture.release(TRACK, GRAB_X + TRACK.travel() * 0.75, GRAB_Y, 20));
        assertEquals(0.75f, gesture.sample(20, MOTION));
    }

    @Test
    void releaseUsesTheFinalPositionEvenAfterAnArmedDrag() {
        var gesture = new UiSlideConfirm.Gesture();
        double endX = GRAB_X + TRACK.travel();
        assertTrue(gesture.begin(TRACK, GRAB_X, GRAB_Y, 0, MOTION));
        gesture.drag(TRACK, endX + 50, GRAB_Y);
        assertTrue(gesture.armed(16));
        assertFalse(gesture.completed());

        double justShort = Math.nextDown(endX);
        gesture.drag(TRACK, justShort, GRAB_Y);
        assertEquals(1f, gesture.sample(24, MOTION));
        assertFalse(gesture.armed(24));
        gesture.drag(TRACK, endX, GRAB_Y);
        assertTrue(gesture.armed(24));
        assertFalse(gesture.release(TRACK, justShort, GRAB_Y, 24));
        assertFalse(gesture.completed());
        assertFalse(gesture.held());
        assertEquals(0f, gesture.sampleCompletion(40, MOTION));
    }

    @Test
    void reachingTheEndOnReleaseCompletesOnceUntilReset() {
        var gesture = new UiSlideConfirm.Gesture();
        double endX = GRAB_X + TRACK.travel();
        assertTrue(gesture.begin(TRACK, GRAB_X, GRAB_Y, 0, MOTION));
        gesture.drag(TRACK, endX - 20, GRAB_Y);
        assertTrue(gesture.release(TRACK, endX, GRAB_Y, 50));
        assertTrue(gesture.completed());
        assertFalse(gesture.armed(50));
        assertEquals(1f, gesture.sample(500, MOTION));
        assertFalse(gesture.release(TRACK, endX, GRAB_Y, 500));
        assertFalse(gesture.begin(TRACK, endX, GRAB_Y, 500, MOTION));
        assertFalse(gesture.beginKey(500));

        gesture.reset();
        assertFalse(gesture.completed());
        assertEquals(0f, gesture.sample(500, MOTION));
        assertTrue(gesture.begin(TRACK, GRAB_X, GRAB_Y, 500, MOTION));
    }

    @Test
    void successAnimationStartsOnReleaseAndReplaysAfterReset() {
        var gesture = new UiSlideConfirm.Gesture();
        double endX = GRAB_X + TRACK.travel();
        assertTrue(gesture.begin(TRACK, GRAB_X, GRAB_Y, 0, MOTION));
        gesture.drag(TRACK, endX, GRAB_Y);
        assertEquals(0f, gesture.sampleCompletion(16, MOTION));

        assertTrue(gesture.release(TRACK, endX, GRAB_Y, 32));
        assertEquals(0f, gesture.sampleCompletion(32, MOTION));
        float entering = gesture.sampleCompletion(48, MOTION);
        assertTrue(entering > 0f && entering < 1f);
        assertEquals(1f, gesture.sample(48, MOTION));

        gesture.reset();
        assertEquals(0f, gesture.sampleCompletion(64, MOTION));
        assertTrue(gesture.begin(TRACK, GRAB_X, GRAB_Y, 64, MOTION));
        assertTrue(gesture.release(TRACK, endX, GRAB_Y, 80));
        assertEquals(0f, gesture.sampleCompletion(80, MOTION));
        assertEquals(entering, gesture.sampleCompletion(96, MOTION));
    }

    @Test
    void prematureReleaseReturnsWithDampingAndCanBeGrabbedMidReturn() {
        var gesture = new UiSlideConfirm.Gesture();
        assertTrue(gesture.begin(TRACK, GRAB_X, GRAB_Y, 0, MOTION));
        assertFalse(gesture.release(TRACK, GRAB_X + TRACK.travel() * 0.75, GRAB_Y, 20));
        float halfwayBack = gesture.sample(36, MOTION);
        assertTrue(halfwayBack > 0 && halfwayBack < 0.75f);
        double regrabX = TRACK.thumbX(halfwayBack) + TRACK.diameter() * 0.5;
        assertTrue(gesture.begin(TRACK, regrabX, GRAB_Y, 36, MOTION));
        assertEquals(halfwayBack, gesture.sample(52, MOTION));

        gesture.drag(TRACK, 200, GRAB_Y);
        assertTrue(gesture.armed(52));
        gesture.cancel(52);
        assertFalse(gesture.release(TRACK, 200, GRAB_Y, 52));
        assertEquals(0f, gesture.sample(52, REDUCED_MOTION));
        assertFalse(gesture.completed());
    }

    @Test
    void clickingTheKnobImmediatelyAfterCancelAtTheEndDoesNotConfirm() {
        var gesture = new UiSlideConfirm.Gesture();
        double endX = GRAB_X + TRACK.travel();
        assertTrue(gesture.begin(TRACK, GRAB_X, GRAB_Y, 0, MOTION));
        gesture.drag(TRACK, endX, GRAB_Y);
        gesture.cancel(16);
        assertEquals(1f, gesture.sample(16, MOTION));

        assertTrue(gesture.begin(TRACK, endX, GRAB_Y, 16, MOTION));
        assertFalse(gesture.armed(16));
        assertFalse(gesture.release(TRACK, endX, GRAB_Y, 16));
        assertFalse(gesture.completed());
    }

    @Test
    void keyboardHoldUsesElapsedTimeAndOnlyConfirmsOnRelease() {
        var gesture = new UiSlideConfirm.Gesture();
        assertTrue(gesture.beginKey(0));
        assertTrue(gesture.beginKey(250));
        assertFalse(gesture.begin(TRACK, GRAB_X, GRAB_Y, 300, MOTION));
        assertEquals(0.5f, gesture.sample(300, REDUCED_MOTION));
        assertFalse(gesture.releaseKey(599));
        assertEquals(0f, gesture.sample(599, REDUCED_MOTION));

        assertTrue(gesture.beginKey(1000));
        assertTrue(gesture.armed(1600));
        assertFalse(gesture.completed());
        assertTrue(gesture.releaseKey(1600));
        assertTrue(gesture.completed());
        assertEquals(1f, gesture.sampleCompletion(1600, REDUCED_MOTION));
        assertEquals(1f, gesture.sampleCompletion(1600, MOTION));
        assertFalse(gesture.releaseKey(1601));
    }
}
