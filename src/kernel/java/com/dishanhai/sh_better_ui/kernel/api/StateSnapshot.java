// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/** A detached state view. Derived functions receive only their declared inputs. */
public final class StateSnapshot {
    private final long version;
    private final Map<StateKey<?>, ?> values;

    public StateSnapshot(long version, Map<StateKey<?>, ?> values) {
        this.version = version;
        var snapshot = new IdentityHashMap<StateKey<?>, Object>();
        values.forEach((key, value) -> snapshot.put(key, key.copy(value)));
        this.values = snapshot;
    }

    public long version() {
        return version;
    }

    public <T> T get(StateKey<T> key) {
        if (!values.containsKey(key)) {
            throw new NoSuchElementException("State key absent from snapshot: " + key.id());
        }
        return key.copy(values.get(key));
    }
}
