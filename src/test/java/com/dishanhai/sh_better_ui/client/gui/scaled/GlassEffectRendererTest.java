package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：捕获实际绘制算法提交的矩形；不创建窗口或执行 OpenGL。 */
class GlassEffectRendererTest {
    private record Rect(int x0, int y0, int x1, int y1, int color) {}

    private static final class RecordingFill implements GlassEffectRenderer.Fill {
        final List<Rect> fills = new ArrayList<>();

        @Override
        public void fill(int x0, int y0, int x1, int y1, int color) {
            fills.add(new Rect(x0, y0, x1, y1, color));
        }
    }

    @Test
    void flowCoversTheWholeBorderAndEveryCornerExactlyOnce() {
        for (int w : new int[]{1, 2, 13, 70}) {
            for (int h : new int[]{1, 2, 9, 40}) {
                var graphics = new RecordingFill();
                GlassEffectRenderer.drawFlowBorder(graphics, 7, 11, w, h, 350L, false, 0xFFFFFFFF);
                int[][] coverage = new int[h][w];
                for (Rect rect : graphics.fills) {
                    assertInside(rect, 7, 11, w, h);
                    for (int y = rect.y0; y < rect.y1; y++) {
                        for (int x = rect.x0; x < rect.x1; x++) coverage[y - 11][x - 7]++;
                    }
                }
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        boolean border = x == 0 || y == 0 || x == w - 1 || y == h - 1;
                        assertEquals(border ? 1 : 0, coverage[y][x], "重复或缺失边框像素");
                    }
                }
            }
        }
    }

    @Test
    void rippleRemainsInsideItsCurrentButtonAfterRepositioning() {
        var ripple = new ClickRipple();
        ripple.trigger(2, 3, 0, 0, 20, 20, 0L);
        var graphics = new RecordingFill();
        GlassEffectRenderer.drawRipple(graphics, 30, 40, 40, 10, ripple, 150L, false, 0xFFFFFFFF);
        assertFalse(graphics.fills.isEmpty());
        for (Rect rect : graphics.fills) assertInside(rect, 30, 40, 40, 10);
    }

    @Test
    void independentRipplesCanBeDrawnInTheSameFrame() {
        var first = new ClickRipple();
        var second = new ClickRipple();
        first.trigger(10, 10, 0, 0, 20, 20, 0L);
        second.trigger(40, 10, 30, 0, 20, 20, 50L);
        var a = new RecordingFill();
        var b = new RecordingFill();
        GlassEffectRenderer.drawRipple(a, 0, 0, 20, 20, first, 100L, false, 0xFFFFFFFF);
        GlassEffectRenderer.drawRipple(b, 30, 0, 20, 20, second, 100L, false, 0xFFFFFFFF);
        assertFalse(a.fills.isEmpty());
        assertFalse(b.fills.isEmpty());
        for (Rect rect : a.fills) assertInside(rect, 0, 0, 20, 20);
        for (Rect rect : b.fills) assertInside(rect, 30, 0, 20, 20);
    }

    @Test
    void hugeRegionsHaveABoundedNumberOfFillCalls() {
        var graphics = new RecordingFill();
        GlassEffectRenderer.drawFlowBorder(graphics, 0, 0, 1_000_000, 1_000_000, 100L, false, 0xFFFFFFFF);
        assertTrue(graphics.fills.size() <= 128);
        graphics.fills.clear();
        var ripple = new ClickRipple();
        ripple.trigger(500_000, 500_000, 0, 0, 1_000_000, 1_000_000, 0L);
        GlassEffectRenderer.drawRipple(graphics, 0, 0, 1_000_000, 1_000_000,
                ripple, 150L, false, 0xFFFFFFFF);
        assertFalse(graphics.fills.isEmpty());
        assertTrue(graphics.fills.size() <= 256);
        for (Rect rect : graphics.fills) assertInside(rect, 0, 0, 1_000_000, 1_000_000);
    }

    @Test
    void breathingGlowStaysOutsideTheControlAndWithinItsThreePixelMargin() {
        var graphics = new RecordingFill();
        GlassEffectRenderer.drawBreathingGlow(graphics, 10, 20, 40, 15, 1f, 100L, false, 0xFFFFFFFF);
        assertFalse(graphics.fills.isEmpty());
        assertTrue(graphics.fills.size() <= 12);
        for (Rect rect : graphics.fills) {
            assertInside(rect, 7, 17, 46, 21);
            assertFalse(rect.x0 < 50 && rect.x1 > 10 && rect.y0 < 35 && rect.y1 > 20,
                    "外围光晕不能覆盖控件内容");
        }
    }

    @Test
    void reducedRippleUsesOneUniformFillAndInvalidGeometryIsIgnored() {
        var ripple = new ClickRipple();
        ripple.trigger(5, 5, 0, 0, 10, 10, 0L);
        var graphics = new RecordingFill();
        GlassEffectRenderer.drawRipple(graphics, 0, 0, 10, 10, ripple, 100L, true, 0xFFFFFFFF);
        assertEquals(1, graphics.fills.size());
        assertEquals(new Rect(0, 0, 10, 10, graphics.fills.getFirst().color), graphics.fills.getFirst());
        graphics.fills.clear();
        GlassEffectRenderer.drawFlowBorder(graphics, Integer.MAX_VALUE, 0, 2, 10, 0L, false, 0xFFFFFFFF);
        GlassEffectRenderer.drawRipple(graphics, 0, 0, 0, 10, ripple, 100L, false, 0xFFFFFFFF);
        GlassEffectRenderer.drawBreathingGlow(graphics, 0, 0, 10, 10, Float.NaN, 0L, false, 0xFFFFFFFF);
        GlassEffectRenderer.drawBreathingGlow(graphics, 0, 0, 10, 10,
                Float.POSITIVE_INFINITY, 0L, false, 0xFFFFFFFF);
        assertTrue(graphics.fills.isEmpty());
    }

    private static void assertInside(Rect rect, int x, int y, int w, int h) {
        assertTrue(rect.x1 > rect.x0 && rect.y1 > rect.y0);
        assertTrue(rect.x0 >= x && rect.y0 >= y && rect.x1 <= x + w && rect.y1 <= y + h);
    }
}
