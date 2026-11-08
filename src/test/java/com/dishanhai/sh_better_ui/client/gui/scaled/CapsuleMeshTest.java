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

    @Test
    void fittedNestedRadiiKeepTheSameCentersAndPixelGapAtEachScale() {
        for (float width : new float[]{1f, 3f, 9.5f, 27f, 144f, 320f}) {
            for (float height : new float[]{1f, 7f, 19f, 64f}) {
                for (float padding : new float[]{0.5f, 1f, 3.25f}) {
                    float inner = RoundedCorners.fit(width, height, 12f);
                    float outer = RoundedCorners.outsetRadius(width, height, 12f, padding);
                    float outerWidth = width + 2f * padding;
                    float outerHeight = height + 2f * padding;
                    assertEquals(outer, RoundedCorners.fit(outerWidth, outerHeight, outer));
                    assertEquals(inner, RoundedCorners.insetRadius(outerWidth, outerHeight, outer, padding));
                    for (float scale : new float[]{0.75f, 1f, 2f, 3f, 4f}) {
                        assertEquals((inner + padding) * scale, outer * scale, 0.0001f);
                        assertEquals(padding * scale, outer * scale - inner * scale, 0.0001f);
                        assertEquals((width - inner) * scale, (outerWidth - outer - padding) * scale, 0.0001f);
                        assertEquals((height - inner) * scale, (outerHeight - outer - padding) * scale, 0.0001f);
                    }
                }
            }
        }
        assertEquals(3.5f, RoundedCorners.fit(7f, 19f, 12f));
        assertEquals(0f, RoundedCorners.insetRadius(13f, 9f, 1.5f, 2f));
    }

    @Test
    void smallRadiusFallbackKeepsItsInteriorEvenWhenTheStrokeIsThicker() {
        var vertices = mesh(13f, 9f, 1.5f, 2f, 2f, EDGE, FILL);
        assertTrue(vertices.stream().anyMatch(v -> near(v.x, 2.25f) && near(v.y, 2.25f) && v.color == FILL));
        assertTrue(vertices.stream().anyMatch(v -> near(v.x, 10.75f) && near(v.y, 6.75f) && v.color == FILL));
        assertTrue(vertices.stream().anyMatch(v -> v.color == EDGE));
    }

    @Test
    void fillOnlyAndFullyConsumedInteriorUseTheCorrectColor() {
        var fillOnly = mesh(15f, 7f, 2.5f, 0f, 2f, EDGE, FILL);
        assertTrue(fillOnly.stream().anyMatch(v -> v.color == FILL));
        assertTrue(fillOnly.stream().allMatch(v -> v.color == FILL || v.color == (FILL & 0x00FFFFFF)));

        var allBorder = mesh(5f, 3f, 1.5f, 1.5f, 2f, EDGE, FILL);
        assertTrue(allBorder.stream().anyMatch(v -> v.color == EDGE));
        assertTrue(allBorder.stream().allMatch(v -> v.color == EDGE || v.color == (EDGE & 0x00FFFFFF)));
    }

    private static List<Vertex> mesh(float scale, int fill) {
        return mesh(107.5f, 23f, 11.5f, 1f, scale, EDGE, fill);
    }

    private static List<Vertex> mesh(float width, float height, float radius, float stroke,
                                     float scale, int edge, int fill) {
        var vertices = new ArrayList<Vertex>();
        CapsuleMesh.emit(width, height, radius, stroke, scale, edge, fill,
                (x, y, color) -> vertices.add(new Vertex(x, y, color)));
        return vertices;
    }

    private static boolean near(float value, float target) {
        return Math.abs(value - target) < 0.0001f;
    }

    private record Vertex(float x, float y, int color) {}
}
