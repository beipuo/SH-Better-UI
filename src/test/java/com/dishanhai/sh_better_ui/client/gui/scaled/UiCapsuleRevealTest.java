package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：快速切页继续展开，只有明确返回才收回，并且只提交一次返回。 */
class UiCapsuleRevealTest {
    @Test
    void rapidPageChangesNeverRetractTheCurrentOutline() {
        var reveal = new UiCapsuleReveal();
        reveal.startAt(20, 20, 0);
        float previous = reveal.sampleProgress(0, UiPreferences.DEFAULT);
        for (long time = 20; time <= 240; time += 20) {
            reveal.startAt(time, 20, time);
            float progress = reveal.sampleProgress(time, UiPreferences.DEFAULT);
            assertTrue(progress >= previous, "a new page must not retract the outline");
            previous = progress;
        }
        for (long time = 260; time <= 5000; time += 20) reveal.sampleProgress(time, UiPreferences.DEFAULT);
        assertEquals(1f, reveal.sampleProgress(5000, UiPreferences.DEFAULT));
        assertFalse(reveal.active());
    }

    @Test
    void backRetractsBeforeSubmittingAndIgnoresRepeatedCommands() {
        var reveal = new UiCapsuleReveal();
        var calls = new AtomicInteger();
        reveal.close(0, UiPreferences.DEFAULT, calls::incrementAndGet);
        assertTrue(reveal.closing());
        assertEquals(0, calls.get());
        assertEquals(1f, reveal.sampleProgress(0, UiPreferences.DEFAULT));
        reveal.close(20, UiPreferences.DEFAULT, calls::incrementAndGet);
        reveal.startAt(100, 20, 30);
        assertTrue(reveal.sampleProgress(100, UiPreferences.DEFAULT) < 1f);
        float progress = 1f;
        for (long time = 120; time <= 5000 && progress > 0f; time += 20) {
            progress = reveal.sampleProgress(time, UiPreferences.DEFAULT);
            assertEquals(0, calls.get(), "the close must wait until the seed can be drawn");
        }
        assertEquals(0f, progress, "the last frame must use the same seed as opening");
        reveal.finish();
        reveal.close(7000, UiPreferences.DEFAULT, calls::incrementAndGet);
        assertEquals(1, calls.get());
        assertFalse(reveal.closing());
    }

    @Test
    void reducedMotionReturnsImmediatelyAndFinishCompletesAnInterruptedClose() {
        var calls = new AtomicInteger();
        var reveal = new UiCapsuleReveal();
        reveal.close(0, new UiPreferences(false, true, false), calls::incrementAndGet);
        assertEquals(1, calls.get());
        reveal.finish();
        assertEquals(1, calls.get());

        var resized = new UiCapsuleReveal();
        resized.close(0, UiPreferences.DEFAULT, calls::incrementAndGet);
        resized.finish();
        assertEquals(2, calls.get());
    }
}
