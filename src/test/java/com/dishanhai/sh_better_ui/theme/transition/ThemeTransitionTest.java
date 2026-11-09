// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.transition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ThemeTransitionTest {
    private final AtomicLong now = new AtomicLong();
    private final List<TransitionNotice> events = new ArrayList<>();
    private final TransitionManager<Double> motion = new TransitionManager<>(0d, now::get,
            (a, b, t) -> a + (b - a) * t, events::add);
    private final TransitionSpec linear = TransitionSpec.of(100, Easing.LINEAR);

    @ParameterizedTest
    @ValueSource(longs = {0, 25, 50, 99, 100})
    void retargetsFromVisibleValue(long cut) {
        motion.request(100d, linear);
        now.set(cut);
        double visible = motion.update();
        motion.request(-100d, linear);
        assertEquals(visible, motion.value(), 0.0);
        now.addAndGet(25);
        assertEquals(visible + (-100 - visible) * .25, motion.update(), 1e-6);
    }

    @Test
    void repeatedTargetsAreIgnoredAndQueueRunsInOrder() {
        motion.request(100d, linear);
        motion.request(100d, linear);
        motion.request(200d, linear.withPolicy(InterruptPolicy.QUEUE));
        motion.request(300d, linear.withPolicy(InterruptPolicy.QUEUE));
        now.set(100);
        assertEquals(100d, motion.update());
        now.set(200);
        assertEquals(200d, motion.update());
        now.set(300);
        assertEquals(300d, motion.update());
        assertEquals(3, events.stream().filter(e -> e.kind() == TransitionNotice.Kind.COMPLETED).count());
    }

    @Test
    void blendsTheOldTrackAndPausesTheClock() {
        motion.request(100d, linear);
        now.set(25);
        motion.request(200d, linear.withPolicy(InterruptPolicy.BLEND));
        assertEquals(25d, motion.value());
        now.set(50);
        assertEquals(87.5d, motion.update());
        motion.pause();
        now.set(90);
        assertEquals(87.5d, motion.update());
        motion.resume();
        now.set(190);
        assertEquals(200d, motion.update());
    }

    @Test
    void closeCancelsExactlyOnce() {
        motion.request(100d, linear);
        motion.close();
        motion.close();
        assertEquals(1, events.stream().filter(e -> e.kind() == TransitionNotice.Kind.CANCELLED).count());
        assertThrows(IllegalStateException.class, () -> motion.request(9d, linear));
    }

    @Test
    void finishThenSwitchRetainsLatestWaitingTargetAndSnapStopsPendingWork() {
        motion.request(100d, linear);
        motion.request(200d, linear.withPolicy(InterruptPolicy.FINISH_THEN_SWITCH));
        motion.request(300d, linear.withPolicy(InterruptPolicy.FINISH_THEN_SWITCH));
        now.set(100);
        assertEquals(100d, motion.update());
        assertEquals(300d, motion.target());
        motion.request(400d, linear.withPolicy(InterruptPolicy.SNAP));
        assertEquals(400d, motion.value());
        assertEquals(TransitionState.COMPLETED, motion.state());
    }
}
