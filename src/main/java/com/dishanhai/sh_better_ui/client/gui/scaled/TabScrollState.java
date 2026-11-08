package com.dishanhai.sh_better_ui.client.gui.scaled;

/** 山海：与渲染无关的水平标签视口，复用指针捕获并保留小数滚动量。 */
public final class TabScrollState {
    public static final double DRAG_THRESHOLD = 4;
    private final PointerDrag pointer = new PointerDrag();
    private double maxOffset;
    private double offset;
    private double pressedOffset;
    private boolean dragged;

    /** 更新尺寸并夹紧现有位置；布局改变会取消尚未完成的拖动。 */
    public void resize(double viewportWidth, double contentWidth) {
        if (!Double.isFinite(viewportWidth) || !Double.isFinite(contentWidth)
                || viewportWidth < 0 || contentWidth < 0) throw new IllegalArgumentException();
        cancelDrag();
        maxOffset = Math.max(0, contentWidth - viewportWidth);
        scrollTo(offset);
    }

    public double offset() { return offset; }
    public double maxOffset() { return maxOffset; }

    public boolean scrollTo(double value) {
        if (!Double.isFinite(value)) return false;
        double previous = offset;
        offset = Math.max(0, Math.min(maxOffset, value));
        return previous != offset;
    }

    /** 正 delta 向内容末尾滚动；非有限输入忽略。 */
    public boolean scrollBy(double delta) {
        return Double.isFinite(delta) && scrollTo(offset + delta);
    }

    /** 显示内容坐标中的区间，过宽的标签将其起点对齐到视口起点。 */
    public void reveal(double start, double end, double viewportWidth) {
        if (!Double.isFinite(start) || !Double.isFinite(end) || !Double.isFinite(viewportWidth)
                || start < 0 || end < start || viewportWidth < 0) throw new IllegalArgumentException();
        if (start < offset || end - start > viewportWidth) scrollTo(start);
        else if (end > offset + viewportWidth) scrollTo(end - viewportWidth);
    }

    public boolean beginDrag(double x, double y) {
        if (maxOffset == 0 || !pointer.begin(0, x, y)) return false;
        pressedOffset = offset;
        dragged = false;
        return true;
    }

    /** 阈值内不移动内容；一旦开始拖动，即使回到原位也不会成为点击。 */
    public boolean drag(double x, double y) {
        if (pointer.move(0, x, y) == null) return false;
        if (Math.abs(pointer.offsetX()) >= DRAG_THRESHOLD) dragged = true;
        if (dragged) scrollTo(pressedOffset - pointer.offsetX());
        return dragged;
    }

    /** 返回是否发生拖动，供控件抑制放开时的点击。 */
    public boolean endDrag(double x, double y) {
        if (!pointer.active()) return false;
        drag(x, y);
        boolean result = dragged;
        pointer.cancel();
        dragged = false;
        return result;
    }

    /** 取消时恢复按下前的视口。 */
    public boolean cancelDrag() {
        if (!pointer.active()) return false;
        pointer.cancel();
        scrollTo(pressedOffset);
        dragged = false;
        return true;
    }

    public boolean dragging() { return pointer.active() && dragged; }
}
