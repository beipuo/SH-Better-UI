// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.intelligence;

import java.util.Objects;
import java.util.Set;

/** Hard rules are never relaxed by material preferences or automatic placement. */
public record UICorrectnessContract(Set<Rule> hard, Set<Rule> soft, CorrectionPolicy correction) {
    public enum Rule { DIMENSIONS, MINIMUM_SIZE, SAFE_AREA, VISIBLE, NO_OVERLAP, LEGIBILITY,
        TEXT_OVERFLOW, INTERACTION, RESOURCE_VALIDITY }
    public record CorrectionPolicy(boolean enabled, boolean move, boolean resize, boolean material,
                                   boolean safeHide, int maxIterations, long budgetNanos) {
        public static final CorrectionPolicy DEFAULT = new CorrectionPolicy(true, true, true, true, true, 3, 2_000_000);
        public CorrectionPolicy {
            if (maxIterations < 1 || maxIterations > 8 || budgetNanos < 1 || budgetNanos > 100_000_000) {
                throw new IllegalArgumentException("Invalid correction budget");
            }
        }
    }
    public static final UICorrectnessContract ACCESSIBLE = new UICorrectnessContract(
            Set.of(Rule.DIMENSIONS, Rule.MINIMUM_SIZE, Rule.SAFE_AREA, Rule.INTERACTION, Rule.RESOURCE_VALIDITY),
            Set.of(Rule.NO_OVERLAP, Rule.LEGIBILITY, Rule.TEXT_OVERFLOW), CorrectionPolicy.DEFAULT);
    public UICorrectnessContract {
        hard = Set.copyOf(hard);
        soft = Set.copyOf(soft);
        Objects.requireNonNull(correction);
        if (hard.stream().anyMatch(soft::contains)) throw new IllegalArgumentException("Duplicate constraint level");
    }
    public boolean requires(Rule rule) { return hard.contains(rule) || soft.contains(rule); }
}
