package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;

import java.util.Objects;

/** 山海：受光面的位置和圆角，与绘制区域使用同一 GUI 坐标系。 */
public record LaserFlowSurface(Bounds bounds, float radius) {
    public LaserFlowSurface {
        Objects.requireNonNull(bounds);
        if (!Float.isFinite(radius)) throw new IllegalArgumentException("Non-finite laser surface radius");
        radius = UiMath.clamp(radius, 0, Math.min(bounds.width(), bounds.height()) * .5f);
    }
}
