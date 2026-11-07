package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：验证暗色主题的交互状态和文字对比。 */
class DarkModeThemeTest {
    @Test
    void cardTextBrightensIn200MsAndCardLiftsIn300Ms() {
        var interaction = DarkModeTheme.Interaction.card();
        var idle = interaction.sample(true, false, false, true, false, 100);
        assertEquals(DarkModeTheme.TEXT_MUTED, idle.secondaryText());

        var textFinished = interaction.sample(true, false, false, true, false, 300);
        assertEquals(DarkModeTheme.TEXT_SECONDARY, textFinished.secondaryText());
        assertTrue(textFinished.hover() < 1f);

        var cardFinished = interaction.sample(true, false, false, true, false, 400);
        assertEquals(-2f, cardFinished.cardOffset());
        assertEquals(DarkModeTheme.TEXT, cardFinished.titleText());
    }

    @Test
    void buttonPressIsImmediateAndReleaseEasesFrom98Percent() {
        var interaction = DarkModeTheme.Interaction.control();
        var pressed = interaction.sample(true, true, true, true, false, 100);
        assertEquals(1f, pressed.press());
        assertEquals(0.98f, pressed.buttonScale());
        assertTrue(pressed.focused());
        assertEquals(0.98f, interaction.sample(true, false, true, true, false, 130).buttonScale());
        float releasing = interaction.sample(true, false, true, true, false, 230).buttonScale();
        assertTrue(releasing > 0.98f && releasing < 1f);
        assertEquals(1f, interaction.sample(true, false, true, true, false, 330).buttonScale());
    }

    @Test
    void cancelClearsPressFeedbackImmediately() {
        var interaction = DarkModeTheme.Interaction.control();
        interaction.sample(true, true, true, true, false, 100);
        interaction.cancelPress(101);
        var cancelled = interaction.sample(true, false, true, true, false, 101);
        assertEquals(0f, cancelled.press());
        assertEquals(1f, cancelled.buttonScale());
        assertTrue(cancelled.focused());
    }

    @Test
    void reducedMotionKeepsColorAndFocusFeedbackWithoutTransforms() {
        var interaction = DarkModeTheme.Interaction.control();
        var state = interaction.sample(true, true, true, true, true, 100);
        assertEquals(1f, state.hover());
        assertEquals(1f, state.press());
        assertEquals(1f, state.buttonScale());
        assertEquals(0f, state.cardOffset());
        assertTrue(state.focused());

        var disabled = interaction.sample(true, true, true, false, false, 101);
        assertEquals(0f, disabled.hover());
        assertEquals(0f, disabled.press());
        assertFalse(disabled.focused());
    }

    @Test
    void textMaintains45To1ContrastAndFocusColorClearsTheDarkOffset() {
        var interaction = DarkModeTheme.Interaction.control();
        var normal = interaction.sample(false, false, false, true, true, 0);
        var hovered = interaction.sample(true, false, false, true, true, 1);
        for (var state : new DarkModeTheme.State[]{normal, hovered}) {
            assertTrue(contrast(state.controlText(), state.buttonFill(true)) >= 4.5);
            assertTrue(contrast(state.controlText(), state.buttonFill(false)) >= 4.5);
            assertTrue(contrast(state.secondaryText(), DarkModeTheme.SURFACE) >= 4.5);
        }
        assertTrue(contrast(DarkModeTheme.TEXT_MUTED, DarkModeTheme.SURFACE) >= 4.5);
        assertTrue(contrast(DarkModeTheme.ACCENT, DarkModeTheme.BACKGROUND) >= 3.0);
    }

    private static double contrast(int first, int second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * linear(color, 16) + 0.7152 * linear(color, 8) + 0.0722 * linear(color, 0);
    }

    private static double linear(int color, int shift) {
        double value = ((color >>> shift) & 255) / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
