// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.live;

/** Rounded surface bounds in GUI coordinates. Opacity is a fraction in [0, 1]. */
public record SurfaceGeometry(double x, double y, double width, double height, double radius, double opacity) {
    public static final SurfaceGeometry HIDDEN = new SurfaceGeometry(0, 0, 0, 0, 0, 0);

    public SurfaceGeometry {
        x = finite(x);
        y = finite(y);
        width = extent(width, x);
        height = extent(height, y);
        radius = Math.max(0, Math.min(finite(radius), Math.min(width, height) / 2));
        opacity = Math.max(0, Math.min(finite(opacity), 1));
    }

    public boolean visible() {
        return opacity > 0 && width > 0 && height > 0;
    }

    /** Tests the actual visible shape, including its curved corners. */
    public boolean contains(double pointerX, double pointerY) {
        double localX = pointerX - x;
        double localY = pointerY - y;
        if (!visible() || !(localX >= 0 && localX < width && localY >= 0 && localY < height)) return false;
        if (radius == 0) return true;
        double dx = (localX - Math.max(radius, Math.min(localX, width - radius))) / radius;
        double dy = (localY - Math.max(radius, Math.min(localY, height - radius))) / radius;
        return dx * dx + dy * dy <= 1;
    }

    static double finite(double value) {
        return Double.isFinite(value) ? value + 0d : 0d;
    }

    static double extent(double value, double origin) {
        return Math.min(Math.max(0, finite(value)), Double.MAX_VALUE - Math.max(0, origin));
    }
}
