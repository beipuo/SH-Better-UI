package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：公开按钮的提示、选择与原生回调契约。 */
class UiButtonTest {
    @Test
    void defaultPreviewFollowsTheLocalizedLabel() {
        var button = new UiButton(0, 0, 100, 20, Component.translatable("gui.yes"), ignored -> {});
        var initial = button.getTooltip();
        assertNotNull(initial);
        button.setMessage(Component.translatable("gui.no"));
        assertNotNull(button.getTooltip());
        assertNotSame(initial, button.getTooltip());
    }

    @Test
    void explicitPreviewSurvivesLabelChangesAndCanReturnToDefault() {
        var button = new UiButton(0, 0, 100, 20, Component.translatable("gui.yes"), ignored -> {});
        button.setPreview(Component.translatable("sh_better_ui.jei.choose.preview"));
        var explicit = button.getTooltip();
        button.setMessage(Component.translatable("gui.no"));
        assertSame(explicit, button.getTooltip());
        button.setPreview(null);
        assertNotSame(explicit, button.getTooltip());
    }

    @Test
    void onlyCallerConfirmedStateChangesSelectionAndPressCallsActionOnce() {
        var calls = new AtomicInteger();
        var button = new UiButton(0, 0, 100, 20, Component.translatable("gui.yes"), ignored -> calls.incrementAndGet());
        button.onPress();
        assertEquals(1, calls.get());
        assertFalse(button.isSelected());
        button.setSelected(true);
        button.setSelected(true);
        assertTrue(button.isSelected());
        assertEquals(1, calls.get());
        button.setSelected(false);
        assertFalse(button.isSelected());
    }
}
