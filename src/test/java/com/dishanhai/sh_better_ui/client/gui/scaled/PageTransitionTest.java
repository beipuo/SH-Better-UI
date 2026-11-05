package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PageTransitionTest {
    @Test
    void pageSlideKeepsRawProgressAndSettlesAfterItsDuration() {
        PageTransition transition = new PageTransition();
        assertEquals(1f, transition.linear(0), 1e-6f);
        transition.reset(0, 0);
        transition.advance(1, 100);
        assertEquals(0f, transition.linear(100), 1e-6f);
        assertEquals(0.5f, transition.linear(100 + PageTransition.DURATION_MS / 2), 1e-6f);
        assertEquals(0.5f, transition.progress(100 + PageTransition.DURATION_MS / 2), 1e-6f);
        assertTrue(transition.moving(100 + PageTransition.DURATION_MS / 2));
        transition.advance(1, 100 + PageTransition.DURATION_MS);
        assertFalse(transition.moving(100 + PageTransition.DURATION_MS));
        assertEquals(1, transition.currentStage());
    }
}
