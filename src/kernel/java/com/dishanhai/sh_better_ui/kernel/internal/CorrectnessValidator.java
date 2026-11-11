// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.*;
import java.util.*;

import static com.dishanhai.sh_better_ui.kernel.api.intelligence.UICorrectnessContract.Rule;
import static com.dishanhai.sh_better_ui.kernel.api.intelligence.ValidationReport.*;

/** Pure validation of one detached publication. Decorations never become interactive obstacles. */
final class CorrectnessValidator {
    private CorrectnessValidator() {}

    static ValidationReport validate(UISnapshot snapshot) {
        var issues = new ArrayList<Issue>();
        var nodes = snapshot.nodes().values().stream().sorted(Comparator.comparing(n -> n.id().toString())).toList();
        for (var node : nodes) {
            if (node.version() != snapshot.version()) {
                add(issues, node, Rule.INTERACTION, "STATE_VERSION", Evidence.OBSERVED,
                        Map.of("nodeVersion", (double) node.version(), "snapshotVersion", (double) snapshot.version()),
                        List.of(Strategy.SAFE_HIDE));
            }
            var declaration = node.declaration();
            var bounds = node.resolved();
            var c = declaration.constraintsFor(snapshot.viewport().width());
            if (bounds.width() <= 0 || bounds.height() <= 0) {
                add(issues, node, Rule.DIMENSIONS, "ILLEGAL_SIZE", Evidence.MODEL,
                        Map.of("width", bounds.width(), "height", bounds.height()), List.of(Strategy.SAFE_HIDE));
            }
            if (bounds.width() + .001 < c.minW() || bounds.height() + .001 < c.minH()) {
                add(issues, node, Rule.MINIMUM_SIZE, "MINIMUM_SIZE", Evidence.MODEL,
                        Map.of("width", bounds.width(), "height", bounds.height(), "minimumWidth", c.minW(), "minimumHeight", c.minH()),
                        List.of(Strategy.SIZE, Strategy.SAFE_HIDE));
            }
            if (declaration.state().visible() && c.respectSafeArea()) {
                if (!snapshot.viewport().contains(bounds)) {
                    add(issues, node, Rule.SAFE_AREA, "OUTSIDE_VIEWPORT", Evidence.MODEL,
                            Map.of("x", bounds.x(), "y", bounds.y()), List.of(Strategy.POSITION, Strategy.SIZE, Strategy.SAFE_HIDE));
                }
                for (var area : snapshot.safeAreas()) {
                    if (bounds.intersects(area.bounds())) {
                        var overlap = bounds.intersection(area.bounds());
                        add(issues, node, Rule.SAFE_AREA, "SAFE_AREA", area.evidence(),
                                Map.of("overlapArea", overlap.width() * overlap.height(), "observedFrame", (double) area.frameId()),
                                List.of(Strategy.SAFE_AREA, Strategy.SAFE_HIDE));
                    }
                }
            }
            if (node.renderable() && declaration.interactionPolicy().critical() && node.visible().width() * node.visible().height() == 0) {
                add(issues, node, Rule.VISIBLE, "CRITICAL_INVISIBLE", Evidence.MODEL, Map.of(), List.of(Strategy.OCCLUSION, Strategy.POSITION));
            }
            if (node.interactiveEnabled() && (!node.renderable() || !declaration.state().enabled()
                    || !declaration.interactionPolicy().allowIndependentHitGeometry() && !node.visible().contains(node.interactive()))) {
                add(issues, node, Rule.INTERACTION, "HIT_GEOMETRY", Evidence.MODEL, Map.of(), List.of(Strategy.OCCLUSION, Strategy.SAFE_HIDE));
            }
            if (!declaration.text().translationKey().isEmpty()) {
                var measured = node.measurement();
                if (measured.resourceVersion() != snapshot.resourceVersion()) {
                    add(issues, node, Rule.RESOURCE_VALIDITY, "STALE_MEASUREMENT", Evidence.OBSERVED,
                            Map.of("measurementVersion", (double) measured.resourceVersion(), "resourceVersion", (double) snapshot.resourceVersion()),
                            List.of(Strategy.TEXT_OVERFLOW, Strategy.SAFE_HIDE));
                }
                double padding = 2 * declaration.padding();
                if (measured.width() > Math.max(0, bounds.width() - padding) + .001
                        || measured.height() > Math.max(0, bounds.height() - padding) + .001) {
                    add(issues, node, Rule.TEXT_OVERFLOW, "TEXT_OVERFLOW", measured.measured() ? Evidence.OBSERVED : Evidence.MODEL,
                            Map.of("textWidth", measured.width(), "textHeight", measured.height(),
                                    "availableWidth", Math.max(0, bounds.width() - padding), "availableHeight", Math.max(0, bounds.height() - padding)),
                            List.of(Strategy.TEXT_OVERFLOW, Strategy.SIZE));
                }
                var legibility = node.legibility();
                if (legibility.valid() && legibility.worstRatio() + .001 < node.requiredContrast()) {
                    add(issues, node, Rule.LEGIBILITY, "LOW_CONTRAST", legibility.pixelObserved() ? Evidence.PIXELS : Evidence.MODEL,
                            Map.of("sampledMinimum", legibility.worstRatio(), "target", node.requiredContrast()),
                            List.of(Strategy.MATERIAL));
                }
                if (!legibility.valid() && (declaration.interactionPolicy().critical() || declaration.contract().hard().contains(Rule.LEGIBILITY))) {
                    add(issues, node, Rule.LEGIBILITY, "LEGIBILITY_UNVERIFIED", Evidence.LIMITATION, Map.of(), List.of(Strategy.MATERIAL, Strategy.SAFE_HIDE));
                }
                if (!legibility.valid() || node.background().validity() != VisualIntelligence.Validity.VALID) {
                    issues.add(new Issue("BACKGROUND_UNAVAILABLE/" + node.id(), List.of(node.id()), "BACKGROUND_UNAVAILABLE",
                            Severity.INFORMATION, Evidence.LIMITATION, Map.of(), List.of(Strategy.MATERIAL),
                            "sh_better_ui.correctness.issue.background_unavailable", false));
                }
            }
        }
        // Row bins avoid testing every pair for sparse HUDs; huge bounds use a bounded fallback lane.
        var rows = new HashMap<Integer, List<UISnapshot.Node>>();
        var global = new ArrayList<UISnapshot.Node>();
        var checked = new HashSet<String>();
        for (var node : nodes) {
            if (!node.renderable()) continue;
            long firstLong = (long) Math.floor(node.resolved().y() / 32), lastLong = (long) Math.floor(node.resolved().bottom() / 32);
            var candidates = new LinkedHashSet<>(global);
            if (firstLong < Integer.MIN_VALUE || lastLong > Integer.MAX_VALUE
                    || lastLong - firstLong < 0 || lastLong - firstLong > 128) {
                for (var row : rows.values()) candidates.addAll(row);
                global.add(node);
            } else {
                for (long row = firstLong; row <= lastLong; row++) candidates.addAll(rows.getOrDefault((int) row, List.of()));
                for (long row = firstLong; row <= lastLong; row++) rows.computeIfAbsent((int) row, ignored -> new ArrayList<>()).add(node);
            }
            for (var other : candidates) {
                if (!node.resolved().intersects(other.resolved()) || related(snapshot, node, other)) continue;
                var pair = other.id() + "/" + node.id();
                if (!checked.add(pair)) continue;
                var overlap = node.resolved().intersection(other.resolved());
                if (node.declaration().contract().hard().contains(Rule.NO_OVERLAP)
                        || other.declaration().contract().hard().contains(Rule.NO_OVERLAP)
                        || node.declaration().constraints().avoidOverlap() && other.declaration().constraints().avoidOverlap()) {
                    var level = node.declaration().contract().hard().contains(Rule.NO_OVERLAP)
                            || other.declaration().contract().hard().contains(Rule.NO_OVERLAP) ? Severity.HARD : Severity.SOFT;
                    issues.add(new Issue("OVERLAP/" + pair, List.of(other.id(), node.id()), "OVERLAP", level, Evidence.MODEL,
                            Map.of("overlapArea", overlap.width() * overlap.height()), List.of(Strategy.POSITION, Strategy.SIZE),
                            "sh_better_ui.correctness.issue.overlap", false));
                }
                var front = front(node, other);
                var back = front == node ? other : node;
                if (front.declaration().interactionPolicy().blocksInput() && back.interactiveEnabled()) {
                    issues.add(new Issue("INPUT_OCCLUDED/" + pair, List.of(back.id(), front.id()), "INPUT_OCCLUDED",
                            Severity.HARD, Evidence.MODEL, Map.of("overlapArea", overlap.width() * overlap.height()),
                            List.of(Strategy.OCCLUSION, Strategy.POSITION), "sh_better_ui.correctness.issue.input_occluded", false));
                }
            }
        }
        return new ValidationReport(snapshot.version(), issues, List.of(),
                List.of("sh_better_ui.correctness.limitation.external_geometry",
                        "sh_better_ui.correctness.limitation.shadow_model",
                        "sh_better_ui.correctness.limitation.native_observation"), 1);
    }

    private static UISnapshot.Node front(UISnapshot.Node a, UISnapshot.Node b) {
        int z = Integer.compare(a.declaration().visualPolicy().z(), b.declaration().visualPolicy().z());
        return z > 0 || z == 0 && a.id().toString().compareTo(b.id().toString()) > 0 ? a : b;
    }

    private static boolean related(UISnapshot snapshot, UISnapshot.Node a, UISnapshot.Node b) {
        return ancestor(snapshot, a, b.id()) || ancestor(snapshot, b, a.id());
    }

    private static boolean ancestor(UISnapshot snapshot, UISnapshot.Node node, UiId id) {
        for (int depth = 0; node != null && depth < 32; depth++) {
            var parent = node.declaration().parent();
            if (parent == null) return false;
            if (parent.equals(id)) return true;
            node = snapshot.nodes().get(parent);
        }
        return false;
    }

    private static void add(List<Issue> issues, UISnapshot.Node node, Rule rule, String code, Evidence evidence,
                            Map<String, Double> measurements, List<Strategy> strategies) {
        boolean mandatory = rule == Rule.DIMENSIONS || rule == Rule.INTERACTION || rule == Rule.RESOURCE_VALIDITY
                || rule == Rule.LEGIBILITY && node.declaration().interactionPolicy().critical();
        var contract = node.declaration().contract();
        if (!mandatory && !contract.requires(rule) && !(rule == Rule.VISIBLE && node.declaration().interactionPolicy().critical())) return;
        var level = mandatory || contract.hard().contains(rule) || rule == Rule.VISIBLE && node.declaration().interactionPolicy().critical()
                ? Severity.HARD : Severity.SOFT;
        issues.add(new Issue(code + "/" + node.id() + "/" + issues.size(), List.of(node.id()), code, level,
                evidence, measurements, strategies, "sh_better_ui.correctness.issue." + code.toLowerCase(Locale.ROOT), false));
    }
}
