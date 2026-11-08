package com.dishanhai.sh_better_ui.client.gui.scaled;

/** 山海：用调用方提供的单调毫秒时间推进倒计时。可独立用于通知、进度条等控件。 */
public final class PausableCountdown {
    private long durationMs;
    private long remainingMs;
    private long lastUpdateMs;
    private boolean paused;

    /** 时长为零时持续显示，无倒计时进度。 */
    public PausableCountdown(long durationMs, long nowMs) {
        reset(durationMs, nowMs);
    }

    /** 从指定时间重新计时，保留当前暂停状态。 */
    public void reset(long durationMs, long nowMs) {
        if (durationMs < 0) throw new IllegalArgumentException("durationMs");
        this.durationMs = durationMs;
        remainingMs = durationMs;
        lastUpdateMs = nowMs;
    }

    /**
     * 先按上次的暂停状态结算到 nowMs，再应用新的暂停状态。
     * 有多个暂停条件时，传入它们的逻辑或。时间倒退时在上次采样时刻应用新状态。
     */
    public void update(long nowMs, boolean paused) {
        if (nowMs > lastUpdateMs) {
            if (!this.paused && remainingMs > 0) {
                long elapsed = nowMs - lastUpdateMs;
                remainingMs = elapsed < 0 || elapsed >= remainingMs ? 0 : remainingMs - elapsed;
            }
            lastUpdateMs = nowMs;
        }
        this.paused = paused;
    }

    /** 返回剩余时长的比例，范围为 0 到 1。持续显示时返回 0。 */
    public float remainingFraction() {
        return durationMs == 0 ? 0f : remainingMs / (float) durationMs;
    }

    /** 只有正时长的倒计时才会到期。 */
    public boolean expired() {
        return durationMs > 0 && remainingMs == 0;
    }
}
