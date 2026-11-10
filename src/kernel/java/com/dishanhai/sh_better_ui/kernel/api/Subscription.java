// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

/** A lifetime-bound observer. Closing it is idempotent on the UI thread. */
public interface Subscription extends AutoCloseable {
    boolean isActive();

    @Override
    void close();
}
