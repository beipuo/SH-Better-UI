// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Objects;

/** A versioned static capability offer with the provider identity assigned by the kernel. */
public record CapabilityDescriptor(CapabilityKey key, UiVersion version, UiId provider,
                                   int priority, boolean fallback) {
    public CapabilityDescriptor {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(provider, "provider");
    }
}
