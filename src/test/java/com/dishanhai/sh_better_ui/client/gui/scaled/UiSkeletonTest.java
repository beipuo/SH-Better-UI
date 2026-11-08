package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：骨架块的窄窗布局和动效边界。 */
class UiSkeletonTest {
    @Test
    void cardUsesSeparateThumbnailAndTextLines() {
        Bounds card = new Bounds(12, 18, 144, 60);
        var blocks = UiSkeleton.cardBlocks(card);
        assertEquals(3, blocks.size());
        Bounds thumbnail = blocks.get(0).bounds();
        Bounds heading = blocks.get(1).bounds();
        Bounds detail = blocks.get(2).bounds();
        assertEquals(thumbnail.width(), thumbnail.height());
        assertTrue(thumbnail.right() < heading.x());
        assertEquals(heading.x(), detail.x());
        assertTrue(heading.bottom() < detail.y());
        assertTrue(heading.width() > detail.width());
    }

    @Test
    void tinyAndNarrowCardsKeepBlocksInsideTheirBounds() {
        for (int width = 0; width < 48; width++) {
            for (int height = 0; height < 48; height++) {
                Bounds area = new Bounds(-3, 5, width, height);
                var blocks = UiSkeleton.cardBlocks(area);
                assertEquals(width == 0 || height == 0, blocks.isEmpty());
                for (var block : blocks) {
                    Bounds bounds = block.bounds();
                    assertTrue(bounds.width() > 0 && bounds.height() > 0);
                    assertTrue(area.contains(bounds.x(), bounds.y()));
                    assertTrue(bounds.right() <= area.right() && bounds.bottom() <= area.bottom());
                }
            }
        }
    }

    @Test
    void materialHighlightSweepsLeftToRightAndReducedMotionIsStatic() {
        Bounds area = new Bounds(10, 0, 100, 20);
        Bounds left = new Bounds(20, 1, 10, 4);
        Bounds right = new Bounds(90, 10, 10, 4);
        assertTrue(UiSkeleton.highlight(area, left, 550L, false) > 0.9f);
        assertEquals(0f, UiSkeleton.highlight(area, right, 550L, false));
        assertTrue(UiSkeleton.highlight(area, right, 1250L, false) > 0.9f);
        assertEquals(0f, UiSkeleton.highlight(area, left, 1250L, false));
        assertEquals(UiSkeleton.highlight(area, left, 550L, false),
                UiSkeleton.highlight(area, left, -1250L, false));
        assertEquals(0f, UiSkeleton.highlight(area, right, 1250L, true));
    }
}
