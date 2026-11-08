package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：验证死区、轻甩、慢拖和取消。 */
class SwipeGestureTest {
    @Test
    void crossesDeadZoneWithoutLosingTheInitialDisplacement() {
        SwipeGesture swipe = new SwipeGesture();
        assertTrue(swipe.begin(0, 10, 20, 100));
        assertTrue(swipe.move(0, 10, 22, 110));
        assertFalse(swipe.sample(110).moved());
        assertEquals(0, swipe.sample(110).offsetY());
        assertTrue(swipe.move(0, 10, 23, 120));
        assertEquals(3, swipe.sample(120).offsetY());
        assertTrue(swipe.move(0, 10, 21, 130));
        assertEquals(1, swipe.sample(130).offsetY());
        assertTrue(swipe.sample(130).moved());
    }

    @Test
    void quickReleaseAtLastPositionKeepsVelocityButAStopExpiresIt() {
        SwipeGesture swipe = new SwipeGesture();
        swipe.begin(0, 0, 0, 0);
        swipe.move(0, 0, 12, 40);
        SwipeGesture.Sample flick = swipe.release(0, 0, 12, 42);
        assertEquals(300, flick.velocityY());
        assertTrue(flick.dismissesDown(40, 110));

        swipe.begin(0, 0, 0, 100);
        swipe.move(0, 0, 12, 140);
        swipe.move(0, 0, 12, 210);
        SwipeGesture.Sample stopped = swipe.release(0, 0, 12, 241);
        assertEquals(0, stopped.velocityY());
        assertFalse(stopped.dismissesDown(40, 110));
    }

    @Test
    void releaseHonorsFinalPositionAndRejectsHorizontalMotionOrReversal() {
        SwipeGesture swipe = new SwipeGesture();
        swipe.begin(0, 0, 0, 0);
        assertTrue(swipe.release(0, 2, 40, 500).dismissesDown(40, 110));

        swipe.begin(0, 0, 0, 500);
        assertFalse(swipe.release(0, 60, 45, 550).dismissesDown(40, 110));

        swipe.begin(0, 0, 0, 550);
        swipe.move(0, 0, 70, 600);
        SwipeGesture.Sample reversed = swipe.release(0, 0, 60, 720);
        assertTrue(reversed.velocityY() < 0);
        assertFalse(reversed.dismissesDown(40, 110));
    }

    @Test
    void mismatchedOrStaleEventsCannotReleaseAndInvalidCoordinatesCancel() {
        SwipeGesture swipe = new SwipeGesture();
        assertTrue(swipe.begin(0, 10, 20, 30));
        assertFalse(swipe.move(1, 10, 200, 35));
        assertNull(swipe.release(1, 10, 200, 40));
        assertFalse(swipe.move(0, 10, 200, 29));
        assertNull(swipe.release(0, 10, 200, 29));
        assertTrue(swipe.active());
        assertNull(swipe.release(0, Double.NaN, 20, 41));
        assertFalse(swipe.active());

        assertTrue(swipe.begin(0, 10, 20, 42));
        assertTrue(swipe.move(0, 10, 80, 60));
        swipe.cancel();
        assertNull(swipe.release(0, 10, 80, 70));
        assertFalse(swipe.sample(70).moved());
    }
}
