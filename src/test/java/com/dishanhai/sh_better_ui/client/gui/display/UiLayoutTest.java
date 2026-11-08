package com.dishanhai.sh_better_ui.client.gui.display;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import static com.dishanhai.sh_better_ui.client.gui.display.UiLayout.Horizontal;
import static com.dishanhai.sh_better_ui.client.gui.display.UiLayout.Size;
import static com.dishanhai.sh_better_ui.client.gui.display.UiLayout.Vertical;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：验证对齐锚点、排列顺序与控件命中边界。 */
class UiLayoutTest {
    @Test
    void alignsSingleControlOnBothAxes() {
        var area = new Bounds(7, -9, 41, 29);
        var size = new Size(12, 8);

        assertEquals(new Bounds(7, -9, 12, 8), UiLayout.align(area, size, Horizontal.LEFT, Vertical.TOP));
        assertEquals(new Bounds(21, 1, 12, 8), UiLayout.align(area, size, Horizontal.CENTER, Vertical.CENTER));
        assertEquals(new Bounds(36, 12, 12, 8), UiLayout.align(area, size, Horizontal.RIGHT, Vertical.BOTTOM));
    }

    @Test
    void preservesOversizedControlAndRoundsOddCenterTowardTopLeft() {
        var area = new Bounds(-9, 4, 8, 6);
        var size = new Size(11, 11);

        assertEquals(new Bounds(-11, 1, 11, 11), UiLayout.align(area, size, Horizontal.CENTER, Vertical.CENTER));
        assertEquals(new Bounds(-12, -1, 11, 11), UiLayout.align(area, size, Horizontal.RIGHT, Vertical.BOTTOM));
    }

    @Test
    void rowAnchorsTheGroupAndAlignsEachControlToTheContainerBottom() {
        var area = new Bounds(10, -4, 51, 23);
        var sizes = List.of(new Size(7, 8), new Size(13, 4), new Size(6, 11));

        var row = UiLayout.row(area, sizes, 3, Horizontal.RIGHT, Vertical.BOTTOM);

        assertEquals(List.of(new Bounds(29, 11, 7, 8), new Bounds(39, 15, 13, 4), new Bounds(55, 8, 6, 11)), row);
        assertTrue(row.get(0).contains(29, 11));
        assertTrue(row.get(0).contains(35.9, 18.9));
        assertFalse(row.stream().anyMatch(bounds -> bounds.contains(36, 16)));
        assertFalse(row.get(0).contains(29, 19));
    }

    @Test
    void centersAnOversizedRowWithoutChangingControlSizesOrGap() {
        var area = new Bounds(-10, -6, 12, 9);
        var sizes = List.of(new Size(6, 10), new Size(8, 5));

        var row = UiLayout.row(area, sizes, 1, Horizontal.CENTER, Vertical.CENTER);

        assertEquals(List.of(new Bounds(-12, -7, 6, 10), new Bounds(-5, -4, 8, 5)), row);
    }

    @Test
    void columnAnchorsTheGroupAndCentersEachControlWithinTheContainer() {
        var area = new Bounds(-8, 10, 29, 41);
        var sizes = List.of(new Size(6, 5), new Size(12, 9), new Size(8, 3));

        var column = UiLayout.column(area, sizes, 4, Horizontal.CENTER, Vertical.BOTTOM);

        assertEquals(List.of(new Bounds(3, 26, 6, 5), new Bounds(0, 35, 12, 9), new Bounds(2, 48, 8, 3)), column);
    }

    @Test
    void acceptsEmptyAndZeroSizedInputs() {
        var area = new Bounds(5, 7, 0, 0);
        var sizes = List.of(new Size(0, 0), new Size(1, 2));

        assertTrue(UiLayout.row(area, List.of(), 2, Horizontal.RIGHT, Vertical.BOTTOM).isEmpty());
        assertTrue(UiLayout.column(area, List.of(), 2, Horizontal.RIGHT, Vertical.BOTTOM).isEmpty());
        var column = UiLayout.column(area, sizes, 0, Horizontal.LEFT, Vertical.TOP);
        assertEquals(List.of(area, new Bounds(5, 7, 1, 2)), column);
        assertFalse(column.get(0).contains(5, 7));
        assertTrue(column.get(1).contains(5, 7));
    }

    @Test
    void rejectsNegativeSizesAndGaps() {
        var area = new Bounds(0, 0, 10, 10);

        assertThrows(IllegalArgumentException.class, () -> new Size(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Size(1, -1));
        assertThrows(IllegalArgumentException.class,
                () -> UiLayout.row(area, List.of(), -1, Horizontal.LEFT, Vertical.TOP));
        assertThrows(IllegalArgumentException.class,
                () -> UiLayout.column(area, List.of(), -1, Horizontal.LEFT, Vertical.TOP));
    }

    @Test
    void keepsLargeGroupTotalsWhenIndividualBoundsFit() {
        var area = new Bounds(0, 0, 0, 0);
        int max = Integer.MAX_VALUE;
        var wide = new Size(max, 1);
        var tall = new Size(1, max);

        assertEquals(List.of(new Bounds(-max, 0, max, 1), new Bounds(0, 0, max, 1)),
                UiLayout.row(area, List.of(wide, wide), 0, Horizontal.CENTER, Vertical.TOP));
        assertEquals(List.of(new Bounds(0, -max, 1, max), new Bounds(0, 0, 1, max)),
                UiLayout.column(area, List.of(tall, tall), 0, Horizontal.LEFT, Vertical.CENTER));
    }

    @Test
    void rejectsUnrepresentableOriginsAndEdges() {
        var size = new Size(1, 1);
        var right = new Bounds(Integer.MAX_VALUE, 0, 0, 0);
        var top = new Bounds(0, Integer.MIN_VALUE, 0, 0);

        assertThrows(ArithmeticException.class, () -> UiLayout.align(right, size, Horizontal.LEFT, Vertical.TOP));
        assertThrows(ArithmeticException.class, () -> UiLayout.align(top, size, Horizontal.LEFT, Vertical.BOTTOM));
    }

    @Test
    void returnsImmutableLayoutResults() {
        var area = new Bounds(0, 0, 10, 10);
        var sizes = List.of(new Size(4, 4));

        var row = UiLayout.row(area, sizes, 0, Horizontal.LEFT, Vertical.TOP);
        var column = UiLayout.column(area, sizes, 0, Horizontal.LEFT, Vertical.TOP);

        assertThrows(UnsupportedOperationException.class, () -> row.add(area));
        assertThrows(UnsupportedOperationException.class, () -> column.set(0, area));
    }
}
