package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.glass.GlassBlur;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.config.GlassBackdropSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** 山海：客户端 GUI 的左糊右清效果与独立径向渐变。 */
public final class VisualReveal {
    private static final int MUTED_WARM_ORANGE = 0xFFD6A37D;
    private static final int MUTED_GREEN = 0xFF778B78;

    private VisualReveal() {}

    /** 在画好内容后调用，坐标使用当前 GUI pose。返回是否执行了后处理。 */
    public static boolean blurRegion(GuiGraphics graphics, int x, int y, int width, int height,
                                     RevealProfile profile) {
        return GlassBlur.revealRegion(graphics, new GuiRect(x, y, width, height),
                profile.maxRadius(), profile.fullBlurUntil(), profile.sharpFrom());
    }

    /** 绘制独立 RGBA 贴图。贴图应为完整资源，透明部分保留背景。 */
    public static void drawTexture(GuiGraphics graphics, ResourceLocation texture,
                                   int x, int y, int width, int height, RevealProfile profile) {
        if (width <= 0 || height <= 0) return;
        GlassBackdropSettings settings = GlassBackdropSettings.current();
        float radius = settings.enabled() && settings.strength() > 0.01f
                ? profile.maxRadius() * settings.blurRadiusScale() * settings.strength() : 0f;
        VisualRevealRenderer.drawTexture(graphics, texture, x, y, width, height, profile, radius);
    }

    /** 绘制右下暖橙、向外过渡到灰绿的柔和渐变。 */
    public static void drawRadialGradient(GuiGraphics graphics, int x, int y, int width, int height) {
        drawRadialGradient(graphics, x, y, width, height, 1f, 1f, MUTED_WARM_ORANGE, MUTED_GREEN);
    }

    /** 原点为矩形内归一化坐标，(0,0) 是左上角，色值为 ARGB。 */
    public static void drawRadialGradient(GuiGraphics graphics, int x, int y, int width, int height,
                                          float originU, float originV, int innerArgb, int outerArgb) {
        if (width <= 0 || height <= 0) return;
        if (!(originU >= 0f && originU <= 1f && originV >= 0f && originV <= 1f)) {
            throw new IllegalArgumentException("Gradient origin must be in [0, 1]");
        }
        VisualRevealRenderer.drawRadialGradient(graphics, x, y, width, height, originU, originV, innerArgb, outerArgb);
    }
}
