package com.dishanhai.sh_better_ui.client.gui.scaled;

/** 山海：根据滚轮方向和滚动边界决定是否切换到相邻页。每个滚动区域独立持有实例。 */
public final class ScrollPageTurn {
    private static final long WHEEL_IDLE_MS = 180L;

    private int turnedDirection;
    private long lastWheelAt;

    /** 跟随手势更新列表偏移。传入本次指针位移的 Y 分量，列表边界截住超出的部分。 */
    public static double dragOffset(double scrollOffset, double pointerDeltaY, double maxScroll) {
        if (!Double.isFinite(scrollOffset) || !Double.isFinite(pointerDeltaY)
                || !Double.isFinite(maxScroll) || maxScroll < 0) return scrollOffset;
        return Math.clamp(scrollOffset - pointerDeltaY, 0.0, maxScroll);
    }

    /**
     * 传入本次滚轮事件计算出的目标偏移及最大偏移，偏移从顶端的 0 向下递增。
     * 原版滚轮方向为正值向上、负值向下。页面从 0 开始编号，返回当前页表示无需切页。
     * 应在命中滚动区域、子控件未消耗事件后调用，时间可使用 {@link PageTransition#nowMs()}。
     */
    public int targetPage(double wheelDelta, double scrollTarget, double maxScroll,
                          int currentPage, int pageCount, long nowMs) {
        if (!Double.isFinite(wheelDelta) || wheelDelta == 0.0
                || !Double.isFinite(scrollTarget) || !Double.isFinite(maxScroll) || maxScroll < 0.0
                || currentPage < 0 || currentPage >= pageCount) {
            return currentPage;
        }

        int direction = wheelDelta > 0.0 ? -1 : 1;
        if (turnedDirection != direction || nowMs - lastWheelAt >= WHEEL_IDLE_MS) {
            turnedDirection = 0;
        }
        lastWheelAt = nowMs;
        if (turnedDirection != 0) return currentPage;

        int nextPage = currentPage + direction;
        boolean atEdge = direction < 0 ? scrollTarget <= 0.0 : scrollTarget >= maxScroll;
        if (!atEdge || nextPage < 0 || nextPage >= pageCount) return currentPage;

        turnedDirection = direction;
        return nextPage;
    }

    /** 列表来源更换或滚动区域重新打开时，清除上一次滚动手势。 */
    public void reset() {
        turnedDirection = 0;
    }
}
