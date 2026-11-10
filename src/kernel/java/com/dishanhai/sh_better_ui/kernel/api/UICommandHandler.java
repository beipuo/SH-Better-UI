// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

@FunctionalInterface
public interface UICommandHandler<T> {
    /** Pure preparation. The target is a reference and the transaction carries the caller's authority. */
    void prepare(ScopeHandle target, T payload, UITransaction transaction);
}
