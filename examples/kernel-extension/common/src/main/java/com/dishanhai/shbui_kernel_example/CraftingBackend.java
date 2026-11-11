// Author: dishanhai
package com.dishanhai.shbui_kernel_example;

/** Adapter boundary for an optional crafting integration. The example calls it on the client thread. */
@FunctionalInterface
public interface CraftingBackend {
    /** Starts a job for the requested quantity. Return promptly and poll external work through the job. */
    Job start(int quantity);

    interface Job extends AutoCloseable {
        /** Returns the number of completed units. A failed job may throw a RuntimeException. */
        int poll();
        /** Releases resources and cancels unfinished work when the backend supports cancellation. */
        @Override void close();
    }
}
