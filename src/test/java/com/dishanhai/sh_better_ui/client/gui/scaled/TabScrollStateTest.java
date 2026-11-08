package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：标签视口边界、小数滚动与可取消拖动。 */
class TabScrollStateTest {
    @Test
    void wheelAmountsPreserveFractionsAndResizeClampsTheViewport() {
        var state = new TabScrollState();
        state.resize(100, 320);
        assertTrue(state.scrollBy(0.25));
        assertTrue(state.scrollBy(0.25));
        assertEquals(0.5, state.offset());
        assertFalse(state.scrollBy(Double.NaN));
        state.scrollTo(999);
        assertEquals(220, state.offset());
        state.resize(200, 250);
        assertEquals(50, state.offset());
        state.resize(300, 250);
        assertEquals(0, state.offset());
        assertEquals(0, state.maxOffset());
        assertFalse(state.beginDrag(10, 10));
    }

    @Test
    void revealMovesOnlyAsFarAsNecessaryAndAlignsOversizeTabs() {
        var state = new TabScrollState();
        state.resize(100, 400);
        state.reveal(120, 160, 100);
        assertEquals(60, state.offset());
        state.reveal(80, 100, 100);
        assertEquals(60, state.offset());
        state.reveal(20, 40, 100);
        assertEquals(20, state.offset());
        state.reveal(150, 280, 100);
        assertEquals(150, state.offset());
    }

    @Test
    void dragThresholdSuppressesJitterAndCancellationRestoresTheOriginalOffset() {
        var state = new TabScrollState();
        state.resize(100, 400);
        state.scrollTo(90);
        assertTrue(state.beginDrag(50, 10));
        assertFalse(state.drag(48, 10));
        assertEquals(90, state.offset());
        assertTrue(state.drag(20, 10));
        assertEquals(120, state.offset());
        assertTrue(state.drag(50, 10));
        assertTrue(state.dragging());
        assertTrue(state.cancelDrag());
        assertEquals(90, state.offset());
        assertFalse(state.dragging());
        assertFalse(state.cancelDrag());
    }

    @Test
    void releasingAtTheOriginAfterDraggingStillSuppressesClick() {
        var state = new TabScrollState();
        state.resize(100, 400);
        state.beginDrag(50, 10);
        state.drag(10, 10);
        assertTrue(state.endDrag(50, 10));
        assertFalse(state.dragging());
        assertFalse(state.endDrag(50, 10));
    }
}
