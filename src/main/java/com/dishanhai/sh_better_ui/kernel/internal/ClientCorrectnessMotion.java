// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.UIElement;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Shape;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Transform;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.*;

/** Four existing live tracks, with bounded conditional route planning over registered geometry. */
final class ClientCorrectnessMotion implements CorrectnessRuntime.AvoidingMotionAdapter {
    private static final Spring.Preset SPRING = Spring.FLUID_MORPH;
    private static final int ROUTE_CANDIDATES = 12;
    private static final int OBSTACLE_LIMIT = 512;
    private static final double CLEARANCE = 2;
    private final Map<Key, Track> tracks = new HashMap<>();
    private final Map<Key, Environment> environments = new HashMap<>();
    private final long routeBudgetNanos;

    ClientCorrectnessMotion() { this(2_000_000); }

    ClientCorrectnessMotion(long routeBudgetNanos) {
        if (routeBudgetNanos < 1 || routeBudgetNanos > 100_000_000) throw new IllegalArgumentException("Invalid motion route time budget");
        this.routeBudgetNanos = routeBudgetNanos;
    }

    @Override public void environment(long generation, UiId id, Rect viewport, List<Rect> obstacles) {
        if (viewport.space() != Space.GUI || obstacles.size() > OBSTACLE_LIMIT
                || obstacles.stream().anyMatch(r -> r.space() != Space.GUI)) {
            throw new IllegalArgumentException("Invalid registered motion environment");
        }
        environments.put(new Key(generation, id), new Environment(viewport, List.copyOf(obstacles)));
    }

    @Override public CorrectnessRuntime.MotionFrame sample(long generation, UIElement declaration, Rect target,
                                                           long nowNanos, boolean reducedMotion) {
        if (target.space() != Space.GUI) throw new IllegalArgumentException("Motion targets require GUI coordinates");
        var key = new Key(generation, declaration.id());
        long nowMs = nowNanos / 1_000_000;
        var track = tracks.get(key);
        if (track == null) {
            if (tracks.size() >= 256) throw new IllegalStateException("Correctness motion track capacity reached");
            tracks.put(key, new Track(target));
            return new CorrectnessRuntime.MotionFrame(target, null, false);
        }
        if (reducedMotion || !declaration.motionPolicy().interruptible() || target.empty()) {
            track.snap(target);
            return new CorrectnessRuntime.MotionFrame(target, null, false);
        }
        Rect actual = track.sample(nowMs);
        var environment = environments.get(key);
        if (!target.equals(track.target) || !java.util.Objects.equals(environment, track.environment)) {
            var route = route(actual, target, track.velocity(), environment, routeBudgetNanos);
            track.target = target;
            track.environment = environment;
            track.route = route.points;
            track.routeIndex = 0;
            track.condition = route.condition;
            track.command(track.route.get(0), nowMs);
        }
        if (!track.running() && track.routeIndex + 1 < track.route.size()) {
            track.command(track.route.get(++track.routeIndex), nowMs);
        }
        boolean moving = track.running() || track.routeIndex + 1 < track.route.size();
        if (!moving || declaration.motionPolicy().horizonSeconds() <= 0) {
            return new CorrectnessRuntime.MotionFrame(actual, null, moving);
        }
        var projected = projection(actual, track.route.get(track.routeIndex), track.velocity(),
                Math.min(1, declaration.motionPolicy().horizonSeconds()), 16);
        var future = new Trajectory(projected.keyframes(), track.condition
                + "; sampled current spring leg; later waypoints depend on actual settling; no future input", true);
        return new CorrectnessRuntime.MotionFrame(actual, future, true);
    }

    @Override public void forget(long generation, UiId id) {
        var key = new Key(generation, id);
        var track = tracks.remove(key);
        if (track != null) track.close();
        environments.remove(key);
    }

    void close() {
        for (var track : tracks.values()) track.close();
        tracks.clear(); environments.clear();
    }

    private record Key(long generation, UiId id) {}
    private record Environment(Rect viewport, List<Rect> obstacles) {}
    private record Route(List<Rect> points, String condition) {}

    private static Route route(Rect actual, Rect target, Velocity velocity, Environment environment, long budgetNanos) {
        if (environment == null || environment.obstacles.isEmpty()) {
            return new Route(List.of(target), "Direct existing RETARGET spring; external geometry is unobservable");
        }
        long started = System.nanoTime();
        if (clear(actual, List.of(target), environment) && firstLegClear(actual, target, velocity, environment)) {
            return new Route(List.of(target), "Direct spring against registered bounds; sampled safety prediction");
        }
        var candidates = new ArrayList<List<Rect>>(ROUTE_CANDIDATES);
        candidates.add(List.of(at(target.x(), actual.y(), target), target));
        candidates.add(List.of(at(actual.x(), target.y(), target), target));
        var byGeometry = environment.obstacles.stream().filter(r -> !r.empty())
                .sorted(Comparator.comparingDouble(Rect::y).thenComparingDouble(Rect::x)
                        .thenComparingDouble(Rect::width).thenComparingDouble(Rect::height)).toList();
        double w = Math.max(actual.width(), target.width()), h = Math.max(actual.height(), target.height());
        for (var obstacle : byGeometry) {
            if (candidates.size() >= ROUTE_CANDIDATES) break;
            double above = obstacle.y() - h - CLEARANCE, below = obstacle.bottom() + CLEARANCE;
            candidates.add(List.of(at(actual.x(), above, target), at(target.x(), above, target), target));
            candidates.add(List.of(at(actual.x(), below, target), at(target.x(), below, target), target));
            if (candidates.size() >= ROUTE_CANDIDATES) break;
            double left = obstacle.x() - w - CLEARANCE, right = obstacle.right() + CLEARANCE;
            candidates.add(List.of(at(left, actual.y(), target), at(left, target.y(), target), target));
            candidates.add(List.of(at(right, actual.y(), target), at(right, target.y(), target), target));
        }
        candidates.sort(Comparator.comparingDouble(points -> length(actual, points)));
        for (var candidate : candidates) {
            if (System.nanoTime() - started >= budgetNanos) {
                return new Route(List.of(target), "UNRESOLVED: cooperative motion route budget exhausted; no safety claim");
            }
            if (clear(actual, candidate, environment) && firstLegClear(actual, candidate.get(0), velocity, environment)) {
                return new Route(candidate, "Registered-bounds detour with at most two orthogonal waypoints; sampled spring safety");
            }
        }
        return new Route(List.of(target), "UNRESOLVED: no bounded registered-obstacle route; actual velocity is preserved for the safety gate");
    }

    private static Rect at(double x, double y, Rect size) { return new Rect(x, y, size.width(), size.height(), Space.GUI); }

    private static double length(Rect start, List<Rect> route) {
        double result = 0;
        for (var point : route) { result += Math.hypot(point.x() - start.x(), point.y() - start.y()); start = point; }
        return result;
    }

    /** AABB envelopes are conservative; rejecting a clear diagonal is an intentional bounded false positive. */
    private static boolean clear(Rect start, List<Rect> points, Environment environment) {
        for (var point : points) {
            if (!clearBounds(start.union(point), environment)) return false;
            start = point;
        }
        return true;
    }

    private static boolean clearBounds(Rect swept, Environment environment) {
        if (!environment.viewport.contains(swept)) return false;
        for (var obstacle : environment.obstacles) if (swept.intersects(obstacle)) return false;
        return true;
    }

    private static boolean firstLegClear(Rect actual, Rect target, Velocity velocity, Environment environment) {
        var projection = projection(actual, target, velocity, .5, 16);
        Rect previous = actual;
        for (int i = 1; i < projection.keyframes().size(); i++) {
            var state = projection.keyframes().get(i).state();
            var next = new Rect(state.transform().x(), state.transform().y(), state.shape().width(), state.shape().height(), Space.GUI);
            if (!clearBounds(previous.union(next), environment)) return false;
            previous = next;
        }
        return true;
    }

    private static Trajectory projection(Rect actual, Rect target, Velocity velocity, double duration, int samples) {
        var from = state(actual); var to = state(target);
        return SpatialIntelligence.spring(from, to, velocity, SPRING.response(), SPRING.damping(), duration, samples);
    }

    private static MotionState state(Rect bounds) {
        return new MotionState(Shape.rectangle(Math.max(.001, bounds.width()), Math.max(.001, bounds.height())),
                new Transform(bounds.x(), bounds.y(), 1, 1, 0, 0, 0));
    }

    private static final class Track {
        private final InterruptibleAnimation[] channels = new InterruptibleAnimation[4];
        private Rect target;
        private List<Rect> route;
        private int routeIndex;
        private Environment environment;
        private String condition = "Direct existing spring";

        Track(Rect initial) {
            var values = values(initial);
            for (int i = 0; i < channels.length; i++) channels[i] = new InterruptibleAnimation(SPRING, values[i]);
            target = initial; route = List.of(initial);
        }
        Rect sample(long nowMs) {
            for (var channel : channels) channel.sample(nowMs);
            // A size boundary is a projection of an invalid physical overshoot, not a fabricated
            // completion. The kernel still validates declared minima against this actual rectangle.
            for (int i = 2; i < channels.length; i++) if (channels[i].value() < .001f) channels[i].snapTo(.001f);
            return new Rect(channels[0].value(), channels[1].value(), channels[2].value(), channels[3].value(), Space.GUI);
        }
        void command(Rect waypoint, long nowMs) {
            var values = values(waypoint);
            for (int i = 0; i < channels.length; i++) channels[i].transitionTo(values[i], nowMs, InterruptibleAnimation.Policy.RETARGET, 0);
        }
        void snap(Rect next) {
            var values = values(next);
            for (int i = 0; i < channels.length; i++) channels[i].snapTo(values[i]);
            target = next; route = List.of(next); routeIndex = 0; environment = null;
        }
        boolean running() {
            for (var channel : channels) if (channel.running()) return true;
            return false;
        }
        Velocity velocity() {
            return new Velocity(channels[0].velocity(), channels[1].velocity(), 0, 0, 0, channels[2].velocity(), channels[3].velocity());
        }
        void close() { for (var channel : channels) channel.close(); }
        private static float[] values(Rect bounds) {
            return new float[] {(float) bounds.x(), (float) bounds.y(), (float) bounds.width(), (float) bounds.height()};
        }
    }
}
