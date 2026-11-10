// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Objects;

public record UIEventType<T>(UiId id, Class<T> type, boolean cancelable) {
    public UIEventType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
    }
}
