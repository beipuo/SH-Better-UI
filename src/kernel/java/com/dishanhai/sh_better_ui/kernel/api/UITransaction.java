// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Set;
import java.util.function.UnaryOperator;

/** A staging view valid during a command's preparation callback. */
public interface UITransaction {
    <T> UITransaction set(ScopeHandle scope, StateKey<T> key, T value);

    <T> UITransaction update(ScopeHandle scope, StateKey<T> key, UnaryOperator<T> updater);

    UITransaction invalidate(ScopeHandle node, Set<UIDirtyFlag> flags);

    /** Stages independent work for after commit. Its result and failures have a separate receipt. */
    UITransaction afterCommit(UITask effect);

    default UITransaction requestAnimation(ScopeHandle node, Runnable animation) {
        return afterCommit(UITask.ui(node, UIPhase.ANIMATION, UIPriority.ANIMATION, animation));
    }
}
