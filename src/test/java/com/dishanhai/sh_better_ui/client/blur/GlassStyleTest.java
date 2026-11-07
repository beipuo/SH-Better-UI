package com.dishanhai.sh_better_ui.client.blur;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：玻璃材质参数的数值回归。 */
class GlassStyleTest {

    @Test
    void noneStyleWritesAllZeros() {
        float[] tint = {9f, 9f, 9f, 9f};
        float[] params = {9f, 9f, 9f, 9f};
        float[] edge = {9f, 9f, 9f, 9f};
        GlassStyle.NONE.write(3f, tint, params, edge);
        assertArrayEquals(new float[]{0f, 0f, 0f, 0f}, tint, 0f);
        assertArrayEquals(new float[]{0f, 0f, 0f, 0f}, params, 0f);
        assertArrayEquals(new float[]{0f, 0f, 0f, 0f}, edge, 0f);
    }

    @Test
    void virtualUnitsScaleToPixelsButRatiosDoNot() {
        float[] tint = new float[4];
        float[] params = new float[4];
        float[] edge1 = new float[4];
        float[] edge3 = new float[4];
        GlassStyle.OVERLAY.write(1f, tint, params, edge1);
        GlassStyle.OVERLAY.write(3f, tint, params, edge3);
        assertEquals(edge1[0] * 3f, edge3[0], 1e-5f); // 折射 px
        assertEquals(edge1[1] * 3f, edge3[1], 1e-5f); // 亮边宽 px
        assertEquals(edge1[2] * 3f, edge3[2], 1e-5f); // 阴影半径 px
        assertEquals(edge1[3], edge3[3], 1e-6f);      // 阴影强度是比例，不随缩放
    }

    @Test
    void presetsStayInSaneRanges() {
        GlassStyle[] all = {GlassStyle.OVERLAY, GlassStyle.FLIGHT,
                GlassStyle.SWAP, GlassStyle.LOADING, GlassStyle.NONE};
        float[] tint = new float[4];
        float[] params = new float[4];
        float[] edge = new float[4];
        for (GlassStyle s : all) {
            s.write(1f, tint, params, edge);
            assertTrue(tint[3] >= 0f && tint[3] <= 0.3f, "tint amount");
            assertTrue(params[0] >= 0f && params[0] <= 0.5f, "saturation");
            assertTrue(params[1] >= 0f && params[1] <= 0.08f, "grain");
            assertTrue(params[2] >= 0f && params[2] <= 0.08f, "lift");
            assertTrue(params[3] >= 0f && params[3] <= 1f, "rim");
            assertTrue(edge[3] >= 0f && edge[3] <= 1f, "shadow strength");
        }
    }

    @Test
    void onlyOverlayAndFlightHaveRimAndRefraction() {
        float[] tint = new float[4];
        float[] params = new float[4];
        float[] edge = new float[4];
        for (GlassStyle s : new GlassStyle[]{GlassStyle.SWAP, GlassStyle.LOADING}) {
            s.write(1f, tint, params, edge);
            assertEquals(0f, params[3], 0f);
            assertEquals(0f, edge[0], 0f);
        }
        GlassStyle.OVERLAY.write(1f, tint, params, edge);
        assertTrue(params[3] > 0f && edge[0] > 0f);
        GlassStyle.FLIGHT.write(1f, tint, params, edge);
        assertTrue(params[3] > 0f && edge[0] > 0f);
    }

    @Test
    void clearStyleKeepsReducedLiftAndASubtleLensEdge() {
        float[] tint = new float[4];
        float[] params = new float[4];
        float[] edge = new float[4];
        GlassStyle.CLEAR.write(1f, tint, params, edge);
        assertTrue(Math.max(tint[0], Math.max(tint[1], tint[2])) < 0.9f);
        assertTrue(tint[3] > 0f && tint[3] <= 0.05f);
        assertTrue(params[2] > 0f && params[2] <= 0.012f,
                "Retain gentle lift instead of removing it or washing the surface white");
        assertTrue(params[0] <= 0f, "Frosting should soften rather than amplify background colors");
        assertTrue(params[3] > 0f && params[3] <= 0.2f && edge[0] > 0f && edge[0] <= 1f);
        assertEquals(0f, edge[3], 0f);
    }

    @Test
    void frostedBackgroundKeepsNeutralTintAndGentleEdgeLight() {
        float[] tint = new float[4], params = new float[4], edge = new float[4];
        GlassStyle.FROSTED.write(1f, tint, params, edge);
        assertEquals(tint[0], tint[1], 0f);
        assertEquals(tint[1], tint[2], 0f);
        assertTrue(params[0] < 0f);
        assertTrue(params[2] > 0f && params[2] <= 0.012f);
        assertTrue(params[3] > 0f && params[3] <= 0.2f);
        assertEquals(0f, edge[0], 0f);
    }

    @Test
    void backdropSettingsOnlyScaleTintMixAndGrain() {
        float[] tint = new float[4], params = new float[4], edge = new float[4];
        float[] tunedTint = new float[4], tunedParams = new float[4], tunedEdge = new float[4];
        for (GlassStyle style : new GlassStyle[]{GlassStyle.FROSTED, GlassStyle.CLEAR,
                GlassStyle.BACKDROP, GlassStyle.REGION, GlassStyle.CONTROL}) {
            style.write(3f, tint, params, edge);
            style.write(3f, tunedTint, tunedParams, tunedEdge, 0f, 2f);
            assertArrayEquals(new float[]{tint[0], tint[1], tint[2], 0f}, tunedTint, 0f);
            assertArrayEquals(new float[]{params[0], params[1] * 2f, params[2], params[3]}, tunedParams, 0f);
            assertArrayEquals(edge, tunedEdge, 0f);
        }
    }

    @Test
    void denseSamplingIsLimitedToFrostedSurfaces() {
        assertEquals(2f, GlassStyle.CLEAR.blurMode(true));
        assertEquals(2f, GlassStyle.FROSTED.blurMode(true));
        for (GlassStyle style : new GlassStyle[]{GlassStyle.NONE, GlassStyle.SWAP,
                GlassStyle.OVERLAY, GlassStyle.BACKDROP, GlassStyle.CONTROL}) {
            assertEquals(1f, style.blurMode(true));
            assertEquals(0f, style.blurMode(false));
        }
        assertEquals(0f, GlassStyle.CLEAR.blurMode(false));
    }
}
