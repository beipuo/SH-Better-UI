// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.live;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Resolves measured arrangements against a safe viewport and hard occupied regions. */
public final class SurfacePlacement {
    public enum CollisionPolicy {
        /** Try measured arrangements and compression at the anchor, then shift. */
        AVOID,
        /** Try measured arrangements at the anchor, then shift without collision compression. */
        SHIFT,
        /** Use measured arrangements and compression at the anchor. */
        COMPRESS,
        /** Fall back to a measured minimal arrangement at the anchor. */
        COLLAPSE,
        /** Use the first arrangement that fits the viewport, allowing overlap. */
        OVERLAY,
        /** Hide if the first arrangement that fits the viewport overlaps a region. */
        HIDE
    }

    private static final double HYSTERESIS = 3;

    public record Rect(double x, double y, double width, double height) {
        public Rect {
            x = SurfaceGeometry.finite(x);
            y = SurfaceGeometry.finite(y);
            width = SurfaceGeometry.extent(width, x);
            height = SurfaceGeometry.extent(height, y);
        }

        public double right() { return x + width; }
        public double bottom() { return y + height; }

        public boolean empty() { return width <= 0 || height <= 0; }

        public boolean intersects(Rect other) {
            return !empty() && !other.empty() && x < other.right() && right() > other.x
                    && y < other.bottom() && bottom() > other.y;
        }
    }

    /** Desired geometry and the smallest dimensions at which its measured content fits. */
    public record Candidate(SurfacePresentation presentation, SurfaceGeometry geometry, double minWidth, double minHeight) {
        public Candidate {
            Objects.requireNonNull(presentation);
            Objects.requireNonNull(geometry);
            minWidth = Math.max(0, Math.min(SurfaceGeometry.finite(minWidth), geometry.width()));
            minHeight = Math.max(0, Math.min(SurfaceGeometry.finite(minHeight), geometry.height()));
        }

        public Candidate(SurfacePresentation presentation, SurfaceGeometry geometry) {
            this(presentation, geometry, geometry.width(), geometry.height());
        }
    }

    public record Result(SurfacePresentation presentation, SurfaceGeometry geometry) {
        public static final Result HIDDEN = new Result(SurfacePresentation.HIDDEN, SurfaceGeometry.HIDDEN);
    }

    private SurfacePlacement() {}

    /** Candidates are ordered by preference. A previous result supplies optional placement hysteresis. */
    public static Result place(Rect viewport, List<Candidate> candidates, List<Rect> occupied,
                               CollisionPolicy policy, Result previous) {
        if (viewport.empty()) return Result.HIDDEN;
        boolean canCompress = policy == CollisionPolicy.AVOID || policy == CollisionPolicy.COMPRESS;
        boolean first = true;
        for (Candidate candidate : candidates) {
            SurfaceGeometry fitted = fit(candidate, viewport);
            if (fitted == null) continue;
            if (policy == CollisionPolicy.COLLAPSE && !first && candidate.presentation != SurfacePresentation.MINIMAL) {
                continue;
            }
            first = false;
            if (policy == CollisionPolicy.OVERLAY) return new Result(candidate.presentation, fitted);
            if (!blocked(fitted, occupied, 0)) {
                return anchoredResult(candidate.presentation, fitted, viewport, candidates, occupied, policy, previous);
            }
            if (policy == CollisionPolicy.HIDE) return Result.HIDDEN;
            if (canCompress) {
                var compressed = compress(candidate, fitted, occupied);
                if (compressed != null) return new Result(candidate.presentation, compressed);
            }
        }
        if (policy == CollisionPolicy.AVOID || policy == CollisionPolicy.SHIFT) {
            var byTop = occupied.stream().filter(rect -> !rect.empty()).sorted(Comparator.comparingDouble(Rect::y)).toList();
            for (Candidate candidate : candidates) {
                SurfaceGeometry fitted = fit(candidate, viewport);
                if (fitted == null) continue;
                var shifted = shift(candidate.presentation, fitted, viewport, byTop, previous);
                if (shifted != null) return new Result(candidate.presentation, shifted);
            }
        }
        return Result.HIDDEN;
    }

    /** Clamps geometry to the safe viewport, including an in-flight animation sample. */
    public static SurfaceGeometry fit(SurfaceGeometry geometry, Rect viewport) {
        double width = Math.min(geometry.width(), viewport.width);
        double height = Math.min(geometry.height(), viewport.height);
        double x = clamp(geometry.x(), viewport.x, subtractToFit(viewport.right(), width));
        double y = clamp(geometry.y(), viewport.y, subtractToFit(viewport.bottom(), height));
        if (x == geometry.x() && y == geometry.y() && width == geometry.width() && height == geometry.height()) {
            return geometry;
        }
        return new SurfaceGeometry(x, y, width, height, geometry.radius(), geometry.opacity());
    }

    private static SurfaceGeometry fit(Candidate candidate, Rect viewport) {
        if (!candidate.presentation.visible() || !candidate.geometry.visible()) return null;
        var geometry = fit(candidate.geometry, viewport);
        return geometry.width() >= candidate.minWidth && geometry.height() >= candidate.minHeight ? geometry : null;
    }

    private static Result anchoredResult(SurfacePresentation presentation, SurfaceGeometry geometry, Rect viewport,
                                         List<Candidate> candidates, List<Rect> occupied, CollisionPolicy policy,
                                         Result previous) {
        if (previous != null && previous.presentation != presentation && policy != CollisionPolicy.HIDE
                && blocked(geometry, occupied, HYSTERESIS)) {
            for (Candidate candidate : candidates) {
                if (candidate.presentation != previous.presentation
                        || (policy == CollisionPolicy.COLLAPSE && candidate.presentation != SurfacePresentation.MINIMAL)) {
                    continue;
                }
                var prior = fit(candidate, viewport);
                if (prior != null && !blocked(prior, occupied, 0)) return new Result(candidate.presentation, prior);
            }
        }
        return new Result(presentation, geometry);
    }

    private static SurfaceGeometry compress(Candidate candidate, SurfaceGeometry anchor, List<Rect> occupied) {
        if (anchor.width() <= candidate.minWidth && anchor.height() <= candidate.minHeight) return null;
        double center = anchor.x() + anchor.width() / 2;
        var best = compressToWidth(candidate, anchor, occupied, anchor.width());
        for (Rect obstacle : occupied) {
            if (obstacle.empty() || obstacle.bottom() <= anchor.y() || obstacle.y >= anchor.y() + anchor.height()) {
                continue;
            }
            double width = 2 * Math.max(center - obstacle.right(), obstacle.x - center);
            if (width < candidate.minWidth || width >= anchor.width()) continue;
            var narrower = compressToWidth(candidate, anchor, occupied, width);
            if (narrower != null && (best == null || narrower.width() * narrower.height() > best.width() * best.height())) {
                best = narrower;
            }
        }
        return best;
    }

    private static SurfaceGeometry compressToWidth(Candidate candidate, SurfaceGeometry anchor, List<Rect> occupied,
                                                    double width) {
        if (width <= 0) return null;
        double x = anchor.x() + (anchor.width() - width) / 2;
        double height = anchor.height();
        for (Rect obstacle : occupied) {
            if (!obstacle.empty() && obstacle.bottom() > anchor.y() && x < obstacle.right() && x + width > obstacle.x) {
                height = Math.min(height, subtractToFit(obstacle.y, anchor.y()));
                if (height <= 0 || height < candidate.minHeight) return null;
            }
        }
        return new SurfaceGeometry(x, anchor.y(), width, height, anchor.radius(), anchor.opacity());
    }

    private static SurfaceGeometry shift(SurfacePresentation presentation, SurfaceGeometry anchor, Rect viewport,
                                         List<Rect> byTop, Result previous) {
        SurfaceGeometry best = shiftAtX(anchor.x(), anchor, viewport, byTop, null);
        double maxX = subtractToFit(viewport.right(), anchor.width());
        for (Rect obstacle : byTop) {
            best = shiftAtX(clamp(subtractToFit(obstacle.x, anchor.width()), viewport.x, maxX), anchor, viewport, byTop, best);
            best = shiftAtX(clamp(obstacle.right(), viewport.x, maxX), anchor, viewport, byTop, best);
        }
        if (previous != null && previous.presentation == presentation && previous.geometry.visible()) {
            var prior = fit(move(anchor, previous.geometry.x(), previous.geometry.y()), viewport);
            if ((best == null || distance(prior, anchor) <= distance(best, anchor) + HYSTERESIS)
                    && !blocked(prior, byTop, 0)) return prior;
        }
        return best;
    }

    /** With x fixed, jump past each overlapping vertical interval in either direction. */
    private static SurfaceGeometry shiftAtX(double x, SurfaceGeometry anchor, Rect viewport, List<Rect> byTop,
                                             SurfaceGeometry best) {
        if (best != null && Math.abs(x - anchor.x()) >= distance(best, anchor)) return best;
        double below = anchor.y();
        for (Rect obstacle : byTop) {
            if (overlapsX(x, anchor.width(), obstacle) && below < obstacle.bottom() && below + anchor.height() > obstacle.y) {
                below = obstacle.bottom();
            }
        }
        double above = anchor.y();
        for (int index = byTop.size() - 1; index >= 0; index--) {
            Rect obstacle = byTop.get(index);
            if (overlapsX(x, anchor.width(), obstacle) && above < obstacle.bottom() && above + anchor.height() > obstacle.y) {
                above = subtractToFit(obstacle.y, anchor.height());
            }
        }
        if (above >= viewport.y) best = nearer(x, above, anchor, best);
        if (below + anchor.height() <= viewport.bottom()) best = nearer(x, below, anchor, best);
        return best;
    }

    private static SurfaceGeometry nearer(double x, double y, SurfaceGeometry anchor, SurfaceGeometry best) {
        return best == null || Math.hypot(x - anchor.x(), y - anchor.y()) < distance(best, anchor)
                ? move(anchor, x, y) : best;
    }

    private static SurfaceGeometry move(SurfaceGeometry geometry, double x, double y) {
        return new SurfaceGeometry(x, y, geometry.width(), geometry.height(), geometry.radius(), geometry.opacity());
    }

    private static double distance(SurfaceGeometry geometry, SurfaceGeometry anchor) {
        return Math.hypot(geometry.x() - anchor.x(), geometry.y() - anchor.y());
    }

    private static boolean overlapsX(double x, double width, Rect obstacle) {
        return x < obstacle.right() && x + width > obstacle.x;
    }

    private static boolean blocked(SurfaceGeometry geometry, List<Rect> occupied, double margin) {
        for (Rect obstacle : occupied) {
            if (!obstacle.empty() && geometry.x() - margin < obstacle.right()
                    && geometry.x() + geometry.width() + margin > obstacle.x
                    && geometry.y() - margin < obstacle.bottom()
                    && geometry.y() + geometry.height() + margin > obstacle.y) return true;
        }
        return false;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    private static double subtractToFit(double edge, double length) {
        double remaining = edge - length;
        // Prevent a fractional width or height from rounding its far edge into the obstacle.
        return remaining + length > edge ? Math.nextDown(remaining) : remaining;
    }
}
