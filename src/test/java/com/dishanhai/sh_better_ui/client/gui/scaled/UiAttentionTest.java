package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：验证注意力切换、连续改目标和减少动效。 */
class UiAttentionTest {
    @Test
    void firstSelectionIsStableAndAChangedKeyRevealsNewContent() {
        var attention = new UiAttention();
        assertFalse(attention.sample(0, UiPreferences.DEFAULT).visible());
        attention.select("first", 10, 20, 80, 20, 0);
        var first = attention.sample(0, UiPreferences.DEFAULT);
        assertTrue(first.visible());
        assertEquals(10f, first.x());
        assertEquals(1f, first.contentAlpha());
        assertEquals(0f, first.contentOffsetY());

        attention.select("second", 110, 20, 100, 20, 100);
        var beginning = attention.sample(100, UiPreferences.DEFAULT);
        assertEquals(10f, beginning.x());
        assertEquals(0f, beginning.contentAlpha());
        assertEquals(6f, beginning.contentOffsetY());
        assertEquals(1f, beginning.emphasis());
        var moving = attention.sample(116, UiPreferences.DEFAULT);
        assertTrue(moving.x() > 10 && moving.x() < 110);
        assertTrue(moving.contentAlpha() > 0 && moving.contentAlpha() < 1);
        assertEquals(40, moving.contentMouseY(40 + moving.contentOffsetY()), 0.0001);
        var finished = settle(attention, 116);
        assertEquals(110f, finished.x());
        assertEquals(100f, finished.width());
        assertEquals(1f, finished.contentAlpha());
        assertEquals(0f, finished.emphasis());
    }

    @Test
    void sameKeyAndRelayoutDoNotRestartContent() {
        var attention = new UiAttention();
        attention.select("a", 0, 0, 80, 20, 0);
        attention.select("b", 100, 0, 80, 20, 100);
        var before = attention.sample(116, UiPreferences.DEFAULT);
        attention.select(new String("b"), 120, 8, 100, 24, 116);
        var after = attention.sample(116, UiPreferences.DEFAULT);
        assertEquals(before.contentAlpha(), after.contentAlpha());
        assertEquals(before.x(), after.x());
        assertEquals(120f, settle(attention, 116).x());
    }

    @Test
    void rapidReversalPreservesIndicatorPosition() {
        var attention = new UiAttention();
        attention.select("a", 0, 0, 80, 20, 0);
        attention.select("b", 100, 0, 80, 20, 100);
        var before = attention.sample(132, UiPreferences.DEFAULT);
        attention.select("a", 0, 0, 80, 20, 132);
        assertEquals(before.x(), attention.sample(132, UiPreferences.DEFAULT).x());
        assertEquals(0f, settle(attention, 132).x());
    }

    @Test
    void reducedMotionSnapsToTargetAndKeepsSelectionVisible() {
        var attention = new UiAttention();
        attention.select("a", 0, 0, 80, 20, 0);
        attention.select("b", 100, 20, 120, 24, 100);
        var frame = attention.sample(100, new UiPreferences(true, true, false));
        assertTrue(frame.visible());
        assertEquals(100f, frame.x());
        assertEquals(20f, frame.y());
        assertEquals(120f, frame.width());
        assertEquals(24f, frame.height());
        assertEquals(1f, frame.contentAlpha());
        assertEquals(0f, frame.contentOffsetY());
        assertEquals(0f, frame.emphasis());
    }

    @Test
    void clearDropsOldSelectionAndInvalidBoundsLeaveStateIntact() {
        var attention = new UiAttention();
        attention.select("a", 10, 20, 80, 20, 0);
        attention.select("b", Float.NaN, 0, 80, 20, 100);
        attention.select("b", 100, 0, -1, 20, 100);
        assertEquals(10f, attention.sample(100, UiPreferences.DEFAULT).x());
        attention.clear();
        assertFalse(attention.sample(100, UiPreferences.DEFAULT).visible());
        attention.select("b", 100, 0, 80, 20, 100);
        var frame = attention.sample(100, UiPreferences.DEFAULT);
        assertEquals(100f, frame.x());
        assertEquals(1f, frame.contentAlpha());
    }

    private UiAttention.Frame settle(UiAttention attention, long from) {
        for (long time = from + 16; time < from + 1500; time += 16) {
            attention.sample(time, UiPreferences.DEFAULT);
        }
        return attention.sample(from + 1500, UiPreferences.DEFAULT);
    }
}
