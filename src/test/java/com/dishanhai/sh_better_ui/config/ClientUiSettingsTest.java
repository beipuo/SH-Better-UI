package com.dishanhai.sh_better_ui.config;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：客户端配置兼容旧构造入口，编辑与预览不得遗失其他偏好。 */
class ClientUiSettingsTest {
    private static final ControlFeedbackSettings FEEDBACK = new ControlFeedbackSettings(false, true, false, 750);
    private static final TabScrollSettings TABS = new TabScrollSettings(false, false, false, 64);

    @Test
    void legacyConstructorsKeepThePreviousDefaults() {
        var three = new ClientUiSettings(GlassBackdropSettings.DEFAULT, true, true);
        var four = new ClientUiSettings(GlassBackdropSettings.DEFAULT, true, true, false);
        assertTrue(three.widgetBackdropBlur());
        assertFalse(four.widgetBackdropBlur());
        for (var settings : new ClientUiSettings[]{three, four}) {
            assertEquals(ControlFeedbackSettings.DEFAULT, settings.feedback());
            assertEquals(TabScrollSettings.DEFAULT, settings.tabs());
        }
        assertEquals(ControlFeedbackSettings.DEFAULT, new UiPreferences(true, false, false).feedback());
    }

    @Test
    void changingEachGroupPreservesTheOtherGroups() {
        var initial = ClientUiSettings.DEFAULT.withFeedback(FEEDBACK).withTabs(TABS);
        var backdrop = initial.backdrop().withEnabled(false);
        var changedBackdrop = initial.withBackdrop(backdrop);
        assertEquals(FEEDBACK, changedBackdrop.feedback());
        assertEquals(TABS, changedBackdrop.tabs());

        var changedDisplay = changedBackdrop.withDisplay(true, true, false);
        assertSame(backdrop, changedDisplay.backdrop());
        assertEquals(FEEDBACK, changedDisplay.feedback());
        assertEquals(TABS, changedDisplay.tabs());
        assertTrue(changedDisplay.highContrast());
        assertTrue(changedDisplay.reducedMotion());
        assertFalse(changedDisplay.widgetBackdropBlur());

        var changedFeedback = changedDisplay.withFeedback(ControlFeedbackSettings.DEFAULT);
        assertEquals(TABS, changedFeedback.tabs());
        assertSame(backdrop, changedFeedback.backdrop());
        assertTrue(changedFeedback.highContrast());
        var changedTabs = changedDisplay.withTabs(TabScrollSettings.DEFAULT);
        assertEquals(FEEDBACK, changedTabs.feedback());
        assertSame(backdrop, changedTabs.backdrop());
        assertTrue(changedTabs.reducedMotion());
    }

    @Test
    void feedbackAndScrollValuesRejectOutOfRangeInputsAndAllowBothEndpoints() {
        assertThrows(IllegalArgumentException.class, () -> new ControlFeedbackSettings(true, true, true, -1));
        assertThrows(IllegalArgumentException.class, () -> new ControlFeedbackSettings(true, true, true, 2001));
        assertDoesNotThrow(() -> new ControlFeedbackSettings(false, false, false, 0));
        assertDoesNotThrow(() -> new ControlFeedbackSettings(false, false, false, 2000));
        assertThrows(IllegalArgumentException.class, () -> new TabScrollSettings(true, true, true, 7));
        assertThrows(IllegalArgumentException.class, () -> new TabScrollSettings(true, true, true, 129));
        assertDoesNotThrow(() -> new TabScrollSettings(false, false, false, 8));
        assertDoesNotThrow(() -> new TabScrollSettings(false, false, false, 128));
    }

    @Test
    void snapshotAndPreferencesRequireTheirNestedSettings() {
        var defaults = ClientUiSettings.DEFAULT;
        assertThrows(NullPointerException.class, () -> defaults.withBackdrop(null));
        assertThrows(NullPointerException.class, () -> defaults.withFeedback(null));
        assertThrows(NullPointerException.class, () -> defaults.withTabs(null));
        assertThrows(NullPointerException.class, () -> new UiPreferences(false, false, true, null));
        var current = ClientUiSettings.current();
        assertThrows(NullPointerException.class, () -> ClientUiSettings.update(null));
        assertSame(current, ClientUiSettings.current());
    }

    @Test
    void draftPreferencesCarryFeedbackWithoutChangingSavedSettings() {
        var current = ClientUiSettings.current();
        var draft = current.withFeedback(FEEDBACK).withDisplay(true, true, true);
        var preferences = UiPreferences.fromSettings(draft);
        assertEquals(FEEDBACK, preferences.feedback());
        assertTrue(preferences.highContrast());
        assertTrue(preferences.reducedMotion());
        assertFalse(preferences.useBackdropBlur());
        assertSame(current, ClientUiSettings.current());
    }

    @Test
    void configuredPreferencesFollowTheCurrentSnapshot() {
        var previous = ClientUiSettings.current();
        try {
            ClientUiSettings.update(previous.withFeedback(FEEDBACK).withTabs(TABS));
            assertEquals(FEEDBACK, UiPreferences.configured().feedback());
            GlassBackdropSettings.update(previous.backdrop().withEnabled(false));
            assertEquals(FEEDBACK, ClientUiSettings.current().feedback());
            assertEquals(TABS, ClientUiSettings.current().tabs());
        } finally {
            ClientUiSettings.update(previous);
        }
    }
}
