package com.dishanhai.sh_better_ui.client.gui.layout;

import static com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.*;

/** 山海：只在输出阶段把指定点吸附到像素格，尺寸与未吸附布局坐标保持原精度。 */
public record PixelAlignment(double pixelsPerGuiX, double pixelsPerGuiY) {
    public static final PixelAlignment ONE = new PixelAlignment(1);

    public enum Snap {
        /** 适合需要全程保留亚像素的运动。 */
        NONE,
        /** 保持局部坐标 (0,0) 落在 framebuffer 的整数像素边。 */
        ORIGIN,
        /** 保持指定布局锚点落在 framebuffer 的整数像素边。 */
        ANCHOR,
        /** 给原生整数坐标控件使用。 */
        GUI_ORIGIN
    }

    public PixelAlignment {
        if (!Double.isFinite(pixelsPerGuiX) || pixelsPerGuiX <= 0
                || !Double.isFinite(pixelsPerGuiY) || pixelsPerGuiY <= 0) {
            throw new IllegalArgumentException("Pixel scale must be finite and positive");
        }
    }

    public PixelAlignment(double guiScale) { this(guiScale, guiScale); }

    public Anchor toPixels(Anchor gui) { return new Anchor(gui.x() * pixelsPerGuiX, gui.y() * pixelsPerGuiY); }
    public Anchor toGui(Anchor pixels) { return new Anchor(pixels.x() / pixelsPerGuiX, pixels.y() / pixelsPerGuiY); }

    /** 物理像素中取最近整数。正负半格均向坐标增大的方向裁决。 */
    public Anchor snap(Anchor gui) {
        Anchor pixels = toPixels(gui);
        return toGui(new Anchor(nearest(pixels.x()), nearest(pixels.y())));
    }

    Transform apply(Transform world, Anchor localAnchor, Snap mode) {
        if (mode == Snap.NONE) return world;
        Anchor point = mode == Snap.ANCHOR ? world.map(localAnchor) : new Anchor(world.tx(), world.ty());
        Anchor snapped = mode == Snap.GUI_ORIGIN ? ONE.snap(point) : snap(point);
        return world.move(snapped.x() - point.x(), snapped.y() - point.y());
    }

    private static double nearest(double value) {
        double lower = Math.floor(value);
        return value - lower < 0.5 ? lower : lower + 1;
    }
}
