package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：主控面板切页时移除前一页的输入控件，并保留草稿。 */
class ControlPanelScreenTest {
    @Test
    void changingTabsRemovesInactiveControlsAndKeepsTheirDraftValues() {
        var mapping = new KeyMapping("key.sh_better_ui.control_panel.test", InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_4, "key.categories.sh_better_ui");
        var screen = new ControlPanelScreen(null, mapping, settings -> true);
        screen.width = 420;
        screen.height = 240;
        screen.init();

        var backdrop = button(screen, "enabled");
        boolean initialBlur = ClientUiSettings.current().backdrop().enabled();
        var sampling = button(screen, "sampling");
        boolean initialDense = ClientUiSettings.current().backdrop().denseSampling();
        assertEquals(8, sliders(screen));
        assertEquals(2, sampling.getModeIndicatorCount());
        assertEquals(initialDense ? 1 : 0, sampling.getSelectedModeIndex());
        sampling.onPress();
        assertEquals(initialDense ? 0 : 1, sampling.getSelectedModeIndex());
        backdrop.onPress();
        assertEquals(!initialBlur, backdrop.isSelected());

        switchTab(screen, "display");
        assertFalse(screen.children().contains(backdrop));
        assertFalse(screen.children().contains(sampling));
        assertEquals(0, sliders(screen));
        assertNull(findButton(screen, "shortcut.binding"));
        var contrast = button(screen, "contrast");
        boolean initialContrast = ClientUiSettings.current().highContrast();
        contrast.onPress();
        assertEquals(!initialContrast, contrast.isSelected());

        switchTab(screen, "shortcut");
        assertNotNull(findButton(screen, "shortcut.binding"));
        assertFalse(screen.children().contains(contrast));
        assertEquals(0, sliders(screen));

        switchTab(screen, "glass");
        assertEquals(8, sliders(screen));
        assertEquals(!initialBlur, button(screen, "enabled").isSelected());
        assertEquals(initialDense ? 0 : 1, button(screen, "sampling").getSelectedModeIndex());
        assertTrue(button(screen, "apply").active);
        switchTab(screen, "display");
        assertEquals(!initialContrast, button(screen, "contrast").isSelected());
    }

    private static long sliders(ControlPanelScreen screen) {
        return screen.children().stream().filter(UiSlider.class::isInstance).count();
    }

    private static void switchTab(ControlPanelScreen screen, String section) {
        var tab = button(screen, "section." + section);
        tab.onPress();
        screen.keyPressed(GLFW.GLFW_KEY_F12, 0, 0);
        assertSame(tab, screen.getFocused());
        assertTrue(tab.isSelected());
    }

    private static UiButton button(ControlPanelScreen screen, String key) {
        return assertInstanceOf(UiButton.class, findButton(screen, key));
    }

    private static UiButton findButton(ControlPanelScreen screen, String key) {
        return screen.children().stream().filter(UiButton.class::isInstance).map(UiButton.class::cast)
                .filter(button -> button.getMessage().getContents() instanceof TranslatableContents text
                        && text.getKey().equals("sh_better_ui.panel." + key))
                .findFirst().orElse(null);
    }
}
