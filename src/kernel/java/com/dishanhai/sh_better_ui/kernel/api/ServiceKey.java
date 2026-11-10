// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Objects;

/** Identity of a service contract. Provider release versions belong to its descriptor. */
public record ServiceKey<T extends UIService>(UiId id, Class<T> type, int contractVersion) {
    public ServiceKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        if (!UIService.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("UI service type must implement UIService: " + type.getName());
        }
        if (contractVersion < 1) {
            throw new IllegalArgumentException("Service contract version must be positive: " + id);
        }
    }
}
