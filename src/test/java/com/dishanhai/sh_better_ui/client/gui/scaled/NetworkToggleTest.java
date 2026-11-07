package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：开关展示已确认值，点击只提交变更请求。 */
class NetworkToggleTest {
    @Test
    void requestsChangeWithoutReplacingConfirmedValueAndIgnoresPressesWhilePending() {
        var requests = new ArrayList<Boolean>();
        var toggle = new NetworkToggle(new Bounds(0, 0, 100, 20), Component.translatable("gui.yes"),
                false, UiPreferences.DEFAULT, requests::add);

        toggle.onPress();
        assertEquals(List.of(true), requests);
        assertFalse(toggle.value());

        toggle.active = false;
        toggle.onPress();
        toggle.setValue(true);
        assertEquals(List.of(true), requests);
        assertTrue(toggle.value());

        toggle.active = true;
        toggle.onPress();
        assertEquals(List.of(true, false), requests);
        assertTrue(toggle.value());
    }
}
