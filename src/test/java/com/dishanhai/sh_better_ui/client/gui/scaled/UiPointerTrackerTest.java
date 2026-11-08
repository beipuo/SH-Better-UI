package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：验证 GUI 变换命中、有效按压和取消不触发释放。 */
class UiPointerTrackerTest {
    private static final Bounds AREA = new Bounds(10, 20, 100, 80);
    private static final Matrix4f IDENTITY = new Matrix4f();

    @Test
    void mapsTranslatedScaledRotatedPoseToLocalCoordinates() {
        var tracker = new UiPointerTracker();
        var pose = new Matrix4f().translate(150, 90, 0).rotateZ(.7f).scale(1.5f, 2.5f, 1);
        var screen = pose.transformPosition(new Vector3f(35, 60, 0));
        var frame = tracker.update(AREA, pose, mouse(screen.x, screen.y, 0), true, 100);
        assertTrue(frame.inside());
        assertEquals(25, frame.x(), .001);
        assertEquals(40, frame.y(), .001);
    }

    @Test
    void acceptsPressAndReleaseOnlyAfterAnInsideUpSample() {
        var tracker = new UiPointerTracker();
        var first = tracker.update(AREA, IDENTITY, mouse(30, 40, 1), true, 0);
        assertFalse(first.down());
        assertFalse(first.pressed());
        assertFalse(tracker.update(AREA, IDENTITY, mouse(30, 40, 1), true, 10).pressed());
        assertFalse(tracker.update(AREA, IDENTITY, mouse(30, 40, 0), true, 20).released());
        var press = tracker.update(AREA, IDENTITY, mouse(30, 40, 1), true, 30);
        assertTrue(press.down());
        assertTrue(press.pressed());
        assertFalse(tracker.update(AREA, IDENTITY, mouse(30, 40, 1), true, 40).pressed());
        var release = tracker.update(AREA, IDENTITY, mouse(30, 40, 0), true, 50);
        assertTrue(release.released());
        assertFalse(release.down());
        assertFalse(release.cancelled());
        assertFalse(tracker.update(AREA, IDENTITY, mouse(30, 40, 0), true, 60).released());
    }

    @Test
    void leavingCancelsOnceAndHeldReentryDoesNotCreateAnotherPress() {
        var tracker = pressedTracker();
        var left = tracker.update(AREA, IDENTITY, mouse(110, 40, 1), true, 20);
        assertTrue(left.cancelled());
        assertFalse(left.released());
        assertFalse(left.inside());
        assertEquals(Long.MAX_VALUE, left.idleMillis());
        assertFalse(tracker.update(AREA, IDENTITY, mouse(120, 40, 1), true, 30).cancelled());
        var reentry = tracker.update(AREA, IDENTITY, mouse(30, 40, 1), true, 40);
        assertTrue(reentry.inside());
        assertFalse(reentry.down());
        assertFalse(reentry.pressed());
        assertFalse(tracker.update(AREA, IDENTITY, mouse(30, 40, 0), true, 50).released());
        assertTrue(tracker.update(AREA, IDENTITY, mouse(30, 40, 1), true, 60).pressed());
    }

    @Test
    void lossOfFocusGrabWindowLeaveAndMissingSnapshotCancelWithoutReleasing() {
        UiMouseState[] unavailable = {
                new UiMouseState(30, 40, 30, 40, false, false, true, 1),
                new UiMouseState(30, 40, 30, 40, true, true, true, 1),
                new UiMouseState(30, 40, 30, 40, true, false, false, 1),
                null
        };
        for (var mouse : unavailable) {
            var tracker = pressedTracker();
            var cancelled = tracker.update(AREA, IDENTITY, mouse, true, 20);
            assertTrue(cancelled.cancelled());
            assertFalse(cancelled.released());
            assertFalse(tracker.update(AREA, IDENTITY, mouse, true, 30).cancelled());
            assertFalse(tracker.update(AREA, IDENTITY, mouse(30, 40, 0), true, 40).released());
        }
    }

    @Test
    void disablingOrSingularPoseCancelsActivePress() {
        var disabled = pressedTracker().update(AREA, IDENTITY, mouse(30, 40, 1), false, 20);
        assertTrue(disabled.cancelled());
        assertFalse(disabled.released());
        var singular = pressedTracker().update(AREA, new Matrix4f().scale(0, 1, 1),
                mouse(30, 40, 1), true, 20);
        assertTrue(singular.cancelled());
        assertFalse(singular.released());
    }

    @Test
    void rejectsNonFiniteCoordinatesAndMatrices() {
        for (double coordinate : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertFalse(new UiPointerTracker().update(AREA, IDENTITY, mouse(coordinate, 40, 0), true, 0).inside());
        }
        var pose = new Matrix4f().m00(Float.NaN);
        assertFalse(new UiPointerTracker().update(AREA, pose, mouse(30, 40, 0), true, 0).inside());
        pose = new Matrix4f().m30(Float.POSITIVE_INFINITY);
        assertFalse(new UiPointerTracker().update(AREA, pose, mouse(30, 40, 0), true, 0).inside());
    }

    @Test
    void includesTopLeftButExcludesBottomRightAndEmptyAreas() {
        assertTrue(new UiPointerTracker().update(AREA, IDENTITY, mouse(10, 20, 0), true, 0).inside());
        assertFalse(new UiPointerTracker().update(AREA, IDENTITY, mouse(110, 30, 0), true, 0).inside());
        assertFalse(new UiPointerTracker().update(AREA, IDENTITY, mouse(20, 100, 0), true, 0).inside());
        assertFalse(new UiPointerTracker().update(new Bounds(10, 20, 0, 80), IDENTITY,
                mouse(10, 20, 0), true, 0).inside());
    }

    @Test
    void measuresIdleFromPositionChangesAndResetsOnReentry() {
        var tracker = new UiPointerTracker();
        assertEquals(0, tracker.update(AREA, IDENTITY, mouse(30, 40, 0), true, 100).idleMillis());
        assertEquals(2000, tracker.update(AREA, IDENTITY, mouse(30, 40, 0), true, 2100).idleMillis());
        assertEquals(0, tracker.update(AREA, IDENTITY, mouse(31, 40, 0), true, 2200).idleMillis());
        assertEquals(0, tracker.update(AREA, IDENTITY, mouse(31, 40, 0), true, 2100).idleMillis());
        tracker.update(AREA, IDENTITY, null, true, 2300);
        assertEquals(0, tracker.update(AREA, IDENTITY, mouse(31, 40, 0), true, 2400).idleMillis());
    }

    @Test
    void ignoresOtherButtonsAndResetDoesNotEmitARelease() {
        var tracker = new UiPointerTracker();
        tracker.update(AREA, IDENTITY, mouse(30, 40, 0), true, 0);
        assertFalse(tracker.update(AREA, IDENTITY, mouse(30, 40, 2), true, 10).pressed());
        assertFalse(tracker.update(AREA, IDENTITY, mouse(30, 40, 2), true, 20).down());
        tracker.update(AREA, IDENTITY, mouse(30, 40, 1), true, 30);
        tracker.reset();
        var frame = tracker.update(AREA, IDENTITY, mouse(30, 40, 0), true, 40);
        assertFalse(frame.released());
        assertFalse(frame.cancelled());
    }

    private static UiPointerTracker pressedTracker() {
        var tracker = new UiPointerTracker();
        tracker.update(AREA, IDENTITY, mouse(30, 40, 0), true, 0);
        assertTrue(tracker.update(AREA, IDENTITY, mouse(30, 40, 1), true, 10).down());
        return tracker;
    }

    private static UiMouseState mouse(double x, double y, int buttons) {
        return new UiMouseState(x, y, x, y, true, false, true, buttons);
    }
}
