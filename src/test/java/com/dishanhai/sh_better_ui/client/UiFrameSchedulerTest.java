package com.dishanhai.sh_better_ui.client;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCapsuleReveal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：顶层帧快照、父页预绘和界面完成动作的回归覆盖。 */
class UiFrameSchedulerTest {
    @AfterEach
    void clearFrame() {
        UiFrameScheduler.abortFrame();
    }

    @Test
    void nestedPreviewUsesTheSameSnapshotAndOpeningSpringStartsOnThatClock() {
        var owner = screen();
        long now = UiFrameScheduler.rawNowMs();
        UiFrameScheduler.beginFrame(now, () -> owner);
        var motion = ShBetterUiApi.motion();
        var spring = motion.openingSpring(Spring.PANEL);

        UiFrameScheduler.renderPreview(() -> {
            assertEquals(now, PageTransition.nowMs());
            assertEquals(now, motion.nowMs());
            spring.update(motion.nowMs());
            assertEquals(0f, spring.value());
        });

        UiFrameScheduler.endFrame();
        UiFrameScheduler.beginFrame(now + 16L, () -> owner);
        spring.update(motion.nowMs());
        assertTrue(spring.value() > 0f);
        assertTrue(spring.value() < 1f);
    }

    @Test
    void completionRunsAtFrameTailAndRechecksOwnerBetweenActions() {
        var owner = screen();
        var parent = screen();
        var active = new AtomicReference<>(owner);
        var calls = new ArrayList<String>();
        UiFrameScheduler.beginFrame(100L, active::get);

        UiFrameScheduler.renderPreview(() -> {
            UiFrameScheduler.afterFrame(parent, () -> calls.add("inactive"));
            UiFrameScheduler.afterFrame(owner, () -> calls.add("preview"));
        });
        UiFrameScheduler.afterFrame(owner, () -> {
            calls.add("first");
            active.set(parent);
        });
        UiFrameScheduler.afterFrame(owner, () -> calls.add("stale"));
        assertTrue(calls.isEmpty());

        UiFrameScheduler.endFrame();
        assertEquals(List.of("first"), calls);
    }

    @Test
    void closingOwnerInvalidatesQueuedActionsEvenWhenTheSameScreenIsRestored() {
        var owner = screen();
        var calls = new AtomicInteger();
        UiFrameScheduler.beginFrame(100L, () -> owner);
        UiFrameScheduler.afterFrame(owner, () -> UiFrameScheduler.ownerClosing(owner,
                () -> UiFrameScheduler.afterFrame(owner, calls::incrementAndGet)));
        UiFrameScheduler.afterFrame(owner, calls::incrementAndGet);
        UiFrameScheduler.endFrame();
        assertEquals(0, calls.get());
    }

    @Test
    void reopeningInvalidatesQueuedCapsuleCloseBeforeTail() {
        var owner = screen();
        var calls = new AtomicInteger();
        var reveal = new UiCapsuleReveal();
        var reduced = new UiPreferences(false, true, false);
        UiFrameScheduler.beginFrame(100L, () -> owner);

        reveal.close(reduced, calls::incrementAndGet);
        assertTrue(reveal.closing());
        assertEquals(0, calls.get());
        assertTrue(reveal.reopen());
        UiFrameScheduler.endFrame();

        assertFalse(reveal.closing());
        assertEquals(0, calls.get());
    }

    @Test
    void reusingTheScreenCannotResurrectItsPreviousCapsuleClose() {
        var owner = screen();
        var replacement = screen();
        var active = new AtomicReference<>(owner);
        var calls = new ArrayList<String>();
        var reveal = new UiCapsuleReveal();
        var reduced = new UiPreferences(false, true, false);
        UiFrameScheduler.beginFrame(100L, active::get);

        reveal.close(reduced, () -> calls.add("stale"));
        assertTrue(reveal.closing());
        UiFrameScheduler.ownerClosing(owner);
        active.set(replacement);
        UiFrameScheduler.endFrame();
        assertTrue(calls.isEmpty());

        active.set(owner);
        UiFrameScheduler.beginFrame(200L, active::get);
        assertFalse(reveal.active());
        assertTrue(reveal.allowsPointer(10, 10));
        reveal.finish();
        reveal.close(reduced, () -> calls.add("current"));
        assertTrue(reveal.closing());
        UiFrameScheduler.endFrame();
        assertEquals(List.of("current"), calls);
    }

    @Test
    void newFrameAndFailedTailClearEarlierState() {
        var owner = screen();
        var calls = new AtomicInteger();
        UiFrameScheduler.beginFrame(100L, () -> owner);
        UiFrameScheduler.afterFrame(owner, calls::incrementAndGet);
        UiFrameScheduler.beginFrame(200L, () -> owner);
        assertEquals(200L, UiFrameScheduler.nowMs());
        UiFrameScheduler.afterFrame(owner, () -> {
            throw new IllegalStateException();
        });
        assertThrows(IllegalStateException.class, UiFrameScheduler::endFrame);
        UiFrameScheduler.endFrame();
        assertEquals(0, calls.get());
        UiFrameScheduler.afterFrame(null, calls::incrementAndGet);
        assertEquals(1, calls.get());
    }

    private static Screen screen() {
        return new Screen(Component.empty()) {};
    }
}
