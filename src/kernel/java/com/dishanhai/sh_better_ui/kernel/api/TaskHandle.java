// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

public interface TaskHandle {
    enum Status {
        PENDING, RUNNING, SUCCEEDED, CANCELLED, TIMED_OUT, FAILED;

        public boolean isTerminal() {
            return this != PENDING && this != RUNNING;
        }
    }

    long sequence();

    Status status();

    /** Idempotent. Running worker computations can observe cancellation cooperatively. */
    boolean cancel();
}
