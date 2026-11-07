package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：可选按钮涟漪的时间及点击边界。 */
class ClickRippleTest {
    @Test
    void idleAndClearedRippleAreInvisible() {
        var ripple = new ClickRipple();
        assertEquals(0f, ripple.sample(0L, false).opacity());
        assertTrue(ripple.trigger(15, 25, 10, 20, 20, 10, 0L));
        ripple.clear();
        assertEquals(0f, ripple.sample(100L, false).opacity());
    }

    @Test
    void acceptsOriginTimeZeroAndKeepsClickPositionRelativeToButton() {
        var ripple = new ClickRipple();
        assertTrue(ripple.trigger(15, 25, 10, 20, 20, 10, 0L));
        var frame = ripple.sample(100L, false);
        assertEquals(0.25f, frame.originX());
        assertEquals(0.5f, frame.originY());
        assertTrue(frame.radiusProgress() > 0f && frame.radiusProgress() < 1f);
        assertTrue(frame.opacity() > 0f && frame.opacity() <= 1f);
    }

    @Test
    void radiusExpandsAndRippleExpires() {
        var ripple = new ClickRipple();
        ripple.trigger(5, 5, 0, 0, 10, 10, 1_000L);
        float previous = 0f;
        for (long now = 1_000L; now < 1_000L + ClickRipple.DURATION_MS; now += 10L) {
            var frame = ripple.sample(now, false);
            assertTrue(frame.radiusProgress() >= previous);
            assertTrue(Float.isFinite(frame.opacity()));
            previous = frame.radiusProgress();
        }
        assertEquals(0f, ripple.sample(1_000L + ClickRipple.DURATION_MS, false).opacity());
    }

    @Test
    void invalidClicksDoNotReplaceExistingRipple() {
        var ripple = new ClickRipple();
        ripple.trigger(5, 5, 0, 0, 10, 10, 0L);
        assertFalse(ripple.trigger(10, 5, 0, 0, 10, 10, 50L));
        assertFalse(ripple.trigger(5, 5, 0, 0, 0, 10, 50L));
        assertFalse(ripple.trigger(Double.NaN, 5, 0, 0, 10, 10, 50L));
        assertFalse(ripple.trigger(5, Double.POSITIVE_INFINITY, 0, 0, 10, 10, 50L));
        assertEquals(0.5f, ripple.sample(100L, false).originX());
        assertEquals(0f, ripple.sample(ClickRipple.DURATION_MS, false).opacity());
    }

    @Test
    void repeatedClickRestartsAndInstancesAreIndependent() {
        var a = new ClickRipple();
        var b = new ClickRipple();
        a.trigger(2, 2, 0, 0, 10, 10, 0L);
        a.trigger(8, 8, 0, 0, 10, 10, 200L);
        assertEquals(0.8f, a.sample(220L, false).originX());
        assertTrue(a.sample(500L, false).opacity() > 0f);
        assertEquals(0f, b.sample(500L, false).opacity());
    }

    @Test
    void reducedMotionUsesUniformFadeAndClockRollbackIsSafe() {
        var ripple = new ClickRipple();
        ripple.trigger(5, 5, 0, 0, 10, 10, 1_000L);
        var reduced = ripple.sample(1_100L, true);
        assertTrue(reduced.uniform());
        assertEquals(1f, reduced.radiusProgress());
        assertTrue(reduced.opacity() > 0f);
        var rollback = ripple.sample(900L, false);
        assertEquals(0f, rollback.radiusProgress());
        assertTrue(Float.isFinite(rollback.opacity()));
    }
}
