// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * A configuration schema. Consumers share the registered key instance.
 * Validators, migrations and copiers are pure. Copiers must also be safe on reader threads.
 */
public record ConfigKey<T>(UiId id, Class<T> type, T defaultValue, Set<ConfigLayer> layers,
                           Mutability mutability, Persistence persistence, int version, String description,
                           Predicate<? super T> validator, UnaryOperator<T> copier,
                           Map<Integer, UnaryOperator<T>> migrations) {
    public enum Mutability { STARTUP_ONLY, RUNTIME }

    public enum Persistence { SESSION, USER_GLOBAL }

    public ConfigKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(mutability, "mutability");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(validator, "validator");
        Objects.requireNonNull(copier, "copier");
        layers = Set.copyOf(layers);
        migrations = Map.copyOf(migrations);
        if (version < 1 || migrations.keySet().stream().anyMatch(from -> from < 1 || from >= version)
                || layers.contains(ConfigLayer.ENGINE_DEFAULTS)
                || persistence == Persistence.USER_GLOBAL && !layers.contains(ConfigLayer.USER_GLOBAL)) {
            throw new IllegalArgumentException("Invalid configuration schema for " + id);
        }
        defaultValue = typed(id, type, copier.apply(typed(id, type, defaultValue)));
        if (!validator.test(defaultValue)) {
            throw new IllegalArgumentException("Invalid configuration default for " + id);
        }
    }

    /** Suitable for immutable values such as strings, numbers, enums and immutable records. */
    public static <T> ConfigKey<T> of(UiId id, Class<T> type, T initial, Set<ConfigLayer> layers,
                                     Predicate<? super T> validator) {
        return new ConfigKey<>(id, type, initial, layers, Mutability.RUNTIME, Persistence.SESSION, 1,
                id.toString(), validator, UnaryOperator.identity(), Map.of());
    }

    @Override
    public T defaultValue() {
        return copy(defaultValue);
    }

    public T copy(Object value) {
        return typed(id, type, copier.apply(typed(id, type, value)));
    }

    public T validate(Object value) {
        T copy = copy(value);
        if (!validator.test(copy)) {
            throw new IllegalArgumentException("Invalid configuration value for " + id);
        }
        return copy;
    }

    /** Applies each same-type migration before validating the current schema. */
    public T migrate(int fromVersion, Object value) {
        if (fromVersion < 1 || fromVersion > version) {
            throw new IllegalArgumentException("Unsupported configuration version " + fromVersion + " for " + id);
        }
        T next = copy(value);
        for (int from = fromVersion; from < version; from++) {
            var migration = migrations.get(from);
            if (migration == null) {
                throw new IllegalArgumentException("Missing configuration migration from version " + from + " for " + id);
            }
            next = copy(migration.apply(next));
        }
        return validate(next);
    }

    private static <T> T typed(UiId id, Class<T> type, Object value) {
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("Configuration " + id + " requires " + type.getName());
        }
        return type.cast(value);
    }
}
