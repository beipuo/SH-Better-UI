// Author: dishanhai
package com.dishanhai.sh_better_ui.spatial.internal;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Shape;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Transform;
import com.dishanhai.sh_better_ui.spatial.SpatialScene.Spec;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.Light;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.LightType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpatialLightCacheTest {
    @Test
    void movingLightRepaintsOldAndNewRegionsAndKeepsDistantCache() {
        try (var scene = new Scene(() -> {})) {
            create(scene, "old", 8);
            create(scene, "new", 250);
            create(scene, "distant", 530);
            var id = id("light");
            var base = draw(scene);
            scene.light(id, lightAt(12));
            var first = draw(scene);
            assertNotEquals(base.get(0), first.get(0));
            assertEquals(base.subList(1, 3), first.subList(1, 3));
            assertEquals(2, scene.profile().cacheHits());
            assertEquals(1, scene.profile().cacheMisses());

            scene.light(id, lightAt(254));
            var moved = draw(scene);
            assertEquals(base.get(0), moved.get(0));
            assertNotEquals(base.get(1), moved.get(1));
            assertEquals(base.get(2), moved.get(2));
            assertEquals(1, scene.profile().cacheHits());

            assertTrue(scene.removeLight(id));
            assertEquals(base, draw(scene));
            assertEquals(2, scene.profile().cacheHits());
        }
    }

    @Test
    void negativeCellCoordinatesAndLargeRadiusLightsRemainVisible() {
        try (var scene = new Scene(() -> {})) {
            create(scene, "edge", 0);
            var base = draw(scene);
            scene.light(id("across_cell_boundary"), lightAt(-12));
            assertNotEquals(base, draw(scene));
            scene.removeLight(id("across_cell_boundary"));
            scene.light(id("large"), new Light(LightType.POINT, 630, 12, 0, 1.7, 950,
                    0xffff6622, 0, Math.PI));
            assertNotEquals(base, draw(scene));
        }
    }

    private static List<Integer> draw(Scene scene) {
        var colors = new ArrayList<Integer>();
        scene.draw(600, 50, 1, (paint, mesh, blur) -> colors.add(paint.color()));
        return colors;
    }

    private static void create(Scene scene, String name, double x) {
        scene.create(new Spec(id(name), null, Shape.rectangle(8, 8), new Transform(x, 8, 1, 1, 0, 0, 0),
                0, 0, false, false));
    }

    private static Light lightAt(double x) {
        return new Light(LightType.POINT, x, 12, 4, 1.2, 32, 0xffff6622, 0, Math.PI);
    }

    private static UiId id(String path) { return new UiId("test", path); }
}
