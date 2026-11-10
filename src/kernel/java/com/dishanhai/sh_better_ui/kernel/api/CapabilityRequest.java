// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Map;

/** Version constraints for capabilities needed by one operation. */
public record CapabilityRequest(Map<CapabilityKey, VersionRange> required,
                                Map<CapabilityKey, VersionRange> optional) {
    public CapabilityRequest {
        required = Map.copyOf(required);
        optional = Map.copyOf(optional);
        if (required.keySet().stream().anyMatch(optional::containsKey)) {
            throw new IllegalArgumentException("A capability cannot be both required and optional");
        }
    }
}
