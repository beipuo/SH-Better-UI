// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Objects;

/** The module and source assigned to a registration by the host. */
public record ServiceOrigin(UiId moduleId, String source) {
    public ServiceOrigin {
        Objects.requireNonNull(moduleId, "moduleId");
        if (Objects.requireNonNull(source, "source").isBlank()) {
            throw new IllegalArgumentException("The module source must identify its origin");
        }
    }
}
