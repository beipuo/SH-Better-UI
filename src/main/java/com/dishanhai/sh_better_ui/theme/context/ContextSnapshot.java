// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.context;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable data captured at a monotonically assigned environment version.
 * Nested maps with string keys, lists and sets are recursively copied. Only known
 * immutable scalar classes are accepted; mutable leaves, arrays, nulls and cycles
 * are rejected rather than leaking live game objects into a snapshot.
 *
 * @param version nonnegative capture version
 * @param data namespaced field identifiers and their captured values
 */
public record ContextSnapshot(long version, Map<String, Object> data) {
    private static final ContextSnapshot EMPTY = new ContextSnapshot(0, Map.of());

    /**
     * Copies and validates snapshot data.
     *
     * @param version nonnegative capture version
     * @param data captured field values, recursively frozen
     */
    public ContextSnapshot(long version, Map<String, Object> data) {
        if (version < 0) throw new IllegalArgumentException("Negative context version");
        Objects.requireNonNull(data, "data");
        var copy = new LinkedHashMap<String, Object>();
        data.forEach((id, value) -> copy.put(ContextKey.requireId(id), freeze(value)));
        this.version = version;
        this.data = Collections.unmodifiableMap(copy);
    }

    /** @return the shared empty version-zero snapshot */
    public static ContextSnapshot empty() { return EMPTY; }

    /**
     * Creates a version-zero snapshot from typed keys.
     *
     * @param values values keyed by their definitions
     * @return an immutable snapshot
     */
    public static ContextSnapshot of(Map<ContextKey<?>, ?> values) {
        Objects.requireNonNull(values, "values");
        var data = new LinkedHashMap<String, Object>();
        values.forEach((key, value) -> {
            Objects.requireNonNull(key, "key");
            if (!key.type().isInstance(value)) throw new IllegalArgumentException("Wrong context type: " + key.id());
            if (data.putIfAbsent(key.id(), value) != null) throw new IllegalArgumentException("Duplicate context id: " + key.id());
        });
        return new ContextSnapshot(0, data);
    }

    /**
     * Reads a field using its runtime type and fallback.
     *
     * @param key field definition
     * @param <T> field value type
     * @return captured value, or the immutable fallback when absent
     * @throws IllegalArgumentException if captured data has the wrong type
     */
    public <T> T get(ContextKey<T> key) {
        Objects.requireNonNull(key, "key");
        Object value = data.get(key.id());
        if (value == null) return key.fallback();
        if (!key.type().isInstance(value)) throw new IllegalArgumentException("Wrong context type: " + key.id());
        return key.type().cast(value);
    }

    /**
     * Tests physical presence independently of the field's fallback.
     *
     * @param key field definition
     * @return whether the snapshot explicitly contains the field
     */
    public boolean contains(ContextKey<?> key) { return data.containsKey(Objects.requireNonNull(key, "key").id()); }

    static Object freeze(Object value) { return freeze(value, new IdentityHashMap<>()); }

    private static Object freeze(Object value, IdentityHashMap<Object, Boolean> ancestors) {
        Objects.requireNonNull(value, "context value");
        Class<?> type = value.getClass();
        if (type == String.class || type == Boolean.class || type == Byte.class || type == Short.class
                || type == Integer.class || type == Long.class || type == Float.class || type == Double.class
                || type == Character.class || type == BigInteger.class || type == BigDecimal.class || type == UUID.class) {
            return value;
        }
        if (ancestors.put(value, Boolean.TRUE) != null) throw new IllegalArgumentException("Cyclic context container");
        try {
            if (value instanceof Map<?, ?> map) {
                var copy = new LinkedHashMap<String, Object>();
                map.forEach((key, entry) -> {
                    if (!(key instanceof String text)) throw new IllegalArgumentException("Context map keys must be strings");
                    copy.put(text, freeze(entry, ancestors));
                });
                return Collections.unmodifiableMap(copy);
            }
            if (value instanceof List<?> list) {
                var copy = new ArrayList<Object>();
                for (Object entry : list) copy.add(freeze(entry, ancestors));
                return Collections.unmodifiableList(copy);
            }
            if (value instanceof Set<?> set) {
                var copy = new LinkedHashSet<Object>();
                for (Object entry : set) copy.add(freeze(entry, ancestors));
                return Collections.unmodifiableSet(copy);
            }
            throw new IllegalArgumentException("Unsupported mutable context value: " + type.getName());
        } finally {
            ancestors.remove(value);
        }
    }
}
