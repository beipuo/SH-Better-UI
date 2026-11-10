// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.intelligence;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Explainable evidence. PREDICTED/MODEL/HEURISTIC never mean that the native result was measured. */
public record ValidationReport(long version, List<Issue> issues, List<Correction> corrections,
                               List<String> limitations, int revalidationCount) {
    public enum Severity { HARD, SOFT, PREFERENCE, INFORMATION }
    public enum Evidence { OBSERVED, PIXELS, PREDICTED, MODEL, HEURISTIC, LIMITATION }
    public enum Strategy { POSITION, SIZE, MATERIAL, OCCLUSION, MOTION_REPLAN, SAFE_AREA, TEXT_OVERFLOW, SAFE_HIDE }
    public record Issue(String id, List<UiId> nodes, String code, Severity severity, Evidence evidence,
                        Map<String, Double> measurements, List<Strategy> alternatives,
                        String explanationKey, boolean resolved) {
        public Issue {
            Objects.requireNonNull(id); Objects.requireNonNull(code); Objects.requireNonNull(severity);
            Objects.requireNonNull(evidence); Objects.requireNonNull(explanationKey);
            nodes = List.copyOf(nodes); measurements = Map.copyOf(measurements); alternatives = List.copyOf(alternatives);
        }
    }
    public record Correction(UiId nodeId, Strategy strategy, String reason, Map<String, Double> before,
                             Map<String, Double> after, boolean verified) {
        public Correction {
            Objects.requireNonNull(nodeId); Objects.requireNonNull(strategy); Objects.requireNonNull(reason);
            before = Map.copyOf(before); after = Map.copyOf(after);
        }
    }
    public ValidationReport {
        issues = List.copyOf(issues); corrections = List.copyOf(corrections); limitations = List.copyOf(limitations);
        if (revalidationCount < 0) throw new IllegalArgumentException("Negative revalidation count");
    }
    public List<Issue> unresolvedHard() {
        return issues.stream().filter(i -> i.severity == Severity.HARD && !i.resolved).toList();
    }
    public boolean valid() { return unresolvedHard().isEmpty(); }
}
