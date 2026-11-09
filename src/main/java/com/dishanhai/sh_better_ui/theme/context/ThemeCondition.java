// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.context;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A pure environment predicate with explicit dependencies. Implementations must not
 * read mutable external state: changes outside the declared keys do not trigger evaluation.
 */
public interface ThemeCondition {
    /** @return all keys read by this condition, including composed subconditions */
    Set<ContextKey<?>> dependencies();

    /**
     * Evaluates the captured environment.
     *
     * @param context immutable captured environment
     * @return whether this condition currently matches
     */
    boolean test(ContextSnapshot context);

    /**
     * Creates a condition with a defensive copy of its dependency declaration.
     *
     * @param dependencies all fields read by the predicate
     * @param predicate pure, synchronous condition
     * @return a condition with immutable dependencies
     */
    static ThemeCondition of(Set<ContextKey<?>> dependencies, Predicate<ContextSnapshot> predicate) {
        var keys = Set.copyOf(dependencies);
        Objects.requireNonNull(predicate, "predicate");
        return new ThemeCondition() {
            @Override public Set<ContextKey<?>> dependencies() { return keys; }
            @Override public boolean test(ContextSnapshot context) { return predicate.test(Objects.requireNonNull(context, "context")); }
        };
    }

    /**
     * Combines conditions using short-circuit AND.
     *
     * @param other second condition
     * @return a condition declaring the union of both dependency sets
     */
    default ThemeCondition and(ThemeCondition other) {
        Objects.requireNonNull(other, "other");
        var keys = new LinkedHashSet<>(dependencies());
        keys.addAll(other.dependencies());
        return of(keys, context -> test(context) && other.test(context));
    }

    /**
     * Combines conditions using short-circuit OR.
     *
     * @param other second condition
     * @return a condition declaring the union of both dependency sets
     */
    default ThemeCondition or(ThemeCondition other) {
        Objects.requireNonNull(other, "other");
        var keys = new LinkedHashSet<>(dependencies());
        keys.addAll(other.dependencies());
        return of(keys, context -> test(context) || other.test(context));
    }

    /** @return the negation of this condition with the same dependencies */
    default ThemeCondition not() { return of(dependencies(), context -> !test(context)); }
}
