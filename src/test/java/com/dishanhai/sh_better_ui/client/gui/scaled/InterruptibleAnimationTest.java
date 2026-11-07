package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：可打断动画的消费端行为，不依赖 Minecraft、渲染线程或第三方库。 */
class InterruptibleAnimationTest {
    private static InterruptibleAnimation opening() {
        InterruptibleAnimation animation = new InterruptibleAnimation(Spring.PANEL);
        animation.setOpen(true, 1_000L);
        animation.sample(1_032L);
        return animation;
    }

    private static void settle(InterruptibleAnimation animation, long from) {
        for (long now = from; now < from + 1_000L; now += 16L) animation.sample(now);
        assertFalse(animation.running());
    }

    @Test
    void startsAtTheSpecifiedValueWithoutAnImplicitAnimation() {
        InterruptibleAnimation animation = new InterruptibleAnimation(Spring.PANEL, 0.4f);
        assertEquals(0.4f, animation.value());
        assertEquals(0.4f, animation.target());
        assertEquals(0f, animation.velocity());
        assertFalse(animation.running());
    }

    @Test
    void closingAndReopeningPreserveBothPositionAndVelocity() {
        InterruptibleAnimation animation = opening();
        float value = animation.value();
        float velocity = animation.velocity();
        animation.setOpen(false, 1_032L);
        assertEquals(value, animation.value(), 0f);
        assertEquals(velocity, animation.velocity(), 0f);
        assertEquals(0f, animation.target());
        animation.sample(1_048L);
        value = animation.value();
        velocity = animation.velocity();
        animation.setOpen(true, 1_048L);
        assertEquals(value, animation.value(), 0f);
        assertEquals(velocity, animation.velocity(), 0f);
        settle(animation, 1_064L);
        assertEquals(1f, animation.value());
    }

    @Test
    void changingTargetsBetweenFramesSamplesTheOldMotionFirst() {
        InterruptibleAnimation actual = opening();
        InterruptibleAnimation expected = opening();
        expected.sample(1_048L);
        expected.animateTo(-20f, 1_048L);
        actual.animateTo(-20f, 1_048L);
        assertEquals(expected.value(), actual.value(), 0f);
        assertEquals(expected.velocity(), actual.velocity(), 0f);
        settle(actual, 1_064L);
        assertEquals(-20f, actual.value());
    }

    @Test
    void resendingTheSameTargetDoesNotRestartTheAnimation() {
        InterruptibleAnimation actual = opening();
        InterruptibleAnimation expected = opening();
        for (long now = 1_048L; now < 2_000L; now += 16L) {
            actual.animateTo(1f, now);
            assertEquals(expected.sample(now), actual.sample(now), 0f);
        }
        assertFalse(actual.running());
    }

    @Test
    void toggleUsesTheDestinationEvenWhenTheValueStillMovesTheOtherWay() {
        InterruptibleAnimation animation = opening();
        animation.toggle(1_032L);
        assertEquals(0f, animation.target());
        animation.toggle(1_032L);
        assertEquals(1f, animation.target());
        animation.animateToAfter(0f, 1_032L, 100L);
        animation.toggle(1_032L);
        assertEquals(1f, animation.target());
        settle(animation, 1_048L);
        assertEquals(1f, animation.value());
    }

    @Test
    void stopSamplesTheCommandTimeAndCancelsThePendingTarget() {
        InterruptibleAnimation actual = opening();
        InterruptibleAnimation expected = opening();
        actual.animateToAfter(-1f, 1_032L, 100L);
        float stopped = expected.sample(1_048L);
        actual.stop(1_048L);
        assertEquals(stopped, actual.value(), 0f);
        assertEquals(stopped, actual.target(), 0f);
        assertEquals(0f, actual.velocity());
        assertFalse(actual.running());
        assertEquals(stopped, actual.sample(3_000L), 0f);
    }

    @Test
    void finishReachesThePendingDestinationImmediately() {
        InterruptibleAnimation animation = opening();
        animation.animateToAfter(42f, 1_032L, 100L);
        assertEquals(42f, animation.target());
        animation.finish();
        assertEquals(42f, animation.value());
        assertEquals(0f, animation.velocity());
        assertFalse(animation.running());
        assertEquals(42f, animation.sample(2_000L));
    }

    @Test
    void reducedMotionSkipsBothMovementAndDelayAndCanBeDisabledAgain() {
        InterruptibleAnimation animation = opening();
        animation.animateToAfter(100f, 1_032L, 200L);
        assertEquals(100f, animation.sample(1_040L, true));
        assertFalse(animation.running());
        animation.animateTo(200f, 1_050L);
        float value = animation.sample(1_066L, false);
        assertTrue(value > 100f && value < 200f);
        assertTrue(animation.running());
    }

    @Test
    void immediateRetargetSupersedesADelayedDestination() {
        InterruptibleAnimation animation = opening();
        animation.animateToAfter(-10f, 1_032L, 100L);
        animation.animateTo(20f, 1_048L);
        settle(animation, 1_064L);
        assertEquals(20f, animation.value());
    }

    @Test
    void delayedRetargetKeepsMovingUntilItsDeadline() {
        InterruptibleAnimation actual = opening();
        InterruptibleAnimation expected = opening();
        actual.animateToAfter(-10f, 1_032L, 100L);
        assertEquals(expected.sample(1_064L), actual.sample(1_064L), 0f);
        assertEquals(-10f, actual.target());
        settle(actual, 1_080L);
        assertEquals(-10f, actual.value());
    }

    @Test
    void snapCancelsBothMotionAndDelayedCommands() {
        InterruptibleAnimation animation = opening();
        animation.animateToAfter(50f, 1_032L, 100L);
        animation.snapTo(7f);
        assertEquals(7f, animation.sample(2_000L));
        assertEquals(7f, animation.target());
        assertFalse(animation.running());
    }

    @Test
    void separateControlsNeverShareMotionState() {
        InterruptibleAnimation first = opening();
        InterruptibleAnimation second = new InterruptibleAnimation(Spring.PANEL);
        first.animateTo(10f, 1_032L);
        first.sample(1_048L);
        assertEquals(0f, second.sample(1_048L));
        assertFalse(second.running());
    }

    @Test
    void invalidTargetsDoNotCancelAnExistingDelayedCommand() {
        InterruptibleAnimation animation = opening();
        animation.animateToAfter(10f, 1_032L, 100L);
        animation.animateTo(Float.NaN, 1_048L);
        animation.animateToAfter(Float.POSITIVE_INFINITY, 1_048L, 10L);
        animation.snapTo(Float.NEGATIVE_INFINITY);
        assertEquals(10f, animation.target());
        settle(animation, 1_064L);
        assertEquals(10f, animation.value());
    }

    @Test
    void aNonPositiveDelayChangesTheTargetImmediately() {
        InterruptibleAnimation animation = opening();
        animation.animateToAfter(-1f, 1_032L, 0L);
        settle(animation, 1_048L);
        assertEquals(-1f, animation.value());
    }
}
