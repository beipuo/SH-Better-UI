package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.config.TabScrollSettings;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：聚合标签控件的命中、键盘与拖动提交契约。 */
class UiScrollableSectionBarTest {
    @Test
    void fullyVisibleEndTabsKeepTheirEntireNativeOuterFocusRing() {
        var bar = bar(new AtomicInteger());
        bar.setFocused(true);
        assertFocusRingFits(bar, bar.buttons().getFirst());
        bar.select(3);
        assertEquals(bar.maxScroll(), bar.scrollOffset());
        assertFocusRingFits(bar, bar.buttons().getLast());
    }

    @Test
    void partiallyScrolledTabContentRemainsClippedAtTheHorizontalViewportEdges() {
        var bar = bar(new AtomicInteger());
        bar.scrollTo(20);
        var first = bar.buttons().getFirst();
        var second = bar.buttons().get(1);
        assertTrue(first.getX() < bar.getX());
        assertTrue(second.getX() + second.getWidth() > bar.getX() + bar.getWidth());
        var leftClip = bar.buttonRenderClip(first);
        var rightClip = bar.buttonRenderClip(second);
        assertEquals(bar.getX(), leftClip.x());
        assertFalse(leftClip.contains(bar.getX() - 0.5, bar.getY() + 4));
        assertEquals(bar.getX() + bar.getWidth(), rightClip.right());
        assertFalse(rightClip.contains(bar.getX() + bar.getWidth(), bar.getY() + 4));
        // 纵向装饰留白与内容的横向滚动裁切是两项独立约束。
        assertTrue(leftClip.y() <= bar.getY() - 3);
        assertTrue(rightClip.bottom() >= bar.getY() + bar.getHeight() + 3);
    }

    @Test
    void allowingFocusDecorationOutsideTheViewportDoesNotEnlargeTheClickTarget() {
        var calls = new AtomicInteger();
        var bar = bar(calls);
        var decoration = bar.buttonRenderClip(bar.buttons().getFirst());
        assertTrue(decoration.contains(8, 8));
        assertFalse(bar.mouseClicked(8, 20, 0));
        assertFalse(bar.mouseClicked(20, 8, 0));
        assertFalse(bar.mouseReleased(20, 20, 0));
        assertEquals(0, calls.get());
    }

    @Test
    void clippedButtonsCannotBeClickedAndClicksCommitOnlyOnRelease() {
        var calls = new AtomicInteger();
        var bar = bar(calls);
        assertFalse(bar.mouseClicked(120, 20, 0));
        assertEquals(0, calls.get());
        assertTrue(bar.mouseClicked(20, 20, 0));
        assertEquals(0, calls.get());
        assertTrue(bar.mouseReleased(20, 20, 0));
        assertEquals(1, calls.get());
        assertEquals(0, bar.selectedIndex());
        assertTrue(bar.buttons().getFirst().isSelected());
        assertNotNull(bar.buttons().getLast().getTooltip());
    }

    @Test
    void nativeScreenFocusAfterPressPreservesClickAndDragCapture() {
        var calls = new AtomicInteger();
        var bar = bar(calls);
        assertTrue(bar.mouseClicked(20, 20, 0));
        bar.setFocused(true);
        assertTrue(bar.mouseReleased(20, 20, 0));
        assertEquals(1, calls.get());

        assertTrue(bar.mouseClicked(80, 20, 0));
        bar.setFocused(true);
        assertTrue(bar.mouseDragged(20, 20, 0, -60, 0));
        assertEquals(60, bar.scrollOffset());
        assertTrue(bar.mouseReleased(20, 20, 0));
        assertEquals(1, calls.get());
    }

    @Test
    void draggingAwayThenReturningToThePressedTabDoesNotSelectIt() {
        var calls = new AtomicInteger();
        var bar = bar(calls);
        bar.mouseClicked(80, 20, 0);
        bar.setFocused(true);
        bar.mouseDragged(20, 20, 0, -60, 0);
        bar.mouseDragged(80, 20, 0, 60, 0);
        assertEquals(0, bar.scrollOffset());
        assertTrue(bar.mouseReleased(80, 20, 0));
        assertEquals(0, calls.get());
    }

    @Test
    void losingFocusCancelsTheGestureAndRestoresThePreDragPosition() {
        var calls = new AtomicInteger();
        var bar = bar(calls);
        bar.setFocused(true);
        bar.scrollTo(30);
        bar.mouseClicked(80, 20, 0);
        bar.mouseDragged(20, 20, 0, -60, 0);
        assertEquals(90, bar.scrollOffset());
        bar.setFocused(false);
        assertEquals(30, bar.scrollOffset());
        assertFalse(bar.mouseReleased(20, 20, 0));
        assertFalse(bar.focusedButton().isFocused());
        assertEquals(0, calls.get());
    }

    @Test
    void horizontalWheelMovesTowardsTheRequestedSideAndDisabledBarsRejectInputs() {
        var calls = new AtomicInteger();
        var bar = bar(calls);
        assertTrue(bar.mouseScrolled(20, 20, 1, 0));
        assertEquals(32, bar.scrollOffset());
        assertTrue(bar.mouseScrolled(20, 20, -1, 0));
        assertEquals(0, bar.scrollOffset());
        bar.setFocused(true);
        bar.active = false;
        assertFalse(bar.mouseClicked(20, 20, 0));
        assertFalse(bar.mouseScrolled(20, 20, -1));
        assertFalse(bar.keyPressed(GLFW.GLFW_KEY_END, 0, 0));
        assertEquals(0, bar.scrollOffset());
        assertEquals(0, calls.get());
    }

    @Test
    void resizeKeepsKeyboardFocusVisibleEvenWhenSelectionAutoRevealIsDisabled() {
        var calls = new AtomicInteger();
        var bar = bar(calls);
        bar.setSettings(new TabScrollSettings(true, true, false, 32));
        bar.setFocused(true);
        bar.focus(3);
        bar.setBounds(new Bounds(10, 10, 40, 22));
        assertEquals(10, bar.focusedButton().getX());
        assertEquals(0, bar.selectedIndex());
        assertEquals(0, calls.get());
    }

    @Test
    void dragScrollDoesNotSelectTabsAndEscapeRestoresTheViewport() {
        var calls = new AtomicInteger();
        var bar = bar(calls);
        bar.setFocused(true);
        bar.mouseClicked(80, 20, 0);
        assertTrue(bar.mouseDragged(20, 20, 0, -60, 0));
        assertEquals(60, bar.scrollOffset());
        assertTrue(bar.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertEquals(0, bar.scrollOffset());
        assertFalse(bar.mouseReleased(20, 20, 0));
        assertEquals(0, calls.get());
        bar.mouseClicked(80, 20, 0);
        bar.mouseDragged(20, 20, 0, -60, 0);
        bar.mouseReleased(20, 20, 0);
        assertEquals(60, bar.scrollOffset());
        assertEquals(0, calls.get());
    }

    @Test
    void keyboardAlwaysRevealsFocusedTabsEvenWhenSelectionAutoRevealIsDisabled() {
        var calls = new AtomicInteger();
        var bar = bar(calls);
        bar.setSettings(new TabScrollSettings(true, true, false, 32));
        bar.select(3);
        assertEquals(0, bar.scrollOffset());
        bar.setFocused(true);
        assertTrue(bar.keyPressed(GLFW.GLFW_KEY_END, 0, 0));
        assertEquals(3, bar.selectedIndex());
        assertEquals(bar.maxScroll(), bar.scrollOffset());
        assertTrue(bar.focusedButton().isFocused());
        assertTrue(bar.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0));
        assertEquals(0, bar.selectedIndex());
        assertEquals(0, bar.scrollOffset());
        assertEquals(2, calls.get());
    }

    @Test
    void wheelAndDragSettingsAreIndependentAndResizingKeepsSelectionVisible() {
        var calls = new AtomicInteger();
        var bar = bar(calls);
        assertTrue(bar.mouseScrolled(20, 20, -0.25));
        assertEquals(8, bar.scrollOffset());
        assertFalse(bar.mouseScrolled(200, 20, -1));
        bar.setSettings(new TabScrollSettings(false, false, true, 32));
        assertFalse(bar.mouseScrolled(20, 20, -1));
        bar.mouseClicked(80, 20, 0);
        bar.mouseDragged(20, 20, 0, -60, 0);
        assertEquals(0, bar.scrollOffset());
        bar.mouseReleased(20, 20, 0);
        bar.select(3);
        assertEquals(bar.maxScroll(), bar.scrollOffset());
        bar.setBounds(new Bounds(30, 40, 160, 22));
        assertEquals(190, bar.buttons().getLast().getX() + bar.buttons().getLast().getWidth());
        assertEquals(40, bar.buttons().getLast().getY());
    }

    private static UiScrollableSectionBar bar(AtomicInteger calls) {
        var entries = List.of(
                new UiSectionBar.Section(Component.translatable("options.title"), GraphicIcon.SETTINGS),
                new UiSectionBar.Section(Component.translatable("gui.stats"), GraphicIcon.MONITOR),
                new UiSectionBar.Section(Component.translatable("gui.back"), GraphicIcon.BACK),
                new UiSectionBar.Section(Component.translatable("gui.done"), GraphicIcon.SUCCESS));
        return new UiScrollableSectionBar(new Bounds(10, 10, 100, 22), UiPreferences.DEFAULT,
                entries, 0, ignored -> calls.incrementAndGet(), TabScrollSettings.DEFAULT, 80);
    }

    private static void assertFocusRingFits(UiScrollableSectionBar bar, UiButton button) {
        var clip = bar.buttonRenderClip(button);
        assertTrue(clip.x() <= button.getX() - 3);
        assertTrue(clip.y() <= button.getY() - 3);
        assertTrue(clip.right() >= button.getX() + button.getWidth() + 3);
        assertTrue(clip.bottom() >= button.getY() + button.getHeight() + 3);
    }
}
