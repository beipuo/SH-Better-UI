package com.dishanhai.sh_better_ui.client.gui.collision;

import java.util.Objects;

/** 山海：一个控件的期望矩形、布局约束和呈现状态。由所属 world 的客户端线程操作。 */
public final class CollisionBody {
    public enum Strategy { PUSH, COMPRESS, AVOID, OVERLAY, ELASTIC, RESIST }

    public enum Anchor {
        START(0), CENTER(0.5f), END(1);

        final float fraction;

        Anchor(float fraction) {
            this.fraction = fraction;
        }
    }

    final long order;
    final MutableCollisionBounds working = new MutableCollisionBounds();
    final MutableCollisionBounds solved = new MutableCollisionBounds();
    final TransitionController transition;
    private CollisionWorld owner;
    private CollisionShape desired;
    private CompressionModel compression = CompressionModel.UNCONSTRAINED;
    private Strategy strategy = Strategy.PUSH;
    private Anchor horizontalAnchor = Anchor.START;
    private Anchor verticalAnchor = Anchor.START;
    private int priority;
    private int group;
    boolean direct;
    boolean mounted;
    boolean pendingPlacement;
    boolean boundaryClamped;
    int laneIndex;

    CollisionBody(CollisionWorld owner, long order, CollisionShape desired) {
        this(owner, order, desired, true);
    }

    CollisionBody(CollisionWorld owner, long order, CollisionShape desired, boolean presentationEnabled) {
        this.owner = owner;
        this.order = order;
        this.desired = desired;
        solved.set(desired.x(), desired.y(), desired.width(), desired.height());
        transition = presentationEnabled ? new TransitionController(desired) : null;
    }

    public CollisionBody desired(CollisionShape shape) {
        checkAttached();
        Objects.requireNonNull(shape);
        if (!shape.equals(desired)) {
            desired = shape;
            owner.dirty(this);
        }
        return this;
    }

    public CollisionBody moveTo(float x, float y) {
        checkAttached();
        if (desired.x() == x && desired.y() == y) return this;
        return desired(new CollisionShape(x, y, desired.width(), desired.height()));
    }

    public CollisionBody resize(float width, float height) {
        checkAttached();
        if (desired.width() == width && desired.height() == height) return this;
        return desired(new CollisionShape(
                desired.x() + (desired.width() - width) * horizontalAnchor.fraction,
                desired.y() + (desired.height() - height) * verticalAnchor.fraction, width, height));
    }

    public CollisionBody strategy(Strategy strategy) {
        checkAttached();
        Objects.requireNonNull(strategy);
        if (this.strategy != strategy) {
            this.strategy = strategy;
            owner.dirty(this);
        }
        return this;
    }

    public CollisionBody priority(int priority) {
        checkAttached();
        if (this.priority != priority) {
            this.priority = priority;
            owner.priorityChanged(this);
        }
        return this;
    }

    public CollisionBody compression(CompressionModel compression) {
        checkAttached();
        Objects.requireNonNull(compression);
        if (!this.compression.equals(compression)) {
            this.compression = compression;
            owner.dirty(this);
        }
        return this;
    }

    public CollisionBody anchor(Anchor horizontal, Anchor vertical) {
        checkAttached();
        Objects.requireNonNull(horizontal);
        Objects.requireNonNull(vertical);
        if (horizontalAnchor != horizontal || verticalAnchor != vertical) {
            horizontalAnchor = horizontal;
            verticalAnchor = vertical;
            owner.dirty(this);
        }
        return this;
    }

    public CollisionBody group(int group) {
        checkAttached();
        if (this.group != group) {
            owner.moveGroup(this, group);
            this.group = group;
        }
        return this;
    }

    /** 拖动期间立即应用解算结果，关闭后从当前呈现位置继续动画。 */
    public CollisionBody direct(boolean direct) {
        checkAttached();
        this.direct = direct;
        return this;
    }

    public CollisionShape desired() { return desired; }
    public CollisionShape target() { return solved.snapshot(); }
    public CollisionShape visual() { return transition == null ? target() : transition.visual(); }
    public Strategy strategy() { return strategy; }
    public CompressionModel compression() { return compression; }
    public int priority() { return priority; }
    public int group() { return group; }
    public float visualX() { return transition == null ? solved.x : transition.x(); }
    public float visualY() { return transition == null ? solved.y : transition.y(); }
    public float visualRight() { return transition == null ? solved.x + solved.width : transition.right(); }
    public float visualBottom() { return transition == null ? solved.y + solved.height : transition.bottom(); }

    float anchorFraction(boolean horizontal) {
        return (horizontal ? horizontalAnchor : verticalAnchor).fraction;
    }

    boolean belongsTo(CollisionWorld world) {
        return owner == world;
    }

    boolean sharesWorldWith(CollisionBody other) {
        return owner != null && owner == other.owner;
    }

    void detach() {
        owner = null;
    }

    private void checkAttached() {
        if (owner == null) throw new IllegalStateException("Detached collision body");
    }
}
