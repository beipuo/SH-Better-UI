package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** 山海：游标附近揭示的完整纹理资源，按绘制区域映射，半径使用 GUI 单位。 */
public record LaserFlowReveal(ResourceLocation texture, float radius, float opacity) {
    public LaserFlowReveal(ResourceLocation texture) { this(texture, 220f, .6f); }

    public LaserFlowReveal {
        Objects.requireNonNull(texture);
        if (!Float.isFinite(radius) || !Float.isFinite(opacity)) {
            throw new IllegalArgumentException("Non-finite laser reveal parameter");
        }
        radius = Math.max(0, radius);
        opacity = UiMath.clamp(opacity, 0, 1);
    }
}
