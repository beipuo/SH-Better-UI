package com.dishanhai.sh_better_ui.client.gui.display;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.dishanhai.sh_better_ui.client.gui.display.NetworkLayout.RowKind.*;
import static org.junit.jupiter.api.Assertions.*;

/** 山海：覆盖网络风格布局的常规尺寸、折行、分页和大字体。 */
class NetworkLayoutTest {
    private static final List<NetworkLayout.RowKind> DEVICE_ROWS =
            List.of(FIELD, FIELD, FIELD, STATUS, TOGGLE, TOGGLE, TOGGLE);

    @Test
    void fitsTheDeviceLayoutAtTheDefaultGuiMinimum() {
        var result = NetworkLayout.arrange(320, 240, 7, true, DEVICE_ROWS, 9, 4).orElseThrow();

        assertEquals(228, result.panel().width());
        assertEquals(320, result.panel().x() + result.panel().right());
        assertEquals(7, result.tabs().size());
        assertEquals(1, result.tabs().stream().map(Bounds::y).distinct().count());
        var action = result.action().orElseThrow();
        assertEquals(result.tabs().getFirst().y(), action.y());
        assertTrue(action.x() > result.tabs().getLast().right());
        assertTrue(action.bottom() < result.panel().y());
        assertTrue(result.title().height() >= 20);
        assertEquals(1, result.pageCount());
        assertEquals(0, result.pageIndex());
        assertEquals(0, result.previous().width());
        assertEquals(0, result.next().height());
        assertEquals(7, result.rows().size());
        assertTrue(result.rows().get(3).height() >= 2 * 9 + 8);
        assertTrue(result.rows().get(3).y() - result.rows().get(2).bottom()
                > result.rows().get(1).y() - result.rows().get(0).bottom());
        assertTrue(result.rows().getLast().bottom() < result.panel().bottom());
    }

    @Test
    void wrapsTheTabBarAndReservesSpaceForTheRightAlignedAction() {
        var result = NetworkLayout.arrange(140, 240, 7, true, DEVICE_ROWS, 9, 0).orElseThrow();
        var action = result.action().orElseThrow();

        assertEquals(2, result.tabs().stream().map(Bounds::y).distinct().count());
        assertEquals(result.tabs().getLast().y(), action.y());
        assertTrue(result.tabs().getLast().right() < action.x());
        assertEquals(result.rows().getFirst().right(), action.right());
        assertTrue(action.bottom() < result.panel().y());
    }

    @Test
    void paginatesWithoutSplittingOrDroppingRows() {
        int nextRow = 0;
        int pageCount = NetworkLayout.arrange(320, 140, 7, true, DEVICE_ROWS, 9, -2).orElseThrow().pageCount();
        assertTrue(pageCount > 1);
        for (int page = 0; page < pageCount; page++) {
            var result = NetworkLayout.arrange(320, 140, 7, true, DEVICE_ROWS, 9, page).orElseThrow();
            assertEquals(page, result.pageIndex());
            assertEquals(pageCount, result.pageCount());
            assertEquals(nextRow, result.firstRow());
            assertFalse(result.rows().isEmpty());
            assertTrue(result.rows().getLast().bottom() < result.previous().y());
            assertTrue(result.previous().width() >= 20);
            assertTrue(result.next().height() >= 20);
            nextRow += result.rows().size();
        }
        assertEquals(DEVICE_ROWS.size(), nextRow);
        assertEquals(pageCount - 1,
                NetworkLayout.arrange(320, 140, 7, true, DEVICE_ROWS, 9, 999).orElseThrow().pageIndex());
    }

    @Test
    void usesFontHeightForEveryRowAndReturnsEmptyWhenACompletePageCannotFit() {
        var result = NetworkLayout.arrange(320, 240, 1, false, List.of(FIELD, STATUS, TOGGLE), 22, 0).orElseThrow();
        assertEquals(List.of(26, 52, 26), result.rows().stream().map(Bounds::height).toList());
        assertTrue(result.action().isEmpty());
        assertTrue(NetworkLayout.arrange(60, 240, 7, true, DEVICE_ROWS, 9, 0).isEmpty());
        assertTrue(NetworkLayout.arrange(320, 90, 7, true, DEVICE_ROWS, 9, 0).isEmpty());
        assertTrue(NetworkLayout.arrange(320, 240, 7, true, DEVICE_ROWS, Integer.MAX_VALUE, 0).isEmpty());
    }
}
