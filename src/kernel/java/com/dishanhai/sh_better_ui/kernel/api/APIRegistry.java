// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/** Read-only access to services visible to the caller. */
public interface APIRegistry {
    <T extends UIService> Optional<T> find(ServiceKey<T> key);

    default <T extends UIService> T require(ServiceKey<T> key) {
        Objects.requireNonNull(key, "key");
        return find(key).orElseThrow(() -> new NoSuchElementException("UI service unavailable: "
                + key.id() + " (" + key.type().getName() + ", contract " + key.contractVersion() + ")"));
    }

    default Collection<ServiceDescriptor<?>> descriptors() {
        return snapshot().entries().stream().map(RegistrySnapshot.Entry::descriptor).toList();
    }

    RegistrySnapshot snapshot();
}
