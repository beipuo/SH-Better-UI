package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.glass.GuiRect;

/** 山海：胶囊到面板的浮点轮廓。位置、尺寸与圆角共用一个弹簧进度。 */
public record FluidMorph(GuiRect rect, float radius) {
    static final int SEED_WIDTH = 32;
    static final int SEED_HEIGHT = 10;

    /** 面板入场统一从小胶囊起步，坐标为胶囊中心。 */
    public static GuiRect seed(float centerX, float centerY) {
        return new GuiRect(centerX - SEED_WIDTH * 0.5f, centerY - SEED_HEIGHT * 0.5f, SEED_WIDTH, SEED_HEIGHT);
    }

    public static FluidMorph between(GuiRect capsule, GuiRect panel, float panelRadius, float progress) {
        return between(capsule, panel, Math.min(capsule.getW(), capsule.getH()) * 0.5f, panelRadius, progress);
    }

    static FluidMorph between(GuiRect capsule, GuiRect panel, float capsuleRadius, float panelRadius, float progress) {
        float p = GuiMotion.clamp01(progress);
        float from = RoundedCorners.fit(capsule.getW(), capsule.getH(), capsuleRadius);
        float to = RoundedCorners.fit(panel.getW(), panel.getH(), panelRadius);
        if (p == 0f) return new FluidMorph(capsule, from);
        if (p == 1f) return new FluidMorph(panel, to);
        var rect = new GuiRect(GuiMotion.lerp(capsule.getX(), panel.getX(), p),
                GuiMotion.lerp(capsule.getY(), panel.getY(), p),
                GuiMotion.lerp(capsule.getW(), panel.getW(), p),
                GuiMotion.lerp(capsule.getH(), panel.getH(), p));
        return new FluidMorph(rect, RoundedCorners.fit(rect.getW(), rect.getH(), GuiMotion.lerp(from, to, p)));
    }
}
