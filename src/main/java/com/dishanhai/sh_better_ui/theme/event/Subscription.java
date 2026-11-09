// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.event;

/** Idempotent cancellation handle. Close on the thread that created the subscription. */
@FunctionalInterface
public interface Subscription extends AutoCloseable {
    @Override
    void close();
}
