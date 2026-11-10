// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Objects;

/** Metadata declared by a service provider. The registrar attaches the verified source separately. */
public record ServiceDescriptor<T extends UIService>(ServiceKey<T> key, UiVersion providerVersion,
                                                     Visibility visibility, int priority, boolean replaceable) {
    public ServiceDescriptor {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(providerVersion, "providerVersion");
        Objects.requireNonNull(visibility, "visibility");
    }

    public enum Visibility {
        PUBLIC,
        MODULE_PRIVATE,
        KERNEL_PRIVATE
    }
}
