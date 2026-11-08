package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.config.ControlFeedbackSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：关闭装饰动效后仍保留可辨识的静态状态。 */
class ControlFeedbackTest {
    @Test
    void hoverWithoutAnimationChangesImmediatelyAndKeepsFocus() {
        var preferences = new UiPreferences(false, false, true,
                new ControlFeedbackSettings(false, true, true, 0));
        var interaction = DarkModeTheme.Interaction.control();
        var hovered = interaction.sample(true, false, true, true, preferences, 0);
        assertEquals(1f, hovered.hover());
        assertTrue(hovered.focused());
        assertEquals(0f, interaction.sample(false, false, true, true, preferences, 1).hover());
    }

    @Test
    void pressWithoutAnimationKeepsPressFeedbackButRemovesScale() {
        var preferences = new UiPreferences(false, false, true,
                new ControlFeedbackSettings(true, false, true, 0));
        var interaction = DarkModeTheme.Interaction.control();
        var pressed = interaction.sample(false, true, false, true, preferences, 0);
        assertEquals(1f, pressed.press());
        assertEquals(1f, pressed.buttonScale());
        assertEquals(0f, interaction.sample(false, false, false, true, preferences, 1).press());
    }
}
