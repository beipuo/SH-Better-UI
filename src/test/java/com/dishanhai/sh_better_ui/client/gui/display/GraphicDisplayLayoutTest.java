package com.dishanhai.sh_better_ui.client.gui.display;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import static com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Grid;
import static com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Result;
import static org.junit.jupiter.api.Assertions.*;

/** 山海：验证图形化显示的重排、裁切和命中边界。 */
class GraphicDisplayLayoutTest {
    @Test
    void switchesToTwoColumnsAt240GuiUnits() {
        var narrow = GraphicDisplayLayout.arrange(new Bounds(3, 5, 239, 56), 3, 24, false);
        var wide = GraphicDisplayLayout.arrange(new Bounds(3, 5, 240, 56), 3, 24, false);

        assertEquals(List.of(new Bounds(3, 5, 239, 24), new Bounds(3, 33, 239, 24)), narrow.cells());
        assertEquals(List.of(new Bounds(3, 5, 118, 24), new Bounds(125, 5, 118, 24),
                new Bounds(3, 33, 118, 24)), wide.cells());
    }

    @Test
    void customGridFitsColumnsAtItsThresholdAndKeepsThemOnShortPages() {
        var grid = new Grid(40, 3);
        var twoColumns = GraphicDisplayLayout.arrange(new Bounds(0, 0, 127, 52), 3, 24, false, grid);
        var threeColumns = GraphicDisplayLayout.arrange(new Bounds(0, 0, 128, 52), 3, 24, false, grid);
        var oneEntry = GraphicDisplayLayout.arrange(new Bounds(0, 0, 180, 52), 1, 24, false, grid);

        assertEquals(new Bounds(0, 28, 61, 24), twoColumns.cells().get(2));
        assertEquals(new Bounds(88, 0, 40, 24), threeColumns.cells().get(2));
        assertEquals(List.of(new Bounds(0, 0, 57, 24)), oneEntry.cells());
    }

    @Test
    void customGridAssignsRemainderToRightmostColumns() {
        var layout = GraphicDisplayLayout.arrange(new Bounds(-3, 5, 130, 24), 3, 24, false, new Grid(40, 3));

        assertEquals(List.of(new Bounds(-3, 5, 40, 24), new Bounds(41, 5, 41, 24),
                new Bounds(86, 5, 41, 24)), layout.cells());
        assertEquals(-1, layout.hitTest(82, 5));
        assertEquals(2, layout.hitTest(86, 5));
        assertEquals(-1, layout.hitTest(127, 5));
    }

    @Test
    void oddWidthFillsTheRightEdgeAndGapsAreNotClickable() {
        var layout = GraphicDisplayLayout.arrange(new Bounds(10, 20, 241, 56), 4, 24, false);

        assertEquals(new Bounds(132, 20, 119, 24), layout.cells().get(1));
        assertEquals(0, layout.hitTest(127.9, 20));
        assertEquals(-1, layout.hitTest(128, 20));
        assertEquals(-1, layout.hitTest(131.9, 20));
        assertEquals(1, layout.hitTest(132, 20));
        assertEquals(1, layout.hitTest(250.9, 20));
        assertEquals(-1, layout.hitTest(251, 20));
        assertEquals(-1, layout.hitTest(10, 44));
        assertEquals(2, layout.hitTest(10, 48));
    }

    @Test
    void footerClipsHitTestingWithoutShrinkingTheLastVisibleCell() {
        var layout = GraphicDisplayLayout.arrange(new Bounds(10, 20, 100, 65), 4, 24, true);

        assertEquals(new Bounds(10, 20, 100, 37), layout.content());
        assertEquals(new Bounds(10, 61, 24, 24), layout.previous());
        assertEquals(new Bounds(86, 61, 24, 24), layout.next());
        assertEquals(List.of(new Bounds(10, 20, 100, 24), new Bounds(10, 48, 100, 24)), layout.cells());
        assertEquals(1, layout.hitTest(12, 56.9));
        assertEquals(-1, layout.hitTest(12, 57));
        assertEquals(-1, layout.hitTest(12, 61));
    }

    @Test
    void capacityCountsOnlyFullRowsAndUsesTheSameFooterAsArrange() {
        var grid = new Grid(40, 3);
        var fullArea = new Bounds(0, 0, 130, 80);
        var partialArea = new Bounds(0, 0, 130, 79);
        var partialLayout = GraphicDisplayLayout.arrange(partialArea, 9, 24, true, grid);

        assertEquals(9, GraphicDisplayLayout.pageCapacity(fullArea, 24, false, grid));
        assertEquals(6, GraphicDisplayLayout.pageCapacity(fullArea, 24, true, grid));
        assertEquals(3, GraphicDisplayLayout.pageCapacity(partialArea, 24, true, grid));
        assertEquals(6, partialLayout.cells().size());
        assertEquals(51, partialLayout.content().bottom());
        assertEquals(52, partialLayout.cells().get(5).bottom());
        assertEquals(2, GraphicDisplayLayout.pageCapacity(new Bounds(0, 0, 51, 52), 24, true, grid));
        assertEquals(0, GraphicDisplayLayout.pageCapacity(new Bounds(0, 0, 130, 51), 24, true, grid));
        assertEquals(0, GraphicDisplayLayout.pageCapacity(new Bounds(0, 0, 0, 80), 24, false, grid));
        assertEquals(0, GraphicDisplayLayout.pageCapacity(new Bounds(0, 0, 130, 0), 24, false, grid));
    }

    @Test
    void capacityUsesWideIntermediatesAndClampsToIntMaximum() {
        int max = Integer.MAX_VALUE;
        var area = new Bounds(0, 0, max, max);

        assertEquals(max, GraphicDisplayLayout.pageCapacity(area, 1, false, new Grid(1, max)));
        assertEquals(1, GraphicDisplayLayout.pageCapacity(area, max, false, new Grid(max, max)));
    }

    @Test
    void rejectsInvalidGridAndRowDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new Grid(0, 2));
        assertThrows(IllegalArgumentException.class, () -> new Grid(118, 0));
        var area = new Bounds(0, 0, 100, 100);
        assertThrows(IllegalArgumentException.class,
                () -> GraphicDisplayLayout.pageCapacity(area, 0, false, Grid.DEFAULT));
        assertThrows(IllegalArgumentException.class,
                () -> GraphicDisplayLayout.arrange(area, 1, 0, false, Grid.DEFAULT));
    }

    @Test
    void hidesBothControlsWhenTheyCannotFit() {
        var shortArea = new Bounds(0, 0, 80, 27);
        var narrowArea = new Bounds(0, 0, 51, 80);
        for (var area : List.of(shortArea, narrowArea)) {
            var layout = GraphicDisplayLayout.arrange(area, 1, 24, true);
            assertEquals(area, layout.content());
            assertEquals(0, layout.previous().width());
            assertEquals(0, layout.previous().height());
            assertEquals(0, layout.next().width());
            assertEquals(0, layout.next().height());
        }

        var minimum = GraphicDisplayLayout.arrange(new Bounds(0, 0, 52, 28), 1, 24, true);
        assertEquals(0, minimum.content().height());
        assertEquals(4, minimum.next().x() - minimum.previous().right());
        assertTrue(minimum.cells().isEmpty());
    }

    @Test
    void emptyAreasAndEntryListsHaveNoHitTargets() {
        var area = new Bounds(-3, -5, 100, 100);
        var noEntries = GraphicDisplayLayout.arrange(area, 0, 24, false);
        var zeroWidth = GraphicDisplayLayout.arrange(new Bounds(0, 0, 0, 100), 5, 24, true);
        var zeroHeight = GraphicDisplayLayout.arrange(new Bounds(0, 0, 100, 0), 5, 24, true);

        for (var layout : List.of(noEntries, zeroWidth, zeroHeight)) {
            assertTrue(layout.cells().isEmpty());
            assertEquals(-1, layout.hitTest(0, 0));
        }
        assertFalse(new Bounds(3, 5, 0, 24).contains(3, 5));
        assertThrows(IllegalArgumentException.class, () -> new Bounds(3, 5, -1, 24));
        assertThrows(IllegalArgumentException.class, () -> new Bounds(3, 5, 24, -1));
    }

    @Test
    void resultCopiesItsCells() {
        var cell = new Bounds(0, 0, 30, 24);
        var hidden = new Bounds(0, 0, 0, 0);
        var cells = new ArrayList<>(List.of(cell));
        var layout = new Result(cell, cells, hidden, hidden);

        cells.clear();
        assertEquals(List.of(cell), layout.cells());
        assertThrows(UnsupportedOperationException.class, () -> layout.cells().clear());
    }
}
