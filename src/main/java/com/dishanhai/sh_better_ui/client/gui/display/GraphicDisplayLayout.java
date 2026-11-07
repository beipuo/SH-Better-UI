package com.dishanhai.sh_better_ui.client.gui.display;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 山海：以 GUI 逻辑坐标计算图形化显示的内容区、行列和分页控件。 */
public final class GraphicDisplayLayout {
    private static final int GAP = 4;
    private static final int PAGE_BUTTON_SIZE = 24;

    private GraphicDisplayLayout() {}

    /** 以最小列宽决定可容纳的列数。面板更窄时仍保留一列。 */
    public record Grid(int minColumnWidth, int maxColumns) {
        public static final Grid DEFAULT = new Grid(118, 2);

        public Grid {
            if (minColumnWidth <= 0 || maxColumns <= 0) {
                throw new IllegalArgumentException("Column width and count must be positive");
            }
        }
    }

    public record Bounds(int x, int y, int width, int height) {
        public Bounds {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("Negative bounds size");
            }
        }

        public int right() { return x + width; }

        public int bottom() { return y + height; }

        /** 左上边界可命中，右下边界及空矩形不可命中。 */
        public boolean contains(double mouseX, double mouseY) {
            return width > 0 && height > 0 && mouseX >= x && mouseX < right()
                    && mouseY >= y && mouseY < bottom();
        }

        private static Bounds empty() { return new Bounds(0, 0, 0, 0); }
    }

    public record Result(Bounds content, List<Bounds> cells, Bounds previous, Bounds next) {
        public Result {
            cells = List.copyOf(cells);
        }

        /** 返回内容区内命中的当前页条目索引，空白处返回 -1。 */
        public int hitTest(double mouseX, double mouseY) {
            if (!content.contains(mouseX, mouseY)) return -1;
            for (int index = 0; index < cells.size(); index++) {
                if (cells.get(index).contains(mouseX, mouseY)) return index;
            }
            return -1;
        }
    }

    /** 使用默认列宽和列数计算布局。 */
    public static Result arrange(Bounds area, int count, int rowHeight, boolean pagination) {
        return arrange(area, count, rowHeight, pagination, Grid.DEFAULT);
    }

    /**
     * 计算内面板的布局。count 为当前页条目数，rowHeight 为完整条目的逻辑高度。
     * cells 按行返回，最后一行可部分超出 content，绘制时须裁切。隐藏的分页控件使用空矩形。
     */
    public static Result arrange(Bounds area, int count, int rowHeight, boolean pagination, Grid grid) {
        Objects.requireNonNull(grid);
        if (count < 0 || rowHeight <= 0) {
            throw new IllegalArgumentException("Invalid cell count or row height");
        }

        Bounds content = area;
        Bounds previous = Bounds.empty();
        Bounds next = previous;
        int footerHeight = footerHeight(area, pagination);
        if (footerHeight > 0) {
            int footerY = area.bottom() - PAGE_BUTTON_SIZE;
            content = new Bounds(area.x(), area.y(), area.width(), area.height() - footerHeight);
            previous = new Bounds(area.x(), footerY, PAGE_BUTTON_SIZE, PAGE_BUTTON_SIZE);
            next = new Bounds(area.right() - PAGE_BUTTON_SIZE, footerY, PAGE_BUTTON_SIZE, PAGE_BUTTON_SIZE);
        }

        if (content.width() == 0 || content.height() == 0 || count == 0) {
            return new Result(content, List.of(), previous, next);
        }

        int columns = columnCount(content.width(), grid);
        int widthForColumns = content.width() - (columns - 1) * GAP;
        int columnWidth = widthForColumns / columns;
        int firstWideColumn = columns - widthForColumns % columns;
        long rowStride = (long) rowHeight + GAP;
        List<Bounds> cells = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            long rowOffset = (index / columns) * rowStride;
            if (rowOffset >= content.height()) break;
            int column = index % columns;
            int extraWidthBefore = Math.max(0, column - firstWideColumn);
            int xOffset = column * columnWidth + column * GAP + extraWidthBefore;
            int width = columnWidth + (column >= firstWideColumn ? 1 : 0);
            int y = content.y() + (int) rowOffset;
            cells.add(new Bounds(content.x() + xOffset, y, width, rowHeight));
        }
        return new Result(content, cells, previous, next);
    }

    /** 计算一页能完整容纳的条目数，分页占位与 arrange 一致。 */
    public static int pageCapacity(Bounds area, int rowHeight, boolean pagination, Grid grid) {
        Objects.requireNonNull(grid);
        if (rowHeight <= 0) throw new IllegalArgumentException("Row height must be positive");
        if (area.width() == 0) return 0;

        int contentHeight = area.height() - footerHeight(area, pagination);
        long rows = ((long) contentHeight + GAP) / ((long) rowHeight + GAP);
        return (int) Math.min(Integer.MAX_VALUE, rows * columnCount(area.width(), grid));
    }

    private static int footerHeight(Bounds area, boolean pagination) {
        return pagination && area.width() >= PAGE_BUTTON_SIZE * 2 + GAP && area.height() >= PAGE_BUTTON_SIZE + GAP
                ? PAGE_BUTTON_SIZE + GAP : 0;
    }

    private static int columnCount(int width, Grid grid) {
        long fitting = ((long) width + GAP) / ((long) grid.minColumnWidth() + GAP);
        return Math.clamp(fitting, 1, grid.maxColumns());
    }
}
