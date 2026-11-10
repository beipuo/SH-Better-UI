// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

/** A delivery view valid during a single listener callback. Scope references carry no lifetime authority. */
public interface UIEvent<T> {
    T payload();

    UiId source();

    ScopeHandle target();

    ScopeHandle currentTarget();

    UIEventPhase phase();

    /** Stops traversal after the current scope. */
    void stopPropagation();

    /** Also stops remaining listeners at the current scope. */
    void stopImmediatePropagation();

    /** Prevents the kernel default of a cancelable event while leaving propagation intact. */
    void preventDefault();

    /** Records handling without changing delivery or defaults. */
    void markHandled();

    /** Ends delivery. For native input, requires permission to request vanilla blocking. */
    void consume();
}
