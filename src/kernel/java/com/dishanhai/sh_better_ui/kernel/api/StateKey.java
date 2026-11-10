// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Objects;
import java.util.function.UnaryOperator;

/** A state schema and its lifetime. Share the registered key instance with consumers. */
public record StateKey<T>(UiId id, Class<T> type, T initialValue, Kind kind, UnaryOperator<T> copier) {
    public enum Kind { GLOBAL, SCREEN, COMPONENT, EPHEMERAL }

    public StateKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(copier, "copier");
        initialValue = typed(id, type, copier.apply(typed(id, type, initialValue)));
    }

    /** Uses identity copying for immutable values. */
    public static <T> StateKey<T> of(UiId id, Class<T> type, T initial, Kind kind) {
        return new StateKey<>(id, type, initial, kind, UnaryOperator.identity());
    }

    @Override
    public T initialValue() {
        return copy(initialValue);
    }

    /** A mutable value requires a pure, thread-safe defensive copier. */
    public T copy(Object value) {
        return typed(id, type, copier.apply(typed(id, type, value)));
    }

    private static <T> T typed(UiId id, Class<T> type, Object value) {
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("State " + id + " requires " + type.getName());
        }
        return type.cast(value);
    }
}
