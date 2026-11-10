// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Offers selected from one capability snapshot, with unmet constraints. */
public record CapabilityResolution(Map<CapabilityKey, CapabilityDescriptor> selected,
                                   Set<CapabilityKey> requiredMissing, Set<CapabilityKey> optionalMissing) {
    public CapabilityResolution {
        selected = Collections.unmodifiableMap(new TreeMap<>(Map.copyOf(selected)));
        requiredMissing = Collections.unmodifiableSet(new TreeSet<>(Set.copyOf(requiredMissing)));
        optionalMissing = Collections.unmodifiableSet(new TreeSet<>(Set.copyOf(optionalMissing)));
    }

    public CapabilityStatus status(CapabilityKey key) {
        var descriptor = selected.get(Objects.requireNonNull(key, "key"));
        if (descriptor == null) {
            return CapabilityStatus.UNAVAILABLE;
        }
        return descriptor.fallback() ? CapabilityStatus.DEGRADED : CapabilityStatus.AVAILABLE;
    }
}
