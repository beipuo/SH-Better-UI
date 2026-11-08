package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiSwipeToast.CloseReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：检查通知倒计时、退出和收合之间的约定。 */
class SwipeToastTimelineTest {
    @Test
    void timerStartsWithFirstLayoutAndResumesAfterPause() {
        var toast = new SwipeToastTimeline(100, 400, false, 0);
        assertNull(toast.advance(500, false, true));
        assertTrue(toast.isOpen());
        toast.layoutReady(500);
        assertNull(toast.advance(500, false, true));
        assertNull(toast.advance(560, true, true));
        assertEquals(0.4f, toast.fuse(), 0.001f);
        assertNull(toast.advance(2_000, false, true));
        assertEquals(CloseReason.TIMEOUT, toast.advance(2_040, false, true));
        assertNull(toast.advance(2_080, false, true));
    }

    @Test
    void inlineCloseReportsAfterSlotCollapses() {
        var toast = new SwipeToastTimeline(0, 400, true, 0);
        toast.layoutReady(0);
        toast.advance(400, false, false);
        assertTrue(toast.close(CloseReason.SWIPE, 400));
        assertFalse(toast.close(CloseReason.PROGRAMMATIC, 401));
        assertNull(toast.advance(680, false, false));
        assertEquals(0f, toast.reveal());
        assertEquals(1f, toast.slot());
        assertNull(toast.advance(780, false, false));
        assertTrue(toast.slot() > 0f && toast.slot() < 1f);
        assertEquals(CloseReason.SWIPE, toast.advance(880, false, false));
        assertEquals(0f, toast.slot());
    }

    @Test
    void reopeningInterruptsExitAndDiscardsItsCallback() {
        var toast = new SwipeToastTimeline(1_000, 400, true, 0);
        toast.layoutReady(0);
        toast.advance(400, false, false);
        toast.close(CloseReason.PROGRAMMATIC, 400);
        toast.advance(500, false, false);
        float inFlight = toast.reveal();
        assertTrue(inFlight > 0f && inFlight < 1f);
        toast.open(500);
        assertNull(toast.advance(500, false, false));
        assertEquals(inFlight, toast.reveal());
        assertEquals(1f, toast.fuse());
        assertNull(toast.advance(1_000, false, false));
        assertEquals(1f, toast.slot());
        toast.close(CloseReason.ACTION, 1_001);
        assertEquals(CloseReason.ACTION, toast.advance(1_001, false, true));
    }
}
