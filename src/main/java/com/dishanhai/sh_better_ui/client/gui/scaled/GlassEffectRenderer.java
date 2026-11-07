package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 山海：可选玻璃装饰。先画底板与装饰，再画文字图标；坐标与当前 pose 一致。
 * 只提交 fill，不改变 shader、颜色、scissor、pose 或全局后处理请求。
 */
public final class GlassEffectRenderer {
    private static final int MAX_RIPPLE_ROWS = 128;
    private static final int MAX_EDGE_SEGMENTS = 32;

    /** 同包几何验证使用；生产入口仍使用原生 GuiGraphics。 */
    @FunctionalInterface
    interface Fill {
        void fill(int x0, int y0, int x1, int y1, int color);
    }

    private GlassEffectRenderer() {}

    /** 控件内部的局部亮纹；每个控件使用独立 ClickRipple，可同时绘制多个。 */
    public static void drawRipple(GuiGraphics g, int x, int y, int w, int h, ClickRipple ripple,
                                  long nowMs, boolean reducedMotion, int argb) {
        drawRipple(g::fill, x, y, w, h, ripple, nowMs, reducedMotion, argb);
    }

    static void drawRipple(Fill fill, int x, int y, int w, int h, ClickRipple ripple,
                           long nowMs, boolean reducedMotion, int argb) {
        if (!validRect(x, y, w, h) || ripple == null) return;
        ClickRipple.Frame frame = ripple.sample(nowMs, reducedMotion);
        int color = GlassEffectMotion.withOpacity(argb, frame.opacity());
        if ((color >>> 24) == 0) return;
        if (frame.uniform()) {
            fill.fill(x, y, x + w, y + h, GlassEffectMotion.withOpacity(color, 0.3f));
            return;
        }

        double cx = x + (double) w * frame.originX();
        double cy = y + (double) h * frame.originY();
        double maxX = Math.max(cx - x, (double) x + w - cx);
        double maxY = Math.max(cy - y, (double) y + h - cy);
        double radius = Math.hypot(maxX, maxY) * frame.radiusProgress();
        double inner = Math.max(0d, radius - Math.max(1.5d, Math.min(w, h) * 0.12d));
        int rows = Math.min(h, MAX_RIPPLE_ROWS);
        for (int i = 0; i < rows; i++) {
            int y0 = y + (int) ((long) i * h / rows);
            int y1 = y + (int) ((long) (i + 1) * h / rows);
            double dy = (y0 + (double) y1) * 0.5d - cy;
            double outerSquare = radius * radius - dy * dy;
            if (outerSquare <= 0d) continue;
            double outerX = Math.sqrt(outerSquare);
            double innerSquare = inner * inner - dy * dy;
            if (innerSquare <= 0d) {
                fillSpan(fill, x, w, cx - outerX, cx + outerX, y0, y1, color);
            } else {
                double innerX = Math.sqrt(innerSquare);
                fillSpan(fill, x, w, cx - outerX, cx - innerX, y0, y1, color);
                fillSpan(fill, x, w, cx + innerX, cx + outerX, y0, y1, color);
            }
        }
    }

    /** 沿矩形四边运行的 1 GUI 像素流光；每帧最多 128 个矩形，不新增模糊采样。 */
    public static void drawFlowBorder(GuiGraphics g, int x, int y, int w, int h,
                                      long nowMs, boolean reducedMotion, int argb) {
        drawFlowBorder(g::fill, x, y, w, h, nowMs, reducedMotion, argb);
    }

    static void drawFlowBorder(Fill fill, int x, int y, int w, int h,
                               long nowMs, boolean reducedMotion, int argb) {
        if (!validRect(x, y, w, h) || (argb >>> 24) == 0) return;
        if (w == 1 || h == 1) {
            fill.fill(x, y, x + w, y + h, GlassEffectMotion.withOpacity(argb, 0.35f));
            return;
        }
        float phase = GlassEffectMotion.phase(nowMs, GlassEffectMotion.FLOW_PERIOD_MS);
        double perimeter = 2d * w + 2d * h - 4d;
        double distance = 0d;
        for (int edge = 0; edge < 4; edge++) {
            int length = switch (edge) {
                case 0 -> w;
                case 1 -> h - 1;
                case 2 -> w - 1;
                default -> h - 2;
            };
            int segments = Math.min(length, MAX_EDGE_SEGMENTS);
            for (int i = 0; i < segments; i++) {
                int a = (int) ((long) i * length / segments);
                int b = (int) ((long) (i + 1) * length / segments);
                float position = (float) ((distance + (a + (double) b) * 0.5d) / perimeter);
                int color = GlassEffectMotion.withOpacity(argb,
                        GlassEffectMotion.flowIntensity(position, phase, reducedMotion));
                switch (edge) {
                    case 0 -> fill.fill(x + a, y, x + b, y + 1, color);
                    case 1 -> fill.fill(x + w - 1, y + 1 + a, x + w, y + 1 + b, color);
                    case 2 -> fill.fill(x + w - 1 - b, y + h - 1, x + w - 1 - a, y + h, color);
                    default -> fill.fill(x, y + h - 1 - b, x + 1, y + h - 1 - a, color);
                }
            }
            distance += length;
        }
    }

    /** 控件外侧 3 GUI 像素柔光；focusAmount 可接已有 hover 弹簧，0 时不绘制。 */
    public static void drawBreathingGlow(GuiGraphics g, int x, int y, int w, int h, float focusAmount,
                                         long nowMs, boolean reducedMotion, int argb) {
        drawBreathingGlow(g::fill, x, y, w, h, focusAmount, nowMs, reducedMotion, argb);
    }

    static void drawBreathingGlow(Fill fill, int x, int y, int w, int h, float focusAmount,
                                  long nowMs, boolean reducedMotion, int argb) {
        if (!validRect(x, y, w, h) || !Float.isFinite(focusAmount) || focusAmount <= 0f
                || x < Integer.MIN_VALUE + 3 || y < Integer.MIN_VALUE + 3
                || (long) x + w > Integer.MAX_VALUE - 3L || (long) y + h > Integer.MAX_VALUE - 3L) return;
        float strength = GuiMotion.clamp01(focusAmount) * GlassEffectMotion.breath(nowMs, reducedMotion);
        for (int margin = 3; margin >= 1; margin--) {
            float falloff = switch (margin) { case 1 -> 0.45f; case 2 -> 0.22f; default -> 0.08f; };
            int color = GlassEffectMotion.withOpacity(argb, strength * falloff);
            if ((color >>> 24) == 0) continue;
            int left = x - margin, top = y - margin;
            int right = x + w + margin, bottom = y + h + margin;
            fill.fill(left, top, right, top + 1, color);
            fill.fill(left, bottom - 1, right, bottom, color);
            fill.fill(left, top + 1, left + 1, bottom - 1, color);
            fill.fill(right - 1, top + 1, right, bottom - 1, color);
        }
    }

    private static boolean validRect(int x, int y, int w, int h) {
        return w > 0 && h > 0 && (long) x + w <= Integer.MAX_VALUE && (long) y + h <= Integer.MAX_VALUE;
    }

    private static void fillSpan(Fill fill, int x, int w, double left, double right,
                                 int y0, int y1, int color) {
        int from = (int) Math.max(x, Math.ceil(left));
        int to = (int) Math.min((double) x + w, Math.ceil(right));
        if (to > from) fill.fill(from, y0, to, y1, color);
    }
}
