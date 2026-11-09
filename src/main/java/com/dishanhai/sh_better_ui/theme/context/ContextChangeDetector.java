// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.context;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Projects captures onto relevant keys and versions only their effective changes.
 * Missing fields compare equal to their key fallback. This class is caller-thread confined.
 */
public final class ContextChangeDetector {
    private Set<ContextKey<?>> relevantKeys;
    private ContextSnapshot snapshot = ContextSnapshot.empty();

    /**
     * Creates a detector for the declared dependencies.
     *
     * @param relevantKeys fields that may change the effective environment
     */
    public ContextChangeDetector(Set<ContextKey<?>> relevantKeys) { setRelevantKeys(relevantKeys); }

    /**
     * Replaces the projection; the next update compares newly relevant keys against fallbacks.
     *
     * @param keys immutable-copy source of relevant fields
     */
    public void setRelevantKeys(Set<ContextKey<?>> keys) {
        var ids = new LinkedHashSet<String>();
        for (ContextKey<?> key : Objects.requireNonNull(keys, "keys")) {
            if (!ids.add(key.id())) throw new IllegalArgumentException("Duplicate context id: " + key.id());
        }
        relevantKeys = Set.copyOf(keys);
    }

    /** @return the immutable set of currently observed dependencies */
    public Set<ContextKey<?>> relevantKeys() { return relevantKeys; }

    /** @return the last effective snapshot, initially empty */
    public ContextSnapshot snapshot() { return snapshot; }

    /**
     * Captures once and detects relevant changes.
     *
     * @param provider environment adapter
     * @return changed keys and the effective snapshot
     */
    public Change capture(EnvironmentProvider provider) { return update(Objects.requireNonNull(provider, "provider").capture()); }

    /**
     * Updates from a capture, ignoring its version and unrelated fields.
     *
     * @param captured immutable source snapshot
     * @return a reused snapshot reference when no relevant effective value changed
     */
    public Change update(ContextSnapshot captured) {
        Objects.requireNonNull(captured, "captured");
        LinkedHashSet<ContextKey<?>> changed = null;
        for (ContextKey<?> key : relevantKeys) {
            Object value = captured.data().getOrDefault(key.id(), key.fallback());
            if (!Objects.equals(snapshot.data().getOrDefault(key.id(), key.fallback()), value)) {
                if (changed == null) changed = new LinkedHashSet<>();
                changed.add(key);
            }
        }
        if (changed == null) return new Change(snapshot, Set.of());
        var values = new LinkedHashMap<String, Object>();
        for (ContextKey<?> key : relevantKeys) values.put(key.id(), captured.data().getOrDefault(key.id(), key.fallback()));
        snapshot = new ContextSnapshot(Math.incrementExact(snapshot.version()), values);
        return new Change(snapshot, changed);
    }

    /**
     * Immutable result of one environment observation.
     *
     * @param snapshot effective snapshot
     * @param changedKeys fields whose effective values changed
     */
    public record Change(ContextSnapshot snapshot, Set<ContextKey<?>> changedKeys) {
        /** Copies the changed-key set and rejects null snapshots. */
        public Change {
            Objects.requireNonNull(snapshot, "snapshot");
            changedKeys = Set.copyOf(changedKeys);
        }
    }
}
