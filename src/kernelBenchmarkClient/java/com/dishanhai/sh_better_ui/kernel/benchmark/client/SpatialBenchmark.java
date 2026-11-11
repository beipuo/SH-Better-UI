// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.benchmark.client;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Shape;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Transform;
import com.dishanhai.sh_better_ui.spatial.SpatialScene;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.*;
import com.dishanhai.sh_better_ui.spatial.SpatialUI;

import java.util.Locale;

/** Real render workloads. Coordinates use the same GUI space as the native benchmark. */
final class SpatialBenchmark {
    enum Mode {
        STATIC, MOVING, LIGHTING, MATERIALS, CLIPPING, RETARGET;

        String label() { return name().toLowerCase(Locale.ROOT); }
    }

    private static final int COLUMNS = 90;
    private static final int ROWS = 56;
    private static final int LIGHTS = 12;
    private static final Material[] SURFACES = {
            Material.SOFT_RAISED, Material.SOFT_INSET, Material.METALLIC, Material.HOLOGRAPHIC
    };

    private final SpatialScene scene;
    private final Mode mode;
    private final SpatialScene.Node[] nodes;
    private final SpatialUI.Motion[] motions;
    private final UiId[] lights = new UiId[LIGHTS];

    SpatialBenchmark(SpatialUI service, SpatialScene scene, Mode mode, int count) {
        this.scene = scene;
        this.mode = mode;
        nodes = new SpatialScene.Node[count];
        motions = mode == Mode.RETARGET ? new SpatialUI.Motion[count] : null;
        scene.settings(new Settings(true, true, mode == Mode.LIGHTING, mode == Mode.MATERIALS, Quality.HIGH));
        UiId root = mode == Mode.CLIPPING ? clipRoot(scene) : null;
        for (int index = 0; index < count; index++) {
            var id = new UiId("sh_better_ui", "bench/spatial/" + index);
            Shape shape = switch (mode) {
                case STATIC, MOVING, RETARGET -> Shape.rounded(5, 4, 1.25);
                case LIGHTING -> Shape.ellipse(5, 4);
                case MATERIALS -> Shape.capsule(5, 4);
                case CLIPPING -> Shape.rectangle(5, 4);
            };
            var node = scene.create(new SpatialScene.Spec(id, root, shape, transform(index, 0),
                    index % 4, 0, false, false));
            node.elevation(Elevation.RAISED);
            if (mode == Mode.MATERIALS) {
                // Include a bounded number of real backdrop captures even at the largest node count.
                node.material(index < 4 ? Material.GLASS : SURFACES[index % SURFACES.length]);
            }
            nodes[index] = node;
            if (motions != null) {
                motions[index] = service.motion(scene, node, new UiId("sh_better_ui", "bench/spatial_elevation"),
                        2, SpatialUI.SpringSpec.INTERACTION, node::elevation);
            }
        }
        if (mode == Mode.LIGHTING) {
            for (int index = 0; index < LIGHTS; index++) {
                lights[index] = new UiId("sh_better_ui", "bench/spatial_light/" + index);
            }
        }
        update(0);
    }

    private static UiId clipRoot(SpatialScene scene) {
        UiId outer = new UiId("sh_better_ui", "bench/spatial_clip_outer");
        UiId inner = new UiId("sh_better_ui", "bench/spatial_clip_inner");
        var outerNode = scene.create(new SpatialScene.Spec(outer, null, Shape.rounded(338, 218, 11),
                new Transform(7, 4, 1, 1, .012, 0, 0), -2, 0, false, true));
        var innerNode = scene.create(new SpatialScene.Spec(inner, outer, Shape.rounded(300, 190, 8),
                new Transform(5, 3, 1, 1, -.016, 0, 0), -1, 0, false, true));
        var transparent = new Material(0, 0, 1, 0, 0, 0, 0);
        outerNode.material(transparent);
        innerNode.material(transparent);
        return inner;
    }

    private static Transform transform(int index, double motion) {
        return new Transform(4 + (index % COLUMNS) * 4 + motion,
                5 + (index / COLUMNS % ROWS) * 3.8, 1, 1, motion * .12, 2.5, 2);
    }

    void update(int frame) {
        switch (mode) {
            case MOVING -> {
                double motion = Math.sin(frame * .17);
                for (int index = 0; index < nodes.length; index++) {
                    var node = nodes[index];
                    node.transform(transform(index, motion));
                    node.elevation(3 + motion * 2);
                }
            }
            case LIGHTING -> {
                for (int index = 0; index < lights.length; index++) {
                    double x = 20 + 30 * (index % 6) + 12 * Math.sin(frame * .09 + index);
                    double y = 46 + 85 * (index / 6);
                    scene.light(lights[index], new Light(LightType.POINT, x, y, 20, .85,
                            72, index % 2 == 0 ? 0xffffd0aa : 0xff8ebeff, 0, Math.PI));
                }
            }
            case RETARGET -> {
                float elevation = (frame & 1) == 0 ? 7 : 2;
                for (var motion : motions) motion.play(elevation, SpatialUI.MotionPolicy.RETARGET, 0);
            }
            default -> {}
        }
    }
}
