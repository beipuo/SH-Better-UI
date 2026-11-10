// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.intelligence;

import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.SpringProjection;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Kind;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Shape;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Transform;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

import static com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.*;

/** Pure prediction over declared trajectories. A radial envelope explicitly bounds rotation error. */
final class SpatialMotion {
    private SpatialMotion() {}

    static Prediction predict(UiId id, Trajectory trajectory, double horizon, int samples, long budgetNanos) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(trajectory);
        if (!Double.isFinite(horizon) || horizon <= 0 || horizon > 60 || samples < 2 || samples > 1024 || budgetNanos < 1) {
            throw new IllegalArgumentException("Invalid prediction horizon or budget");
        }
        long start = System.nanoTime();
        double end = Math.min(horizon, last(trajectory).seconds());
        Rect swept = bounds(trajectory.keyframes().get(0).state());
        boolean conservative = trajectory.sampledModel();
        double error = 0;
        for (int i = 1; i < trajectory.keyframes().size(); i++) {
            var first = trajectory.keyframes().get(i - 1);
            var second = trajectory.keyframes().get(i);
            if (first.seconds() >= end) break;
            var to = sample(trajectory, Math.min(end, second.seconds()));
            var envelope = envelope(first.state(), to);
            swept = swept.union(envelope.bounds);
            conservative |= envelope.conservative;
            error = Math.max(error, envelope.error);
        }
        // The geometry pass is bounded by the declared 1024-keyframe cap. The sampling budget
        // controls inspector detail and cannot discard the conservative envelope of skipped frames.
        var times = new TreeSet<Double>();
        times.add(0d);
        times.add(end);
        boolean budgetExceeded = System.nanoTime() - start >= budgetNanos;
        for (int i = 1; i < samples - 1 && !budgetExceeded; i++) {
            times.add(end * i / (samples - 1));
            budgetExceeded = System.nanoTime() - start >= budgetNanos;
        }
        var path = new ArrayList<Keyframe>(times.size());
        for (double time : times) path.add(new Keyframe(time, sample(trajectory, time)));
        return new Prediction(id, path, swept, conservative, trajectory.sampledModel() ? -1 : error,
                trajectory.condition(), budgetExceeded, trajectory);
    }

    static CollisionPrediction collisions(Prediction first, Prediction second) {
        Objects.requireNonNull(first);
        Objects.requireNonNull(second);
        double end = Math.min(first.path().get(first.path().size() - 1).seconds(),
                second.path().get(second.path().size() - 1).seconds());
        var times = new TreeSet<Double>();
        times.add(0d);
        times.add(end);
        for (var key : first.trajectory().keyframes()) if (key.seconds() > 0 && key.seconds() < end) times.add(key.seconds());
        for (var key : second.trajectory().keyframes()) if (key.seconds() > 0 && key.seconds() < end) times.add(key.seconds());
        var knots = new ArrayList<>(times);
        var intervals = new ArrayList<Interval>();
        boolean sampledModel = first.trajectory().sampledModel() || second.trajectory().sampledModel();
        boolean conservative = sampledModel;
        double timeError = 0;
        if (first.sweptBounds().intersects(second.sweptBounds())) {
            for (int i = 1; i < knots.size(); i++) {
                double from = knots.get(i - 1), to = knots.get(i);
                var a0 = sample(first.trajectory(), from);
                var a1 = sample(first.trajectory(), to);
                var b0 = sample(second.trajectory(), from);
                var b1 = sample(second.trajectory(), to);
                var ae = envelope(a0, a1);
                var be = envelope(b0, b1);
                if (!ae.bounds.intersects(be.bounds)) continue;
                if (translation(a0, a1) && translation(b0, b1) && axisAlignedBox(a0) && axisAlignedBox(b0)) {
                    var interval = exactInterval(from, to, bounds(a0), bounds(a1), bounds(b0), bounds(b1));
                    if (interval != null) append(intervals, interval);
                } else {
                    append(intervals, new Interval(from, to, ae.bounds.intersection(be.bounds)));
                    conservative = true;
                    timeError = Math.max(timeError, to - from);
                }
            }
        }
        return new CollisionPrediction(first.id(), second.id(), intervals, conservative, sampledModel ? -1 : timeError,
                first.condition() + "; " + second.condition());
    }

    private static void append(List<Interval> intervals, Interval next) {
        if (!intervals.isEmpty()) {
            var previous = intervals.get(intervals.size() - 1);
            if (next.fromSeconds() <= previous.toSeconds() + 1e-12) {
                intervals.set(intervals.size() - 1, new Interval(previous.fromSeconds(),
                        Math.max(previous.toSeconds(), next.toSeconds()), previous.overlap().union(next.overlap())));
                return;
            }
        }
        intervals.add(next);
    }

    /** Continuous slab test for two translating axis-aligned boxes; no temporal sample can tunnel through. */
    private static Interval exactInterval(double from, double to, Rect a0, Rect a1, Rect b0, Rect b1) {
        double[] interval = {0, 1};
        if (!axisInterval(a0.x(), a0.right(), b0.x(), b0.right(),
                (a1.x() - a0.x()) - (b1.x() - b0.x()), interval)
                || !axisInterval(a0.y(), a0.bottom(), b0.y(), b0.bottom(),
                (a1.y() - a0.y()) - (b1.y() - b0.y()), interval)
                || interval[0] >= interval[1]) return null;
        double middle = (interval[0] + interval[1]) / 2;
        Rect a = translated(a0, (a1.x() - a0.x()) * middle, (a1.y() - a0.y()) * middle);
        Rect b = translated(b0, (b1.x() - b0.x()) * middle, (b1.y() - b0.y()) * middle);
        return new Interval(lerp(from, to, interval[0]), lerp(from, to, interval[1]), a.intersection(b));
    }

    private static boolean axisInterval(double amin, double amax, double bmin, double bmax,
                                         double delta, double[] interval) {
        if (delta == 0) return amin < bmax && bmin < amax;
        double enter = (bmin - amax) / delta, exit = (bmax - amin) / delta;
        if (enter > exit) { double swap = enter; enter = exit; exit = swap; }
        interval[0] = Math.max(interval[0], enter);
        interval[1] = Math.min(interval[1], exit);
        return interval[0] < interval[1];
    }

    private static Rect translated(Rect rect, double x, double y) {
        return new Rect(rect.x() + x, rect.y() + y, rect.width(), rect.height(), rect.space());
    }

    private record Envelope(Rect bounds, boolean conservative, double error) {}

    private static Envelope envelope(MotionState a, MotionState b) {
        if (translation(a, b)) {
            return new Envelope(bounds(a).union(bounds(b)), !axisAlignedBox(a),
                    axisAlignedBox(a) ? 0 : Math.hypot(a.shape().width(), a.shape().height())
                            * Math.max(Math.abs(a.transform().scaleX()), Math.abs(a.transform().scaleY())));
        }
        // Every transformed point is within this radius of (translation + transform origin).
        // Dimensions, origin and scale interpolate linearly, so endpoint maxima bound each term.
        double radius = Math.max(localRadius(a), localRadius(b))
                * Math.max(maxScale(a), maxScale(b));
        double ax = a.transform().x() + a.transform().originX();
        double ay = a.transform().y() + a.transform().originY();
        double bx = b.transform().x() + b.transform().originX();
        double by = b.transform().y() + b.transform().originY();
        var box = new Rect(Math.min(ax, bx) - radius, Math.min(ay, by) - radius,
                Math.abs(bx - ax) + 2 * radius, Math.abs(by - ay) + 2 * radius, Space.GUI);
        return new Envelope(box, true, 2 * radius);
    }

    private static double maxScale(MotionState state) {
        return Math.max(Math.abs(state.transform().scaleX()), Math.abs(state.transform().scaleY()));
    }

    private static double localRadius(MotionState state) {
        var t = state.transform();
        double x = Math.max(Math.abs(t.originX()), Math.abs(state.shape().width() - t.originX()));
        double y = Math.max(Math.abs(t.originY()), Math.abs(state.shape().height() - t.originY()));
        return Math.hypot(x, y);
    }

    private static Rect bounds(MotionState state) {
        var matrix = state.transform().matrix();
        double left = Double.POSITIVE_INFINITY, top = Double.POSITIVE_INFINITY;
        double right = Double.NEGATIVE_INFINITY, bottom = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            var point = matrix.point((i & 1) == 0 ? 0 : state.shape().width(), (i & 2) == 0 ? 0 : state.shape().height());
            left = Math.min(left, point.x()); top = Math.min(top, point.y());
            right = Math.max(right, point.x()); bottom = Math.max(bottom, point.y());
        }
        return new Rect(left, top, right - left, bottom - top, Space.GUI);
    }

    private static boolean axisAlignedBox(MotionState state) {
        var shape = state.shape();
        return shape.kind() == Kind.BOX && shape.topLeft() == 0 && shape.topRight() == 0
                && shape.bottomLeft() == 0 && shape.bottomRight() == 0
                && state.transform().radians() == 0;
    }

    private static boolean translation(MotionState a, MotionState b) {
        var x = a.transform(); var y = b.transform();
        return a.shape().equals(b.shape()) && x.scaleX() == y.scaleX() && x.scaleY() == y.scaleY()
                && x.radians() == y.radians() && x.originX() == y.originX() && x.originY() == y.originY();
    }

    private static MotionState sample(Trajectory trajectory, double seconds) {
        var keys = trajectory.keyframes();
        if (seconds <= 0) return keys.get(0).state();
        if (seconds >= last(trajectory).seconds()) return last(trajectory).state();
        int low = 1, high = keys.size() - 1;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (keys.get(middle).seconds() < seconds) low = middle + 1; else high = middle;
        }
        var left = keys.get(low - 1); var right = keys.get(low);
        return interpolate(left.state(), right.state(), (seconds - left.seconds()) / (right.seconds() - left.seconds()));
    }

    private static Keyframe last(Trajectory trajectory) {
        return trajectory.keyframes().get(trajectory.keyframes().size() - 1);
    }

    private static MotionState interpolate(MotionState a, MotionState b, double progress) {
        var x = a.transform(); var y = b.transform(); var s = a.shape(); var t = b.shape();
        if (s.kind() != t.kind() || Math.signum(x.scaleX()) != Math.signum(y.scaleX())
                || Math.signum(x.scaleY()) != Math.signum(y.scaleY())) {
            throw new IllegalArgumentException("Trajectory cannot interpolate shape kind or cross singular scale");
        }
        var shape = new Shape(s.kind(), lerp(s.width(), t.width(), progress), lerp(s.height(), t.height(), progress),
                lerp(s.topLeft(), t.topLeft(), progress), lerp(s.topRight(), t.topRight(), progress),
                lerp(s.bottomRight(), t.bottomRight(), progress), lerp(s.bottomLeft(), t.bottomLeft(), progress));
        var transform = new Transform(lerp(x.x(), y.x(), progress), lerp(x.y(), y.y(), progress),
                lerp(x.scaleX(), y.scaleX(), progress), lerp(x.scaleY(), y.scaleY(), progress),
                lerp(x.radians(), y.radians(), progress), lerp(x.originX(), y.originX(), progress), lerp(x.originY(), y.originY(), progress));
        return new MotionState(shape, transform);
    }

    private static double lerp(double a, double b, double progress) { return a + (b - a) * progress; }

    static Trajectory spring(MotionState actual, MotionState target, Velocity velocity, double response,
                              double damping, double duration, int samples) {
        Objects.requireNonNull(actual); Objects.requireNonNull(target); Objects.requireNonNull(velocity);
        if (!Double.isFinite(response) || response < .08 || response > 2 || !Double.isFinite(damping)
                || damping < .3 || damping > 2 || !Double.isFinite(duration) || duration <= 0 || duration > 10
                || samples < 2 || samples > 1024 || actual.shape().kind() != target.shape().kind()) {
            throw new IllegalArgumentException("Invalid spring prediction parameters");
        }
        float[] from = channels(actual), to = channels(target);
        float[] speeds = {(float) velocity.x(), (float) velocity.y(), (float) velocity.scaleX(),
                (float) velocity.scaleY(), (float) velocity.radians(), 0, 0, (float) velocity.width(),
                (float) velocity.height(), 0, 0, 0, 0};
        float[][] projected = SpringProjection.project(from, to, speeds,
                new Spring.Preset(response, damping, .00001f, .00001f), duration, samples);
        var keys = new ArrayList<Keyframe>(samples);
        keys.add(new Keyframe(0, actual));
        for (int i = 1; i < samples; i++) {
            var p = projected[i];
            var shape = new Shape(actual.shape().kind(), p[7], p[8], Math.max(0, p[9]),
                    Math.max(0, p[10]), Math.max(0, p[11]), Math.max(0, p[12]));
            keys.add(new Keyframe(duration * i / (samples - 1), new MotionState(shape,
                    new Transform(p[0], p[1], p[2], p[3], p[4], p[5], p[6]))));
        }
        return new Trajectory(keys, "spring projection with existing capped integrator; sampled piecewise-linear path; no future input", true);
    }

    private static float[] channels(MotionState state) {
        var t = state.transform(); var s = state.shape();
        return new float[] {(float) t.x(), (float) t.y(), (float) t.scaleX(), (float) t.scaleY(), (float) t.radians(),
                (float) t.originX(), (float) t.originY(), (float) s.width(), (float) s.height(),
                (float) s.topLeft(), (float) s.topRight(), (float) s.bottomRight(), (float) s.bottomLeft()};
    }
}
