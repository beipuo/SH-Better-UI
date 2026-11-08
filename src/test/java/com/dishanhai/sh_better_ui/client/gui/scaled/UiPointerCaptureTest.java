package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：旧画面收到按下后，拖拽和放开不会落到新画面的控件。 */
class UiPointerCaptureTest {
    @Test
    void drainsOnlyTheOldPressAndItsMatchingRelease() {
        UiPointerCapture.drainRelease(0);
        assertFalse(UiPointerCapture.release(null, 1, 0, 0));
        assertTrue(UiPointerCapture.drag(null, 0, 0, 0));
        assertTrue(UiPointerCapture.release(null, 0, 0, 0));
        assertFalse(UiPointerCapture.release(null, 0, 0, 0));
    }

    @Test
    void aNewPressClearsAnUnobservedRelease() {
        UiPointerCapture.drainRelease(0);
        UiPointerCapture.beforePress(null, 0);
        assertFalse(UiPointerCapture.drag(null, 0, 0, 0));
        assertFalse(UiPointerCapture.release(null, 0, 0, 0));
    }
}
