// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.live;

import com.dishanhai.sh_better_ui.kernel.api.live.SurfaceGeometry;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfacePlacement.Rect;
import com.dishanhai.sh_better_ui.kernel.internal.live.SurfaceTransition.Target;
import org.junit.jupiter.api.Test;

import static com.dishanhai.sh_better_ui.kernel.api.live.SurfacePresentation.*;
import static com.dishanhai.sh_better_ui.kernel.internal.live.SurfaceTransition.Interruption.*;
import static com.dishanhai.sh_better_ui.kernel.internal.live.SurfaceTransition.Motion.*;
import static org.junit.jupiter.api.Assertions.*;

class SurfaceTransitionTest {
    private static final Rect VIEWPORT = new Rect(0, 0, 300, 180);
    private static final Target<String> A = new Target<>(COMPACT, new SurfaceGeometry(60, 12, 80, 20, 8, 1), "a");
    private static final Target<String> B = new Target<>(EXPANDED, new SurfaceGeometry(110, 12, 140, 68, 12, 1), "b");
    private static final Target<String> C = new Target<>(SPLIT, new SurfaceGeometry(10, 12, 125, 30, 8, 1), "c");

    @Test
    void fullMotionExposesSeparateRequestedCommittedDrawnAndInputStates() {
        var transition = new SurfaceTransition<String>();
        transition.update(A, VIEWPORT, 0, FULL, SNAP);
        transition.update(B, VIEWPORT, 10, FULL, RETARGET);
        var frame = transition.update(B, VIEWPORT, 35, FULL, RETARGET);
        assertEquals(B, frame.target());
        assertEquals(A, frame.current());
        assertTrue(frame.animated().width() > A.geometry().width());
        assertTrue(frame.animated().width() < B.geometry().width());
        assertEquals(HIDDEN, frame.input().presentation());
        assertEquals(2, frame.contentMix().size());

        var settled = transition.update(B, VIEWPORT, 2000, FULL, RETARGET);
        assertFalse(settled.running());
        assertEquals(B, settled.current());
        assertEquals(B.geometry(), settled.animated());
        assertSame(settled.animated(), settled.input().geometry());
        assertTrue(settled.input().geometry().contains(130, 20));
    }

    @Test
    void preservesTheDrawnGeometryAndContentMixAtAnInterruption() {
        var transition = new SurfaceTransition<String>();
        transition.update(A, VIEWPORT, 0, FULL, SNAP);
        transition.update(B, VIEWPORT, 10, FULL, RETARGET);
        var before = transition.update(B, VIEWPORT, 60, FULL, RETARGET);
        var after = transition.update(C, VIEWPORT, 60, FULL, PRESERVE_VELOCITY);
        assertEquals(before.animated(), after.animated());
        assertEquals(before.contentMix(), after.contentMix());
        var next = transition.update(C, VIEWPORT, 61, FULL, PRESERVE_VELOCITY);
        assertTrue(next.animated().x() > after.animated().x());
        assertEquals(1, next.contentMix().stream().mapToDouble(SurfaceTransition.Layer::weight).sum(), 1e-7);
    }

    @Test
    void reducedMotionFadesWithoutMovingGeometry() {
        var transition = new SurfaceTransition<String>();
        transition.update(A, VIEWPORT, 0, REDUCED, SNAP);
        var frame = transition.update(B, VIEWPORT, 10, REDUCED, RETARGET);
        assertEquals(B.geometry(), frame.animated());
        assertEquals("a", frame.contentMix().get(0).content());
        assertTrue(frame.running());
        var noMotion = transition.update(C, VIEWPORT, 10, NONE, RETARGET);
        assertEquals(C, noMotion.current());
        assertEquals("c", noMotion.contentMix().get(0).content());
        assertFalse(noMotion.running());
    }

    @Test
    void completeThenStartKeepsTheCurrentDestinationAndOnlyTheLatestSuccessor() {
        var transition = new SurfaceTransition<String>();
        transition.update(A, VIEWPORT, 0, FULL, SNAP);
        transition.update(B, VIEWPORT, 10, FULL, RETARGET);
        var queued = transition.update(A, VIEWPORT, 20, FULL, COMPLETE_THEN_START);
        assertEquals(A, queued.target());
        transition.update(C, VIEWPORT, 25, FULL, COMPLETE_THEN_START);
        var next = transition.update(C, VIEWPORT, 1200, FULL, COMPLETE_THEN_START);
        assertEquals(B, next.current());
        assertEquals(B.geometry(), next.animated());
        assertEquals(C, next.target());
        assertTrue(next.running());
    }

    @Test
    void cancelStopsTheDrawnStateAndReleasesInput() {
        var transition = new SurfaceTransition<String>();
        transition.update(A, VIEWPORT, 0, FULL, SNAP);
        transition.update(B, VIEWPORT, 10, FULL, RETARGET);
        var before = transition.update(B, VIEWPORT, 40, FULL, RETARGET);
        var cancelled = transition.update(C, VIEWPORT, 40, FULL, CANCEL);
        assertEquals(before.animated(), cancelled.animated());
        assertEquals(before.contentMix(), cancelled.contentMix());
        assertEquals(HIDDEN, cancelled.input().presentation());
        assertFalse(cancelled.running());
        assertEquals(cancelled.animated(), transition.update(C, VIEWPORT, 300, FULL, CANCEL).animated());
    }

    @Test
    void hidingDisablesInputWhileTheSurfaceIsStillDrawn() {
        var transition = new SurfaceTransition<String>();
        transition.update(A, VIEWPORT, 0, FULL, SNAP);
        var hidden = new Target<String>(SUSPENDED, SurfaceGeometry.HIDDEN, null);
        var fading = transition.update(hidden, VIEWPORT, 20, FULL, RETARGET);
        assertEquals(A.geometry(), fading.animated());
        assertEquals(HIDDEN, fading.input().presentation());
        assertEquals("a", fading.contentMix().get(0).content());
        var settled = transition.update(hidden, VIEWPORT, 1000, FULL, RETARGET);
        assertEquals(SUSPENDED, settled.current().presentation());
        assertEquals(SurfaceGeometry.HIDDEN, settled.animated());
        assertTrue(settled.contentMix().isEmpty());
    }

    @Test
    void aContentRefreshWithTheSameIdentityDoesNotRestartTheMix() {
        var transition = new SurfaceTransition<String>();
        transition.update(A, VIEWPORT, 0, FULL, SNAP);
        transition.update(B, VIEWPORT, 10, FULL, RETARGET);
        var before = transition.update(B, VIEWPORT, 60, FULL, RETARGET);
        var updated = new Target<>(B.presentation(), B.geometry(), "new b", B.content());
        var after = transition.update(updated, VIEWPORT, 60, FULL, RETARGET);
        assertEquals(before.contentMix().get(1).weight(), after.contentMix().get(1).weight());
        assertEquals("new b", after.contentMix().get(1).content());
    }

    @Test
    void resizingClipsEachSampleToTheSafeBounds() {
        var transition = new SurfaceTransition<String>();
        transition.update(B, VIEWPORT, 0, FULL, SNAP);
        var resized = new Rect(6, 8, 64, 30);
        for (long now : new long[]{5, 17, 65, 700}) {
            var geometry = transition.update(A, resized, now, FULL, PRESERVE_VELOCITY).animated();
            assertTrue(geometry.x() >= resized.x());
            assertTrue(geometry.y() >= resized.y());
            assertTrue(geometry.x() + geometry.width() <= resized.right());
            assertTrue(geometry.y() + geometry.height() <= resized.bottom());
        }
    }
}
