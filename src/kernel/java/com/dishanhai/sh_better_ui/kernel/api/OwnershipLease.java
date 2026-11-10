// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceClaim;

/** An exclusive resource assignment owned by a live scope. */
public interface OwnershipLease extends AutoCloseable {
    enum Status { ACTIVE, QUEUED, RELEASED }

    ResourceClaim resource();

    UiId owner();

    long scopeGeneration();

    boolean isActive();

    default Status status() {
        return isActive() ? Status.ACTIVE : Status.RELEASED;
    }

    default int priority() {
        return 0;
    }

    default long lifetimeNanos() {
        return 0;
    }

    @Override
    void close();
}
