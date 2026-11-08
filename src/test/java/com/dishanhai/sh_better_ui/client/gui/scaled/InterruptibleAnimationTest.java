package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import static com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation.Policy.*;
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

    @Test
    void aLongFrameCatchesUpWithoutReplayingTheOpening() {
        var animation = new InterruptibleAnimation(Spring.PANEL);
        animation.setOpen(true, 1_000L);
        assertEquals(1f, animation.sample(2_000L), 0.01f);
    }

    @Test
    void reversalCanInterruptInBothDirectionsWithoutJumping() {
        var animation = opening();
        float openValue = animation.value();
        float openSpeed = animation.velocity();
        long close = animation.transitionTo(0f, 1_032L, REVERSE, 30);
        assertEquals(openValue, animation.value());
        assertEquals(-openSpeed, animation.velocity());
        float closingValue = animation.sample(1_042L);
        assertTrue(closingValue < openValue);

        long reopen = animation.transitionTo(1f, 1_042L, REVERSE, 30);
        assertEquals(closingValue, animation.value());
        assertTrue(animation.velocity() > 0f);
        settle(animation, 1_050L);
        assertFalse(animation.takeCompletion(close));
        assertTrue(animation.takeCompletion(reopen));
        assertFalse(animation.takeCompletion(reopen));
    }

    @Test
    void aRetargetAtCompletionInvalidatesAnUnclaimedEvent() {
        var animation = opening();
        long closing = animation.transitionTo(0f, 1_032L, REVERSE, 30);
        settle(animation, 1_064L);
        long reopen = animation.transitionTo(1f, 2_064L, IMMEDIATE, 30);
        assertFalse(animation.takeCompletion(closing));
        assertTrue(animation.takeCompletion(reopen));
    }

    @Test
    void prioritiesOnlyConflictWithinTheSameProperty() {
        var opacity = new InterruptibleAnimation(Spring.HOVER);
        var offset = new InterruptibleAnimation(Spring.SCROLL);
        long open = opacity.transitionTo(1f, 100L, RETARGET, 50);
        assertEquals(0L, opacity.transitionTo(0f, 110L, RETARGET, 10));
        assertEquals(0L, opacity.transitionTo(0f, 110L, IGNORE, 50));
        assertNotEquals(0L, offset.transitionTo(80f, 110L, RETARGET, 10));
        long close = opacity.transitionTo(0f, 112L, IGNORE, 51);
        settle(opacity, 112L);
        settle(offset, 112L);
        assertFalse(opacity.takeCompletion(open));
        assertTrue(opacity.takeCompletion(close));
        assertEquals(80f, offset.value());
        assertNotEquals(0L, opacity.transitionTo(1f, 1_200L, IGNORE, 10));
    }

    @Test
    void rapidHoverUpdatesKeepOnlyTheNewestTarget() {
        var hover = new InterruptibleAnimation(Spring.HOVER);
        long command = 0L;
        for (int event = 0; event < 500; event++) {
            command = hover.transitionTo(event % 2, event, RETARGET, 10);
        }
        assertEquals(1f, hover.target());
        settle(hover, 500L);
        assertTrue(hover.takeCompletion(command));
    }

    @Test
    void blendingStartsFromTheLiveValueAndCanBeRetargeted() {
        var animation = opening();
        float before = animation.value();
        long blend = animation.blendTo(0f, 1_032L, 20, 100L, InterruptibleAnimation.BlendCurve.LINEAR);
        assertEquals(before, animation.sample(1_032L));
        assertEquals(before / 2f, animation.sample(1_082L), 0.00001f);
        float velocity = animation.velocity();
        animation.transitionTo(1f, 1_082L, RETARGET, 20);
        assertEquals(before / 2f, animation.value(), 0.00001f);
        assertEquals(velocity, animation.velocity());
        settle(animation, 1_100L);
        assertFalse(animation.takeCompletion(blend));
    }

    @Test
    void repeatingAnIdenticalBlendKeepsItsOriginalDeadline() {
        var animation = new InterruptibleAnimation(Spring.PANEL);
        long first = animation.transitionTo(1f, 100L, BLEND, 30);
        float before = animation.sample(200L);
        long latest = animation.transitionTo(1f, 200L, BLEND, 30);
        assertEquals(before, animation.value());
        assertEquals(1f, animation.sample(280L));
        assertFalse(animation.takeCompletion(first));
        assertTrue(animation.takeCompletion(latest));
    }

    @Test
    void queueIsBoundedAndPlaysInSubmissionOrder() {
        var animation = new InterruptibleAnimation(Spring.HOVER);
        animation.animateTo(1f, 1_000L);
        long last = 0L;
        for (int phase = 2; phase <= 9; phase++) {
            last = animation.transitionTo(phase, 1_032L, QUEUE, 20);
            assertNotEquals(0L, last);
        }
        assertEquals(0L, animation.transitionTo(10f, 1_032L, QUEUE, 20));
        assertEquals(9f, animation.target());
        long now = 1_032L;
        for (int phase = 1; phase <= 9; phase++) {
            now += 250L;
            assertEquals((float) phase, animation.sample(now));
        }
        assertTrue(animation.takeCompletion(last));
    }

    @Test
    void finishThenSwitchCoalescesAndReducedMotionSkipsTheRemainingStages() {
        var animation = opening();
        long intermediate = animation.transitionTo(2f, 1_032L, FINISH_THEN_SWITCH, 30);
        long last = animation.transitionTo(3f, 1_032L, FINISH_THEN_SWITCH, 30);
        assertEquals(1f, animation.sample(1_282L));
        assertTrue(animation.running());
        assertEquals(3f, animation.sample(1_283L, true));
        assertFalse(animation.takeCompletion(intermediate));
        assertTrue(animation.takeCompletion(last));
    }

    @Test
    void closingATrackCancelsQueuedMotionAndCompletion() {
        var animation = opening();
        long command = animation.transitionTo(2f, 1_032L, QUEUE, 30);
        float before = animation.value();
        animation.close();
        assertFalse(animation.running());
        assertEquals(0L, animation.transitionTo(1f, 2_000L, IMMEDIATE, 100));
        assertEquals(before, animation.sample(2_000L, true));
        assertFalse(animation.takeCompletion(command));
    }

    @Test
    void twentyAnd144FpsAdvanceInComparableWallTime() {
        var slow = new InterruptibleAnimation(Spring.PANEL);
        var fast = new InterruptibleAnimation(Spring.PANEL);
        slow.setOpen(true, 1_000L);
        fast.setOpen(true, 1_000L);
        long fastFrame = 1L;
        for (long elapsed = 50L; elapsed <= 500L; elapsed += 50L) {
            while (Math.round(fastFrame * 1000d / 144d) < elapsed) {
                fast.sample(1_000L + Math.round(fastFrame++ * 1000d / 144d));
            }
            assertEquals(fast.sample(1_000L + elapsed), slow.sample(1_000L + elapsed), 0.02f);
        }
        assertFalse(slow.running());
        assertFalse(fast.running());
    }
}
