package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** 山海：在当前 GUI pose 内绘制加载状态，加载生命周期由接入方管理。 */
public final class UiLoading {
    public enum Edge { TOP, BOTTOM }

    private static final long SPIN_MS = 900L;
    private static final long TOP_CYCLE_MS = 1600L;
    private static final float SPIN_SWEEP = 0.28f;
    private static final float TOP_SWEEP = 0.25f;
    private static final int EDGE_DIAMETER = 12;
    private static final int TOP_HEIGHT = 2;

    private UiLoading() {}

    /** 未知进度圆环。时间单位为毫秒，减少动效时显示固定弧段。 */
    public static void spinner(GuiGraphics graphics, Bounds area, long nowMs, UiPreferences preferences) {
        drawRing(graphics, area, spinningArc(nowMs, preferences.reducedMotion()), preferences);
    }

    /** 确定进度圆环。弧长直接反映 0–1 的进度。 */
    public static void spinner(GuiGraphics graphics, Bounds area, float progress, UiPreferences preferences) {
        drawRing(graphics, area, progressArc(progress), preferences);
    }

    /** 在列表视口的顶边或底边内侧居中绘制小圆环。 */
    public static void edgeSpinner(GuiGraphics graphics, Bounds viewport, Edge edge, long nowMs,
                                   UiPreferences preferences) {
        spinner(graphics, edgeBounds(viewport, edge), nowMs, preferences);
    }

    /** 用调用方传入的当前进度绘制线性条。进度限制在 0–1，非有限值按 0 处理。 */
    public static void progressBar(GuiGraphics graphics, Bounds area, float progress, UiPreferences preferences) {
        drawBar(graphics, area, 0f, filledWidth(area.width(), progress), preferences);
    }

    /** 在视口顶边绘制确定进度条。 */
    public static void topBar(GuiGraphics graphics, Bounds viewport, float progress, UiPreferences preferences) {
        progressBar(graphics, topBounds(viewport), progress, preferences);
    }

    /** 在视口顶边绘制固定长度的往返段。减少动效时停在中央。 */
    public static void topBar(GuiGraphics graphics, Bounds viewport, long nowMs, UiPreferences preferences) {
        Bounds area = topBounds(viewport);
        float width = area.width() * TOP_SWEEP;
        float offset = (area.width() - width) * topTravel(nowMs, preferences.reducedMotion());
        drawBar(graphics, area, offset, width, preferences);
    }

    record Arc(float start, float sweep) {}

    static Arc spinningArc(long nowMs, boolean reducedMotion) {
        return new Arc(reducedMotion ? 0f : cycle(nowMs, SPIN_MS), SPIN_SWEEP);
    }

    static Arc progressArc(float progress) {
        return new Arc(0f, fraction(progress));
    }

    static float filledWidth(int width, float progress) {
        return width * fraction(progress);
    }

    static float topTravel(long nowMs, boolean reducedMotion) {
        if (reducedMotion) return 0.5f;
        float travel = 1f - Math.abs(2f * cycle(nowMs, TOP_CYCLE_MS) - 1f);
        return travel * travel * (3f - 2f * travel);
    }

    static Bounds edgeBounds(Bounds viewport, Edge edge) {
        int shortSide = Math.min(viewport.width(), viewport.height());
        int inset = Math.min(2, shortSide / 8);
        int diameter = Math.min(EDGE_DIAMETER, shortSide - 2 * inset);
        int x = viewport.x() + (viewport.width() - diameter) / 2;
        int y = switch (edge) {
            case TOP -> viewport.y() + inset;
            case BOTTOM -> viewport.bottom() - inset - diameter;
        };
        return new Bounds(x, y, diameter, diameter);
    }

    private static Bounds topBounds(Bounds viewport) {
        return new Bounds(viewport.x(), viewport.y(), viewport.width(), Math.min(TOP_HEIGHT, viewport.height()));
    }

    private static float cycle(long nowMs, long period) {
        return Math.floorMod(nowMs, period) / (float) period;
    }

    private static float fraction(float progress) {
        return Float.isFinite(progress) ? GuiMotion.clamp01(progress) : 0f;
    }

    private static int track(UiPreferences preferences) {
        return preferences.highContrast() ? DarkModeTheme.hoverBorderColor() : DarkModeTheme.borderColor();
    }

    private static int accent(UiPreferences preferences) {
        return preferences.highContrast() ? DarkModeTheme.textColor() : LiquidGlassTheme.accentColor();
    }

    private static void drawBar(GuiGraphics graphics, Bounds area, float offset, float fillWidth,
                                UiPreferences preferences) {
        if (area.width() == 0 || area.height() == 0) return;
        float radius = area.height() * 0.5f;
        RoundedCorners.draw(graphics, area.x(), area.y(), area.width(), area.height(), radius,
                0f, 0, track(preferences), false);
        RoundedCorners.draw(graphics, area.x() + offset, area.y(), fillWidth, area.height(), radius,
                0f, 0, accent(preferences), false);
    }

    private static void drawRing(GuiGraphics graphics, Bounds area, Arc arc, UiPreferences preferences) {
        int diameter = Math.min(area.width(), area.height());
        if (diameter == 0) return;
        float centerX = area.x() + area.width() * 0.5f;
        float centerY = area.y() + area.height() * 0.5f;
        float radius = diameter * 0.5f;
        if (diameter < 3) {
            int color = arc.sweep() > 0f ? accent(preferences) : track(preferences);
            RoundedCorners.draw(graphics, centerX - radius, centerY - radius, diameter, diameter,
                    radius, 0f, 0, color, false);
            return;
        }

        var pose = graphics.pose().last().pose();
        double scale = Minecraft.getInstance().getWindow().getGuiScale()
                * Math.min(Math.hypot(pose.m00(), pose.m01()), Math.hypot(pose.m10(), pose.m11()));
        float stroke = Math.max(1f, diameter / 6f);
        float feather = (float) Math.min(stroke * 0.5f, 1.0 / Math.max(0.01, scale));
        int segments = 4 * Math.clamp((int) Math.ceil(Math.sqrt(radius * scale / 0.32)), 6, 48);
        var ring = new Ring(radius, radius - stroke, feather, segments);
        double end = (double) arc.start() + arc.sweep();

        graphics.flush();
        var vertices = CapsuleRenderer.colorVertices(graphics, centerX, centerY);
        ring.emitArc(vertices, arc.start(), end, accent(preferences));
        ring.emitArc(vertices, end, arc.start() + 1.0, track(preferences));
        graphics.flush();
    }

    /** 从十二点方向顺时针生成相邻四边形，圆弧内外边缘向透明过渡。 */
    record Ring(float outer, float inner, float feather, int segments) {
        void emitArc(CapsuleMesh.VertexSink vertices, double start, double end, int color) {
            double sweep = end - start;
            if (sweep <= 0.0) return;
            int steps = (int) Math.ceil(segments * sweep);
            int transparent = color & 0x00FFFFFF;
            Ray previous = Ray.at(start);
            for (int i = 1; i <= steps; i++) {
                Ray next = Ray.at(start + sweep * i / steps);
                band(vertices, previous, next, outer, outer - feather, transparent, color);
                band(vertices, previous, next, outer - feather, inner + feather, color, color);
                band(vertices, previous, next, inner + feather, inner, color, transparent);
                previous = next;
            }
        }

        private static void band(CapsuleMesh.VertexSink vertices, Ray from, Ray to, float outer, float inner,
                                 int outerColor, int innerColor) {
            if (outer <= inner) return;
            from.vertex(vertices, outer, outerColor);
            from.vertex(vertices, inner, innerColor);
            to.vertex(vertices, inner, innerColor);
            to.vertex(vertices, outer, outerColor);
        }
    }

    private record Ray(float x, float y) {
        static Ray at(double turn) {
            double angle = turn * 2.0 * Math.PI;
            return new Ray((float) Math.sin(angle), (float) -Math.cos(angle));
        }

        void vertex(CapsuleMesh.VertexSink vertices, float radius, int color) {
            vertices.vertex(x * radius, y * radius, color);
        }
    }
}
