package com.dishanhai.sh_better_ui.client.gui.display;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.util.Objects;

/** 山海：可着色的 16×16 GUI 图标，可使用库内图标或调用方的透明 PNG。 */
public record GraphicIcon(ResourceLocation texture) {
    public static final int PIXELS = 16;
    public static final GraphicIcon INFO = builtIn("info");
    public static final GraphicIcon SUCCESS = builtIn("success");
    public static final GraphicIcon WARNING = builtIn("warning");
    public static final GraphicIcon ERROR = builtIn("error");
    public static final GraphicIcon PREVIOUS = builtIn("previous");
    public static final GraphicIcon NEXT = builtIn("next");
    public static final GraphicIcon ADD = builtIn("add");
    public static final GraphicIcon REMOVE = builtIn("remove");
    public static final GraphicIcon SEARCH = builtIn("search");
    public static final GraphicIcon SETTINGS = builtIn("settings");
    public static final GraphicIcon REFRESH = builtIn("refresh");
    public static final GraphicIcon CLOSE = builtIn("close");
    public static final GraphicIcon HOME = builtIn("home");
    public static final GraphicIcon LIST = builtIn("list");
    public static final GraphicIcon WIRELESS = builtIn("wireless");
    public static final GraphicIcon TOPOLOGY = builtIn("topology");
    public static final GraphicIcon STATISTICS = builtIn("statistics");
    public static final GraphicIcon MEMBERS = builtIn("members");

    public GraphicIcon {
        Objects.requireNonNull(texture, "texture");
    }

    /** 按正整数倍缩放，坐标和返回尺寸均使用逻辑 GUI 单位。 */
    public static int size(int scale) {
        if (scale <= 0) throw new IllegalArgumentException("scale must be positive");
        return Math.multiplyExact(PIXELS, scale);
    }

    /** 在客户端渲染线程绘制，ARGB 色值与外层颜色相乘。 */
    public void draw(GuiGraphics graphics, int x, int y, int scale, int argb) {
        int size = size(scale);
        if ((argb >>> 24) == 0) return;

        float[] color = RenderSystem.getShaderColor().clone();
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blending = GL11.glIsEnabled(GL11.GL_BLEND);
        int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        int equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        try {
            graphics.flush();
            if (!depthTest) RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(color[0] * ((argb >>> 16) & 255) / 255f,
                    color[1] * ((argb >>> 8) & 255) / 255f, color[2] * (argb & 255) / 255f,
                    color[3] * (argb >>> 24) / 255f);
            graphics.blit(texture, x, y, size, size, 0f, 0f, PIXELS, PIXELS, PIXELS, PIXELS);
        } finally {
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
            RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
            if (blending) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();
            if (depthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
        }
    }

    private static GraphicIcon builtIn(String name) {
        return new GraphicIcon(ResourceLocation.tryParse("sh_better_ui:textures/gui/display/" + name + ".png"));
    }
}
