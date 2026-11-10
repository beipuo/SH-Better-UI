// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Objects;

/** Command inputs should be immutable, including when submitted from another thread. */
public record UICommand<T>(CommandKey<T> key, ScopeHandle target, T payload) {
    public UICommand {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(target, "target");
        if (!key.type().isInstance(payload)) {
            throw new IllegalArgumentException("UI command " + key.id() + " requires " + key.type().getName());
        }
    }
}
