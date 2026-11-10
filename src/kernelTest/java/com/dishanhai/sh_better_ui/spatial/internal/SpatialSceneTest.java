// Author: dishanhai
package com.dishanhai.sh_better_ui.spatial.internal;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Shape;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Transform;
import com.dishanhai.sh_better_ui.spatial.SpatialScene.*;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SpatialSceneTest {
    private final Scene scene = new Scene(() -> {});

    @Test
    void resolvesInheritedTransformsAndInvalidatesOnlyWhenNeeded() {
        var parent = scene.create(spec("parent", null, Shape.rectangle(20, 20), 0, 0, false, true));
        var child = scene.create(spec("child", parent.id(), Shape.ellipse(6, 4), 0, 0, false, false));
        parent.transform(new Transform(10, 10, -2, 1, Math.PI / 2, 0, 0));
        child.transform(new Transform(3, 2, 1, 1, 0, 0, 0));
        var matrix = child.worldTransform();
        var point = matrix.point(2, 1);
        assertEquals(7, point.x(), 1e-9);
        assertEquals(0, point.y(), 1e-9);
        assertEquals(2, matrix.inverse().point(point.x(), point.y()).x(), 1e-9);
        assertSame(matrix, child.worldTransform());
        parent.transform(Transform.IDENTITY);
        assertNotSame(matrix, child.worldTransform());
    }

    @Test
    void hitPriorityModalAndClippingAreIndependentOfDrawOrder() {
        var clip = scene.create(spec("clip", null, Shape.rectangle(10, 10), 0, 0, false, true));
        var low = scene.create(spec("low", clip.id(), Shape.rounded(20, 20, 4), 0, 2, true, false));
        var top = scene.create(spec("top", null, Shape.rectangle(20, 20), 5, 0, true, false));
        assertEquals(low, scene.hitTest(5, 5).orElseThrow());
        assertEquals(top, scene.hitTest(11, 5).orElseThrow());
        clip.transform(new Transform(20, 0, 1, 1, Math.PI / 2, 0, 0));
        assertEquals(top, scene.hitTest(5, 5).orElseThrow());
        scene.modal(clip.id());
        assertTrue(scene.hitTest(5, 5).isEmpty());
        assertEquals(low, scene.hitTest(15, 5).orElseThrow());
        clip.close();
        assertTrue(scene.find(low.id()).isEmpty());
        assertEquals(top, scene.hitTest(5, 5).orElseThrow());
    }

    @Test
    void preservesLayerOrderEvenWhenLaterNodesHaveEquivalentPaint() {
        var back = scene.create(spec("back", null, Shape.rectangle(25, 25), 1, 9, true, false));
        var front = scene.create(spec("front", null, Shape.rectangle(25, 25), 2, 0, true, false));
        back.elevation(4);
        front.elevation(4);
        List<SpatialStyle.ShadowType> passes = new ArrayList<>();
        scene.draw(100, 100, 1, (paint, mesh, blur) -> passes.add(paint.type()));
        assertEquals(List.of(SpatialStyle.ShadowType.AMBIENT, SpatialStyle.ShadowType.DIRECTIONAL,
                SpatialStyle.ShadowType.CONTACT, SpatialStyle.ShadowType.SURFACE,
                SpatialStyle.ShadowType.AMBIENT, SpatialStyle.ShadowType.DIRECTIONAL,
                SpatialStyle.ShadowType.CONTACT, SpatialStyle.ShadowType.SURFACE), passes);
    }

    @Test
    void reportsFullOpaqueCoverageSeparatelyFromModalAndTransparentHits() {
        var back = scene.create(spec("back", null, Shape.rounded(10, 10, 2), 0, 1, true, false));
        var front = scene.create(spec("front", null, Shape.rectangle(10, 10), 1, 0, true, false));
        scene.draw(100, 100, 1, (paint, mesh, blur) -> {});
        assertTrue(scene.occlusion(back.id()).covered());
        assertFalse(scene.occlusion(back.id()).modalBlocked());
        assertEquals(back, scene.hitTest(5, 5).orElseThrow());
        scene.modal(front.id());
        assertTrue(scene.occlusion(back.id()).modalBlocked());
        front.opacity(.5);
        assertFalse(scene.occlusion(back.id()).covered());
        front.material(new SpatialStyle.Material(0, 1, 1, 0, 0, 0, 0));
        front.hitTransparency(HitTransparency.VISIBLE_SURFACE);
        assertTrue(scene.hitTest(5, 5).isEmpty());
    }

    @Test
    void extremeGeometryRemainsFiniteAndRejectsInvalidParameters() {
        Random random = new Random(20261110);
        for (int i = 0; i < 1000; i++) {
            double width = .001 + random.nextDouble() * 32768;
            double height = .001 + random.nextDouble() * 32768;
            var shape = Shape.rounded(width, height, 32768);
            assertTrue(Double.isFinite(shape.distance(-width, 2 * height)));
            assertTrue(shape.distance(width / 2, height / 2) < 0);
        }
        assertThrows(IllegalArgumentException.class, () -> Shape.rectangle(Double.NaN, 2));
        assertThrows(IllegalArgumentException.class, () -> new Transform(0, 0, 0, 1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SpatialStyle.Shadow(
                SpatialStyle.ShadowType.DROP, 0, 0, -1, 0, 1, 0, false));
    }

    @Test
    void shadowInterpolationPreservesStraightAlphaWithoutInvisibleColorBleed() {
        var a = new SpatialStyle.Shadow(SpatialStyle.ShadowType.DROP, 0, 0, 0, 0, 1, 0x00ff0000, false);
        var b = new SpatialStyle.Shadow(SpatialStyle.ShadowType.DROP, 4, 8, 6, 0, 1, 0xff0000ff, true);
        assertEquals(a, a.interpolate(b, 0));
        assertEquals(b, a.interpolate(b, 1));
        var half = a.interpolate(b, .5);
        assertEquals(4, half.y());
        assertEquals(3, half.blur());
        assertEquals(0x800000ff, half.color());
    }

    private static Spec spec(String id, UiId parent, Shape shape, int z, int hit, boolean interactive, boolean clip) {
        return new Spec(new UiId("test", id), parent, shape, Transform.IDENTITY, z, hit, interactive, clip);
    }
}
