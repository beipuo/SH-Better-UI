// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.function.Consumer;

public interface UIEventBus {
    <T> Subscription subscribe(UIEventType<T> type, ScopeHandle scope, UIEventPhase phase, int priority,
                               Consumer<UIEvent<T>> listener);

    /** Synchronous UI delivery with bounded recursion. */
    <T> EventResult emit(UIEventType<T> type, ScopeHandle target, T payload);

    /** Queues ordinary delivery in EVENT_DISPATCH. Use an immutable payload. */
    <T> TaskHandle post(UIEventType<T> type, ScopeHandle target, T payload);
}
