// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.context;

import java.util.Objects;

/**
 * A typed, namespaced environment field with an immutable fallback.
 *
 * @param id stable namespaced identifier
 * @param type runtime value type; use collection interfaces rather than mutable concrete classes
 * @param fallback non-null value returned when the field is absent
 * @param source descriptive origin; this does not authenticate external data
 * @param <T> field value type
 */
public record ContextKey<T>(String id, Class<T> type, T fallback, Source source) {
    /** Constructs a validated key and defensively freezes its fallback. */
    public ContextKey {
        requireId(id);
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        Object frozen = ContextSnapshot.freeze(fallback);
        if (!type.isInstance(frozen)) {
            throw new IllegalArgumentException("Fallback does not match context type: " + id);
        }
        fallback = type.cast(frozen);
    }

    static String requireId(String id) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Expected a namespaced identifier: " + id);
        }
        return id;
    }

    /** Identifies the adapter responsible for supplying a field. */
    public enum Source {
        /** Local client settings and window state. */
        CLIENT,
        /** World or player state supplied by the game adapter. */
        SERVER,
        /** Values supplied by an optional integration. */
        EXTERNAL
    }
}
