// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.Subscription;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Indexed subscriptions bound to both the issuer and the observed scope. Owner-thread access. */
final class ScopedSubscriptions<K, V> {
    private final Map<K, Bucket> byKey = new HashMap<>();
    private final Map<ScopeLifecycle.Scope, Set<Entry>> byScope = new HashMap<>();
    private final Runnable validationGuard;
    private final Comparator<Entry> order;

    ScopedSubscriptions(Runnable validationGuard) {
        this(validationGuard, null);
    }

    ScopedSubscriptions(Runnable validationGuard, Comparator<? super V> order) {
        this.validationGuard = validationGuard;
        this.order = order == null ? null : Comparator.comparing(Entry::listener, order);
    }

    Subscription add(K key, ScopeLifecycle.Scope issuer, ScopeLifecycle.Scope target, V listener) {
        issuer.assertOwner();
        var entry = new Entry(key, issuer, target, Objects.requireNonNull(listener, "listener"));
        var bucket = byKey.computeIfAbsent(key, ignored -> new Bucket());
        bucket.entries.add(entry);
        bucket.snapshot = null;
        add(byScope, issuer, entry);
        if (issuer != target) {
            add(byScope, target, entry);
        }
        return entry;
    }

    List<Entry> forKey(K key) {
        var bucket = byKey.get(key);
        return bucket == null ? List.of() : bucket.snapshot();
    }

    void remove(ScopeLifecycle.Scope scope) {
        List.copyOf(byScope.getOrDefault(scope, Set.of())).forEach(Entry::close);
    }

    void removeKey(K key) {
        forKey(key).forEach(Entry::close);
    }

    void removeForIssuer(ScopeLifecycle.Scope issuer, Predicate<ScopeLifecycle.Scope> denied) {
        for (Entry entry : List.copyOf(byScope.getOrDefault(issuer, Set.of()))) {
            if (entry.issuer == issuer && denied.test(entry.target)) {
                entry.close();
            }
        }
    }

    private static <K, V> void add(Map<K, Set<V>> index, K key, V value) {
        index.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(value);
    }

    private static <K, V> void remove(Map<K, Set<V>> index, K key, V value) {
        Set<V> values = index.get(key);
        values.remove(value);
        if (values.isEmpty()) {
            index.remove(key);
        }
    }

    private final class Bucket {
        final Set<Entry> entries = new LinkedHashSet<>();
        List<Entry> snapshot;

        List<Entry> snapshot() {
            if (snapshot == null) {
                if (order == null || entries.size() < 2) {
                    snapshot = List.copyOf(entries);
                } else {
                    var sorted = new ArrayList<>(entries);
                    sorted.sort(order);
                    snapshot = List.copyOf(sorted);
                }
            }
            return snapshot;
        }
    }

    final class Entry implements Subscription {
        private final K key;
        private final ScopeLifecycle.Scope issuer;
        private final ScopeLifecycle.Scope target;
        private volatile V listener;

        private Entry(K key, ScopeLifecycle.Scope issuer, ScopeLifecycle.Scope target, V listener) {
            this.key = key;
            this.issuer = issuer;
            this.target = target;
            this.listener = listener;
        }

        V listener() {
            return listener;
        }

        ScopeLifecycle.Scope target() {
            return target;
        }

        @Override
        public boolean isActive() {
            return listener != null && issuer.isOpen() && target.isOpen();
        }

        @Override
        public void close() {
            if (listener == null) {
                return;
            }
            issuer.assertOwner();
            validationGuard.run();
            listener = null;
            var bucket = byKey.get(key);
            bucket.entries.remove(this);
            bucket.snapshot = null;
            if (bucket.entries.isEmpty()) {
                byKey.remove(key);
            }
            remove(byScope, issuer, this);
            if (issuer != target) {
                remove(byScope, target, this);
            }
        }
    }
}
