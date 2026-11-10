// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel;

import com.dishanhai.sh_better_ui.kernel.api.live.SurfaceGeometry;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfacePlacement;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfacePlacement.Candidate;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfacePlacement.Rect;
import com.dishanhai.sh_better_ui.kernel.api.live.SurfacePlacement.Result;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.dishanhai.sh_better_ui.kernel.api.live.SurfacePlacement.CollisionPolicy.*;
import static com.dishanhai.sh_better_ui.kernel.api.live.SurfacePresentation.*;
import static org.junit.jupiter.api.Assertions.*;

class SurfacePlacementTest {
    private static final Rect VIEWPORT = new Rect(0, 0, 120, 90);
    private static final Candidate COMPACT_AT_TOP = new Candidate(COMPACT,
            new SurfaceGeometry(30, 0, 60, 20, 8, 1), 40, 14);
    private static final Candidate MINIMAL_AT_TOP = new Candidate(MINIMAL,
            new SurfaceGeometry(45, 0, 30, 12, 6, 1));

    @Test
    void hitTestUsesTheVisibleRoundedShape() {
        var geometry = new SurfaceGeometry(10, 20, 50, 10, 50, 2);
        assertEquals(5, geometry.radius());
        assertEquals(1, geometry.opacity());
        assertFalse(geometry.contains(10, 20));
        assertTrue(geometry.contains(15, 20));
        assertTrue(geometry.contains(10, 25));
        assertFalse(geometry.contains(60, 25));
        assertFalse(SurfaceGeometry.HIDDEN.contains(0, 0));
    }

    @Test
    void normalizesInvalidBoundsAndKeepsTheSurfaceInTheSafeViewport() {
        var viewport = new Rect(10, 20, 52, 24);
        var candidate = new Candidate(FLOATING, new SurfaceGeometry(-20, 80, 70, 20, 8, 1), 30, 16);
        var result = SurfacePlacement.place(viewport, List.of(candidate), List.of(), AVOID, null);
        assertEquals(new SurfaceGeometry(10, 24, 52, 20, 8, 1), result.geometry());
        assertEquals(HIDDEN, SurfacePlacement.place(new Rect(0, 0, -20, Double.NaN),
                List.of(candidate), List.of(), OVERLAY, null).presentation());
    }

    @Test
    void compressesWithinMeasuredBoundsBeforeMovingTheSurface() {
        var obstacle = new Rect(0, 0, 35, 30);
        var result = SurfacePlacement.place(VIEWPORT, List.of(COMPACT_AT_TOP), List.of(obstacle), AVOID, null);
        assertEquals(COMPACT, result.presentation());
        assertEquals(new SurfaceGeometry(35, 0, 50, 20, 8, 1), result.geometry());
    }

    @Test
    void triesMinimalAtTheAnchorBeforeShifting() {
        var result = SurfacePlacement.place(VIEWPORT, List.of(COMPACT_AT_TOP, MINIMAL_AT_TOP),
                List.of(new Rect(0, 0, 42, 30)), AVOID, null);
        assertEquals(MINIMAL, result.presentation());
        assertEquals(MINIMAL_AT_TOP.geometry(), result.geometry());
    }

    @Test
    void keepsPreviousPresentationWhenAResizeBarelyClearsTheObstacle() {
        var compact = new Candidate(COMPACT, COMPACT_AT_TOP.geometry());
        var candidates = List.of(compact, MINIMAL_AT_TOP);
        Result previous = SurfacePlacement.place(VIEWPORT, candidates, List.of(new Rect(0, 0, 31, 20)), AVOID, null);
        assertEquals(MINIMAL, previous.presentation());
        assertEquals(MINIMAL, SurfacePlacement.place(VIEWPORT, candidates,
                List.of(new Rect(0, 0, 29, 20)), AVOID, previous).presentation());
        assertEquals(COMPACT, SurfacePlacement.place(VIEWPORT, candidates,
                List.of(new Rect(0, 0, 25, 20)), AVOID, previous).presentation());
    }

    @Test
    void keepsAValidPreviousShiftWhenItIsNearlyAsClose() {
        var candidate = new Candidate(FLOATING, new SurfaceGeometry(45, 40, 30, 20, 4, 1));
        var obstacle = new Rect(43, 40, 34, 20);
        var previous = new Result(FLOATING, new SurfaceGeometry(45, 61, 30, 20, 4, 1));
        assertEquals(previous, SurfacePlacement.place(VIEWPORT, List.of(candidate), List.of(obstacle), SHIFT, previous));
    }

    @Test
    void shiftCanFindADiagonalOpening() {
        var candidate = new Candidate(FLOATING, new SurfaceGeometry(45, 32, 30, 20, 4, 1));
        var obstacles = List.of(new Rect(30, 0, 60, 90), new Rect(0, 20, 120, 50));
        var result = SurfacePlacement.place(VIEWPORT, List.of(candidate), obstacles, SHIFT, null);
        assertEquals(FLOATING, result.presentation());
        assertEquals(new SurfaceGeometry(0, 0, 30, 20, 4, 1), result.geometry());
        var shifted = new Rect(result.geometry().x(), result.geometry().y(),
                result.geometry().width(), result.geometry().height());
        assertTrue(obstacles.stream().noneMatch(shifted::intersects));
    }

    @Test
    void fractionalShiftStaysOutsideTheObstacleAfterRounding() {
        var viewport = new Rect(0, 0, 80, 60);
        var candidate = new Candidate(FLOATING, new SurfaceGeometry(55.7, 45, 14.4, 9.2, 4, 1));
        var obstacles = List.of(new Rect(30.7, 48.9, 32.3, 29.5), new Rect(63.8, 27.8, 12.8, 39.8));
        var placed = SurfacePlacement.place(viewport, List.of(candidate), obstacles, SHIFT, null).geometry();
        assertTrue(placed.visible());
        var shifted = new Rect(placed.x(), placed.y(), placed.width(), placed.height());
        assertTrue(obstacles.stream().noneMatch(shifted::intersects));
        assertEquals(49.4, placed.x(), 1e-10);
        assertEquals(39.7, placed.y(), 1e-10);
    }

    @Test
    void explicitCollisionPoliciesChooseTheirFallback() {
        var candidates = List.of(COMPACT_AT_TOP, MINIMAL_AT_TOP);
        var obstacle = List.of(new Rect(0, 0, 42, 30));
        assertEquals(COMPACT_AT_TOP.geometry(),
                SurfacePlacement.place(VIEWPORT, candidates, obstacle, OVERLAY, null).geometry());
        assertEquals(HIDDEN, SurfacePlacement.place(VIEWPORT, candidates, obstacle, HIDE, null).presentation());
        assertEquals(MINIMAL, SurfacePlacement.place(VIEWPORT, candidates, obstacle, COLLAPSE, null).presentation());
        assertEquals(HIDDEN, SurfacePlacement.place(VIEWPORT, List.of(COMPACT_AT_TOP), obstacle, COMPRESS, null).presentation());
    }
}
