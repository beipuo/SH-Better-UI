package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiToast.Effect;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToast.Frame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：验证通知轨迹的边界与停留阶段。 */
class UiToastTest {
    private static final long HOLD_MS = 1_000L;
    private static final int SCREEN_WIDTH = 320;
    private static final int HEIGHT = 38;
    private static final Frame SETTLED = new Frame(0f, 0f, 1f, 1f);

    @Test
    void slideStartsOutsideTheRightEdgeAndSettlesOnTheLeft() {
        Frame start = sample(Effect.SLIDE_FROM_RIGHT, 0L);
        assertEquals(SCREEN_WIDTH, 8f + start.offsetX());
        assertTrue(sample(Effect.SLIDE_FROM_RIGHT, 160L).offsetX() < start.offsetX());
        assertEquals(SETTLED, sample(Effect.SLIDE_FROM_RIGHT, 320L));
        assertEquals(SETTLED, sample(Effect.SLIDE_FROM_RIGHT, 1_320L));
        assertTrue(sample(Effect.SLIDE_FROM_RIGHT, 1_430L).offsetX() > 0f);
        assertEquals(start, sample(Effect.SLIDE_FROM_RIGHT, 1_540L));
    }

    @Test
    void dropStartsWithItsBottomOutsideTheTopEdge() {
        Frame start = sample(Effect.DROP_FROM_TOP, 0L);
        assertEquals(0f, 8f + start.offsetY() + HEIGHT);
        assertTrue(sample(Effect.DROP_FROM_TOP, 160L).offsetY() > start.offsetY());
        assertEquals(0f, sample(Effect.DROP_FROM_TOP, 320L).offsetY(), 0f);
        assertEquals(start, sample(Effect.DROP_FROM_TOP, 1_540L));
    }

    @Test
    void fadeAndPopAreInvisibleAtTheEndsAndFullyVisibleWhileHolding() {
        for (Effect effect : new Effect[]{Effect.FADE, Effect.POP}) {
            assertEquals(0f, sample(effect, 0L).opacity());
            assertEquals(SETTLED, sample(effect, 320L));
            assertEquals(SETTLED, sample(effect, 1_320L));
            assertEquals(0f, sample(effect, 1_540L).opacity());
        }
        assertEquals(1f, sample(Effect.FADE, 160L).scale());
        assertTrue(sample(Effect.POP, 160L).scale() < 1f);
    }

    @Test
    void reducedMotionKeepsEveryEffectAtItsFinalPosition() {
        for (Effect effect : Effect.values()) {
            assertEquals(SETTLED, UiToast.sample(effect, true, 0L, HOLD_MS, SCREEN_WIDTH, HEIGHT));
        }
        assertEquals(SETTLED, sample(Effect.NONE, 0L));
    }

    private static Frame sample(Effect effect, long ageMs) {
        return UiToast.sample(effect, false, ageMs, HOLD_MS, SCREEN_WIDTH, HEIGHT);
    }
}
