// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.*;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Shape;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Transform;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpatialIntelligenceTest {
    private static final long BUDGET = 1_000_000_000;
    private static UiId id(String value) { return new UiId("test", value); }
    private static Rect rect(double x, double y, double width, double height) {
        return new Rect(x, y, width, height, Space.GUI);
    }
    private static Node node(String id, double x, double y, int priority) {
        return new Node(id(id), null, rect(x, y, 30, 20),
                new Constraints(Anchor.ABSOLUTE, 30, 20, 30, 20, 30, 20, 0, priority, true, true));
    }
    private static MotionState state(double x, double y, double angle) {
        return new MotionState(Shape.rectangle(20, 10), new Transform(x, y, 1, 1, angle, 10, 5));
    }

    @Test void rectanglesRejectInvalidExtentsAndMixedCoordinateSpaces() {
        assertThrows(IllegalArgumentException.class, () -> rect(0, 0, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> rect(Double.NaN, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> rect(Double.MAX_VALUE, 0, Double.MAX_VALUE, 1));
        var gui = rect(0, 0, 10, 10);
        var pixels = new Rect(0, 0, 10, 10, Space.FRAMEBUFFER);
        assertThrows(IllegalArgumentException.class, () -> gui.intersects(pixels));
        assertThrows(IllegalArgumentException.class, () -> gui.union(pixels));
        assertTrue(gui.contains(0, 0));
        assertFalse(gui.contains(10, 5));
        assertEquals(rect(10, 0, 0, 10), gui.intersection(rect(10, 0, 4, 10)));
        assertEquals(gui, rect(50, 50, 0, 0).union(gui));
    }

    @Test void layoutIsDeterministicWhenDeclarationOrderChanges() {
        var a = node("a", 5, 5, 80);
        var b = node("b", 5, 5, 20);
        var viewport = rect(0, 0, 160, 90);
        var forward = SpatialIntelligence.solve(List.of(a, b), viewport, List.of(), 64, BUDGET);
        var reverse = SpatialIntelligence.solve(List.of(b, a), viewport, List.of(), 64, BUDGET);
        assertEquals(forward.bounds(), reverse.bounds());
        assertFalse(forward.bounds().get(a.id()).intersects(forward.bounds().get(b.id())));
        assertEquals(a.preferred(), forward.bounds().get(a.id()));
        assertTrue(forward.conflicts().isEmpty(), forward.conflicts().toString());
    }

    @Test void safeAreasRemainFixedAndEveryResolvedNodeFitsViewport() {
        var viewport = rect(0, 0, 200, 120);
        var safe = rect(70, 0, 60, 40);
        var child = node("safe", 80, 5, 10);
        var result = SpatialIntelligence.solve(List.of(child), viewport, List.of(safe), 64, BUDGET);
        var bounds = result.bounds().get(child.id());
        assertTrue(viewport.contains(bounds));
        assertFalse(safe.intersects(bounds));
        assertTrue(result.conflicts().isEmpty(), result.conflicts().toString());
    }

    @Test void parentLocalAnchorsResolveAfterParentAndDoNotCollideWithParent() {
        var parent = new Node(id("parent"), null, rect(20, 10, 100, 70), Constraints.defaults(100, 70));
        var policy = new Constraints(Anchor.TOP_CENTER, 20, 10, 20, 10, 20, 10, 3, 10, true, true);
        var child = new Node(id("child"), parent.id(), new Rect(0, 0, 20, 10, Space.LOCAL), policy);
        var result = SpatialIntelligence.solve(List.of(child, parent), rect(0, 0, 200, 120), List.of(), 64, BUDGET);
        assertEquals(rect(60, 13, 20, 10), result.bounds().get(child.id()));
        assertTrue(result.conflicts().isEmpty(), result.conflicts().toString());
    }

    @Test void impossibleMinimumIsReportedInsteadOfSilentlyShrunk() {
        var result = SpatialIntelligence.solve(List.of(node("too_large", 0, 0, 1)),
                rect(0, 0, 10, 10), List.of(), 64, BUDGET);
        assertTrue(result.conflicts().stream().anyMatch(c -> c.code().equals("MINIMUM_SIZE")));
    }

    @Test void missingParentsAndCyclesHaveExplicitConflicts() {
        var a = new Node(id("a"), id("b"), rect(0, 0, 10, 10), Constraints.defaults(10, 10));
        var b = new Node(id("b"), id("a"), rect(0, 0, 10, 10), Constraints.defaults(10, 10));
        var result = SpatialIntelligence.solve(List.of(a, b), rect(0, 0, 100, 100), List.of(), 64, BUDGET);
        assertEquals(2, result.conflicts().size());
        assertTrue(result.bounds().isEmpty());
        assertFalse(result.budgetExceeded());
    }

    @Test void boundedPlacementReportsUnprocessedNodes() {
        var result = SpatialIntelligence.solve(List.of(node("a", 0, 0, 1), node("b", 50, 0, 1)),
                rect(0, 0, 200, 100), List.of(), 1, BUDGET);
        assertTrue(result.budgetExceeded());
        assertTrue(result.iterations() <= 1);
        assertEquals(1, result.bounds().size());
        assertTrue(result.conflicts().stream().anyMatch(c -> c.code().equals("LAYOUT_BUDGET")));
    }

    @Test void rotationSweptBoundsIncludeIntermediateCoverage() {
        var trajectory = new Trajectory(List.of(new Keyframe(0, state(0, 0, 0)),
                new Keyframe(1, state(0, 0, Math.PI))), "no future input");
        var prediction = SpatialIntelligence.predict(id("rotating"), trajectory, 1, 17, BUDGET);
        assertTrue(prediction.sweptBounds().contains(10, -4));
        assertTrue(prediction.conservative());
        assertTrue(prediction.path().size() > 2);
        assertEquals("no future input", prediction.condition());
        assertEquals(0, trajectory.keyframes().get(0).state().transform().radians());
    }

    @Test void fullRotationCannotDisappearWhenEndpointsAreIdentical() {
        var trajectory = new Trajectory(List.of(new Keyframe(0, state(0, 0, 0)),
                new Keyframe(1, state(0, 0, Math.PI * 2))), "spin");
        var prediction = SpatialIntelligence.predict(id("spin"), trajectory, 1, 2, BUDGET);
        assertTrue(prediction.sweptBounds().height() >= 20);
        assertTrue(prediction.conservative());
    }

    @Test void translationCollisionReportsInteriorTimeInterval() {
        var moving = SpatialIntelligence.predict(id("moving"), new Trajectory(List.of(
                new Keyframe(0, state(0, 0, 0)), new Keyframe(1, state(100, 0, 0))), "linear"), 1, 2, BUDGET);
        var fixed = SpatialIntelligence.predict(id("fixed"), new Trajectory(List.of(
                new Keyframe(0, state(50, 0, 0)), new Keyframe(1, state(50, 0, 0))), "linear"), 1, 2, BUDGET);
        var collision = SpatialIntelligence.collisions(moving, fixed);
        assertFalse(collision.intervals().isEmpty());
        var interval = collision.intervals().get(0);
        assertEquals(.3, interval.fromSeconds(), 1e-8);
        assertEquals(.7, interval.toSeconds(), 1e-8);
        assertFalse(collision.conservative());
    }

    @Test void separatedTrajectoriesDoNotClaimCollision() {
        var left = SpatialIntelligence.predict(id("left"), new Trajectory(List.of(
                new Keyframe(0, state(0, 0, 0)), new Keyframe(1, state(100, 0, 0))), "linear"), 1, 4, BUDGET);
        var right = SpatialIntelligence.predict(id("right"), new Trajectory(List.of(
                new Keyframe(0, state(0, 50, 0)), new Keyframe(1, state(100, 50, 0))), "linear"), 1, 4, BUDGET);
        assertTrue(SpatialIntelligence.collisions(left, right).intervals().isEmpty());
    }

    @Test void springProjectionPreservesInitialVelocityAndDoesNotMutateInput() {
        var initial = state(10, 0, 0);
        var projected = SpatialIntelligence.spring(initial, state(80, 0, 0),
                new Velocity(30, 0, 0, 0, 0, 0, 0), .2, .8, .4, 32);
        assertEquals(initial, projected.keyframes().get(0).state());
        assertTrue(projected.keyframes().get(1).state().transform().x() > 10);
        assertEquals(10, initial.transform().x());
        assertTrue(projected.condition().contains("spring"));
    }

    @Test void sampledSpringDoesNotClaimAnExactContinuousMotionErrorBound() {
        var spring = SpatialIntelligence.spring(state(0, 0, 0), state(100, 0, 0), Velocity.ZERO, .2, .8, .4, 8);
        var prediction = SpatialIntelligence.predict(id("spring"), spring, .4, 8, BUDGET);
        assertTrue(prediction.conservative());
        assertEquals(-1, prediction.spatialError(), "The live frame cadence is unknown, so continuous-model error is unknown");
    }

    @Test void sparseSamplingRejectsTrajectoriesThatCrossSingularScales() {
        var mirrored = new MotionState(Shape.rectangle(20, 10), new Transform(0, 0, -1, 1, 0, 10, 5));
        assertThrows(IllegalArgumentException.class, () -> new Trajectory(List.of(
                new Keyframe(0, state(0, 0, 0)), new Keyframe(1, mirrored)), "invalid scale crossing"));
    }

    @Test void safeAreasCanBeExplicitlyIgnoredWithoutDisablingNodeCollisionPolicy() {
        var policy = new Constraints(Anchor.ABSOLUTE, 30, 20, 30, 20, 30, 20, 0, 0, false, true);
        var declared = new Node(id("ignore_safe"), null, rect(50, 20, 30, 20), policy);
        var result = SpatialIntelligence.solve(List.of(declared), rect(0, 0, 200, 100),
                List.of(rect(40, 10, 80, 60)), 64, BUDGET);
        assertEquals(declared.preferred(), result.bounds().get(declared.id()));
        assertTrue(result.conflicts().isEmpty());
    }

    @Test void preferredBoundsResolveAnchorsWithoutRepairingOutOfViewportIntent() {
        var absolute = node("unrepaired", 380, 20, 1);
        assertEquals(absolute.preferred(), SpatialIntelligence.preferred(absolute, rect(0, 0, 400, 240), null));
        var centered = new Node(id("centered"), null, rect(0, 0, 100, 30),
                new Constraints(Anchor.TOP_CENTER, 40, 20, 100, 30, 200, 100, 4, 1, true, true));
        assertEquals(rect(150, 4, 100, 30), SpatialIntelligence.preferred(centered, rect(0, 0, 400, 240), null));
    }
}
