package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：胶囊端点与边缘抗锯齿的回归测试。 */
class CapsuleMeshTest {
    private static final int EDGE = 0xA6CBD4E3;
    private static final int FILL = 0xB82B333E;

    @Test
    void oddHeightUsesFullSemicircleAndSubpixelVertices() {
        var vertices = mesh(2f, FILL);
        assertTrue(vertices.stream().anyMatch(v -> near(v.x, -0.25f) && near(v.y, 11.5f)));
        assertTrue(vertices.stream().anyMatch(v -> near(v.x, 107.75f) && near(v.y, 11.5f)));
        assertTrue(vertices.stream().anyMatch(v -> v.x > 0 && v.x < 11.5f && v.y > 0
                && v.y < 11.5f && v.y != Math.round(v.y)));
        assertTrue(vertices.stream().anyMatch(v -> v.color == EDGE));
        assertTrue(vertices.stream().anyMatch(v -> v.color == FILL));
    }

    @Test
    void transparentFringeTracksOneFramebufferPixelAtEachScale() {
        for (float scale : new float[]{1f, 2f, 4f}) {
            var vertices = mesh(scale, 0);
            double outside = vertices.stream().mapToDouble(Vertex::y).min().orElseThrow();
            double inside = vertices.stream().filter(v -> v.color == EDGE)
                    .mapToDouble(Vertex::y).min().orElseThrow();
            assertEquals(1d, (inside - outside) * scale, 0.0001);
            assertTrue(vertices.stream().allMatch(v -> v.color == EDGE || v.color == (EDGE & 0x00FFFFFF)));
        }
    }

    private static List<Vertex> mesh(float scale, int fill) {
        var vertices = new ArrayList<Vertex>();
        CapsuleMesh.emit(107.5f, 23f, 1f, scale, EDGE, fill,
                (x, y, color) -> vertices.add(new Vertex(x, y, color)));
        return vertices;
    }

    private static boolean near(float value, float target) {
        return Math.abs(value - target) < 0.0001f;
    }

    private record Vertex(float x, float y, int color) {}
}
