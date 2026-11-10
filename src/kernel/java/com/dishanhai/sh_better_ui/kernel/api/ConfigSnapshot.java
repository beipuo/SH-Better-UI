// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/** Effective values at one scope and publication version. */
public final class ConfigSnapshot {
    private final long version;
    private final Map<ConfigKey<?>, ?> values;

    public ConfigSnapshot(long version, Map<ConfigKey<?>, ?> values) {
        this.version = version;
        var snapshot = new IdentityHashMap<ConfigKey<?>, Object>();
        values.forEach((key, value) -> snapshot.put(key, key.copy(value)));
        this.values = snapshot;
    }

    public long version() {
        return version;
    }

    public <T> T get(ConfigKey<T> key) {
        if (!values.containsKey(key)) {
            throw new NoSuchElementException("Configuration key absent from snapshot: " + key.id());
        }
        return key.copy(values.get(key));
    }
}
