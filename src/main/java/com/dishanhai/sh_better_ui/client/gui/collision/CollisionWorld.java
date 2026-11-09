package com.dishanhai.sh_better_ui.client.gui.collision;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.Consumer;

/** 山海：单线程 UI 碰撞世界。收集意图后用同一帧时钟更新，回调中的改动在下一次更新生效。 */
public final class CollisionWorld {
    public enum Axis { AUTO, HORIZONTAL, VERTICAL }

    private final LayoutSolver solver;
    private final HashMap<Integer, Group> groupsById = new HashMap<>();
    private final ArrayList<Group> groups = new ArrayList<>();
    private final ArrayList<CollisionEvent> events = new ArrayList<>();
    private CollisionShape viewport;
    private Axis axis = Axis.AUTO;
    private Consumer<? super CollisionEvent> listener;
    private long nextOrder;
    private long frameTime = Long.MIN_VALUE;
    private boolean updating;

    public CollisionWorld(CollisionShape viewport) {
        this(viewport, new LayoutSolver());
    }

    public CollisionWorld(CollisionShape viewport, LayoutSolver solver) {
        this.viewport = Objects.requireNonNull(viewport);
        this.solver = Objects.requireNonNull(solver);
    }

    public CollisionWorld viewport(CollisionShape viewport) {
        Objects.requireNonNull(viewport);
        if (!this.viewport.equals(viewport)) {
            this.viewport = viewport;
            dirtyAll();
        }
        return this;
    }

    public CollisionShape viewport() { return viewport; }

    public CollisionWorld axis(Axis axis) {
        Objects.requireNonNull(axis);
        if (this.axis != axis) {
            this.axis = axis;
            dirtyAll();
        }
        return this;
    }

    /** 安装一个观察者。传 null 取消观察。回调发生在本帧所有呈现几何更新之后。 */
    public CollisionWorld onCollision(Consumer<? super CollisionEvent> listener) {
        this.listener = listener;
        return this;
    }

    public CollisionBody add(CollisionShape desired) {
        var body = new CollisionBody(this, nextOrder++, Objects.requireNonNull(desired));
        addToGroup(body, 0);
        return body;
    }

    public boolean contains(CollisionBody body) {
        return body != null && body.belongsTo(this);
    }

    public boolean remove(CollisionBody body) {
        if (!contains(body)) return false;
        removeFromGroup(body);
        body.detach();
        return true;
    }

    /** 清空控件与观察者。已持有的 body 仍可读取最后的几何。 */
    public void clear() {
        for (int index = 0; index < groups.size(); index++) {
            var bodies = groups.get(index).bodies;
            for (int i = 0; i < bodies.size(); i++) bodies.get(i).detach();
        }
        groupsById.clear();
        groups.clear();
        listener = null;
        frameTime = Long.MIN_VALUE;
    }

    /** 求解有变化的组并推进呈现状态，返回当前未解决约束数。时间使用单调毫秒。 */
    public int update(long nowMs, boolean reducedMotion) {
        if (updating) throw new IllegalStateException("Recursive collision update");
        updating = true;
        try {
            frameTime = Math.max(frameTime, nowMs);
            int unresolved = 0;
            var observer = listener;
            for (int index = 0; index < groups.size(); index++) {
                var group = groups.get(index);
                boolean solve = group.dirty || group.pendingMembers > 0;
                if (solve) {
                    if (group.orderDirty) LayoutSolver.order(group.bodies);
                    group.unresolved = solver.solve(group.bodies, viewport, axis, observer == null ? null : events);
                    group.dirty = false;
                    group.orderDirty = false;
                    group.pendingMembers = 0;
                }
                unresolved += group.unresolved;
                for (int i = 0; i < group.bodies.size(); i++) {
                    present(group.bodies.get(i), solve, reducedMotion);
                }
            }
            if (observer != null) {
                for (int i = 0; i < events.size(); i++) observer.accept(events.get(i));
            }
            return unresolved;
        } finally {
            events.clear();
            updating = false;
        }
    }

    private void present(CollisionBody body, boolean solved, boolean reducedMotion) {
        var target = body.solved;
        if (solved) {
            var work = body.working;
            target.set(work.x, work.y, work.width, work.height);
            body.pendingPlacement = false;
        }
        if (!body.mounted || body.direct || reducedMotion) {
            body.transition.snap(target.x, target.y, target.width, target.height);
        } else {
            if (solved) body.transition.retarget(target.x, target.y, target.width, target.height, frameTime);
            body.transition.update(frameTime, viewport, body.compression(),
                    body.anchorFraction(true), body.anchorFraction(false));
        }
        body.mounted = true;
    }

    void dirty(CollisionBody body) {
        if (!body.pendingPlacement) groupsById.get(body.group()).dirty = true;
    }

    void priorityChanged(CollisionBody body) {
        var group = groupsById.get(body.group());
        group.orderDirty = true;
        if (!body.pendingPlacement) group.dirty = true;
    }

    void moveGroup(CollisionBody body, int destination) {
        removeFromGroup(body);
        addToGroup(body, destination);
    }

    private void addToGroup(CollisionBody body, int id) {
        var group = groupsById.get(id);
        if (group == null) {
            group = new Group();
            groupsById.put(id, group);
            groups.add(group);
        }
        group.bodies.add(body);
        group.pendingMembers++;
        group.orderDirty = true;
        body.pendingPlacement = true;
    }

    private void removeFromGroup(CollisionBody body) {
        var group = groupsById.get(body.group());
        group.bodies.remove(body);
        if (body.pendingPlacement) group.pendingMembers--;
        else group.dirty = true;
        if (group.bodies.isEmpty()) {
            groupsById.remove(body.group());
            groups.remove(group);
        }
    }

    private void dirtyAll() {
        for (int index = 0; index < groups.size(); index++) groups.get(index).dirty = true;
    }

    private static final class Group {
        private final ArrayList<CollisionBody> bodies = new ArrayList<>();
        private boolean dirty;
        private boolean orderDirty;
        private int pendingMembers;
        private int unresolved;
    }
}
