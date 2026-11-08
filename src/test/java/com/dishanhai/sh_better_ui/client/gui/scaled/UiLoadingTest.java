package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：验证加载几何与进度读取，无需启动游戏。 */
class UiLoadingTest {
    @Test
    void determinateIndicatorsUseCurrentProgressAndClampBadInputs() {
        assertEquals(90.0f, UiLoading.filledWidth(120, 0.75f));
        assertEquals(30.0f, UiLoading.filledWidth(120, 0.25f));
        assertEquals(0f, UiLoading.filledWidth(120, Float.NaN));
        assertEquals(0f, UiLoading.filledWidth(120, Float.POSITIVE_INFINITY));
        assertEquals(0f, UiLoading.filledWidth(120, -0.5f));
        assertEquals(120f, UiLoading.filledWidth(120, 1.25f));
        assertEquals(new UiLoading.Arc(0f, 0.25f), UiLoading.progressArc(0.25f));
        assertEquals(new UiLoading.Arc(0f, 1f), UiLoading.progressArc(1f));
    }

    @Test
    void unknownProgressMovesWhileReducedMotionKeepsTheSameVisibleShape() {
        var start = UiLoading.spinningArc(0L, false);
        var later = UiLoading.spinningArc(250L, false);
        assertNotEquals(start.start(), later.start());
        assertEquals(start.sweep(), later.sweep());
        assertEquals(start, UiLoading.spinningArc(900L, false));
        assertTrue(UiLoading.spinningArc(Long.MIN_VALUE, false).start() >= 0f);
        assertEquals(start, UiLoading.spinningArc(250L, true));
        assertTrue(start.sweep() > 0f && start.sweep() < 1f);
        assertEquals(0f, UiLoading.topTravel(0L, false));
        assertEquals(1f, UiLoading.topTravel(800L, false));
        assertEquals(0f, UiLoading.topTravel(1600L, false));
        assertEquals(0.5f, UiLoading.topTravel(0L, true));
        assertEquals(0.5f, UiLoading.topTravel(800L, true));
    }

    @Test
    void listSpinnerStaysInsideTheRequestedEdgeEvenWhenNarrow() {
        var viewport = new Bounds(20, 30, 80, 40);
        assertEquals(new Bounds(54, 32, 12, 12), UiLoading.edgeBounds(viewport, UiLoading.Edge.TOP));
        assertEquals(new Bounds(54, 56, 12, 12), UiLoading.edgeBounds(viewport, UiLoading.Edge.BOTTOM));
        var tiny = new Bounds(10, 11, 3, 2);
        assertEquals(new Bounds(10, 11, 2, 2), UiLoading.edgeBounds(tiny, UiLoading.Edge.BOTTOM));
        var empty = new Bounds(5, 7, 10, 0);
        assertEquals(new Bounds(10, 7, 0, 0), UiLoading.edgeBounds(empty, UiLoading.Edge.TOP));
    }

    @Test
    void quarterArcStaysWithinItsRadiiAndOnlyCoversTheFirstQuadrant() {
        var ring = new UiLoading.Ring(6f, 4f, 0.5f, 24);
        var colors = new ArrayList<Integer>();
        int ink = 0xFF9ACDFF;
        ring.emitArc((x, y, color) -> {
            assertTrue(x >= -1e-5f && y <= 1e-5f);
            double distance = Math.hypot(x, y);
            assertTrue(distance >= 4f - 1e-5f && distance <= 6f + 1e-5f);
            colors.add(color);
        }, 0.0, 0.25, ink);
        assertTrue(colors.contains(ink));
        assertTrue(colors.contains(ink & 0x00FFFFFF));
        assertEquals(0, colors.size() % 4);
    }
}
