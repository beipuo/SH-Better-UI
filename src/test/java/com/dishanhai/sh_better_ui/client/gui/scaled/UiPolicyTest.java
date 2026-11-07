package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：覆盖画面快照、展开控件、可访问偏好与动作结果。 */
class UiPolicyTest {
    @Test
    void boundedHistoryRestoresTheNewestSnapshotsInOrder() {
        record View(String query, int offset) {}
        var history = new UiHistory<View>(2);
        assertFalse(history.canRestore());
        history.remember(new View("a", 0));
        history.remember(new View("b", 40));
        history.remember(new View("c", 80));
        assertEquals(new View("c", 80), history.restore().orElseThrow());
        assertEquals(new View("b", 40), history.restore().orElseThrow());
        assertTrue(history.restore().isEmpty());
        history.remember(new View("d", 20));
        history.clear();
        assertFalse(history.canRestore());
    }

    @Test
    void disclosureKeepsExpandedStateAcrossNativeButtonRebuilds() {
        var disclosure = new UiDisclosure(false);
        var show = Component.translatable("gui.yes");
        var hide = Component.translatable("gui.no");
        var changes = new ArrayList<Boolean>();
        var button = disclosure.button(0, 0, 120, 26, show, hide, changes::add);
        assertSame(show, button.getMessage());
        assertNotNull(button.getTooltip());
        assertFalse(((UiButton) button).isSelected());
        button.onPress();
        assertSame(hide, button.getMessage());
        assertTrue(disclosure.expanded());
        assertTrue(((UiButton) button).isSelected());
        var rebuilt = disclosure.button(4, 8, 140, 26, show, hide, changes::add);
        assertSame(hide, rebuilt.getMessage());
        assertTrue(((UiButton) rebuilt).isSelected());
        rebuilt.onPress();
        assertFalse(disclosure.expanded());
        assertFalse(((UiButton) rebuilt).isSelected());
        assertEquals(java.util.List.of(true, false), changes);
    }

    @Test
    void sharedPreferencesKeepStaticFeedbackAndDisableBackdropBlurInHighContrast() {
        var prefs = new UiPreferences(true, true, true);
        assertFalse(prefs.useBackdropBlur());
        assertTrue(UiPreferences.DEFAULT.useBackdropBlur());
        var interaction = DarkModeTheme.Interaction.control();
        var state = interaction.sample(false, true, true, true, prefs, 0);
        assertTrue(state.highContrast());
        assertEquals(DarkModeTheme.TEXT_SECONDARY, state.secondaryText());
        assertNotEquals(DarkModeTheme.ACCENT, state.focusColor());
        assertEquals(1f, state.press());
        assertEquals(1f, state.buttonScale());

        var transition = new PageTransition();
        transition.advance(2, 0, prefs);
        assertEquals(2, transition.currentStage());
        assertFalse(transition.moving(0));
        var animation = new InterruptibleAnimation(Spring.PANEL);
        animation.setOpen(true, 0);
        assertEquals(1f, animation.sample(0, prefs));
    }

    @Test
    void statusOutlivesFlashAndDistinguishesCancellationFromTimeout() {
        var feedback = new ButtonFeedback();
        assertTrue(feedback.tryBegin(1, 0));
        feedback.pending(1, 10, 0);
        assertEquals(ButtonFeedback.Status.PENDING, feedback.status(1, 1));
        assertTrue(feedback.resolve(10, ButtonFeedback.Tone.SUCCESS, 100));
        assertEquals(ButtonFeedback.Phase.IDLE, feedback.phase(1, 1_000));
        assertEquals(ButtonFeedback.Status.SUCCEEDED, feedback.status(1, 1_000));
        assertTrue(feedback.tryBegin(1, 1_000));
        assertEquals(ButtonFeedback.Status.READY, feedback.status(1, 1_000));

        feedback.pending(1, 11, 1_000);
        assertTrue(feedback.resolveCancelled(11, 1_100));
        assertEquals(ButtonFeedback.Status.CANCELLED, feedback.status(1, 1_100));
        assertFalse(feedback.resolve(11, ButtonFeedback.Tone.SUCCESS, 1_200));

        feedback.pending(1, 12, 1_500, 50);
        assertEquals(ButtonFeedback.Status.TIMED_OUT, feedback.status(1, 1_550));
        assertFalse(feedback.resolveCancelled(12, 1_551));
        feedback.flash(1, ButtonFeedback.Tone.PARTIAL, 1_600);
        assertEquals(ButtonFeedback.Status.PARTIAL, feedback.status(1, 1_600));
        feedback.flash(1, ButtonFeedback.Tone.FAILED, 1_700);
        assertEquals(ButtonFeedback.Status.FAILED, feedback.status(1, 1_700));
    }
}
