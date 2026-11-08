package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiSwipeToast.CloseReason;

/** 山海：滑动通知的显示、退出和行内收合顺序。 */
final class SwipeToastTimeline {
    private static final long COLLAPSE_MS = 200;

    private enum Phase { OPEN, EXIT, COLLAPSE, GONE }

    private final PausableCountdown fuse;
    private final long slideMs;
    private final boolean inline;
    private long durationMs;
    private long phaseAt;
    private Phase phase = Phase.OPEN;
    private CloseReason reason;
    private float fromReveal;
    private float fromSlot;
    private float reveal;
    private float slot;
    private boolean waitingForLayout = true;

    SwipeToastTimeline(long durationMs, long slideMs, boolean inline, long now) {
        this.durationMs = durationMs;
        this.slideMs = slideMs;
        this.inline = inline;
        phaseAt = now;
        fuse = new PausableCountdown(durationMs, now);
        fuse.update(now, true);
    }

    void setDuration(long durationMs, long now) {
        fuse.reset(durationMs, now);
        this.durationMs = durationMs;
    }

    void open(long now) {
        if (isOpen()) return;
        phase = Phase.OPEN;
        phaseAt = now;
        fromReveal = reveal;
        fromSlot = slot;
        reason = null;
        fuse.reset(durationMs, now);
    }

    void layoutReady(long now) {
        if (!waitingForLayout) return;
        waitingForLayout = false;
        if (isOpen()) phaseAt = now;
    }

    boolean close(CloseReason why, long now) {
        if (!isOpen()) return false;
        phase = Phase.EXIT;
        phaseAt = now;
        fromReveal = reveal;
        reason = why;
        return true;
    }

    /** 返回且只返回一次已完成的关闭；行内先完成收合。 */
    CloseReason advance(long now, boolean paused, boolean reducedMotion) {
        if (phase == Phase.OPEN) {
            if (waitingForLayout) return null;
            float progress = ease(progress(now, phaseAt, reducedMotion ? 0 : slideMs));
            reveal = GuiMotion.lerp(fromReveal, 1f, progress);
            slot = GuiMotion.lerp(fromSlot, 1f, progress);
            fuse.update(now, paused);
            if (!fuse.expired()) return null;
            close(CloseReason.TIMEOUT, now);
        }
        if (phase == Phase.EXIT) {
            long exitMs = reducedMotion ? 0 : Math.round(slideMs * 0.7);
            reveal = fromReveal * (1f - ease(progress(now, phaseAt, exitMs)));
            if (reveal > 0f) return null;
            if (!inline) return finish();
            phase = Phase.COLLAPSE;
            phaseAt += exitMs;
            fromSlot = slot;
        }
        if (phase == Phase.COLLAPSE) {
            slot = fromSlot * (1f - ease(progress(now, phaseAt, reducedMotion || slideMs == 0 ? 0 : COLLAPSE_MS)));
            if (slot <= 0f) return finish();
        }
        return null;
    }

    private CloseReason finish() {
        phase = Phase.GONE;
        slot = 0f;
        var finished = reason;
        reason = null;
        return finished;
    }

    private static float progress(long now, long start, long duration) {
        return duration == 0 ? 1f : GuiMotion.clamp01((now - start) / (float) duration);
    }

    private static float ease(float p) {
        float remaining = 1f - p;
        return 1f - remaining * remaining * remaining;
    }

    boolean isOpen() { return phase == Phase.OPEN; }
    float reveal() { return reveal; }
    float slot() { return slot; }
    float fuse() { return fuse.remainingFraction(); }
}
