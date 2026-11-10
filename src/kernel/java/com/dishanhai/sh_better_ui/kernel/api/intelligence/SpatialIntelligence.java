// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.intelligence;

import com.dishanhai.sh_better_ui.client.gui.collision.CollisionBody;
import com.dishanhai.sh_better_ui.client.gui.collision.CollisionShape;
import com.dishanhai.sh_better_ui.client.gui.collision.CollisionWorld;
import com.dishanhai.sh_better_ui.client.gui.collision.CompressionModel;
import com.dishanhai.sh_better_ui.client.gui.collision.LayoutSolver;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Shape;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Transform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure Java 17 geometry and predictive analysis used by the kernel's correctness service.
 * Layout reuses the existing collision solver; predictions never advance a live animation.
 */
public final class SpatialIntelligence {
    private static final int NODE_LIMIT = 256;
    private static final int SAFE_AREA_LIMIT = 128;
    private static final double SOLVER_LIMIT = 1_000_000;

    private SpatialIntelligence() {}

    public enum Space { GUI, FRAMEBUFFER, PHYSICAL, LOCAL, WORLD_PROJECTED }

    /** Half-open bounds. Operations involving two rectangles require identical spaces. */
    public record Rect(double x, double y, double width, double height, Space space) {
        public Rect {
            Objects.requireNonNull(space);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width)
                    || !Double.isFinite(height) || width < 0 || height < 0
                    || !Double.isFinite(x + width) || !Double.isFinite(y + height)) {
                throw new IllegalArgumentException("Invalid spatial bounds");
            }
        }

        public double right() { return x + width; }
        public double bottom() { return y + height; }
        public boolean empty() { return width == 0 || height == 0; }
        public boolean contains(double px, double py) {
            return !empty() && px >= x && px < right() && py >= y && py < bottom();
        }
        public boolean contains(Rect other) {
            sameSpace(other);
            return other.x >= x && other.y >= y && other.right() <= right() && other.bottom() <= bottom();
        }
        public boolean intersects(Rect other) {
            sameSpace(other);
            return !empty() && !other.empty() && x < other.right() && other.x < right()
                    && y < other.bottom() && other.y < bottom();
        }
        public Rect intersection(Rect other) {
            sameSpace(other);
            double left = Math.max(x, other.x), top = Math.max(y, other.y);
            return new Rect(left, top, Math.max(0, Math.min(right(), other.right()) - left),
                    Math.max(0, Math.min(bottom(), other.bottom()) - top), space);
        }
        public Rect union(Rect other) {
            sameSpace(other);
            if (empty()) return other;
            if (other.empty()) return this;
            double left = Math.min(x, other.x), top = Math.min(y, other.y);
            return new Rect(left, top, Math.max(right(), other.right()) - left,
                    Math.max(bottom(), other.bottom()) - top, space);
        }
        private void sameSpace(Rect other) {
            if (space != Objects.requireNonNull(other).space) throw new IllegalArgumentException("Mixed coordinate spaces");
        }
    }

    public enum Anchor { ABSOLUTE, TOP_LEFT, TOP_CENTER, TOP_RIGHT, CENTER, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT }

    /** Absolute preferred rectangles are GUI coordinates; child LOCAL rectangles are parent-relative. */
    public record Constraints(Anchor anchor, double minW, double minH, double preferredW, double preferredH,
                              double maxW, double maxH, double margin, int priority,
                              boolean respectSafeArea, boolean avoidOverlap) {
        public Constraints {
            Objects.requireNonNull(anchor);
            for (double value : new double[] {minW, minH, preferredW, preferredH, maxW, maxH, margin}) {
                if (!Double.isFinite(value) || value < 0 || value > SOLVER_LIMIT) {
                    throw new IllegalArgumentException("Invalid layout constraint extent");
                }
            }
            if (minW > preferredW || preferredW > maxW || minH > preferredH || preferredH > maxH) {
                throw new IllegalArgumentException("Expected minimum <= preferred <= maximum");
            }
        }
        public static Constraints defaults(double width, double height) {
            return new Constraints(Anchor.ABSOLUTE, width, height, width, height, width, height, 0, 0, true, true);
        }
    }

    public record Node(UiId id, UiId parent, Rect preferred, Constraints constraints) {
        public Node {
            Objects.requireNonNull(id);
            Objects.requireNonNull(preferred);
            Objects.requireNonNull(constraints);
            if (preferred.space != Space.GUI && !(parent != null && preferred.space == Space.LOCAL)) {
                throw new IllegalArgumentException("Layout needs GUI bounds, or LOCAL bounds with a parent");
            }
        }
    }

    public record Conflict(UiId nodeId, String code, Rect evidence, String explanation) {
        public Conflict {
            Objects.requireNonNull(nodeId);
            Objects.requireNonNull(code);
            Objects.requireNonNull(evidence);
            Objects.requireNonNull(explanation);
        }
    }

    public record LayoutResult(Map<UiId, Rect> bounds, List<Conflict> conflicts, int iterations, boolean budgetExceeded) {
        public LayoutResult {
            bounds = Collections.unmodifiableMap(new LinkedHashMap<>(bounds));
            conflicts = List.copyOf(conflicts);
        }
    }

    /**
     * Stable priority/ID placement, parents before descendants. Each placement uses the existing
     * bounded solver against fixed higher-priority bounds. The time budget is cooperative: checked
     * between solver calls, not a promise to preempt an individual Java method. Unprocessed nodes
     * have an explicit conflict and no valid resolved bounds. maxIterations limits placements.
     */
    public static LayoutResult solve(List<Node> nodes, Rect viewport, List<Rect> safeAreas,
                                     int maxIterations, long budgetNanos) {
        Objects.requireNonNull(nodes);
        Objects.requireNonNull(viewport);
        Objects.requireNonNull(safeAreas);
        if (nodes.size() > NODE_LIMIT || safeAreas.size() > SAFE_AREA_LIMIT || maxIterations < 1
                || maxIterations > NODE_LIMIT || budgetNanos < 1 || viewport.space != Space.GUI) {
            throw new IllegalArgumentException("Invalid layout bounds or analysis budget");
        }
        requireSolverRange(viewport);
        for (var area : safeAreas) {
            if (area.space != Space.GUI) throw new IllegalArgumentException("Safe area must use GUI coordinates");
        }
        long started = System.nanoTime();
        var declarations = new LinkedHashMap<UiId, Node>();
        for (var node : nodes) {
            if (declarations.putIfAbsent(node.id, node) != null) throw new IllegalArgumentException("Duplicate UI node " + node.id);
        }
        var remaining = new ArrayList<>(nodes);
        remaining.sort(Comparator.comparingInt((Node node) -> node.constraints.priority).reversed().thenComparing(Node::id));
        var resolved = new LinkedHashMap<UiId, Rect>();
        var conflicts = new ArrayList<Conflict>();
        int iterations = 0;
        boolean budgetExceeded = false;
        while (!remaining.isEmpty()) {
            Node next = null;
            for (var node : remaining) {
                if (node.parent == null || resolved.containsKey(node.parent)) { next = node; break; }
            }
            if (next == null) {
                for (var node : remaining) conflicts.add(new Conflict(node.id, "PARENT_UNRESOLVED", node.preferred,
                        "Parent is missing, cyclic, or has no valid resolved bounds"));
                break;
            }
            if (iterations == maxIterations || System.nanoTime() - started >= budgetNanos) {
                budgetExceeded = true;
                for (var node : remaining) conflicts.add(new Conflict(node.id, "LAYOUT_BUDGET", node.preferred,
                        "Placement or cooperative time budget exhausted; this node was not committed"));
                break;
            }
            remaining.remove(next);
            iterations++;
            Rect parent = next.parent == null ? viewport : resolved.get(next.parent);
            Rect available = inset(parent.intersection(viewport), next.constraints.margin);
            Rect desired = desired(next, available, parent);
            requireSolverRange(desired);
            var policy = next.constraints;
            if (available.width < policy.minW || available.height < policy.minH) {
                conflicts.add(new Conflict(next.id, "MINIMUM_SIZE", available,
                        "Available parent/viewport space cannot satisfy the declared minimum size"));
                continue;
            }
            var obstacles = new ArrayList<Rect>();
            if (policy.respectSafeArea) {
                for (var safe : safeAreas) {
                    var clipped = safe.intersection(available);
                    if (!clipped.empty()) obstacles.add(clipped);
                }
            }
            if (policy.avoidOverlap) {
                for (var entry : resolved.entrySet()) {
                    if (ancestor(entry.getKey(), next, declarations)) continue;
                    var clipped = entry.getValue().intersection(available);
                    if (!clipped.empty()) obstacles.add(clipped);
                }
            }
            int previousConflicts = conflicts.size();
            var world = CollisionWorld.layoutOnly(collision(available), new LayoutSolver(64));
            for (var obstacle : obstacles) {
                world.add(collision(obstacle)).strategy(CollisionBody.Strategy.RESIST).priority(Integer.MAX_VALUE);
            }
            var body = world.add(collision(desired)).strategy(CollisionBody.Strategy.ELASTIC)
                    .priority(Integer.MIN_VALUE).compression(new CompressionModel((float) policy.minW,
                            (float) policy.minH, (float) policy.maxW, (float) policy.maxH, 1));
            world.update(0, true);
            var target = body.target();
            var result = new Rect(target.x(), target.y(), target.width(), target.height(), Space.GUI);
            // Float solver output is measured again in the public double-coordinate contract.
            boolean safe = available.contains(result) && result.width + .001 >= policy.minW && result.height + .001 >= policy.minH;
            for (var obstacle : obstacles) {
                var overlap = result.intersection(obstacle);
                if (overlap.width > .001 && overlap.height > .001) {
                    conflicts.add(new Conflict(next.id, "UNRESOLVED_OVERLAP", overlap,
                            "Existing bounded collision solver could not separate this node from protected bounds"));
                    safe = false;
                }
            }
            if (safe) resolved.put(next.id, result);
            else if (conflicts.size() == previousConflicts) {
                conflicts.add(new Conflict(next.id, "INVALID_RESOLUTION", result,
                        "Solver output violates viewport or minimum-size constraints"));
            }
            world.clear();
        }
        return new LayoutResult(resolved, conflicts, iterations, budgetExceeded);
    }

    private static boolean ancestor(UiId id, Node node, Map<UiId, Node> nodes) {
        UiId parent = node.parent;
        for (int depth = 0; parent != null && depth < nodes.size(); depth++) {
            if (parent.equals(id)) return true;
            Node declaration = nodes.get(parent);
            parent = declaration == null ? null : declaration.parent;
        }
        return false;
    }

    /**
     * Resolves only declared anchor, preferred size and margin, without collision correction or
     * clamping the resulting position or size. A null parent uses the viewport as the anchor frame.
     */
    public static Rect preferred(Node node, Rect viewport, Rect parent) {
        Objects.requireNonNull(node); Objects.requireNonNull(viewport);
        if (viewport.space != Space.GUI || parent != null && parent.space != Space.GUI) {
            throw new IllegalArgumentException("Preferred layout needs GUI anchor frames");
        }
        Rect anchorFrame = parent == null ? viewport : parent;
        return desired(node, inset(anchorFrame.intersection(viewport), node.constraints.margin), anchorFrame);
    }

    private static Rect inset(Rect rect, double margin) {
        double x = Math.min(margin, rect.width / 2), y = Math.min(margin, rect.height / 2);
        return new Rect(rect.x + x, rect.y + y, rect.width - 2 * x, rect.height - 2 * y, rect.space);
    }

    private static Rect desired(Node node, Rect available, Rect parent) {
        var c = node.constraints;
        double w = c.preferredW, h = c.preferredH;
        double x = node.preferred.x, y = node.preferred.y;
        if (node.preferred.space == Space.LOCAL) { x += parent.x; y += parent.y; }
        if (c.anchor != Anchor.ABSOLUTE) {
            x = switch (c.anchor) {
                case TOP_CENTER, CENTER, BOTTOM_CENTER -> available.x + (available.width - w) / 2;
                case TOP_RIGHT, BOTTOM_RIGHT -> available.right() - w;
                default -> available.x;
            };
            y = switch (c.anchor) {
                case CENTER -> available.y + (available.height - h) / 2;
                case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> available.bottom() - h;
                default -> available.y;
            };
        }
        return new Rect(x, y, w, h, Space.GUI);
    }

    private static void requireSolverRange(Rect rect) {
        if (Math.abs(rect.x) > SOLVER_LIMIT || Math.abs(rect.y) > SOLVER_LIMIT
                || rect.width > SOLVER_LIMIT || rect.height > SOLVER_LIMIT
                || rect.right() > SOLVER_LIMIT || rect.bottom() > SOLVER_LIMIT) {
            throw new IllegalArgumentException("Bounds exceed the existing collision solver range");
        }
    }

    private static CollisionShape collision(Rect rect) {
        return new CollisionShape((float) rect.x, (float) rect.y, (float) rect.width, (float) rect.height);
    }

    public record MotionState(Shape shape, Transform transform) {
        public MotionState { Objects.requireNonNull(shape); Objects.requireNonNull(transform); }
    }
    public record Keyframe(double seconds, MotionState state) {
        public Keyframe {
            if (!Double.isFinite(seconds) || seconds < 0 || seconds > 60) throw new IllegalArgumentException("Invalid motion time");
            Objects.requireNonNull(state);
        }
    }
    /**
     * Piecewise linear transforms and dimensions; radians preserve complete rotations.
     * sampledModel marks a sampled physical model whose continuous live-frame error is unknown.
     */
    public record Trajectory(List<Keyframe> keyframes, String condition, boolean sampledModel) {
        public Trajectory(List<Keyframe> keyframes, String condition) { this(keyframes, condition, false); }
        public Trajectory {
            keyframes = List.copyOf(keyframes);
            Objects.requireNonNull(condition);
            if (keyframes.size() < 2 || keyframes.size() > 1024 || keyframes.get(0).seconds != 0) {
                throw new IllegalArgumentException("Trajectory needs 2..1024 ordered keyframes starting at zero");
            }
            for (int i = 1; i < keyframes.size(); i++) {
                if (keyframes.get(i).seconds <= keyframes.get(i - 1).seconds) throw new IllegalArgumentException("Unordered keyframes");
                var a = keyframes.get(i - 1).state;
                var b = keyframes.get(i).state;
                if (a.shape.kind() != b.shape.kind()
                        || Math.signum(a.transform.scaleX()) != Math.signum(b.transform.scaleX())
                        || Math.signum(a.transform.scaleY()) != Math.signum(b.transform.scaleY())) {
                    throw new IllegalArgumentException("Trajectory changes shape kind or crosses singular scale");
                }
            }
        }
    }
    public record Velocity(double x, double y, double scaleX, double scaleY, double radians, double width, double height) {
        public static final Velocity ZERO = new Velocity(0, 0, 0, 0, 0, 0, 0);
        public Velocity {
            for (double value : new double[] {x, y, scaleX, scaleY, radians, width, height}) {
                if (!Double.isFinite(value) || Math.abs(value) > 1_000_000) throw new IllegalArgumentException("Invalid motion velocity");
            }
        }
    }
    /** spatialError is -1 when error relative to a sampled physical model is unknown. */
    public record Prediction(UiId id, List<Keyframe> path, Rect sweptBounds, boolean conservative,
                             double spatialError, String condition, boolean budgetExceeded, Trajectory trajectory) {
        public Prediction {
            Objects.requireNonNull(id); path = List.copyOf(path); Objects.requireNonNull(sweptBounds);
            Objects.requireNonNull(condition); Objects.requireNonNull(trajectory);
            if (path.size() < 2 || path.size() > 1024 || sweptBounds.space != Space.GUI
                    || !Double.isFinite(spatialError) || spatialError < -1) {
                throw new IllegalArgumentException("Invalid motion prediction");
            }
        }
    }
    public record Interval(double fromSeconds, double toSeconds, Rect overlap) {
        public Interval {
            Objects.requireNonNull(overlap);
            if (!Double.isFinite(fromSeconds) || !Double.isFinite(toSeconds) || fromSeconds < 0
                    || toSeconds <= fromSeconds || overlap.space != Space.GUI) {
                throw new IllegalArgumentException("Invalid collision interval");
            }
        }
    }
    /** timeError is -1 when live-model interpolation error has no proven time bound. */
    public record CollisionPrediction(UiId first, UiId second, List<Interval> intervals,
                                      boolean conservative, double timeError, String condition) {
        public CollisionPrediction { intervals = List.copyOf(intervals); }
    }

    public static Prediction predict(UiId id, Trajectory trajectory, double horizonSeconds, int maxSamples, long budgetNanos) {
        return SpatialMotion.predict(id, trajectory, horizonSeconds, maxSamples, budgetNanos);
    }
    public static CollisionPrediction collisions(Prediction first, Prediction second) {
        return SpatialMotion.collisions(first, second);
    }
    /** Projects fresh copies of the existing Spring model with the supplied actual velocity. */
    public static Trajectory spring(MotionState actual, MotionState target, Velocity velocity, double response,
                                    double damping, double durationSeconds, int maxSamples) {
        return SpatialMotion.spring(actual, target, velocity, response, damping, durationSeconds, maxSamples);
    }
}
