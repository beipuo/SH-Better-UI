package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：验证可达状态和原生聚合控件的键盘切换。 */
class UiWizardNavigationTest {
    private static final Bounds AREA = new Bounds(10, 20, 200, 48);

    @Test
    void focusedTabsWrapAndSkipDisabledEntries() {
        var changes = new ArrayList<Integer>();
        var label = Component.translatable("gui.done");
        var tabs = ShBetterUiApi.controls().underlinedTabs(AREA, List.of(
                new UiUnderlinedTabs.Tab(label, label),
                new UiUnderlinedTabs.Tab(label, label, false),
                new UiUnderlinedTabs.Tab(label, label)), 0, changes::add);

        assertFalse(tabs.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0));
        tabs.setFocused(true);
        assertTrue(tabs.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0));
        assertEquals(2, tabs.selection().selectedIndex());
        assertTrue(tabs.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0));
        assertEquals(0, tabs.selection().selectedIndex());
        assertTrue(tabs.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0));
        assertEquals(List.of(2, 0, 2), changes);
        assertFalse(tabs.selection().select(1));
        assertTrue(tabs.selection().select(0));
        assertFalse(tabs.mouseClicked(30, 55, GLFW.GLFW_MOUSE_BUTTON_LEFT));
        tabs.active = false;
        assertFalse(tabs.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0));
        assertEquals(List.of(2, 0, 2), changes);
    }

    @Test
    void singleEnabledTabDoesNotChangeOrLoopForever() {
        var selection = new UiTabSelection(3, 1, index -> index == 1);
        assertFalse(selection.move(-1));
        assertFalse(selection.move(1));
        assertFalse(selection.select(3));
        assertEquals(1, selection.selectedIndex());
    }

    @Test
    void stepperKeepsProgressWhenReturningAndOnlyHostAdvanceUnlocks() {
        var changes = new ArrayList<Integer>();
        var stepper = ShBetterUiApi.controls().stepper(AREA, 2, changes::add);
        var state = stepper.progress();
        stepper.setFocused(true);

        assertTrue(stepper.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0));
        assertFalse(state.isReachable(3));
        assertEquals(List.of(), changes);
        assertTrue(stepper.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0));
        assertEquals(UiStepperState.Status.CURRENT, state.status(1));
        assertEquals(UiStepperState.Status.COMPLETED, state.status(0));
        assertEquals(UiStepperState.Status.UPCOMING, state.status(2));
        assertTrue(state.isReachable(2));
        assertTrue(stepper.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0));

        assertTrue(state.advance());
        assertEquals(3, state.currentIndex());
        assertEquals(UiStepperState.Status.COMPLETED, state.status(2));
        assertFalse(state.advance());
        assertEquals(List.of(1, 2), changes);
    }
}
