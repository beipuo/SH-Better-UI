package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：命令发生在两帧之间时，打断和延迟目标仍使用正确的时间段。 */
class SpringInterruptionTest {
    private static Spring moving() {
        Spring spring = new Spring(Spring.HOVER);
        spring.setTarget(1f, 1_000L);
        spring.update(1_020L);
        return spring;
    }

    @Test
    void retargetSamplesTheOldTrajectoryBeforeChangingDirection() {
        Spring actual = moving();
        Spring expected = moving();
        expected.update(1_040L);
        expected.setTarget(0f, 1_040L);
        actual.setTarget(0f, 1_040L);
        assertEquals(expected.value(), actual.value(), 0f);
        actual.update(1_056L);
        expected.update(1_056L);
        assertEquals(expected.value(), actual.value(), 0f);
    }

    @Test
    void waitingForADelayedTargetKeepsTheOriginalTrajectory() {
        Spring actual = moving();
        Spring expected = moving();
        actual.setTargetAfter(0f, 1_020L, 100L);
        actual.update(1_060L);
        expected.update(1_060L);
        assertEquals(expected.value(), actual.value(), 0f);
        assertEquals(1f, actual.target());
        assertFalse(actual.settled());
    }

    @Test
    void schedulingAlsoSamplesTheTimeBetweenFrames() {
        Spring actual = moving();
        Spring expected = moving();
        expected.update(1_040L);
        actual.setTargetAfter(0f, 1_040L, 100L);
        assertEquals(expected.value(), actual.value(), 0f);
    }

    @Test
    void crossingTheDeadlineMatchesExplicitlySplitUpdates() {
        Spring actual = moving();
        Spring expected = moving();
        actual.setTargetAfter(0f, 1_020L, 20L);
        actual.update(1_060L);
        expected.update(1_040L);
        expected.setTarget(0f, 1_040L);
        expected.update(1_060L);
        assertEquals(expected.value(), actual.value(), 0.000001f);
        assertEquals(0f, actual.target());
    }

    @Test
    void theDeadlineItselfDoesNotAdvanceTheNewTrajectory() {
        Spring actual = new Spring(Spring.PANEL);
        actual.setTargetAfter(1f, 1_000L, 100L);
        actual.update(1_100L);
        assertEquals(0f, actual.value(), 0f);
        assertEquals(1f, actual.target());
        actual.update(1_116L);
        assertTrue(actual.value() > 0f);
    }

    @Test
    void anOldTargetCanSettleWhileTheDelayedTargetRemainsPending() {
        Spring actual = moving();
        actual.setTargetAfter(0f, 1_020L, 2_000L);
        for (long now = 1_036L; now < 2_000L; now += 16L) actual.update(now);
        assertEquals(1f, actual.value());
        assertFalse(actual.settled());
        actual.update(3_020L);
        assertEquals(1f, actual.value());
        actual.update(3_036L);
        assertTrue(actual.value() < 1f);
    }

    @Test
    void aBackwardTimestampDoesNotMakeTheNextFrameIntegrateTwice() {
        Spring actual = moving();
        float before = actual.value();
        actual.update(1_010L);
        actual.update(1_020L);
        assertEquals(before, actual.value(), 0f);
    }

    @Test
    void immediateRetargetCancelsThePendingDestination() {
        Spring actual = moving();
        actual.setTargetAfter(-1f, 1_020L, 100L);
        actual.setTarget(2f, 1_040L);
        for (long now = 1_056L; now < 2_000L; now += 16L) actual.update(now);
        assertEquals(2f, actual.value());
        assertTrue(actual.settled());
    }
}
