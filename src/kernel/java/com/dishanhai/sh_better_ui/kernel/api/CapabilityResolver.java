// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.List;

/** Queries the engine's current static capability offers. */
public interface CapabilityResolver {
    default boolean supports(CapabilityKey key) {
        return status(key) != CapabilityStatus.UNAVAILABLE;
    }

    CapabilityStatus status(CapabilityKey key);

    List<CapabilityDescriptor> available();

    CapabilityResolution resolve(CapabilityRequest request);
}
