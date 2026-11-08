package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：开关以调用方确认的状态为准。 */
class UiToggleButtonTest {
    @Test
    void requestsWaitForConfirmationAndProgrammaticUpdatesAreSilent() {
        var requests = new ArrayList<Boolean>();
        var on = Component.translatable("options.on");
        var off = Component.translatable("options.off");
        var toggle = new UiToggleButton(new Bounds(0, 0, 90, 22), false, UiPreferences.DEFAULT,
                UiStyle.LIQUID_GLASS, value -> value ? on : off, requests::add);
        toggle.setIcons(GraphicIcon.ENABLE, GraphicIcon.DISABLE);

        toggle.button().onPress();
        assertEquals(List.of(true), requests);
        assertFalse(toggle.value());
        assertSame(off, toggle.button().getMessage());

        toggle.setValue(true);
        assertEquals(List.of(true), requests);
        assertTrue(toggle.button().isSelected());
        assertSame(on, toggle.button().getMessage());
        toggle.button().active = false;
        toggle.button().onPress();
        assertEquals(List.of(true), requests);
    }

    @Test
    void indicatorsFollowConfirmedOffAndOnValues() {
        var requests = new ArrayList<Boolean>();
        var toggle = new UiToggleButton(new Bounds(0, 0, 90, 22), false, UiPreferences.DEFAULT,
                UiStyle.LIQUID_GLASS, value -> Component.translatable(value ? "options.on" : "options.off"),
                requests::add);
        var button = toggle.button();
        assertEquals(0, button.getModeIndicatorCount());

        toggle.setModeIndicatorsVisible(true);
        assertEquals(2, button.getModeIndicatorCount());
        assertEquals(0, button.getSelectedModeIndex());
        button.onPress();
        assertEquals(List.of(true), requests);
        assertEquals(0, button.getSelectedModeIndex());

        toggle.setValue(true);
        assertEquals(List.of(true), requests);
        assertEquals(1, button.getSelectedModeIndex());

        toggle.setModeIndicatorsVisible(false);
        toggle.setValue(false);
        assertEquals(0, button.getModeIndicatorCount());
        assertEquals(-1, button.getSelectedModeIndex());
    }
}
