package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.DotMatrixProfile.PointerLens;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：验证透镜的点位、尺寸、发光和指针生命周期。 */
class DotMatrixLensTest {
    @Test
    void bulgeMagnifiesCenterAndCompressesOuterBand() {
        var lens = new DotMatrixLens();
        var frame = settle(lens, PointerLens.DEFAULT, pointer(100, 100, false));
        var center = frame.sample(100, 100);
        assertEquals(100f, center.x());
        assertEquals(1.72f, center.scale(), 0.002f);
        assertEquals(0.8f, center.glow(), 0.002f);
        assertTrue(frame.sample(120, 100).x() > 120);
        assertTrue(frame.sample(172, 100).scale() < 1f);
        assertNull(frame.sample(196, 100));
    }

    @Test
    void negativeStrengthPinchesAndTwistRotatesAroundTheCursor() {
        var lens = new DotMatrixLens();
        var profile = new PointerLens(96, -0.6f, 90f, 0, 0, 0f, false);
        var frame = settle(lens, profile, pointer(100, 100, false));
        var point = frame.sample(120, 100);
        assertTrue(Math.hypot(point.x() - 100, point.y() - 100) < 20);
        assertTrue(point.y() > 100);
        assertEquals(0f, point.glow());
    }

    @Test
    void followsWithInertiaDeepensOnHoldAndFadesOnLeave() {
        var lens = new DotMatrixLens();
        settle(lens, PointerLens.DEFAULT, pointer(100, 100, false));
        var next = lens.advance(PointerLens.DEFAULT, pointer(140, 100, true), 2050, false);
        assertEquals(100f, next.x());
        var moving = lens.advance(PointerLens.DEFAULT, pointer(140, 100, true), 2100, false);
        assertTrue(moving.x() > 100 && moving.x() < 140);
        assertTrue(moving.strength() > 0.72f);
        lens.advance(PointerLens.DEFAULT, pointer(140, 100, false), 2150, false);
        var released = lens.advance(PointerLens.DEFAULT, pointer(140, 100, false), 2200, false);
        assertTrue(released.strength() < moving.strength());

        var outside = new UiPointerTracker.Frame(0, 0, false, false, false, false, false, 0);
        var fading = lens.advance(PointerLens.DEFAULT, outside, 2250, false);
        assertTrue(fading.strength() > 0 && fading.strength() < released.strength());
        for (long now = 2300; now <= 4250; now += 50) {
            fading = lens.advance(PointerLens.DEFAULT, outside, now, false);
        }
        assertNull(fading.sample(140, 100));
    }

    @Test
    void rendererUsesWarpAndGlowButPreservesAnchorsWhenMotionIsReducedOrBlocked() {
        var profile = DotMatrixProfile.VIOLET
                .withPalette(new DotMatrixProfile.Palette(0, 0x775522AA, 0xFFAA88EE, 0f))
                .withGrid(new DotMatrixProfile.Grid(8, 2, 1f, 0))
                .withMotion(new DotMatrixProfile.Motion(DotMatrixProfile.Effect.STEADY, 1000, 0f, 0, 0f))
                .withInteraction(new DotMatrixProfile.Interaction(0, 0))
                .withPointerLens(new PointerLens(32, 0.6f, 0f, 24, 0xFFFFFFFF, 0.8f, true));
        var dots = new UiDotMatrix(profile);
        var input = pointer(20, 20, false);
        var idle = draw(dots, null, 0, UiPreferences.DEFAULT);
        for (long now = 0; now < 1500; now += 50) draw(dots, input, now, UiPreferences.DEFAULT);
        var active = draw(dots, input, 1500, UiPreferences.DEFAULT);
        assertEquals(idle.getLast(), active.getLast());
        assertTrue(active.stream().anyMatch(dot -> dot.x() > 28f && dot.x() < 36f && dot.y() == 20f));
        assertTrue(active.stream().anyMatch(dot -> dot.radius() > 1f && dot.feather() == 0.3f));
        assertTrue(active.stream().anyMatch(dot -> dot.feather() == 1.6f));

        var reduced = draw(dots, input, 1550, new UiPreferences(false, true, true));
        assertEquals(idle.stream().map(dot -> List.of(dot.x(), dot.y(), dot.radius())).toList(),
                reduced.stream().filter(dot -> dot.feather() == 0.3f)
                        .map(dot -> List.of(dot.x(), dot.y(), dot.radius())).toList());
        assertEquals(idle, draw(dots, null, 1600, UiPreferences.DEFAULT));
    }

    @Test
    void discClipsCoreAndFeatherWithTheSameWindingAsGuiQuads() {
        var vertices = new ArrayList<Vertex>();
        UiDotMatrix.disc((x, y, color) -> vertices.add(new Vertex(x, y, color)),
                12, 12, 1, 11, 2, 0.3f, 0xCCFFFFFF);
        assertFalse(vertices.isEmpty());
        assertTrue(vertices.stream().allMatch(v -> v.x() >= 0 && v.x() <= 12 && v.y() >= 0 && v.y() <= 12));
        assertTrue(vertices.stream().anyMatch(v -> v.color() >>> 24 == 0));
        for (int i = 0; i < vertices.size(); i += 4) {
            float area = 0;
            for (int j = 0; j < 4; j++) {
                var a = vertices.get(i + j);
                var b = vertices.get(i + (j + 1) % 4);
                area += a.x() * b.y() - a.y() * b.x();
            }
            assertTrue(area <= 0.0001f);
        }
    }

    private static DotMatrixLens.Frame settle(DotMatrixLens lens, PointerLens profile, UiPointerTracker.Frame input) {
        for (long now = 0; now < 2000; now += 50) lens.advance(profile, input, now, false);
        return lens.advance(profile, input, 2000, false);
    }

    private static UiPointerTracker.Frame pointer(float x, float y, boolean down) {
        return new UiPointerTracker.Frame(x, y, true, down, false, false, false, 0);
    }

    private static List<Dot> draw(UiDotMatrix matrix, UiPointerTracker.Frame pointer, long now,
                                  UiPreferences preferences) {
        var dots = new ArrayList<Dot>();
        matrix.forEachDot(new Bounds(100, 50, 88, 88), pointer, now, preferences, (l, t, r, b, c) -> {},
                (x, y, radius, feather, argb) -> dots.add(new Dot(x, y, radius, feather, argb)));
        return dots;
    }

    private record Dot(float x, float y, float radius, float feather, int argb) {}
    private record Vertex(float x, float y, int color) {}
}
