// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.function.UnaryOperator;

/** Single-use owner-thread state transaction. */
public interface StateTransaction {
    <T> StateTransaction set(ScopeHandle scope, StateKey<T> key, T value);

    /** The updater receives a defensive copy and must be pure. */
    <T> StateTransaction update(ScopeHandle scope, StateKey<T> key, UnaryOperator<T> updater);

    long commit();
}
