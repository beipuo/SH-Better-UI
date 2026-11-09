package com.dishanhai.sh_better_ui.client.gui.collision;

import java.util.ArrayList;
import java.util.Comparator;

import static com.dishanhai.sh_better_ui.client.gui.collision.CollisionBody.Strategy.*;

/** 山海：共同截面上的平移链。先为整条链预留尺寸，再按优先级选择位置。 */
final class CollisionLane {
    private static final Comparator<CollisionBody> HORIZONTAL =
            (first, second) -> CollisionResolver.compareOrder(first, second, true);
    private static final Comparator<CollisionBody> VERTICAL =
            (first, second) -> CollisionResolver.compareOrder(first, second, false);

    private final ArrayList<CollisionBody> lane = new ArrayList<>();
    private double[] offsets = new double[0];
    private double[] earliest = new double[0];
    private double[] latest = new double[0];

    boolean solve(ArrayList<CollisionBody> bodies, CollisionShape viewport, boolean horizontal) {
        try {
            return solveLane(bodies, viewport, horizontal);
        } finally {
            lane.clear();
        }
    }

    private boolean solveLane(ArrayList<CollisionBody> bodies, CollisionShape viewport, boolean horizontal) {
        float crossStart = Float.NEGATIVE_INFINITY;
        float crossEnd = Float.POSITIVE_INFINITY;
        for (int i = 0; i < bodies.size(); i++) {
            var body = bodies.get(i);
            var box = body.working;
            body.laneIndex = -1;
            if (body.strategy() == OVERLAY || box.width <= CollisionShape.SOLVER_EPSILON
                    || box.height <= CollisionShape.SOLVER_EPSILON) continue;
            if (body.strategy() != PUSH && body.strategy() != AVOID && body.strategy() != RESIST) return false;
            crossStart = Math.max(crossStart, box.start(!horizontal));
            crossEnd = Math.min(crossEnd, box.end(!horizontal));
            if (crossEnd - crossStart <= CollisionShape.SOLVER_EPSILON) return false;
            lane.add(body);
        }
        lane.sort(horizontal ? HORIZONTAL : VERTICAL);
        int count = lane.size();
        if (offsets.length < count) {
            int capacity = Math.max(count, offsets.length * 2);
            offsets = new double[capacity];
            earliest = new double[capacity];
            latest = new double[capacity];
        }

        double occupied = 0;
        for (int i = 0; i < count; i++) {
            var body = lane.get(i);
            offsets[i] = occupied;
            occupied += body.working.size(horizontal);
            body.laneIndex = i;
        }
        // Subtract the preceding widths. The remaining coordinates must be nondecreasing.
        double lower = horizontal ? viewport.x() : viewport.y();
        double upper = (horizontal ? viewport.right() : viewport.bottom()) - occupied;
        for (int i = 0; i < count; i++) {
            var body = lane.get(i);
            if (body.strategy() == RESIST) lower = Math.max(lower, body.working.start(horizontal) - offsets[i]);
            earliest[i] = lower;
        }
        for (int i = count - 1; i >= 0; i--) {
            var body = lane.get(i);
            if (body.strategy() == RESIST) upper = Math.min(upper, body.working.start(horizontal) - offsets[i]);
            if (earliest[i] > upper) return false;
            latest[i] = upper;
        }

        for (int rank = 0; rank < bodies.size(); rank++) {
            var body = bodies.get(rank);
            var box = body.working;
            int index = body.laneIndex;
            if (index < 0) continue;
            double preferred = box.start(horizontal) - offsets[index];
            double position = Math.max(earliest[index], Math.min(preferred, latest[index]));
            box.setAxis(horizontal, (float) (position + offsets[index]), box.size(horizontal));
            for (int i = index - 1; i >= 0 && latest[i] > position; i--) latest[i] = position;
            for (int i = index + 1; i < count && earliest[i] < position; i++) earliest[i] = position;
        }
        return true;
    }
}
