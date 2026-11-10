// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public interface UIStateStore {
    void define(StateKey<?> key);

    /** Binds a pure computation at this scope. Inputs must be global or available at the same scope. */
    <T> void derive(ScopeHandle scope, StateKey<T> key, Set<StateKey<?>> dependencies,
                    Function<StateSnapshot, T> compute);

    <T> T get(StateKey<T> key);

    <T> T get(ScopeHandle scope, StateKey<T> key);

    <T> void update(StateKey<T> key, UnaryOperator<T> updater);

    StateSnapshot snapshot();

    StateSnapshot snapshot(ScopeHandle scope);

    StateTransaction beginTransaction();

    <T> Subscription observe(ScopeHandle scope, StateKey<T> key, Consumer<ValueChange<T>> listener);
}
