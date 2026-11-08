package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.CommonComponents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：浮层锚点贴边、翻转和操作确认契约。 */
class UiOverlayHostTest {
    @Test
    void movingBodyUsesTheDisplayedHalfOpenBounds() {
        var settled = new UiOverlayHost.Bounds(12, 30, 80, 22);
        var moving = settled.offsetY(UiOverlayHost.entryOffset(0.5f, true));
        assertEquals(new UiOverlayHost.Bounds(12, 37, 80, 22), moving);
        assertFalse(moving.contains(12, 36));
        assertTrue(moving.contains(12, 37));
        assertTrue(moving.contains(91, 58));
        assertFalse(moving.contains(91, 59));
        assertTrue(UiOverlayHost.pointerInViewport(91, 54, 120, 55));
        assertFalse(UiOverlayHost.pointerInViewport(91, 55, 120, 55));
    }

    @Test
    void reducedMotionAndSpringOvershootKeepLayersAtTheirFinalPosition() {
        var enter = new InterruptibleAnimation(Spring.PANEL);
        enter.setOpen(true, 100);
        assertEquals(9, UiOverlayHost.entryOffset(enter.sample(100, false), false));
        assertEquals(0, UiOverlayHost.entryOffset(enter.sample(100, true), false));
        assertEquals(0, UiOverlayHost.entryOffset(1.1f, true));
    }

    @Test
    void bubblePrefersBelowAndFlipsNearTheBottom() {
        assertEquals(new UiOverlayHost.Bounds(20, 26, 44, 22),
                UiOverlayHost.placeBubble(new Rect2i(20, 10, 16, 12), 160, 100, 44, 22));
        assertEquals(new UiOverlayHost.Bounds(112, 57, 44, 22),
                UiOverlayHost.placeBubble(new Rect2i(148, 83, 12, 10), 160, 100, 44, 22));
    }

    @Test
    void bubbleStillFitsWhenNeitherSideHasEnoughSpace() {
        assertEquals(new UiOverlayHost.Bounds(4, 8, 50, 40),
                UiOverlayHost.placeBubble(new Rect2i(-8, 20, 20, 12), 80, 52, 50, 40));
    }

    @Test
    void onlyVisibleNonemptyAnchorsCanPlaceABubble() {
        assertTrue(UiOverlayHost.intersectsViewport(new Rect2i(-8, -8, 20, 20), 80, 52));
        assertFalse(UiOverlayHost.intersectsViewport(new Rect2i(-8, 10, 8, 20), 80, 52));
        assertFalse(UiOverlayHost.intersectsViewport(new Rect2i(10, 10, 0, 20), 80, 52));
        assertFalse(UiOverlayHost.intersectsViewport(new Rect2i(80, 10, 8, 20), 80, 52));
    }

    @Test
    void actionCanCarryAnExplicitConfirmation() {
        Runnable action = () -> {};
        var prompt = new UiConfirmation.Prompt(CommonComponents.GUI_YES, CommonComponents.GUI_NO,
                CommonComponents.GUI_OK, CommonComponents.GUI_CANCEL);
        assertNull(new UiOverlayHost.Action(CommonComponents.GUI_OK, action).confirmation());
        var confirmed = new UiOverlayHost.Action(CommonComponents.GUI_OK, action, prompt);
        assertEquals(prompt, confirmed.confirmation());
        assertEquals(GraphicIcon.WARNING, confirmed.icon());
        var custom = new UiOverlayHost.Action(CommonComponents.GUI_OK, action, prompt, GraphicIcon.REMOVE);
        assertEquals(GraphicIcon.REMOVE, custom.icon());
        assertEquals(prompt, custom.confirmation());
    }

    @Test
    void iconReservesTextSpaceAndFallsBackOnSmallBounds() {
        assertEquals(20, UiOverlayHost.iconInset(GraphicIcon.BUBBLE, 44, 16));
        assertEquals(0, UiOverlayHost.iconInset(GraphicIcon.BUBBLE, 43, 16));
        assertEquals(0, UiOverlayHost.iconInset(GraphicIcon.BUBBLE, 100, 15));
        assertEquals(0, UiOverlayHost.iconInset(null, 100, 100));
    }
}
