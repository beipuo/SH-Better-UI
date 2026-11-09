// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.context;

import com.dishanhai.sh_better_ui.theme.transition.TransitionSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Caller-thread-confined environment rule resolver with explicit temporal hysteresis.
 * Call resolve every frame/tick even with no changed keys: pending deadlines advance
 * without rerunning predicates. A runtime exception disables only that evaluation and
 * is exposed as a diagnostic; that rule retries on its next dependency change.
 */
public final class ThemeRuleResolver {
    private static final Comparator<State> ORDER = Comparator.comparingInt((State state) -> state.rule.priority())
            .thenComparing(state -> state.rule.id());
    private final LongSupplier clock;
    private final Map<String, State> states = new LinkedHashMap<>();
    private final Map<String, Diagnostic> failures = new LinkedHashMap<>();
    private ContextSnapshot previous;
    private Selection selection = new Selection(List.of(), TransitionSpec.immediate(), Set.of());
    private long lastNow = Long.MIN_VALUE;

    /** Creates a resolver using monotonic system milliseconds. */
    public ThemeRuleResolver() { this(() -> System.nanoTime() / 1_000_000L); }

    /**
     * Creates a resolver using a supplied millisecond clock, useful for deterministic tests.
     * Clock regressions are clamped to the last observed time.
     *
     * @param clock millisecond time source
     */
    public ThemeRuleResolver(LongSupplier clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

    /**
     * Registers or replaces a rule, restarting that rule's evaluation and timers.
     * Re-registering the identical rule instance preserves its state.
     *
     * @param rule immutable rule configuration
     */
    public void register(ThemeRule rule) {
        Objects.requireNonNull(rule, "rule");
        State existing = states.get(rule.id());
        if (existing == null || existing.rule != rule) {
            states.put(rule.id(), new State(rule));
            failures.remove(rule.id());
        }
    }

    /**
     * Removes a rule; its active layer disappears at the next resolve call.
     *
     * @param id namespaced rule identifier
     * @return whether a rule existed
     */
    public boolean remove(String id) {
        failures.remove(id);
        return states.remove(id) != null;
    }

    /**
     * Atomically replaces registrations after validating duplicate identifiers.
     * Existing identical rule instances retain their state and enabled override.
     *
     * @param rules complete desired rule collection
     */
    public void setRules(Collection<ThemeRule> rules) {
        Objects.requireNonNull(rules, "rules");
        var replacement = new LinkedHashMap<String, State>();
        for (ThemeRule rule : rules) {
            Objects.requireNonNull(rule, "rule");
            State existing = states.get(rule.id());
            State next = existing != null && existing.rule == rule ? existing : new State(rule);
            if (replacement.putIfAbsent(rule.id(), next) != null) throw new IllegalArgumentException("Duplicate rule id: " + rule.id());
        }
        failures.entrySet().removeIf(entry -> replacement.get(entry.getKey()) != states.get(entry.getKey()));
        states.clear();
        states.putAll(replacement);
    }

    /**
     * Overrides a rule's enabled state. Disabling bypasses minimum-active hysteresis
     * on the next resolve; enabling starts a fresh matching/stability period.
     *
     * @param id rule identifier
     * @param enabled desired enabled state
     * @return whether the rule exists
     */
    public boolean setEnabled(String id, boolean enabled) {
        State state = states.get(id);
        if (state == null) return false;
        if (state.enabled != enabled) {
            state.enabled = enabled;
            state.dirty = true;
            state.evaluated = false;
            state.active = false;
            state.raw = false;
            state.failed = false;
            failures.remove(id);
        }
        return true;
    }

    /** @return the immutable union of dependencies of enabled rules */
    public Set<ContextKey<?>> dependencies() {
        var dependencies = new LinkedHashSet<ContextKey<?>>();
        for (State state : states.values()) if (state.enabled) dependencies.addAll(state.rule.condition().dependencies());
        return Collections.unmodifiableSet(dependencies);
    }

    /** @return the last immutable selection */
    public Selection selection() { return selection; }

    /** True when a stability/debounce timer still needs to advance without a new capture. */
    public boolean pending() {
        for (State state : states.values()) {
            if (state.enabled && !state.failed && state.raw != state.active) return true;
        }
        return false;
    }

    /**
     * Compares relevant effective values automatically and advances pending timers.
     * Snapshot versions alone are deliberately not trusted as a change signal.
     *
     * @param context current immutable environment
     * @return the resolved immutable selection
     */
    public Selection resolve(ContextSnapshot context) {
        Objects.requireNonNull(context, "context");
        var changed = new LinkedHashSet<ContextKey<?>>();
        for (ContextKey<?> key : dependencies()) {
            if (previous == null || !Objects.equals(previous.data().getOrDefault(key.id(), key.fallback()),
                    context.data().getOrDefault(key.id(), key.fallback()))) changed.add(key);
        }
        return resolve(context, changed);
    }

    /**
     * Reevaluates only new/changed rules and those depending on the supplied changed keys.
     * Pending deadlines are checked on every call regardless of environment changes.
     *
     * @param context current immutable environment
     * @param changedKeys complete set of relevant changes since the previous resolve
     * @return selection ordered from lowest to highest priority; an empty list delegates fallback to the owner
     */
    public Selection resolve(ContextSnapshot context, Set<ContextKey<?>> changedKeys) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(changedKeys, "changedKeys");
        long now = Math.max(lastNow, clock.getAsLong());
        lastNow = now;
        var ordered = new ArrayList<>(states.values());
        ordered.sort(ORDER);
        for (State state : ordered) {
            if (!state.enabled) {
                state.active = false;
                state.raw = false;
                state.dirty = false;
                continue;
            }
            if (state.dirty || !state.evaluated || !Collections.disjoint(state.rule.condition().dependencies(), changedKeys)) {
                evaluate(state, context, now);
            }
            if (!state.failed && state.raw != state.active && mature(state, now)) {
                state.active = state.raw;
                if (state.active) state.activeSince = now;
            }
        }
        previous = context;
        compose(ordered);
        return selection;
    }

    private void evaluate(State state, ContextSnapshot context, long now) {
        state.quietSince = now;
        state.dirty = false;
        try {
            boolean matched = state.rule.condition().test(context);
            if (!state.evaluated || matched != state.raw) state.rawSince = now;
            state.raw = matched;
            state.failed = false;
            failures.remove(state.rule.id());
        } catch (RuntimeException failure) {
            state.failed = true;
            state.raw = false;
            state.active = false;
            failures.put(state.rule.id(), new Diagnostic(state.rule.id(), now, failure.getClass().getName(),
                    Objects.toString(failure.getMessage(), "")));
        }
        state.evaluated = true;
    }

    private static boolean mature(State state, long now) {
        if (elapsed(now, state.quietSince) < state.rule.debounceMillis()) return false;
        return state.raw ? elapsed(now, state.rawSince) >= state.rule.stabilityMillis()
                : elapsed(now, state.activeSince) >= state.rule.minActiveMillis();
    }

    private static long elapsed(long now, long since) {
        long difference = now - since;
        return difference < 0 && now >= since ? Long.MAX_VALUE : Math.max(0, difference);
    }

    private void compose(List<State> ordered) {
        var themes = new LinkedHashSet<String>();
        var active = new LinkedHashSet<String>();
        TransitionSpec transition = selection.transition();
        for (State state : ordered) if (state.active) {
            // Last occurrence owns precedence when several rules select the same theme.
            themes.remove(state.rule.themeId());
            themes.add(state.rule.themeId());
            active.add(state.rule.id());
            transition = state.rule.transition();
        }
        var ids = List.copyOf(themes);
        if (!selection.themeIds().equals(ids) || !selection.activeRuleIds().equals(active)
                || !selection.transition().equals(transition)) {
            selection = new Selection(ids, transition, active);
        }
    }

    /**
     * Returns immutable per-rule state without invoking predicates or advancing time.
     *
     * @return states keyed by rule identifier
     */
    public Map<String, DebugState> debugStates() {
        var result = new LinkedHashMap<String, DebugState>();
        for (State state : states.values()) {
            result.put(state.rule.id(), new DebugState(state.enabled, state.evaluated, state.raw, state.active,
                    state.enabled && !state.failed && state.evaluated && state.raw != state.active,
                    state.failed, state.rawSince, state.quietSince, state.activeSince));
        }
        return Collections.unmodifiableMap(result);
    }

    /** @return an immutable copy of current single-rule evaluation failures */
    public List<Diagnostic> diagnostics() { return List.copyOf(failures.values()); }

    /**
     * Immutable result of theme rule composition.
     *
     * @param themeIds deduplicated theme identifiers in low-to-high precedence order
     * @param transition highest active rule's transition, retained when all rules leave
     * @param activeRuleIds all active rule identifiers, including duplicate-theme contributors
     */
    public record Selection(List<String> themeIds, TransitionSpec transition, Set<String> activeRuleIds) {
        /** Defensively copies all collections and validates the transition. */
        public Selection {
            themeIds = List.copyOf(themeIds);
            Objects.requireNonNull(transition, "transition");
            activeRuleIds = Collections.unmodifiableSet(new LinkedHashSet<>(activeRuleIds));
        }
    }

    /**
     * Immutable rule state for diagnostics.
     *
     * @param enabled effective enabled state
     * @param evaluated whether the condition has been evaluated
     * @param matching latest raw condition result
     * @param active whether the theme currently contributes
     * @param pending whether hysteresis is delaying a different target state
     * @param failed whether the last condition evaluation threw
     * @param matchingSinceMillis clock time of the last raw-condition change
     * @param quietSinceMillis clock time of the last relevant dependency evaluation
     * @param activeSinceMillis clock time of the most recent activation
     */
    public record DebugState(boolean enabled, boolean evaluated, boolean matching, boolean active,
                             boolean pending, boolean failed, long matchingSinceMillis,
                             long quietSinceMillis, long activeSinceMillis) { }

    /**
     * Immutable isolated failure diagnostic, containing no mutable throwable.
     *
     * @param ruleId failed rule identifier
     * @param atMillis failure clock time
     * @param exceptionType fully qualified exception class name
     * @param message diagnostic exception message, never intended as user-facing UI text
     */
    public record Diagnostic(String ruleId, long atMillis, String exceptionType, String message) { }

    private static final class State {
        private final ThemeRule rule;
        private boolean enabled;
        private boolean dirty = true;
        private boolean evaluated;
        private boolean raw;
        private boolean active;
        private boolean failed;
        private long rawSince;
        private long quietSince;
        private long activeSince;

        private State(ThemeRule rule) { this.rule = rule; enabled = rule.enabled(); }
    }
}
