package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：用可控时间检验手势、回弹和视口几何。 */
class UiBottomSheetTest {
    private static final Bounds VIEWPORT = new Bounds(10, 20, 300, 400);
    private static final UiPreferences REDUCED_MOTION = new UiPreferences(false, true, false);

    @Test
    void usesTheDrawnHandleAndCapturesUntilTheFinalReleaseCoordinate() {
        UiBottomSheet sheet = sheet(1);
        var frame = sample(sheet, 0);
        assertEquals(new Bounds(10, 320, 300, 100), frame.panel());
        assertEquals(420, frame.content().bottom());
        Bounds handle = frame.handle();
        assertFalse(sheet.begin(frame, 0, handle.right(), handle.y(), 0));
        assertFalse(sheet.begin(frame, 1, handle.x(), handle.y(), 0));
        assertTrue(sheet.begin(frame, 0, handle.x(), handle.y(), 0));

        assertTrue(sheet.drag(0, -20, handle.y() - 35, 200));
        assertEquals(135, sample(sheet, 200).panel().height());
        assertFalse(sheet.release(1, -20, handle.y() - 55, 400));
        assertTrue(sheet.release(0, -20, handle.y() - 55, 400));
        assertEquals(2, sheet.targetAnchor());
        assertEquals(155, sample(sheet, 400).panel().height());
    }

    @Test
    void identicalReleasePositionsCanSnapToDifferentAnchors() {
        UiBottomSheet slow = sheet(1);
        UiBottomSheet fast = sheet(1);
        double y = begin(slow, 0);
        begin(fast, 0);
        slow.drag(0, 0, y - 20, 160);
        fast.drag(0, 0, y - 20, 40);
        var slowPreview = sample(slow, 162);
        var fastPreview = sample(fast, 42);
        assertEquals(slowPreview.panel(), fastPreview.panel());
        assertEquals(1, slowPreview.targetAnchor());
        assertEquals(2, fastPreview.targetAnchor());

        slow.release(0, 0, y - 20, 162);
        fast.release(0, 0, y - 20, 42);
        assertEquals(slowPreview.targetAnchor(), slow.targetAnchor());
        assertEquals(fastPreview.targetAnchor(), fast.targetAnchor());
    }

    @Test
    void equalMotionAtDifferentMousePollingRatesChoosesTheSameAnchor() {
        UiBottomSheet dense = sheet(1);
        UiBottomSheet sparse = sheet(1);
        double y = begin(dense, 0);
        begin(sparse, 0);
        for (int ms = 1; ms <= 32; ms++) dense.drag(0, 0, y - ms * 0.5, ms);
        for (int ms = 8; ms <= 32; ms += 8) sparse.drag(0, 0, y - ms * 0.5, ms);
        dense.release(0, 0, y - 16, 32);
        sparse.release(0, 0, y - 16, 32);
        assertEquals(2, sparse.targetAnchor());
        assertEquals(sparse.targetAnchor(), dense.targetAnchor());
    }

    @Test
    void aPauseClearsMomentumAndReversalFollowsTheNewDirection() {
        UiBottomSheet paused = sheet(2);
        UiBottomSheet reversed = sheet(2);
        double y = begin(paused, 0);
        begin(reversed, 0);
        paused.drag(0, 0, y - 30, 30);
        reversed.drag(0, 0, y - 30, 30);
        assertEquals(3, sample(paused, 30).targetAnchor());
        assertEquals(2, sample(paused, 160).targetAnchor());
        paused.release(0, 0, y - 30, 160);
        reversed.release(0, 0, y - 5, 50);
        assertEquals(2, paused.targetAnchor());
        assertEquals(0, reversed.targetAnchor());
    }

    @Test
    void releaseMomentumContinuesThroughTheFirstSpringFrame() {
        UiBottomSheet still = sheet(1);
        UiBottomSheet moving = sheet(1);
        double y = begin(still, 0);
        begin(moving, 0);
        still.release(0, 0, y - 12, 160);
        moving.release(0, 0, y - 12, 70);
        assertEquals(1, still.targetAnchor());
        assertEquals(1, moving.targetAnchor());
        assertEquals(112, sample(moving, 70).panel().height());
        assertTrue(sample(moving, 78).panel().height() > sample(still, 168).panel().height());
    }

    @Test
    void extraTravelIsResistedAndReboundsBeforeSettlingExactly() {
        UiBottomSheet sheet = sheet(3);
        double y = begin(sheet, 0);
        sheet.drag(0, 0, y - 30, 100);
        int first = sample(sheet, 100).panel().height();
        sheet.drag(0, 0, y - 60, 200);
        int second = sample(sheet, 200).panel().height();
        assertTrue(first > 300 && first < 330);
        assertTrue(second > first && second - first < first - 300);
        assertTrue(second < 324);
        assertTrue(sheet.release(0, 0, y - 60, 320));

        boolean rebounded = false;
        for (long time = 336; time <= 960; time += 16) {
            rebounded |= sample(sheet, time).panel().height() < 300;
        }
        var end = sample(sheet, 960);
        assertTrue(rebounded);
        assertEquals(300, end.panel().height());
        assertFalse(end.settling());
    }

    @Test
    void regrabbingASettlingSheetPreservesPositionAndCancelRestoresThePriorTarget() {
        UiBottomSheet sheet = sheet(1);
        sheet.animateTo(3, 0);
        var moving = sample(sheet, 32);
        assertTrue(moving.settling());
        double y = moving.handle().y() + 6;
        assertTrue(sheet.begin(moving, 0, moving.handle().x(), y, 72));
        assertEquals(moving.panel(), sample(sheet, 72).panel());

        sheet.drag(0, 0, y - 10, 92);
        var dragging = sample(sheet, 92);
        assertEquals(moving.panel().height() + 10, dragging.panel().height());
        sheet.cancelGesture(92);
        assertEquals(3, sheet.targetAnchor());
        assertEquals(dragging.panel(), sample(sheet, 92).panel());
        assertFalse(sheet.release(0, 0, y - 10, 94));
    }

    @Test
    void changingViewportHeightCancelsTheDragAndResizesTheAnchors() {
        UiBottomSheet sheet = sheet(1);
        double y = begin(sheet, 0);
        sheet.drag(0, 0, y - 20, 20);
        Bounds resized = new Bounds(0, 0, 200, 280);
        var frame = sheet.sample(resized, 30, REDUCED_MOTION);
        assertFalse(frame.dragging());
        assertEquals(70, frame.panel().height());
        assertFalse(sheet.release(0, 0, y - 20, 40));
    }

    @Test
    void reducedMotionKeepsDirectDragButSkipsStretchAndSettling() {
        UiBottomSheet sheet = sheet(2);
        var initial = sheet.sample(VIEWPORT, 0, REDUCED_MOTION);
        double y = initial.handle().y() + 6;
        assertTrue(sheet.begin(initial, 0, initial.handle().x(), y, 0));
        sheet.drag(0, 0, y - 35, 100);
        assertEquals(235, sheet.sample(VIEWPORT, 100, REDUCED_MOTION).panel().height());
        sheet.drag(0, 0, y - 130, 200);
        var stretched = sheet.sample(VIEWPORT, 200, REDUCED_MOTION);
        assertTrue(stretched.dragging());
        assertEquals(300, stretched.panel().height());
        sheet.drag(0, 0, y - 120, 220);
        assertEquals(290, sheet.sample(VIEWPORT, 220, REDUCED_MOTION).panel().height());

        sheet.release(0, 0, y - 120, 340);
        assertEquals(3, sheet.targetAnchor());
        assertFalse(sheet.sample(VIEWPORT, 340, REDUCED_MOTION).settling());
        sheet.animateTo(0, 400);
        var hidden = sheet.sample(VIEWPORT, 400, REDUCED_MOTION);
        assertEquals(0, hidden.panel().height());
        assertFalse(sheet.begin(hidden, 0, 150, VIEWPORT.bottom() - 1, 400));
    }

    @Test
    void invalidReleaseCoordinatesCancelWithoutChangingTheAnchor() {
        UiBottomSheet sheet = sheet(1);
        double y = begin(sheet, 0);
        sheet.drag(0, 0, y - 150, 30);
        assertTrue(sheet.release(0, 0, Double.NaN, 40));
        assertEquals(1, sheet.targetAnchor());
        assertFalse(sample(sheet, 40).dragging());
    }

    @Test
    void rejectsUnorderedOrInvalidAnchorsAndCopiesTheInput() {
        assertThrows(IllegalArgumentException.class, () -> new UiBottomSheet(0));
        assertThrows(IllegalArgumentException.class, () -> new UiBottomSheet(1, 0f));
        assertThrows(IllegalArgumentException.class, () -> new UiBottomSheet(0, 0.5f, 0.5f));
        assertThrows(IllegalArgumentException.class, () -> new UiBottomSheet(0, 1.2f));
        assertThrows(IllegalArgumentException.class, () -> new UiBottomSheet(0, Float.NaN));

        float[] anchors = {0f, 0.5f};
        UiBottomSheet sheet = new UiBottomSheet(1, anchors);
        anchors[1] = 1f;
        sheet.snapTo(1);
        assertEquals(200, sample(sheet, 0).panel().height());
    }

    private static UiBottomSheet sheet(int anchor) {
        return new UiBottomSheet(anchor, 0f, 0.25f, 0.5f, 0.75f);
    }

    private static UiBottomSheet.Frame sample(UiBottomSheet sheet, long nowMs) {
        return sheet.sample(VIEWPORT, nowMs, UiPreferences.DEFAULT);
    }

    private static double begin(UiBottomSheet sheet, long nowMs) {
        var frame = sample(sheet, nowMs);
        double y = frame.handle().y() + frame.handle().height() / 2.0;
        assertTrue(sheet.begin(frame, 0, frame.handle().x(), y, nowMs));
        return y;
    }
}
