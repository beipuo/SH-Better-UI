package com.dishanhai.sh_better_ui.client.gui.display;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiMath;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 山海：按原生 GUI 逻辑坐标排布网络风格页签、面板和内容行。 */
public final class NetworkLayout {
    private static final int PANEL_SIZE = 228;
    private static final int MARGIN = 6;
    private static final int SIDE_PADDING = 12;
    private static final int VERTICAL_PADDING = 8;
    private static final int TAB_SIZE = 22;
    private static final int TAB_GAP = 3;
    private static final int PANEL_GAP = 4;
    private static final int TITLE_GAP = 8;
    private static final int PAGE_BUTTON_SIZE = 20;
    private static final int FOOTER_GAP = 4;
    private static final Bounds EMPTY = new Bounds(0, 0, 0, 0);

    private NetworkLayout() {}

    public enum RowKind { FIELD, STATUS, TOGGLE }

    /** rows 对应输入列表从 firstRow 开始的连续条目。页码从 0 开始。 */
    public record Result(Bounds panel, Bounds title, List<Bounds> tabs, Optional<Bounds> action,
                         List<Bounds> rows, int firstRow, int pageIndex, int pageCount, Bounds previous, Bounds next) {
        public Result {
            tabs = List.copyOf(tabs);
            rows = List.copyOf(rows);
        }
    }

    /**
     * 支持一至七个页签及一个独立的右侧操作按钮。窄窗口自动换行，内容按完整行分页。
     * 请求页码会限制在有效范围内。空间不足以放下控件时返回空结果。
     */
    public static Optional<Result> arrange(int screenWidth, int screenHeight, int tabCount, boolean hasAction,
                                           List<RowKind> rows, int lineHeight, int pageIndex) {
        if (tabCount < 1 || tabCount > 7 || lineHeight < 1) {
            throw new IllegalArgumentException("Expected one to seven tabs and a positive line height");
        }
        if (screenWidth < 2 * (MARGIN + SIDE_PADDING) + 2 * PAGE_BUTTON_SIZE + FOOTER_GAP
                || screenHeight < 2 * MARGIN) {
            return Optional.empty();
        }
        List<RowKind> kinds = List.copyOf(rows);
        int panelWidth = Math.min(PANEL_SIZE, screenWidth - 2 * MARGIN);
        int innerWidth = panelWidth - 2 * SIDE_PADDING;
        int tabsPerLine = (innerWidth + TAB_GAP) / (TAB_SIZE + TAB_GAP);
        int buttonCount = tabCount + (hasAction ? 1 : 0);
        int tabLines = UiMath.ceilDiv(buttonCount, tabsPerLine);
        int tabsHeight = tabLines * (TAB_SIZE + TAB_GAP) - TAB_GAP;
        int panelHeight = Math.min(PANEL_SIZE, screenHeight - 2 * MARGIN - tabsHeight - PANEL_GAP);
        long titleHeight = Math.max(20, rowHeight(RowKind.FIELD, lineHeight));
        if (panelHeight < titleHeight + 2 * VERTICAL_PADDING) return Optional.empty();

        int innerHeight = panelHeight - (int) titleHeight - 2 * VERTICAL_PADDING - TITLE_GAP;
        var starts = new ArrayList<Integer>();
        starts.add(0);
        boolean paginated = fittingEnd(kinds, 0, innerHeight, lineHeight) < kinds.size();
        if (paginated) {
            int pageHeight = innerHeight - PAGE_BUTTON_SIZE - FOOTER_GAP;
            for (int start = 0; start < kinds.size();) {
                int end = fittingEnd(kinds, start, pageHeight, lineHeight);
                if (end == start) return Optional.empty();
                starts.add(end);
                start = end;
            }
        } else {
            starts.add(kinds.size());
        }

        int pageCount = starts.size() - 1;
        int page = UiMath.clamp(pageIndex, 0, pageCount - 1);
        int firstRow = starts.get(page);
        int x = (screenWidth - panelWidth) / 2;
        int top = (screenHeight - panelHeight - tabsHeight - PANEL_GAP) / 2;
        var panel = new Bounds(x, top + tabsHeight + PANEL_GAP, panelWidth, panelHeight);
        int innerX = x + SIDE_PADDING;
        var title = new Bounds(innerX + 6, panel.y() + VERTICAL_PADDING, innerWidth - 12, (int) titleHeight);
        var tabs = new ArrayList<Bounds>();
        for (int i = 0; i < tabCount; i++) {
            tabs.add(new Bounds(innerX + i % tabsPerLine * (TAB_SIZE + TAB_GAP),
                    top + i / tabsPerLine * (TAB_SIZE + TAB_GAP), TAB_SIZE, TAB_SIZE));
        }
        var action = hasAction
                ? Optional.of(new Bounds(innerX + innerWidth - TAB_SIZE,
                        top + (tabLines - 1) * (TAB_SIZE + TAB_GAP), TAB_SIZE, TAB_SIZE))
                : Optional.<Bounds>empty();
        var visibleRows = new ArrayList<Bounds>();
        int rowY = title.bottom() + TITLE_GAP;
        for (int i = firstRow; i < starts.get(page + 1); i++) {
            RowKind kind = kinds.get(i);
            if (i > firstRow) rowY += gap(kinds.get(i - 1), kind);
            int height = (int) rowHeight(kind, lineHeight);
            visibleRows.add(new Bounds(innerX, rowY, innerWidth, height));
            rowY += height;
        }
        int footerY = panel.bottom() - VERTICAL_PADDING - PAGE_BUTTON_SIZE;
        var previous = paginated ? new Bounds(innerX, footerY, PAGE_BUTTON_SIZE, PAGE_BUTTON_SIZE) : EMPTY;
        var next = paginated
                ? new Bounds(innerX + innerWidth - PAGE_BUTTON_SIZE, footerY, PAGE_BUTTON_SIZE, PAGE_BUTTON_SIZE) : EMPTY;
        return Optional.of(new Result(panel, title, tabs, action, visibleRows, firstRow, page, pageCount, previous, next));
    }

    private static int fittingEnd(List<RowKind> rows, int start, int availableHeight, int lineHeight) {
        long height = 0;
        for (int i = start; i < rows.size(); i++) {
            RowKind kind = rows.get(i);
            if (i > start) height += gap(rows.get(i - 1), kind);
            height += rowHeight(kind, lineHeight);
            if (height > availableHeight) return i;
        }
        return rows.size();
    }

    private static long rowHeight(RowKind kind, int lineHeight) {
        return switch (kind) {
            case FIELD -> Math.max(18, lineHeight + 4L);
            case STATUS -> Math.max(28, 2L * lineHeight + 8);
            case TOGGLE -> Math.max(16, lineHeight + 4L);
        };
    }

    private static int gap(RowKind previous, RowKind next) {
        if (previous != next) return 8;
        return next == RowKind.TOGGLE ? 2 : 4;
    }
}
