package com.dishanhai.sh_better_ui.client.gui.layout;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;

/** 山海：视觉布局用的二维逻辑坐标。所有数值在最终绘制前保持浮点精度。 */
public final class AlignmentGeometry {
    private AlignmentGeometry() {}

    /** 指定坐标空间中的一个点，也可用于保存局部枢轴。 */
    public record Anchor(double x, double y) {
        public Anchor { finite(x); finite(y); }
    }

    /** 左闭右开的矩形。尺寸为零时没有可命中的面积。 */
    public record Rect(double x, double y, double width, double height) {
        public Rect {
            finite(x); finite(y);
            nonnegative(width); nonnegative(height);
            finite(x + width); finite(y + height);
        }

        public Rect(Bounds bounds) { this(bounds.x(), bounds.y(), bounds.width(), bounds.height()); }

        public double right() { return x + width; }
        public double bottom() { return y + height; }
        public Anchor center() { return new Anchor(x + width / 2, y + height / 2); }
        public boolean empty() { return width == 0 || height == 0; }

        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }

        public boolean overlaps(Rect other) {
            return !empty() && !other.empty() && x < other.right() && right() > other.x
                    && y < other.bottom() && bottom() > other.y;
        }

        public Rect intersect(Rect other) {
            double left = Math.max(x, other.x), top = Math.max(y, other.y);
            return edges(left, top, Math.min(right(), other.right()), Math.min(bottom(), other.bottom()));
        }

        public Rect union(Rect other) {
            if (empty()) return other;
            if (other.empty()) return this;
            return edges(Math.min(x, other.x), Math.min(y, other.y),
                    Math.max(right(), other.right()), Math.max(bottom(), other.bottom()));
        }

        public Rect inset(Insets insets) {
            return edges(Math.min(right(), x + insets.left), Math.min(bottom(), y + insets.top),
                    right() - insets.right, bottom() - insets.bottom);
        }

        public Rect outset(Insets insets) {
            return edges(x - insets.left, y - insets.top, right() + insets.right, bottom() + insets.bottom);
        }

        static Rect edges(double left, double top, double right, double bottom) {
            return new Rect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
        }
    }

    /** 非负的左、上、右、下边距。 */
    public record Insets(double left, double top, double right, double bottom) {
        public static final Insets NONE = new Insets(0, 0, 0, 0);
        public Insets {
            nonnegative(left); nonnegative(top); nonnegative(right); nonnegative(bottom);
        }
        public static Insets all(double value) { return new Insets(value, value, value, value); }
    }

    /**
     * 二维仿射变换。矩阵按行排列，乘列向量。
     * 允许零比例（例如折叠动画），此时没有可逆的命中区域。
     */
    public record Transform(double xx, double xy, double yx, double yy, double tx, double ty) {
        public static final Transform IDENTITY = translation(0, 0);

        public Transform {
            finite(xx); finite(xy); finite(yx); finite(yy); finite(tx); finite(ty);
        }

        public static Transform translation(double x, double y) { return new Transform(1, 0, 0, 1, x, y); }
        public static Transform scale(double x, double y) { return new Transform(x, 0, 0, y, 0, 0); }
        public static Transform rotation(double radians) {
            double sin = Math.sin(radians), cos = Math.cos(radians);
            return new Transform(cos, -sin, sin, cos, 0, 0);
        }

        /** 将 child 从局部映射到此变换的输入空间，然后应用此变换。 */
        public Transform compose(Transform child) {
            return new Transform(xx * child.xx + xy * child.yx, xx * child.xy + xy * child.yy,
                    yx * child.xx + yy * child.yx, yx * child.xy + yy * child.yy,
                    xx * child.tx + xy * child.ty + tx, yx * child.tx + yy * child.ty + ty);
        }

        /** 在输出空间整体平移。 */
        public Transform move(double x, double y) { return new Transform(xx, xy, yx, yy, tx + x, ty + y); }

        /** 保持局部枢轴在变换前后的位置，另保留显式位移。 */
        public Transform around(Anchor pivot) {
            return move(pivot.x - xx * pivot.x - xy * pivot.y, pivot.y - yx * pivot.x - yy * pivot.y);
        }

        public Anchor map(Anchor point) {
            return new Anchor(xx * point.x + xy * point.y + tx, yx * point.x + yy * point.y + ty);
        }

        /** 返回变换后四个角的轴对齐包围盒。 */
        public Rect map(Rect rect) {
            Anchor origin = map(new Anchor(rect.x, rect.y));
            if (rect.empty()) return new Rect(origin.x, origin.y, 0, 0);
            double ax = xx * rect.width, ay = yx * rect.width;
            double bx = xy * rect.height, by = yy * rect.height;
            return new Rect(origin.x + Math.min(0, ax) + Math.min(0, bx),
                    origin.y + Math.min(0, ay) + Math.min(0, by), Math.abs(ax) + Math.abs(bx),
                    Math.abs(ay) + Math.abs(by));
        }

        boolean contains(Rect local, double x, double y) {
            double determinant = xx * yy - xy * yx;
            if (determinant == 0 || !Double.isFinite(determinant)) return false;
            double dx = x - tx, dy = y - ty;
            return local.contains((yy * dx - xy * dy) / determinant, (xx * dy - yx * dx) / determinant);
        }
    }

    static void finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite alignment coordinate: " + value);
    }

    static void nonnegative(double value) {
        finite(value);
        if (value < 0) throw new IllegalArgumentException("Negative alignment size: " + value);
    }
}
