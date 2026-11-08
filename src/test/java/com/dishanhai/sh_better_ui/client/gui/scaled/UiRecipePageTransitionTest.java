package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPageSlide.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：覆盖配方切换的速度曲线、页面接缝、反向和无动效状态。 */
class UiRecipePageTransitionTest {
    private static final Bounds VIEW = new Bounds(12, 18, 197, 93);
    private static final UiPreferences PREFS = UiPreferences.DEFAULT;
    private static final long QUARTER = PageTransition.DURATION_MS / 4;

    @Test
    void pagesStayAdjacentAtSubpixelOffsetsAndBlurOnlyInMotion() {
        for (var direction : Direction.values()) {
            var transition = new UiRecipePageTransition();
            transition.start(direction, VIEW, 0, PREFS);
            assertFalse(transition.sample(0, PREFS).blur().isActive());

            var frame = transition.sample(QUARTER, PREFS);
            float distance = direction.axisX() != 0 ? VIEW.width() : VIEW.height();
            assertEquals(0.15625f, frame.progress());
            assertEquals(-direction.axisX() * distance * 0.84375f, frame.incomingX(), 0.0001f);
            assertEquals(-direction.axisY() * distance * 0.84375f, frame.incomingY(), 0.0001f);
            assertEquals(direction.axisX() * distance, frame.outgoingX() - frame.incomingX(), 0.0001f);
            assertEquals(direction.axisY() * distance, frame.outgoingY() - frame.incomingY(), 0.0001f);
            assertEquals(0.75f, frame.blur().getStrength());
            assertEquals(8f, frame.blur().getRadiusVirtual());
            assertEquals(Math.abs(direction.axisX()), frame.blur().getAxisX());
            assertEquals(Math.abs(direction.axisY()), frame.blur().getAxisY());

            var still = transition.sample(PageTransition.DURATION_MS, PREFS);
            assertFalse(still.active());
            assertEquals(0f, still.incomingX(), 0.0001f);
            assertEquals(0f, still.incomingY(), 0.0001f);
            assertFalse(still.blur().isActive());
        }
    }

    @Test
    void reversingTheSamePairPreservesBothPositions() {
        var transition = new UiRecipePageTransition();
        transition.start(Direction.LEFT, VIEW, 0, PREFS);
        var previous = transition.sample(QUARTER, PREFS);
        transition.start(Direction.RIGHT, VIEW, QUARTER, PREFS);
        var reversed = transition.sample(QUARTER, PREFS);
        assertEquals(previous.incomingX(), reversed.outgoingX(), 0.0001f);
        assertEquals(previous.outgoingX(), reversed.incomingX(), 0.0001f);
        assertEquals(previous.blur().getStrength(), reversed.blur().getStrength());
        assertNotEquals(previous.companion().incoming(), reversed.companion().incoming());
        assertEquals(previous.companion().veilOpacity(), reversed.companion().veilOpacity());

        transition.start(Direction.UP, VIEW, QUARTER, PREFS);
        var newAxis = transition.sample(QUARTER, PREFS);
        assertEquals(VIEW.height(), newAxis.incomingY());
        assertEquals(0f, newAxis.outgoingY());
    }

    @Test
    void incomingOnlyTabSwitchKeepsTheExistingMotionAndSettlesOnTime() {
        var transition = new UiRecipePageTransition();
        transition.enter(Direction.LEFT, 24f, 0, PREFS);
        var previous = transition.sample(QUARTER, PREFS);
        transition.enter(Direction.RIGHT, 24f, QUARTER, PREFS);
        assertEquals(previous, transition.sample(QUARTER, PREFS));

        var middle = transition.sample(QUARTER * 2, PREFS);
        assertEquals(12f, middle.incomingX());
        assertEquals(4f, middle.blur().getRadiusVirtual());
        assertFalse(transition.sample(QUARTER * 4, PREFS).active());
    }

    @Test
    void companionSwitchesUnderVeilAndReducedMotionImmediatelyRevealsTheTarget() {
        var transition = new UiRecipePageTransition();
        transition.start(Direction.DOWN, VIEW, 0, PREFS);
        assertEquals(new UiRecipePageTransition.Companion(false, 0f, 0.3125f),
                transition.sample(QUARTER, PREFS).companion());
        assertEquals(new UiRecipePageTransition.Companion(true, 6f, 1f),
                transition.sample(QUARTER * 2, PREFS).companion());
        assertEquals(new UiRecipePageTransition.Companion(true, 1.875f, 0.3125f),
                transition.sample(QUARTER * 3, PREFS).companion());

        var reduced = new UiPreferences(false, true, true);
        var still = transition.sample(QUARTER * 3, reduced);
        assertEquals(new UiRecipePageTransition.Companion(true, 0f, 0f), still.companion());
        assertFalse(still.blur().isActive());
        assertFalse(transition.sample(QUARTER * 3 + 1, PREFS).active());
    }
}
