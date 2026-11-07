package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：验证拖曳捕获、列表跟手和缩放锚点。 */
class DirectManipulationTest {
    @Test
    void dragCapturesOnlyItsButtonAndUsesTheFinalPointerPosition() {
        var drag = new PointerDrag();
        assertTrue(drag.begin(0, 10, 20));
        assertFalse(drag.begin(1, 0, 0));
        assertNull(drag.move(1, 100, 200));
        assertNull(drag.release(1, 100, 200));
        assertTrue(drag.active());
        assertEquals(new PointerDrag.Delta(15, -8), drag.move(0, 25, 12));
        assertEquals(new PointerDrag.Delta(3, 20), drag.release(0, 28, 32));
        assertEquals(18, drag.offsetX());
        assertEquals(12, drag.offsetY());
        assertFalse(drag.active());
        assertNull(drag.release(0, 28, 32));
    }

    @Test
    void cancelClearsPreviewAndReleasesCapture() {
        var drag = new PointerDrag();
        assertTrue(drag.begin(0, 10, 20));
        drag.move(0, 50, 30);
        drag.cancel();
        assertFalse(drag.active());
        assertEquals(0, drag.offsetX());
        assertEquals(0, drag.offsetY());
        assertNull(drag.release(0, 50, 30));
        assertTrue(drag.begin(0, 50, 30));
        assertNull(drag.release(0, Double.NaN, 50));
        assertFalse(drag.active());
    }

    @Test
    void listStopsAtBothEdgesAndMovesImmediatelyWhenDirectionReverses() {
        double offset = ScrollPageTurn.dragOffset(30, -100, 80);
        assertEquals(80, offset);
        offset = ScrollPageTurn.dragOffset(offset, 3, 80);
        assertEquals(77, offset);
        offset = ScrollPageTurn.dragOffset(offset, 100, 80);
        assertEquals(0, offset);
        assertEquals(2, ScrollPageTurn.dragOffset(offset, -2, 80));
        assertEquals(0, ScrollPageTurn.dragOffset(0, -2, 0));
    }

    @Test
    void zoomClampsButKeepsTheContentUnderThePointer() {
        var viewport = new ViewportTransform(16, -9, 1.25);
        double x = viewport.contentX(53);
        double y = viewport.contentY(27);
        var enlarged = viewport.zoomAt(53, 27, 2, 0.5, 2);
        assertEquals(2, enlarged.scale());
        assertEquals(x, enlarged.contentX(53), 1e-9);
        assertEquals(y, enlarged.contentY(27), 1e-9);
        assertSame(enlarged, enlarged.zoomAt(53, 27, 2, 0.5, 2));
        var reduced = enlarged.zoomAt(53, 27, 0.1, 0.5, 2);
        assertEquals(0.5, reduced.scale());
        assertEquals(x, reduced.contentX(53), 1e-9);
        assertEquals(y, reduced.contentY(27), 1e-9);
        assertSame(reduced, reduced.zoomAt(53, 27, Double.NaN, 0.5, 2));
    }
}
