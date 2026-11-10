// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.List;
import java.util.Objects;

/** Immutable, ordered service metadata. Instances are queried through the registry. */
public record RegistrySnapshot(long version, List<Entry> entries) {
    public RegistrySnapshot {
        entries = List.copyOf(entries);
    }

    public record Entry(ServiceDescriptor<?> descriptor, ServiceOrigin origin) {
        public Entry {
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(origin, "origin");
        }
    }
}
