// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.intelligence;

import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Matrix;
import java.util.Objects;
import static com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.*;

/** Explicit per-viewport conversion. Framebuffer pixels and physical window pixels are independent inputs. */
public record UICoordinates(double guiWidth, double guiHeight, double framebufferWidth, double framebufferHeight,
                            double physicalWidth, double physicalHeight) {
    public UICoordinates {
        for (double value : new double[] {guiWidth, guiHeight, framebufferWidth, framebufferHeight, physicalWidth, physicalHeight}) {
            if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Coordinate dimensions must be finite and positive");
        }
    }
    public Rect convert(Rect source, Space target) {
        Objects.requireNonNull(source); Objects.requireNonNull(target);
        var from = dimensions(source.space()); var to = dimensions(target);
        double x = to[0] / from[0], y = to[1] / from[1];
        return new Rect(source.x() * x, source.y() * y, source.width() * x, source.height() * y, target);
    }
    private double[] dimensions(Space space) {
        return switch (space) {
            case GUI -> new double[] {guiWidth, guiHeight};
            case FRAMEBUFFER -> new double[] {framebufferWidth, framebufferHeight};
            case PHYSICAL -> new double[] {physicalWidth, physicalHeight};
            default -> throw new IllegalArgumentException("Local/world projected coordinates require their explicit projection transform");
        };
    }
    /** A conservative transformed envelope, explicitly not the exact occupied mask of a rotated shape. */
    public static Rect toGui(Rect local, Matrix localToGui) {
        Objects.requireNonNull(local); Objects.requireNonNull(localToGui);
        if (local.space() != Space.LOCAL && local.space() != Space.WORLD_PROJECTED) throw new IllegalArgumentException("Expected local or projected coordinates");
        var a = localToGui.point(local.x(), local.y()); var b = localToGui.point(local.right(), local.y());
        var c = localToGui.point(local.x(), local.bottom()); var d = localToGui.point(local.right(), local.bottom());
        double x = Math.min(Math.min(a.x(), b.x()), Math.min(c.x(), d.x()));
        double y = Math.min(Math.min(a.y(), b.y()), Math.min(c.y(), d.y()));
        return new Rect(x, y, Math.max(Math.max(a.x(), b.x()), Math.max(c.x(), d.x())) - x,
                Math.max(Math.max(a.y(), b.y()), Math.max(c.y(), d.y())) - y, Space.GUI);
    }
}
