package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** 山海：使用浮点 GUI 单位计算同心圆角。内外层的四边须使用同一个内距。 */
public final class RoundedCorners {
    private RoundedCorners() {}

    /** 半径上限为短边的一半，奇数尺寸保留半个 GUI 单位。 */
    public static float fit(float width, float height, float radius) {
        return Math.min(Math.max(0f, radius), Math.max(0f, Math.min(width, height) * 0.5f));
    }

    /** 给定内层的尺寸，先限制内圆角，再计算四边各外扩 padding 后的外圆角。 */
    public static float outsetRadius(float width, float height, float radius, float padding) {
        return fit(width, height, radius) + Math.max(0f, padding);
    }

    /** 给定外层的尺寸，内缩超过圆弧时形成直角。 */
    public static float insetRadius(float width, float height, float radius, float padding) {
        return Math.max(0f, fit(width, height, radius) - Math.max(0f, padding));
    }

    /** 绘制圆角矩形与内描边。坐标、半径和描边宽度都使用当前 pose 下的 GUI 单位。 */
    public static void draw(GuiGraphics graphics, float x, float y, float width, float height,
                            float radius, float stroke, int edge, int fill) {
        draw(graphics, x, y, width, height, radius, stroke, edge, fill, true);
    }

    static void draw(GuiGraphics graphics, float x, float y, float width, float height,
                     float radius, float stroke, int edge, int fill, boolean writeDepth) {
        if (!(width > 0f && height > 0f) || !Float.isFinite(width) || !Float.isFinite(height)
                || Float.isNaN(radius) || Float.isNaN(stroke) || ((edge | fill) >>> 24) == 0) return;
        float corner = fit(width, height, radius);
        var pose = graphics.pose().last().pose();
        double scaleX = Math.hypot(pose.m00(), pose.m01());
        double scaleY = Math.hypot(pose.m10(), pose.m11());
        float pixelsPerUnit = (float) (Minecraft.getInstance().getWindow().getGuiScale() * Math.min(scaleX, scaleY));
        CapsuleRenderer.draw(graphics, x, y, width, height, corner, Math.max(0f, stroke),
                pixelsPerUnit, edge, fill, writeDepth);
    }
}
