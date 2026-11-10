// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

/** A module-bound submission view. Anonymous engine views expose scheduler observations. */
public interface UIScheduler {
    /** Thread-safe. Rejects invalid scopes and full queues before accepting the task. */
    TaskHandle submit(UITask task);

    SchedulerSnapshot snapshot();

    /** Returns the stable frame time on the UI thread and the monotonic clock elsewhere. */
    long nowNanos();

    boolean isOnUIThread();

    default void assertUIThread() {
        if (!isOnUIThread()) {
            throw new IllegalStateException("UI scheduler operation requires the owner thread");
        }
    }
}
