package com.dishanhai.sh_better_ui.client.gui.collision;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;

import static com.dishanhai.sh_better_ui.client.gui.collision.MutableCollisionBounds.clamp;

/** 山海：脏组的有界迭代和按优先级摆放的降级。每个 body 复用工作矩形。 */
public final class LayoutSolver {
    private static final int PRIORITY_PASSES = 12;
    private static final Comparator<CollisionBody> PLACEMENT_ORDER =
            Comparator.comparingInt((CollisionBody body) -> body.priority()).reversed()
                    .thenComparingLong(body -> body.order);
    private final BitSet contacts = new BitSet();
    private final CollisionLane lane = new CollisionLane();
    private final int maxPasses;

    public LayoutSolver() {
        this(64);
    }

    /** 设置一次组求解的投影轮数，范围为 1 到 256。 */
    public LayoutSolver(int maxPasses) {
        if (maxPasses < 1 || maxPasses > 256) throw new IllegalArgumentException("Invalid collision pass budget");
        this.maxPasses = maxPasses;
    }

    static void order(ArrayList<CollisionBody> bodies) {
        bodies.sort(PLACEMENT_ORDER);
    }

    int solve(ArrayList<CollisionBody> bodies, CollisionShape viewport, CollisionWorld.Axis axis,
              ArrayList<CollisionEvent> events) {
        reset(bodies, viewport);
        contacts.clear();

        boolean observe = events != null;
        int priorityPasses = Math.min(PRIORITY_PASSES, maxPasses);
        projectPasses(bodies, viewport, axis, observe, priorityPasses, false);
        if (hasOverlaps(bodies)) {
            reset(bodies, viewport);
            boolean packed = axis != CollisionWorld.Axis.VERTICAL && lane.solve(bodies, viewport, true)
                    || axis != CollisionWorld.Axis.HORIZONTAL && lane.solve(bodies, viewport, false);
            if (!packed) projectPasses(bodies, viewport, axis, observe, maxPasses - priorityPasses, true);
            if (hasOverlaps(bodies)) {
                reset(bodies, viewport);
                placeInPriorityOrder(bodies, viewport, axis, observe);
            }
        }

        int unresolved = 0;
        for (int second = 0; second < bodies.size(); second++) {
            var body = bodies.get(second);
            if (body.boundaryClamped) emit(events, CollisionEvent.Type.BOUNDARY_CLAMPED, body, null);
            if (body.compression().minWidth() > viewport.width() || body.compression().minHeight() > viewport.height()) {
                unresolved++;
                emit(events, CollisionEvent.Type.MINIMUM_CLIPPED, body, null);
            }
            for (int first = 0; first < second; first++) {
                var obstacle = bodies.get(first);
                if (events != null && contacts.get(pairIndex(first, second))) {
                    emit(events, CollisionEvent.Type.CONTACT, obstacle, body);
                }
                if (CollisionResolver.overlaps(obstacle, body)) {
                    unresolved++;
                    emit(events, CollisionEvent.Type.UNRESOLVED, obstacle, body);
                }
            }
        }
        return unresolved;
    }

    private void projectPasses(ArrayList<CollisionBody> bodies, CollisionShape viewport, CollisionWorld.Axis axis,
                               boolean observe, int count, boolean relax) {
        for (int pass = 0; pass < count; pass++) {
            if (!sweep(bodies, viewport, axis, observe, pass % 2 != 0, relax)) return;
        }
    }

    private boolean sweep(ArrayList<CollisionBody> bodies, CollisionShape viewport, CollisionWorld.Axis axis,
                          boolean observe, boolean reverse, boolean relax) {
        boolean moved = false;
        int count = bodies.size();
        for (int outer = 0; outer < count; outer++) {
            int second = reverse ? count - 1 - outer : outer;
            for (int inner = 0; inner < second; inner++) {
                int first = reverse ? second - 1 - inner : inner;
                moved |= project(bodies, viewport, axis, observe, first, second, false, relax);
            }
        }
        return moved;
    }

    private void placeInPriorityOrder(ArrayList<CollisionBody> bodies, CollisionShape viewport,
                                      CollisionWorld.Axis axis, boolean observe) {
        for (int second = 1; second < bodies.size(); second++) {
            for (int pass = 0; pass < Math.min(second + 1, maxPasses); pass++) {
                boolean moved = false;
                for (int first = 0; first < second; first++) {
                    moved |= project(bodies, viewport, axis, observe, first, second, true, false);
                }
                if (!moved) break;
            }
        }
    }

    private boolean project(ArrayList<CollisionBody> bodies, CollisionShape viewport, CollisionWorld.Axis axis,
                            boolean observe, int first, int second, boolean fixedFirst, boolean relax) {
        var a = bodies.get(first);
        var b = bodies.get(second);
        if (!CollisionResolver.overlaps(a, b)) return false;
        if (observe) contacts.set(pairIndex(first, second));
        return CollisionResolver.separate(a, b, viewport, axis, fixedFirst, relax);
    }

    private static boolean hasOverlaps(ArrayList<CollisionBody> bodies) {
        for (int second = 1; second < bodies.size(); second++) {
            for (int first = 0; first < second; first++) {
                if (CollisionResolver.overlaps(bodies.get(first), bodies.get(second))) return true;
            }
        }
        return false;
    }

    private static int pairIndex(int first, int second) {
        return Math.toIntExact((long) second * (second - 1) / 2 + first);
    }

    private static void emit(ArrayList<CollisionEvent> events, CollisionEvent.Type type,
                             CollisionBody first, CollisionBody second) {
        if (events != null) events.add(new CollisionEvent(type, first, second));
    }

    private static void reset(ArrayList<CollisionBody> bodies, CollisionShape viewport) {
        for (int index = 0; index < bodies.size(); index++) {
            var body = bodies.get(index);
            var shape = body.desired();
            var model = body.compression();
            float legalWidth = clamp(shape.width(), model.minWidth(), model.maxWidth());
            float legalHeight = clamp(shape.height(), model.minHeight(), model.maxHeight());
            float width = Math.min(viewport.width(), legalWidth);
            float height = Math.min(viewport.height(), legalHeight);
            body.working.set(shape.x() + (shape.width() - width) * body.anchorFraction(true),
                    shape.y() + (shape.height() - height) * body.anchorFraction(false), width, height);
            body.boundaryClamped = body.working.fit(viewport) || width != legalWidth || height != legalHeight;
        }
    }
}
