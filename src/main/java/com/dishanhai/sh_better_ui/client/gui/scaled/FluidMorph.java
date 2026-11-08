package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.glass.GuiRect;

/** 山海：胶囊到面板的浮点轮廓。位置、尺寸与圆角共用一个弹簧进度。 */
record FluidMorph(GuiRect rect, float radius) {
    static FluidMorph between(GuiRect capsule, GuiRect panel, float panelRadius, float progress) {
        float p = GuiMotion.clamp01(progress);
        float from = Math.min(capsule.getW(), capsule.getH()) * 0.5f;
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
