// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.intelligence;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static com.dishanhai.sh_better_ui.kernel.api.intelligence.ValidationReport.*;

/** Observed hit/focus state, kept separate from planned visual geometry. Unknown external interceptors stay unknown. */
public record UIInteractionSnapshot(long version, List<Node> nodes, boolean fullyObserved) {
    public record Node(UiId id, long renderedVersion, long hitVersion, boolean visible, boolean enabled,
                       boolean receivesInput, boolean occluded, boolean focused, boolean transparentInterception,
                       boolean intendedInteractive) {
        public Node { Objects.requireNonNull(id); }
        public Node(UiId id, long renderedVersion, long hitVersion, boolean visible, boolean enabled,
                     boolean receivesInput, boolean occluded, boolean focused, boolean transparentInterception) {
            this(id, renderedVersion, hitVersion, visible, enabled, receivesInput, occluded, focused, transparentInterception, true);
        }
    }
    public UIInteractionSnapshot {
        nodes = List.copyOf(nodes);
        if (version < 0 || nodes.size() > 256) throw new IllegalArgumentException("Invalid bounded interaction snapshot");
    }
    public ValidationReport validate() {
        var issues = new ArrayList<Issue>();
        var focused = new ArrayList<UiId>();
        for (var node : nodes) {
            if (node.renderedVersion != node.hitVersion && node.receivesInput) issue(issues, node, "STALE_HIT_VERSION", Severity.HARD);
            if ((!node.visible || !node.enabled) && node.receivesInput) issue(issues, node, "HIDDEN_INPUT", Severity.HARD);
            if (node.occluded && node.receivesInput) issue(issues, node, "OCCLUDED_INPUT", Severity.HARD);
            if (node.transparentInterception) issue(issues, node, "TRANSPARENT_INPUT_LAYER", Severity.HARD);
            if (node.visible && node.enabled && node.intendedInteractive && !node.receivesInput) issue(issues, node, "UNREACHABLE_CONTROL", Severity.SOFT);
            if (node.focused) {
                focused.add(node.id);
                if (!node.visible || !node.enabled || node.occluded) issue(issues, node, "INVALID_FOCUS", Severity.HARD);
            }
        }
        if (focused.size() > 1) issues.add(new Issue("FOCUS_CONFLICT", focused, "FOCUS_CONFLICT", Severity.HARD, Evidence.OBSERVED,
                Map.of("focusedCount", (double) focused.size()), List.of(Strategy.OCCLUSION), "sh_better_ui.correctness.issue.focus_conflict", false));
        return new ValidationReport(version, issues, List.of(), fullyObserved ? List.of() : List.of(
                "sh_better_ui.correctness.limitation.external_input"), 1);
    }
    private static void issue(List<Issue> issues, Node node, String code, Severity severity) {
        issues.add(new Issue(code + "/" + node.id, List.of(node.id), code, severity, Evidence.OBSERVED,
                Map.of("renderedVersion", (double) node.renderedVersion, "hitVersion", (double) node.hitVersion),
                List.of(Strategy.OCCLUSION, Strategy.SAFE_HIDE), "sh_better_ui.correctness.issue." + code.toLowerCase(java.util.Locale.ROOT), false));
    }
}
