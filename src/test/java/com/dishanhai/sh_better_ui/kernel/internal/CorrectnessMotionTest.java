// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.UIElement;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CorrectnessMotionTest {
    private static final Rect VIEW = new Rect(0, 0, 300, 200, Space.GUI);
    private static final UIElement MOVING = UIElement.builder("example:moving")
            .motion(new UIElement.MotionPolicy(true, .25, null)).build();
    private static Rect rect(double x, double y, double w, double h) { return new Rect(x, y, w, h, Space.GUI); }
    private static long ms(long time) { return time * 1_000_000; }

    @Test void firstFrameSnapsAndSubsequentTargetChangesStartAtActualPresentation() {
        var motion = new ClientCorrectnessMotion();
        var initial = rect(10, 20, 40, 20);
        assertEquals(initial, motion.sample(1, MOVING, initial, ms(0), false).actual());
        var target = rect(160, 20, 40, 20);
        var retargeted = motion.sample(1, MOVING, target, ms(16), false);
        assertEquals(initial, retargeted.actual());
        assertTrue(retargeted.moving());
        var advanced = motion.sample(1, MOVING, target, ms(32), false);
        assertTrue(advanced.actual().x() > 10 && advanced.actual().x() < 160);
        assertNotNull(advanced.future());
        assertTrue(advanced.future().sampledModel());
    }

    @Test void futureQueriesNeverAdvanceLiveTracksAndRepeatedFrameIsIdempotent() {
        var motion = new ClientCorrectnessMotion();
        motion.sample(1, MOVING, rect(10, 20, 40, 20), ms(0), false);
        motion.sample(1, MOVING, rect(160, 20, 40, 20), ms(16), false);
        var first = motion.sample(1, MOVING, rect(160, 20, 40, 20), ms(32), false);
        var repeated = motion.sample(1, MOVING, rect(160, 20, 40, 20), ms(32), false);
        assertEquals(first.actual(), repeated.actual());
    }

    @Test void reversingTargetPreservesTheCurrentForwardMomentum() {
        var motion = new ClientCorrectnessMotion();
        motion.sample(1, MOVING, rect(20, 20, 40, 20), ms(0), false);
        motion.sample(1, MOVING, rect(160, 20, 40, 20), ms(16), false);
        var before = motion.sample(1, MOVING, rect(160, 20, 40, 20), ms(48), false);
        var reversed = motion.sample(1, MOVING, rect(0, 20, 40, 20), ms(49), false);
        assertTrue(reversed.actual().x() >= before.actual().x());
        assertTrue(reversed.actual().x() - before.actual().x() < 5);
        var next = motion.sample(1, MOVING, rect(0, 20, 40, 20), ms(50), false);
        assertTrue(next.actual().x() > reversed.actual().x(), "RETARGET retains current velocity before the spring turns around");
    }

    @Test void reducedMotionAndNonInterruptiblePolicyUseImmediateTargets() {
        var motion = new ClientCorrectnessMotion();
        motion.sample(1, MOVING, rect(10, 20, 40, 20), ms(0), false);
        var target = rect(160, 30, 80, 30);
        var reduced = motion.sample(1, MOVING, target, ms(16), true);
        assertEquals(target, reduced.actual());
        assertFalse(reduced.moving());
        var staticPolicy = MOVING.toBuilder().motion(new UIElement.MotionPolicy(false, .25, null)).build();
        var immediate = motion.sample(1, staticPolicy, rect(20, 40, 50, 25), ms(32), false);
        assertEquals(rect(20, 40, 50, 25), immediate.actual());
        assertFalse(immediate.moving());
    }

    @Test void dynamicSizeUsesTheSameLiveFourChannelPresentation() {
        var motion = new ClientCorrectnessMotion();
        motion.sample(1, MOVING, rect(10, 20, 40, 20), ms(0), false);
        motion.sample(1, MOVING, rect(10, 20, 100, 50), ms(16), false);
        var next = motion.sample(1, MOVING, rect(10, 20, 100, 50), ms(32), false);
        assertTrue(next.actual().width() > 40 && next.actual().width() < 100);
        assertTrue(next.actual().height() > 20 && next.actual().height() < 50);
    }

    @Test void forgettingAScopeReleasesTracksAndNextMountStartsFresh() {
        var motion = new ClientCorrectnessMotion();
        motion.sample(1, MOVING, rect(10, 20, 40, 20), ms(0), false);
        motion.sample(1, MOVING, rect(160, 20, 40, 20), ms(16), false);
        motion.forget(1, MOVING.id());
        assertEquals(rect(160, 20, 40, 20), motion.sample(1, MOVING, rect(160, 20, 40, 20), ms(32), false).actual());
    }

    @Test void knownObstacleChoosesABoundedDetourAndTheActualPathStaysClear() {
        var motion = new ClientCorrectnessMotion(100_000_000);
        var view = rect(0, 0, 200, 100);
        var obstacle = rect(50, 30, 25, 35);
        var start = rect(10, 40, 20, 10);
        var target = rect(130, 40, 20, 10);
        motion.environment(1, MOVING.id(), view, List.of(obstacle));
        motion.sample(1, MOVING, start, ms(0), false);
        var begin = motion.sample(1, MOVING, target, ms(16), false);
        assertEquals(start, begin.actual());
        assertTrue(begin.future().condition().contains("detour"), begin.future().condition());
        for (long time = 32; time <= 2400; time += 16) {
            var frame = motion.sample(1, MOVING, target, ms(time), false);
            assertFalse(frame.actual().intersects(obstacle), "time=" + time + ", bounds=" + frame.actual());
        }
        assertEquals(target.x(), motion.sample(1, MOVING, target, ms(2500), false).actual().x(), .1);
    }

    @Test void impossibleCorridorIsReportedWithoutTeleportingOrClaimingResolution() {
        var motion = new ClientCorrectnessMotion(100_000_000);
        var start = rect(10, 40, 20, 10);
        var target = rect(130, 40, 20, 10);
        motion.environment(1, MOVING.id(), rect(0, 0, 200, 100), List.of(rect(45, 0, 40, 100)));
        motion.sample(1, MOVING, start, ms(0), false);
        var blocked = motion.sample(1, MOVING, target, ms(16), false);
        assertEquals(start, blocked.actual());
        assertNotNull(blocked.future());
        assertTrue(blocked.future().condition().contains("UNRESOLVED"), blocked.future().condition());
    }

    @Test void exhaustedRouteBudgetIsExplicitAndLeavesTheActualPositionContinuous() {
        var motion = new ClientCorrectnessMotion(1);
        var start = rect(10, 40, 20, 10);
        motion.environment(1, MOVING.id(), rect(0, 0, 200, 100), List.of(rect(50, 30, 25, 35)));
        motion.sample(1, MOVING, start, ms(0), false);
        var frame = motion.sample(1, MOVING, rect(130, 40, 20, 10), ms(16), false);
        assertEquals(start, frame.actual());
        assertTrue(frame.future().condition().contains("UNRESOLVED"));
        assertTrue(frame.future().condition().contains("budget"));
    }
}
