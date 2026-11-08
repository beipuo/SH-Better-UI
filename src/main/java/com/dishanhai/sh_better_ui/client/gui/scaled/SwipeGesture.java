package com.dishanhai.sh_better_ui.client.gui.scaled;

/** 山海：采样一段滑动的位移和速度。坐标用 GUI 单位，时间用调用方的单调毫秒时钟。 */
public final class SwipeGesture {
    private static final long VELOCITY_WINDOW_MS = 100L;
    private static final Sample STILL = new Sample(0, 0, 0, 0, false);

    private final PointerDrag pointer = new PointerDrag();
    private final double deadZone;
    private long lastEventMs;
    private long lastMotionMs;
    private double velocityX;
    private double velocityY;
    private boolean moved;

    public SwipeGesture() {
        this(3);
    }

    public SwipeGesture(double deadZone) {
        if (!Double.isFinite(deadZone) || deadZone < 0) throw new IllegalArgumentException("deadZone");
        this.deadZone = deadZone;
    }

    /** 按下已命中的区域后开始采样。同一时刻可以记录多次输入。 */
    public boolean begin(int button, double x, double y, long nowMs) {
        if (!pointer.begin(button, x, y)) return false;
        lastEventMs = lastMotionMs = nowMs;
        velocityX = velocityY = 0;
        moved = false;
        return true;
    }

    public boolean move(int button, double x, double y, long nowMs) {
        return nowMs >= lastEventMs && record(pointer.move(button, x, y), nowMs);
    }

    /** 记录放开位置并结束采样。若事件不匹配、过时或坐标无效，返回 null。 */
    public Sample release(int button, double x, double y, long nowMs) {
        if (nowMs < lastEventMs) return null;
        return record(pointer.release(button, x, y), nowMs) ? current(nowMs) : null;
    }

    public void cancel() {
        pointer.cancel();
    }

    public boolean active() {
        return pointer.active();
    }

    /** 在死区内或手势结束后返回零。放开时的最终采样通过 release() 获取。 */
    public Sample sample(long nowMs) {
        return active() ? current(nowMs) : STILL;
    }

    private boolean record(PointerDrag.Delta delta, long nowMs) {
        if (delta == null) return false;
        double x = pointer.offsetX();
        double y = pointer.offsetY();
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            cancel();
            return false;
        }
        if (delta.x() != 0 || delta.y() != 0) {
            long elapsed = nowMs - lastEventMs;
            double rate = elapsed >= 0 ? 1_000.0 / Math.max(1L, elapsed) : 0;
            velocityX = rate == 0 ? 0 : delta.x() * rate;
            velocityY = rate == 0 ? 0 : delta.y() * rate;
            if (!Double.isFinite(velocityX) || !Double.isFinite(velocityY)) {
                cancel();
                return false;
            }
            lastMotionMs = nowMs;
            moved = moved || Math.hypot(x, y) >= deadZone;
        }
        lastEventMs = nowMs;
        return true;
    }

    private Sample current(long nowMs) {
        if (!moved) return STILL;
        long idle = nowMs - lastMotionMs;
        boolean recent = nowMs >= lastMotionMs && idle >= 0 && idle < VELOCITY_WINDOW_MS;
        return new Sample(pointer.offsetX(), pointer.offsetY(), recent ? velocityX : 0, recent ? velocityY : 0, true);
    }

    /** 位移是相对按下点的 GUI 单位，速度是每秒 GUI 单位。 */
    public record Sample(double offsetX, double offsetY, double velocityX, double velocityY, boolean moved) {
        /** 距离为 GUI 单位，甩动阈值为每秒 GUI 单位，均须为正数。 */
        public boolean dismissesDown(double distance, double flickVelocity) {
            if (!Double.isFinite(distance) || distance <= 0) throw new IllegalArgumentException("distance");
            if (!Double.isFinite(flickVelocity) || flickVelocity <= 0) {
                throw new IllegalArgumentException("flickVelocity");
            }
            return moved && offsetY > 0 && offsetY >= Math.abs(offsetX) && velocityY >= 0
                    && (offsetY >= distance || (velocityY >= flickVelocity && velocityY >= Math.abs(velocityX)));
        }
    }
}
