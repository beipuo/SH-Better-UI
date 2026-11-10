package com.dishanhai.sh_better_ui.client.gui.collision;

import java.util.Objects;

import static com.dishanhai.sh_better_ui.client.gui.collision.MutableCollisionBounds.clamp;

/** 山海：独立推进矩形的四个弹簧通道并投影呈现边界。 */
public final class TransitionController {
    private final SpringMotion x;
    private final SpringMotion y;
    private final SpringMotion width;
    private final SpringMotion height;
    private final MutableCollisionBounds visual = new MutableCollisionBounds();

    public TransitionController(CollisionShape initial) {
        Objects.requireNonNull(initial);
        x = new SpringMotion(initial.x());
        y = new SpringMotion(initial.y());
        width = new SpringMotion(initial.width());
        height = new SpringMotion(initial.height());
        visual.set(initial.x(), initial.y(), initial.width(), initial.height());
    }

    public void retarget(CollisionShape target, long nowMs) {
        retarget(target.x(), target.y(), target.width(), target.height(), nowMs);
    }

    void retarget(float left, float top, float w, float h, long nowMs) {
        x.retarget(left, nowMs);
        y.retarget(top, nowMs);
        width.retarget(w, nowMs);
        height.retarget(h, nowMs);
    }

    public void snap(CollisionShape target) {
        snap(target.x(), target.y(), target.width(), target.height());
    }

    void snap(float left, float top, float w, float h) {
        x.snap(left);
        y.snap(top);
        width.snap(w);
        height.snap(h);
        visual.set(left, top, w, h);
    }

    public void update(long nowMs, CollisionShape viewport) {
        update(nowMs, viewport, CompressionModel.UNCONSTRAINED, 0, 0);
    }

    void update(long nowMs, CollisionShape viewport, CompressionModel limits, float anchorX, float anchorY) {
        x.update(nowMs);
        y.update(nowMs);
        width.update(nowMs);
        height.update(nowMs);
        float w = Math.min(viewport.width(), clamp(width.value(), limits.minWidth(), limits.maxWidth()));
        float h = Math.min(viewport.height(), clamp(height.value(), limits.minHeight(), limits.maxHeight()));
        visual.set(x.value() + (width.value() - w) * anchorX, y.value() + (height.value() - h) * anchorY, w, h);
        visual.fit(viewport);
    }

    public CollisionShape visual() { return visual.snapshot(); }
    public float x() { return visual.x; }
    public float y() { return visual.y; }
    public float right() { return visual.x + visual.width; }
    public float bottom() { return visual.y + visual.height; }

    boolean settled() { return x.settled() && y.settled() && width.settled() && height.settled(); }
}
