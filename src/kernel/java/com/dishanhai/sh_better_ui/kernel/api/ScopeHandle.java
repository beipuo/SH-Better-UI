// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceClaim;

/** A host-issued lifetime. Mutations and close callbacks run on the kernel owner thread. */
public interface ScopeHandle extends AutoCloseable {
    UiId id();

    UiId owner();

    ScopeKind kind();

    /** Unique within one engine. Remounting the same ID allocates a new generation. */
    long generation();

    boolean isOpen();

    ScopeHandle openChild(ScopeKind kind, UiId id);

    /** Tracks a cleanup by object identity. The scope runs it at most once. */
    void track(AutoCloseable resource);

    /** Claims an exclusive named resource until the lease or scope closes. */
    OwnershipLease claim(ResourceClaim resource, AutoCloseable release);

    /** Closes a child scope. The host closes engine and module roots. */
    @Override
    void close();
}
