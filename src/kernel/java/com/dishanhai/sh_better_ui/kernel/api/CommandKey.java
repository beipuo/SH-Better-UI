// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Objects;

public record CommandKey<T>(UiId id, Class<T> type) {
    public CommandKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
    }
}
