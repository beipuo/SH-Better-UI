package com.dishanhai.sh_better_ui.client.gui.layout;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiMath;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.*;
import com.dishanhai.sh_better_ui.client.gui.layout.VisualAlignment.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.*;
import static com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.Strategy.*;

/** 山海：按轴裁决锚点约束，再做单向避让和容器限制。 */
final class AlignmentSolver {
    private static final double EPSILON = 1e-9;

    private AlignmentSolver() {}

    record Result(AlignmentPlacement current, AlignmentPlacement target, Rect currentClamp, Rect targetClamp, Debug debug) {
        boolean samePlacement(Result old) { return current.equals(old.current) && target.equals(old.target); }
    }

    private record Decision(double delta, int priority, Reference reference) {
        private static final Decision FREE = new Decision(0, 0, Reference.CURRENT);
    }
    private record Evaluation(Constraint constraint, String target, Anchor source, Anchor goal, double delta) {}

    static Result solve(Node node, Rect screen, Direction direction, long nowMs, boolean reducedMotion) {
        var diagnostics = new ArrayList<Diagnostic>();
        var relations = new ArrayList<Relation>();
        var parentTarget = placement(node.parent, Reference.TARGET);
        var parentCurrent = placement(node.parent, Reference.CURRENT);
        boolean keepAnchor = node.strategies.contains(KEEP_ANCHOR);
        var target = AlignmentPlacement.measure(node.target, parentTarget, keepAnchor);
        var current = AlignmentPlacement.measure(node.current, parentCurrent, keepAnchor);
        Rect targetClamp = clampArea(node, parentTarget, screen);
        Rect currentClamp = clampArea(node, parentCurrent, screen);

        var targetResult = arrange(node, target, target, Reference.TARGET, targetClamp, screen, direction,
                diagnostics, null, nowMs, true);
        var currentResult = arrange(node, current, target, Reference.CURRENT, currentClamp, screen, direction,
                diagnostics, relations, nowMs, reducedMotion);
        return new Result(currentResult, targetResult, currentClamp, targetClamp,
                new Debug(Set.copyOf(node.reasons), List.copyOf(relations), List.copyOf(diagnostics)));
    }

    private static AlignmentPlacement arrange(Node node, AlignmentPlacement base, AlignmentPlacement target,
                                              Reference state, Rect clamp, Rect screen, Direction direction,
                                              List<Diagnostic> diagnostics, List<Relation> relations,
                                              long nowMs, boolean reducedMotion) {
        var horizontal = axis(node, Axis.HORIZONTAL, base, target, state, screen, direction, diagnostics, relations);
        var vertical = axis(node, Axis.VERTICAL, base, target, state, screen, direction, diagnostics, relations);
        double dx = horizontal.delta(), dy = vertical.delta();
        if (state == Reference.CURRENT && node.targetMotion != null) {
            dx = node.targetMotion.place(Axis.HORIZONTAL, dx, !reducedMotion && horizontal.reference() == Reference.TARGET, nowMs);
            dy = node.targetMotion.place(Axis.VERTICAL, dy, !reducedMotion && vertical.reference() == Reference.TARGET, nowMs);
        }
        var aligned = base.move(dx, dy);
        var placed = avoid(node, aligned, state);
        if (clamp != null) {
            Rect occupied = placed.occupied();
            if (!occupied.empty()) {
                if (occupied.width() > clamp.width() || occupied.height() > clamp.height()) {
                    diagnostics.add(new Diagnostic(Issue.OUTSIDE_PARENT, parentName(node), null, state));
                }
                placed = placed.move(inside(occupied.x(), occupied.width(), clamp.x(), clamp.right()),
                        inside(occupied.y(), occupied.height(), clamp.y(), clamp.bottom()));
            }
        }
        if (!node.previous.isEmpty()) {
            Rect occupied = placed.occupied();
            for (Node other : node.previous) {
                Rect obstacle = placement(other, state).occupied();
                if (blocked(occupied, obstacle, node.flowAxis, node.gap)) {
                    diagnostics.add(new Diagnostic(Issue.OVERLAP, other.name, node.flowAxis, state));
                }
            }
        }
        if (!aligned.equals(placed)) {
            for (Axis axis : Axis.values()) {
                double change = axis == Axis.HORIZONTAL
                        ? placed.world().tx() - aligned.world().tx() : placed.world().ty() - aligned.world().ty();
                if (Math.abs(change) > EPSILON && constrained(node, axis)) {
                    diagnostics.add(new Diagnostic(Issue.POLICY_OVERRIDE, node.name, axis, state));
                }
            }
        }
        return placed;
    }

    private static boolean constrained(Node node, Axis axis) {
        for (Constraint constraint : node.constraints) {
            if ((axis == Axis.HORIZONTAL ? constraint.rule.horizontal() : constraint.rule.vertical()) != null) return true;
        }
        return false;
    }

    private static Decision axis(Node node, Axis axis, AlignmentPlacement base, AlignmentPlacement target,
                               Reference state, Rect screen, Direction direction,
                               List<Diagnostic> diagnostics, List<Relation> relations) {
        Decision best = null;
        List<Evaluation> evaluations = null;
        Constraint winner = null;
        for (Constraint constraint : node.constraints) {
            Rule rule = constraint.rule;
            AxisRule align = axis == Axis.HORIZONTAL ? rule.horizontal() : rule.vertical();
            if (align == null) continue;
            boolean useTarget = state == Reference.TARGET || rule.reference() == Reference.TARGET;
            var evaluation = evaluate(constraint, align, axis, useTarget ? target : base,
                    useTarget ? Reference.TARGET : Reference.CURRENT, screen, direction);
            if (evaluation == null) {
                diagnostics.add(new Diagnostic(Issue.MISSING_BASELINE, targetName(node, rule.target()), axis, state));
                continue;
            }
            if (evaluations == null) evaluations = new ArrayList<>();
            evaluations.add(evaluation);
            if (best == null || rule.priority() > best.priority()) {
                best = new Decision(evaluation.delta(), rule.priority(), rule.reference());
                winner = constraint;
            }
        }
        if (best == null) return Decision.FREE;
        for (Evaluation evaluation : evaluations) {
            boolean selected = evaluation.constraint() == winner;
            if (!selected && Math.abs(evaluation.delta() - best.delta()) > EPSILON) {
                diagnostics.add(new Diagnostic(Issue.CONFLICT, evaluation.target(), axis, state));
            }
            if (relations != null) {
                relations.add(new Relation(evaluation.target(), axis, evaluation.source(), evaluation.goal(),
                        evaluation.constraint().rule.priority(), selected));
            }
        }
        return best;
    }

    private static Evaluation evaluate(Constraint constraint, AxisRule align, Axis axis, AlignmentPlacement base,
                                       Reference reference, Rect screen, Direction direction) {
        Node node = constraint.node;
        Rule rule = constraint.rule;
        Target relative = rule.target();
        Node other = relative instanceof NodeTarget explicit ? explicit.node()
                : relative == ParentTarget.PARENT ? node.parent : null;
        var destination = placement(other, reference);
        Rect sourceRect = base.bounds(rule.bounds(), false);
        Rect targetRect;
        if (destination == null) targetRect = relative instanceof FixedTarget fixed ? fixed.rect() : screen;
        else if (other == node.parent && rule.bounds() == Bounds.LAYOUT) targetRect = destination.contentArea(true);
        else targetRect = destination.bounds(rule.bounds(), true);

        Anchor source = point(align.source(), axis, sourceRect, base, rule, direction, false);
        Anchor goal = point(align.target(), axis, targetRect, destination, rule, direction, true);
        if (source == null || goal == null) return null;
        if (align.source() == Align.CENTER) source = offset(source, rule.opticalOffset(), axis);
        goal = offset(goal, rule.offset(), axis);
        double delta = coordinate(goal, axis) - coordinate(source, axis);
        return new Evaluation(constraint, targetName(node, relative), source, goal, delta);
    }

    private static Anchor point(Align align, Axis axis, Rect rect, AlignmentPlacement placement,
                                Rule rule, Direction direction, boolean target) {
        Anchor center = rect.center();
        if (align == Align.CENTER) {
            if (rule.optical() && rule.bounds() == Bounds.VISUAL && placement != null) {
                Anchor optical = placement.opticalCenter(target);
                return axis == Axis.HORIZONTAL ? new Anchor(optical.x(), center.y()) : new Anchor(center.x(), optical.y());
            }
            return center;
        }
        return switch (align) {
            case LEFT -> new Anchor(rect.x(), center.y());
            case RIGHT -> new Anchor(rect.right(), center.y());
            case START -> new Anchor(direction == Direction.LEFT_TO_RIGHT ? rect.x() : rect.right(), center.y());
            case END -> new Anchor(direction == Direction.LEFT_TO_RIGHT ? rect.right() : rect.x(), center.y());
            case TOP -> new Anchor(center.x(), rect.y());
            case BOTTOM -> new Anchor(center.x(), rect.bottom());
            case BASELINE -> {
                Double baseline = placement == null
                        ? (rect.height() == 0 ? Double.valueOf(rect.y()) : null) : placement.baseline();
                yield baseline == null ? null : new Anchor(center.x(), baseline);
            }
            case CENTER -> throw new AssertionError();
        };
    }

    private static AlignmentPlacement avoid(Node node, AlignmentPlacement placed, Reference state) {
        if (node.previous.isEmpty()) return placed;
        boolean reflow = node.strategies.contains(REFLOW);
        boolean horizontal = node.flowAxis == Axis.HORIZONTAL;
        Rect occupied = placed.occupied();
        if (occupied.empty()) return placed;
        var obstacles = new ArrayList<Rect>(node.previous.size());
        for (Node previous : node.previous) {
            Rect obstacle = placement(previous, state).occupied();
            if (!obstacle.empty() && (reflow || crosses(occupied, obstacle, node.flowAxis))) obstacles.add(obstacle);
        }
        if (!reflow) obstacles.sort(Comparator.comparingDouble(horizontal ? Rect::x : Rect::y));
        double start = horizontal ? occupied.x() : occupied.y();
        double length = horizontal ? occupied.width() : occupied.height();
        double position = start;
        for (Rect obstacle : obstacles) {
            double before = (horizontal ? obstacle.x() : obstacle.y()) - node.gap;
            double after = (horizontal ? obstacle.right() : obstacle.bottom()) + node.gap;
            if (reflow || (position < after - EPSILON && position + length > before + EPSILON)) {
                position = Math.max(position, after);
            }
        }
        return horizontal ? placed.move(position - start, 0) : placed.move(0, position - start);
    }

    private static boolean blocked(Rect a, Rect b, Axis axis, double gap) {
        if (a.empty() || b.empty()) return false;
        if (!crosses(a, b, axis)) return false;
        return axis == Axis.HORIZONTAL
                ? a.x() < b.right() + gap - EPSILON && a.right() > b.x() - gap + EPSILON
                : a.y() < b.bottom() + gap - EPSILON && a.bottom() > b.y() - gap + EPSILON;
    }

    private static boolean crosses(Rect a, Rect b, Axis axis) {
        return axis == Axis.HORIZONTAL ? a.y() < b.bottom() && a.bottom() > b.y()
                : a.x() < b.right() && a.right() > b.x();
    }

    private static Rect clampArea(Node node, AlignmentPlacement parent, Rect screen) {
        return !node.strategies.contains(CLAMP_TO_PARENT) ? null : parent == null ? screen : parent.contentArea(true);
    }

    private static double inside(double start, double size, double min, double max) {
        return UiMath.clamp(start, min, Math.max(min, max - size)) - start;
    }

    private static AlignmentPlacement placement(Node node, Reference state) {
        return node == null ? null : state == Reference.CURRENT ? node.result.current() : node.result.target();
    }

    private static double coordinate(Anchor point, Axis axis) { return axis == Axis.HORIZONTAL ? point.x() : point.y(); }
    private static Anchor offset(Anchor point, Anchor offset, Axis axis) {
        return axis == Axis.HORIZONTAL ? new Anchor(point.x() + offset.x(), point.y())
                : new Anchor(point.x(), point.y() + offset.y());
    }
    private static String parentName(Node node) { return node.parent == null ? "screen" : node.parent.name; }

    private static String targetName(Node node, Target target) {
        if (target instanceof NodeTarget explicit) return explicit.node().name;
        if (target instanceof FixedTarget fixed) return fixed.rect().toString();
        return target == ParentTarget.SCREEN ? "screen" : parentName(node);
    }
}
