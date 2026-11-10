// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceClaim;

import java.util.Objects;

/** A scoped resource request. The lifetime starts at admission. Zero means until released. */
public record ResourceRequest(ResourceClaim resource, ScopeHandle scope, int priority, long lifetimeNanos,
                              ConflictPolicy policy) {
    public ResourceRequest {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(policy, "policy");
        if (lifetimeNanos < 0) {
            throw new IllegalArgumentException("UI resource lifetime must be nonnegative");
        }
    }
}
