package com.dishanhai.sh_better_ui.client.glass;

import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 山海：中性浅色玻璃底。先画背景模糊，再画半透明板与细亮边；调用方随后绘制文字和图标。
 * 不持有界面状态，原生 Screen 和 LDLib 都可以复用。模糊不可用时底板仍可见。
 */
public final class GlassSurface {
    private static final int PANEL_FILL = 0xA6F4F8FF;
    private static final int CONTROL_FILL = 0xB3FFFFFF;
    private static final int CONTROL_HOVER = 0xD1FFFFFF;
    private static final int EDGE = 0xA6FFFFFF;
    private static final int EDGE_HOVER = 0xD9FFFFFF;
    private static final int EDGE_SHADE = 0x7098A8B4;
    private static final int HIGHLIGHT = 0x99FFFFFF;

    private GlassSurface() {}

    /** 面板和卡片；坐标为当前 pose 下的 GUI 单位。 */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        if (w < 3 || h < 3) return;
        GlassBlur.backdrop(g, new GuiRect(x, y, w, h), 1f, GlassStyle.CLEAR, 8f);
        plate(g, x, y, w, h, PANEL_FILL, EDGE);
    }

    /** 按钮与页签；hover 为 0..1，可直接接入现有弹簧动画。 */
    public static void control(GuiGraphics g, int x, int y, int w, int h, float hover) {
        control(g, x, y, w, h, hover, true);
    }

    /** 已位于毛玻璃面板内时可关闭重复背景采样。 */
    public static void control(GuiGraphics g, int x, int y, int w, int h, float hover, boolean sampleBackground) {
        if (w < 3 || h < 3) return;
        float t = GuiMotion.clamp01(hover);
        if (sampleBackground) GlassBlur.backdrop(g, new GuiRect(x, y, w, h), 1f, GlassStyle.CLEAR, 5f);
        plate(g, x, y, w, h,
                GuiMotion.lerpColor(CONTROL_FILL, CONTROL_HOVER, t),
                GuiMotion.lerpColor(EDGE, EDGE_HOVER, t));
    }

    /** 山海：烟灰面板采用中等背景模糊，不额外提亮、折射或绘制强高光。 */
    public static void smokePanel(GuiGraphics g, int x, int y, int w, int h) {
        if (w < 3 || h < 3) return;
        GlassBlur.backdrop(g, new GuiRect(x, y, w, h), 1f, GlassStyle.NONE, 12f);
        GlassColors colors = GlassColors.SMOKE;
        plate(g, x, y, w, h, colors.PANEL_FILL, colors.ROOT_EDGE,
                colors.DISPLAY_EDGE, colors.LIT);
    }

    /** 烟灰根板内的控件不重复模糊；沿用按钮弹簧的 hover 值。 */
    public static void smokeControl(GuiGraphics g, int x, int y, int w, int h, float hover) {
        if (w < 3 || h < 3) return;
        GlassColors colors = GlassColors.SMOKE;
        float t = GuiMotion.clamp01(hover);
        plate(g, x, y, w, h,
                GuiMotion.lerpColor(colors.BUTTON_FILL, colors.BUTTON_FILL_HOVER, t),
                colors.BUTTON_EDGE, colors.DISPLAY_EDGE, colors.LIT);
    }

    private static void plate(GuiGraphics g, int x, int y, int w, int h, int fill, int edge) {
        plate(g, x, y, w, h, fill, edge, EDGE_SHADE, HIGHLIGHT);
    }

    private static void plate(GuiGraphics g, int x, int y, int w, int h,
                              int fill, int edge, int shade, int highlight) {
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        g.fill(x, y, x + w, y + 1, edge);
        g.fill(x, y + 1, x + 1, y + h, edge);
        g.fill(x + 1, y + h - 1, x + w, y + h, shade);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, shade);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, highlight);
    }
}
