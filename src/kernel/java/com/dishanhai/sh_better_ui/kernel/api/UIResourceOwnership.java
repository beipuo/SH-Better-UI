// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

public interface UIResourceOwnership {
    /**
     * Acquires a declared resource on the UI thread. The release callback belongs to an accepted
     * lease, including while queued. RETARGET of a live animation returns its existing lease and
     * keeps the original release callback.
     */
    OwnershipLease acquire(ResourceRequest request, AutoCloseable release);
}
