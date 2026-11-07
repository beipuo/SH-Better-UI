package com.dishanhai.sh_better_ui.client.gui.scaled;

/** 山海：视口内容到屏幕 GUI 坐标的平移与缩放，绘制和命中共用。 */
public record ViewportTransform(double x, double y, double scale) {
    public ViewportTransform {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException();
        }
    }

    public double contentX(double screenX) { return (screenX - x) / scale; }
    public double contentY(double screenY) { return (screenY - y) / scale; }

    /** 应用滚轮或捏合提供的相对比例，保留锚点下的内容位置。 */
    public ViewportTransform zoomAt(double anchorX, double anchorY, double factor,
                                    double minScale, double maxScale) {
        if (!Double.isFinite(minScale) || !Double.isFinite(maxScale) || minScale <= 0 || minScale > maxScale) {
            throw new IllegalArgumentException();
        }
        if (!Double.isFinite(anchorX) || !Double.isFinite(anchorY) || !Double.isFinite(factor) || factor <= 0) {
            return this;
        }
        double nextScale = Math.clamp(scale * factor, minScale, maxScale);
        if (nextScale == scale) return this;
        return new ViewportTransform(anchorX - contentX(anchorX) * nextScale,
                anchorY - contentY(anchorY) * nextScale, nextScale);
    }
}
