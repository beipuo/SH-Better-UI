package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 山海：验证滚轮切页的方向、边界和连续输入。 */
class ScrollPageTurnTest {
    private final ScrollPageTurn pageTurn = new ScrollPageTurn();

    @Test
    void turnsOnlyAtTheEdgeInTheWheelDirection() {
        assertEquals(1, pageTurn.targetPage(-1, 99, 100, 1, 3, 0));
        assertEquals(1, pageTurn.targetPage(1, 1, 100, 1, 3, 10));
        assertEquals(2, pageTurn.targetPage(-1, 100, 100, 1, 3, 20));
        assertEquals(0, pageTurn.targetPage(1, 0, 100, 1, 3, 30));
    }

    @Test
    void keepsFirstLastAndSinglePagesInRange() {
        assertEquals(0, pageTurn.targetPage(1, 0, 100, 0, 3, 0));
        assertEquals(2, pageTurn.targetPage(-1, 100, 100, 2, 3, 10));
        assertEquals(0, pageTurn.targetPage(-1, 0, 0, 0, 1, 20));
        assertEquals(0, pageTurn.targetPage(1, 0, 0, 0, 1, 30));
        assertEquals(0, pageTurn.targetPage(-1, 0, 0, 0, 0, 40));
    }

    @Test
    void continuousWheelInputCannotSkipShortPages() {
        assertEquals(1, pageTurn.targetPage(-0.1, 0, 0, 0, 4, 0));
        assertEquals(1, pageTurn.targetPage(-0.1, 0, 0, 1, 4, 170));
        assertEquals(1, pageTurn.targetPage(-0.1, 0, 0, 1, 4, 349));
        assertEquals(2, pageTurn.targetPage(-0.1, 0, 0, 1, 4, 529));
        assertEquals(1, pageTurn.targetPage(0.1, 0, 0, 2, 4, 530));
    }

    @Test
    void resetAllowsAnIndependentWheelGesture() {
        assertEquals(1, pageTurn.targetPage(-1, 0, 0, 0, 3, 0));
        pageTurn.reset();
        assertEquals(2, pageTurn.targetPage(-1, 0, 0, 1, 3, 1));
    }

    @Test
    void ignoresZeroAndInvalidInput() {
        assertEquals(1, pageTurn.targetPage(0, 0, 0, 1, 3, 0));
        assertEquals(1, pageTurn.targetPage(Double.NaN, 0, 0, 1, 3, 0));
        assertEquals(1, pageTurn.targetPage(-1, Double.POSITIVE_INFINITY, 0, 1, 3, 0));
        assertEquals(1, pageTurn.targetPage(-1, 0, -1, 1, 3, 0));
        assertEquals(-1, pageTurn.targetPage(-1, 0, 0, -1, 3, 0));
        assertEquals(2, pageTurn.targetPage(-1, 0, 0, 1, 3, 0));
    }
}
