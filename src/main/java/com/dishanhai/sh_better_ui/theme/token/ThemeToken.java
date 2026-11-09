// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.token;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable namespaced, typed token key with an immutable fallback value. */
public record ThemeToken<T>(String id, TokenType<T> type, T defaultValue) {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-zA-Z0-9_./-]+");

    public ThemeToken {
        Objects.requireNonNull(id);
        Objects.requireNonNull(type);
        if (id.length() > 256 || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Expected namespaced token ID: " + id);
        }
        type.cast(defaultValue);
    }

    @Override
    public String toString() { return id; }
}
