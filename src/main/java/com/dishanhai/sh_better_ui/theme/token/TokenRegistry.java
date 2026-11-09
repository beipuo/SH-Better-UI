// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.token;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Owner-thread token catalog. Immutable snapshots may be captured by resource workers. */
public final class TokenRegistry {
    private static final int MAX_TOKENS = 4096;
    private final Thread owner = Thread.currentThread();
    private volatile Map<String, ThemeToken<?>> snapshot = Map.of();

    /** Registers one definition and rejects conflicting definitions of the same ID. */
    public <T> ThemeToken<T> register(ThemeToken<T> token) {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Register tokens on the owner thread");
        Objects.requireNonNull(token);
        ThemeToken<?> current = snapshot.get(token.id());
        if (current != null) {
            if (!current.equals(token)) throw new IllegalArgumentException("Conflicting token ID: " + token.id());
            return token;
        }
        if (snapshot.size() >= MAX_TOKENS) throw new IllegalStateException("Theme token catalog is full");
        var next = new LinkedHashMap<>(snapshot);
        next.put(token.id(), token);
        snapshot = Collections.unmodifiableMap(next);
        return token;
    }

    /** Returns the current ordered, immutable catalog. */
    public Map<String, ThemeToken<?>> snapshot() { return snapshot; }
}
