// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.context;

import com.dishanhai.sh_better_ui.theme.transition.TransitionSpec;

import java.util.Objects;

/**
 * Immutable rule configuration. Matching themes are composed in ascending priority
 * and then identifier order, so the highest priority applies last.
 *
 * @param id namespaced rule identifier
 * @param condition pure predicate and declared dependencies
 * @param themeId namespaced theme identifier
 * @param priority composition priority; larger values override smaller values
 * @param transition transition requested when this rule has highest active priority
 * @param enabled initial enabled state
 * @param stabilityMillis continuous matching time required before entering
 * @param debounceMillis quiet time after any dependency change before entering or leaving
 * @param minActiveMillis minimum active time before a normal exit; disabling bypasses it
 */
public record ThemeRule(String id, ThemeCondition condition, String themeId, int priority,
                        TransitionSpec transition, boolean enabled, long stabilityMillis,
                        long debounceMillis, long minActiveMillis) {
    /** Validates identifiers and timings and freezes the condition's dependency declaration. */
    public ThemeRule {
        ContextKey.requireId(id);
        ContextKey.requireId(themeId);
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(transition, "transition");
        requireDuration(stabilityMillis);
        requireDuration(debounceMillis);
        requireDuration(minActiveMillis);
        condition = ThemeCondition.of(condition.dependencies(), condition::test);
    }

    /**
     * Starts a rule definition.
     *
     * @param id namespaced rule identifier
     * @return a builder requiring a condition and theme
     */
    public static Builder builder(String id) { return new Builder(ContextKey.requireId(id)); }

    private static long requireDuration(long millis) {
        if (millis < 0) throw new IllegalArgumentException("Rule durations must be nonnegative");
        return millis;
    }

    /** Configures an immutable theme rule. */
    public static final class Builder {
        private final String id;
        private ThemeCondition condition;
        private String themeId;
        private int priority;
        private TransitionSpec transition = TransitionSpec.immediate();
        private boolean enabled = true;
        private long stability;
        private long debounce;
        private long minActive;

        private Builder(String id) { this.id = id; }

        /**
         * Sets the matching condition.
         * @param condition pure predicate with all dependencies declared
         * @return this builder
         */
        public Builder when(ThemeCondition condition) { this.condition = Objects.requireNonNull(condition, "condition"); return this; }

        /**
         * Sets the theme composed by this rule.
         * @param themeId namespaced theme identifier
         * @return this builder
         */
        public Builder theme(String themeId) { this.themeId = ContextKey.requireId(themeId); return this; }

        /**
         * Sets the ascending composition priority.
         * @param priority larger values apply later
         * @return this builder
         */
        public Builder priority(int priority) { this.priority = priority; return this; }

        /**
         * Sets the transition used when this is the highest active rule.
         * @param transition immutable transition specification
         * @return this builder
         */
        public Builder transition(TransitionSpec transition) { this.transition = Objects.requireNonNull(transition, "transition"); return this; }

        /**
         * Sets the initial enabled state.
         * @param enabled whether the resolver initially considers this rule
         * @return this builder
         */
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }

        /**
         * Requires a continuously true predicate before entry; false resets this timer.
         * @param millis nonnegative duration in milliseconds
         * @return this builder
         */
        public Builder stability(long millis) { stability = requireDuration(millis); return this; }

        /**
         * Requires quiet dependencies before either entry or exit, even if the predicate stays equal.
         * @param millis nonnegative duration in milliseconds
         * @return this builder
         */
        public Builder debounce(long millis) { debounce = requireDuration(millis); return this; }

        /**
         * Prevents normal exits until the rule has remained active this long.
         * @param millis nonnegative duration in milliseconds
         * @return this builder
         */
        public Builder minActive(long millis) { minActive = requireDuration(millis); return this; }

        /**
         * Completes the rule definition.
         * @return immutable validated rule
         * @throws IllegalStateException if the condition or theme is missing
         */
        public ThemeRule build() {
            if (condition == null || themeId == null) throw new IllegalStateException("A rule requires a condition and theme");
            return new ThemeRule(id, condition, themeId, priority, transition, enabled, stability, debounce, minActive);
        }
    }
}
