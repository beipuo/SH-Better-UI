package com.dishanhai.sh_better_ui.client.gui.display;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 山海：按 GUI 逻辑坐标定位单个控件、横排和直排。 */
public final class UiLayout {
    private UiLayout() {}

    public enum Horizontal {
        LEFT, CENTER, RIGHT;

        private long position(int x, int width, long contentWidth) {
            long remaining = width - contentWidth;
            return switch (this) {
                case LEFT -> x;
                case CENTER -> x + Math.floorDiv(remaining, 2);
                case RIGHT -> x + remaining;
            };
        }
    }

    public enum Vertical {
        TOP, CENTER, BOTTOM;

        private long position(int y, int height, long contentHeight) {
            long remaining = height - contentHeight;
            return switch (this) {
                case TOP -> y;
                case CENTER -> y + Math.floorDiv(remaining, 2);
                case BOTTOM -> y + remaining;
            };
        }
    }

    public record Size(int width, int height) {
        public Size {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("Negative layout size");
            }
        }
    }

    /**
     * 在容器内定位给定尺寸。居中时向左／上取整，空间不足时保留尺寸并允许超出容器。
     * 坐标或边界超出 int 范围时抛出 ArithmeticException。
     */
    public static Bounds align(Bounds area, Size size, Horizontal horizontal, Vertical vertical) {
        long x = horizontal.position(area.x(), area.width(), size.width());
        long y = vertical.position(area.y(), area.height(), size.height());
        return bounds(x, y, size);
    }

    /**
     * 横向依序排列。水平对齐作用于整组，垂直对齐作用于各项，均以容器为基准。
     * 返回不可变列表，尺寸、取整和坐标范围与 align 一致；gap 必须非负。
     */
    public static List<Bounds> row(Bounds area, List<Size> sizes, int gap,
                                   Horizontal horizontal, Vertical vertical) {
        long width = totalGap(sizes.size(), gap);
        Objects.requireNonNull(area);
        Objects.requireNonNull(horizontal);
        Objects.requireNonNull(vertical);
        if (sizes.isEmpty()) return List.of();
        for (Size size : sizes) width += size.width();

        long x = horizontal.position(area.x(), area.width(), width);
        var result = new ArrayList<Bounds>(sizes.size());
        for (Size size : sizes) {
            long y = vertical.position(area.y(), area.height(), size.height());
            result.add(bounds(x, y, size));
            x += (long) size.width() + gap;
        }
        return List.copyOf(result);
    }

    /**
     * 垂直依序排列。垂直对齐作用于整组，水平对齐作用于各项，均以容器为基准。
     * 返回不可变列表，尺寸、取整和坐标范围与 align 一致；gap 必须非负。
     */
    public static List<Bounds> column(Bounds area, List<Size> sizes, int gap,
                                      Horizontal horizontal, Vertical vertical) {
        long height = totalGap(sizes.size(), gap);
        Objects.requireNonNull(area);
        Objects.requireNonNull(horizontal);
        Objects.requireNonNull(vertical);
        if (sizes.isEmpty()) return List.of();
        for (Size size : sizes) height += size.height();

        long y = vertical.position(area.y(), area.height(), height);
        var result = new ArrayList<Bounds>(sizes.size());
        for (Size size : sizes) {
            long x = horizontal.position(area.x(), area.width(), size.width());
            result.add(bounds(x, y, size));
            y += (long) size.height() + gap;
        }
        return List.copyOf(result);
    }

    private static long totalGap(int count, int gap) {
        if (gap < 0) throw new IllegalArgumentException("Negative layout gap");
        return (long) Math.max(0, count - 1) * gap;
    }

    private static Bounds bounds(long x, long y, Size size) {
        // Bounds 的 right()/bottom() 使用 int 加法，保证两条边也可表示。
        Math.toIntExact(x + size.width());
        Math.toIntExact(y + size.height());
        return new Bounds(Math.toIntExact(x), Math.toIntExact(y), size.width(), size.height());
    }
}
