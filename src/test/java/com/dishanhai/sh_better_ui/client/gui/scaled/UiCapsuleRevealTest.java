package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：快速切页继续展开，只有明确返回才收回，并且只提交一次返回。 */
class UiCapsuleRevealTest {
    private static final Bounds PANEL = new Bounds(10, 40, 400, 220);

    @Test
    void fullTargetSkeletonFadesBeforeContentAppearsInTheCapsule() {
        float fadeStart = UiCapsuleReveal.CONTENT_FADE_START;
        assertEquals(0f, UiCapsuleReveal.previewOpacity(0f));
        assertTrue(UiCapsuleReveal.previewOpacity(fadeStart / 2f) > 0f);
        assertEquals(0f, UiCapsuleReveal.previewOpacity(fadeStart));
        assertEquals(0f, UiCapsuleReveal.previewOpacity(0.7f));
    }

    @Test
    void customSkeletonUsesTheCurrentTargetLayoutWithoutRepositioningBlocks() {
        var reveal = new UiCapsuleReveal();
        assertEquals(UiSkeleton.cardBlocks(PANEL), reveal.skeletonBlocks(PANEL));

        reveal.setSkeletonLayout(target -> List.of(
                new UiSkeleton.Block(new Bounds(target.x() + 12, target.y() + 30, target.width() - 24, 18))));
        assertEquals(new Bounds(22, 70, 376, 18), reveal.skeletonBlocks(PANEL).getFirst().bounds());
        assertEquals(new Bounds(102, 95, 226, 18),
                reveal.skeletonBlocks(new Bounds(90, 65, 250, 180)).getFirst().bounds());
    }

    @Test
    void returnCapsuleIsTransparentBeforeTheScreenHandoff() {
        assertEquals(1f, LiquidGlassTheme.revealCoverage(1f));
        assertEquals(0.5f, LiquidGlassTheme.revealCoverage(0.14f));
        assertEquals(0, Math.round(255 * LiquidGlassTheme.revealCoverage(0.005f)));
        assertEquals(0f, LiquidGlassTheme.revealCoverage(0f));
    }

    @Test
    void glassFillBecomesTranslucentAfterSamplingReachesFullStrength() {
        float contentStart = UiCapsuleReveal.CONTENT_FADE_START;
        assertEquals(1f, LiquidGlassTheme.revealCoverage(contentStart));
        assertEquals(255, LiquidGlassTheme.revealFill(contentStart) >>> 24);
        assertEquals(LiquidGlassTheme.POPOVER_FILL, LiquidGlassTheme.revealFill(1f));
    }

    @Test
    void openingKeepsSamplingUntilTheSpringSettles() {
        var reveal = new UiCapsuleReveal();
        reveal.startAt(200, 45, 0);
        float previous = reveal.sampleProgress(0, UiPreferences.DEFAULT);
        for (long now = 1; now <= 5000 && reveal.active(); now++) {
            float progress = reveal.sampleProgress(now, UiPreferences.DEFAULT);
            if (progress == 1f) assertTrue(previous > 0.997f);
            previous = progress;
        }
        assertEquals(1f, previous);
        assertFalse(reveal.active());
    }

    @Test
    void closingEndsAtTheActualEntryButtonWhileOpeningKeepsTheSmallCapsule() {
        var source = new UiButton(280, 20, 120, 22, Component.empty(), ignored -> {});
        var reveal = new UiCapsuleReveal();
        reveal.startFrom(source);
        assertEquals(FluidMorph.seed(340, 31), reveal.frame(PANEL, 0f, 0).rect());
        reveal.finish();
        reveal.close(0, UiPreferences.DEFAULT, () -> {});
        assertEquals(new GuiRect(280, 20, 120, 22), reveal.frame(PANEL, 0f, 0).rect());
        assertEquals(11f, reveal.frame(PANEL, 0f, 0).radius());
    }

    @Test
    void reversingDuringOpeningKeepsTheCurrentOutline() {
        var source = new UiButton(280, 20, 120, 22, Component.empty(), ignored -> {});
        var reveal = new UiCapsuleReveal();
        reveal.startFrom(source);
        reveal.startAt(340, 31, 0);
        reveal.sampleProgress(0, UiPreferences.DEFAULT);
        float progress = reveal.sampleProgress(100, UiPreferences.DEFAULT);
        var before = reveal.frame(PANEL, progress, 100);
        reveal.close(100, UiPreferences.DEFAULT, () -> {});
        assertEquals(before, reveal.frame(PANEL, reveal.sampleProgress(100, UiPreferences.DEFAULT), 100));
        assertEquals(new GuiRect(280, 20, 120, 22), reveal.frame(PANEL, 0f, 100).rect());
        assertTrue(reveal.sampleProgress(101, UiPreferences.DEFAULT) < progress);
    }

    @Test
    void reopeningAndClosingAgainKeepTheOutlineAndOnlyCommitTheLatestClose() {
        var source = new UiButton(280, 20, 120, 22, Component.empty(), ignored -> {});
        var reveal = new UiCapsuleReveal();
        var canceled = new AtomicInteger();
        var committed = new AtomicInteger();
        reveal.startFrom(source);
        reveal.sampleProgress(0, UiPreferences.DEFAULT);
        float pivot = reveal.sampleProgress(100, UiPreferences.DEFAULT);
        reveal.close(100, UiPreferences.DEFAULT, canceled::incrementAndGet);
        float closing = reveal.sampleProgress(120, UiPreferences.DEFAULT);
        var before = reveal.frame(PANEL, closing, 120);

        assertTrue(reveal.reopen(120));
        assertFalse(reveal.closing());
        assertEquals(before, reveal.frame(PANEL, reveal.sampleProgress(120, UiPreferences.DEFAULT), 120));
        float reopening = reveal.sampleProgress(160, UiPreferences.DEFAULT);
        assertTrue(reopening > pivot && reopening < 1f);
        var expanded = reveal.frame(PANEL, reopening, 160);

        reveal.close(160, UiPreferences.DEFAULT, committed::incrementAndGet);
        assertEquals(expanded, reveal.frame(PANEL, reveal.sampleProgress(160, UiPreferences.DEFAULT), 160));
        assertFalse(reveal.commitClose());
        for (long time = 180; time <= 5000 && reveal.active(); time += 20) {
            reveal.sampleProgress(time, UiPreferences.DEFAULT);
            reveal.commitClose();
        }
        assertFalse(reveal.reopen(5000));
        assertFalse(reveal.commitClose());
        assertEquals(0, canceled.get());
        assertEquals(1, committed.get());
    }

    @Test
    void earlyReversalCommitsAtTheFirstInvisibleFrame() {
        var reveal = new UiCapsuleReveal();
        var calls = new AtomicInteger();
        reveal.startAt(200, 45, 0);
        reveal.sampleProgress(0, UiPreferences.DEFAULT);
        assertTrue(reveal.sampleProgress(16, UiPreferences.DEFAULT) > 0f);
        reveal.close(16, UiPreferences.DEFAULT, calls::incrementAndGet);
        assertEquals(0f, reveal.sampleProgress(48, UiPreferences.DEFAULT));
        assertEquals(0, calls.get());
        assertTrue(reveal.commitClose());
        assertEquals(1, calls.get());
    }

    @Test
    void parentHandoffCanStillBeReversedAfterTheCapsuleHasCollapsed() {
        var source = new UiButton(280, 20, 120, 22, Component.empty(), ignored -> {});
        var reveal = new UiCapsuleReveal();
        var calls = new AtomicInteger();
        reveal.startFrom(source);
        reveal.close(0, UiPreferences.DEFAULT, calls::incrementAndGet);

        assertTrue(reveal.renderWithHandoff(null, PANEL, UiPreferences.DEFAULT, () -> fail(), () -> false));
        assertFalse(reveal.reopenFromSource(50, 50, 0));
        assertTrue(reveal.reopenFromSource(300, 30, 0));
        assertFalse(reveal.closing());
        assertEquals(0, calls.get());
    }

    @Test
    void closingUsesTheRebuiltEntryEvenAfterTheParentRevealMovesItsOrigin() {
        var source = new AtomicReference<>(new UiButton(280, 20, 120, 22, Component.empty(), ignored -> {}));
        var reveal = new UiCapsuleReveal();
        reveal.startFrom(source::get);
        reveal.finish();
        reveal.startAt(200, 45, 0);
        reveal.finish();
        source.set(new UiButton(360, 30, 100, 24, Component.empty(), ignored -> {}));
        reveal.close(0, UiPreferences.DEFAULT, () -> {});
        assertEquals(new GuiRect(360, 30, 100, 24), reveal.frame(PANEL, 0f, 0).rect());
        source.set(new UiButton(300, 10, 80, 20, Component.empty(), ignored -> {}));
        assertEquals(new GuiRect(300, 10, 80, 20), reveal.frame(PANEL, 0f, 0).rect());
        source.set(null);
        assertEquals(FluidMorph.seed(200, 45), reveal.frame(PANEL, 0f, 0).rect());
    }

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
            assertEquals(0, calls.get(), "the close must wait for the render handoff");
        }
        assertEquals(0f, progress, "the last frame must reach the exact closing endpoint");
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
