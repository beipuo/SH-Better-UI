// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Objects;

/** Identifies a capability independently of its providers and available versions. */
public record CapabilityKey(UiId id) implements Comparable<CapabilityKey> {
    public CapabilityKey {
        Objects.requireNonNull(id, "id");
    }

    @Override
    public int compareTo(CapabilityKey other) {
        return id.compareTo(other.id);
    }
}
