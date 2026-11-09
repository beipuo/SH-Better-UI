package com.dishanhai.sh_better_ui.client.gui.scaled;

/** 山海：单个控件的点击亮纹时间轴。坐标保留为相对值，重排后仍跟随控件。 */
public final class ClickRipple {
    public static final long DURATION_MS = 420L;

    /** 圆心是控件内的 0..1 坐标；uniform 表示减少动效时整块淡出。 */
    public record Frame(float originX, float originY, float radiusProgress, float opacity, boolean uniform) {
        private static final Frame NONE = new Frame(0.5f, 0.5f, 0f, 0f, false);
    }

    private boolean active;
    private long startedAt;
    private float originX;
    private float originY;

    /** 仅接受控件内的实际点击，右边和下边不计入；不可用控件由调用方跳过。 */
    public boolean trigger(double pointerX, double pointerY, int x, int y, int w, int h, long nowMs) {
        if (w <= 0 || h <= 0 || !Double.isFinite(pointerX) || !Double.isFinite(pointerY)
                || pointerX < x || pointerY < y || pointerX >= (double) x + w || pointerY >= (double) y + h) {
            return false;
        }
        originX = (float) ((pointerX - x) / w);
        originY = (float) ((pointerY - y) / h);
        startedAt = nowMs;
        active = true;
        return true;
    }

    /** 调用方使用同一毫秒时钟；时间回退时夹到起点，结束后不再分配新帧。 */
    public Frame sample(long nowMs, boolean reducedMotion) {
        if (!active) return Frame.NONE;
        double elapsed = Math.max(0d, (double) nowMs - startedAt) * ThemeEffects.local("clickRipple").speed();
        if (elapsed >= DURATION_MS) {
            clear();
            return Frame.NONE;
        }
        float progress = (float) (elapsed / DURATION_MS);
        float opacity = Math.min(1f, (float) elapsed / 40f) * (1f - progress);
        float radius = reducedMotion ? 1f : 1f - (1f - progress) * (1f - progress);
        return new Frame(originX, originY, radius, opacity, reducedMotion);
    }

    /** 页面关闭或控件被移除时调用。 */
    public void clear() {
        active = false;
    }
}
