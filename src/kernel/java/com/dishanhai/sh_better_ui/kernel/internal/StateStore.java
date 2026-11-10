// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

final class StateStore {
    private static final Comparator<Address> ORDER = Comparator.comparing(Address::key)
            .thenComparingLong(Address::generation);
    private final ScopeLifecycle.Scope root;
    private final MutationCoordinator mutations;
    private final BiConsumer<String, Throwable> failures;
    private final ScopedSubscriptions<UiId, Watch> observers;
    private volatile Published published = new Published(0, Map.of(), Map.of());
    private Graph graph = Graph.of(Map.of());

    StateStore(ScopeLifecycle.Scope root, MutationCoordinator mutations, BiConsumer<String, Throwable> failures) {
        this.root = root;
        this.mutations = mutations;
        this.failures = failures;
        observers = new ScopedSubscriptions<>(mutations::assertOutsideValidation);
    }

    UIStateStore view(ModuleAccess access) {
        return new View(access);
    }

    private void define(ModuleAccess access, StateKey<?> key) {
        Objects.requireNonNull(key, "key");
        mutations.write(() -> {
            access.requireSchemaOwner(key.id());
            var base = published;
            var keys = new LinkedHashMap<>(base.keys());
            if (keys.putIfAbsent(key.id(), new Binding(key, access.requireIssuer())) != null) {
                throw new IllegalArgumentException("Duplicate UI state key: " + key.id());
            }
            published = new Published(base.version() + 1, Map.copyOf(keys), base.values());
            return null;
        });
    }

    private <T> void derive(ModuleAccess access, ScopeHandle scope, StateKey<T> key,
                            Set<StateKey<?>> dependencies, Function<StateSnapshot, T> compute) {
        Objects.requireNonNull(compute, "compute");
        mutations.write(() -> {
            Published base = published;
            var binding = requireKey(base, key);
            var target = access.write(scope);
            requireOwner(access, binding);
            var result = address(binding, target);
            var inputs = new ArrayList<Address>();
            for (StateKey<?> dependency : Set.copyOf(dependencies)) {
                var input = requireKey(base, dependency);
                if (input.owner() != binding.owner()
                        || key.kind() == StateKey.Kind.GLOBAL && dependency.kind() != StateKey.Kind.GLOBAL) {
                    throw new IllegalArgumentException("Invalid derived state dependency: " + dependency.id());
                }
                inputs.add(address(input, target));
            }
            inputs.sort(ORDER);
            var nextEntries = new LinkedHashMap<>(graph.entries());
            if (nextEntries.putIfAbsent(result, new Derived(List.copyOf(inputs), compute)) != null) {
                throw new IllegalArgumentException("State is already derived: " + key.id());
            }
            var nextGraph = Graph.of(nextEntries);
            rejectCycle(result, nextGraph);
            var values = new LinkedHashMap<>(base.values());
            var changes = new LinkedHashSet<Address>();
            evaluate(base, nextGraph, result, values, changes);
            recompute(base, nextGraph, values, changes, Set.of(result));
            ensureCurrent(base);
            access.write(target);
            requireOwner(access, binding);
            publish(base, values, changes, true);
            graph = nextGraph;
            return null;
        });
    }

    /** The old graph is acyclic, so a new cycle must include the newly derived address. */
    private static void rejectCycle(Address result, Graph graph) {
        var path = new HashMap<Address, Address>();
        var frontier = new ArrayDeque<Address>();
        path.put(result, null);
        frontier.add(result);
        while (!frontier.isEmpty()) {
            Address from = frontier.removeFirst();
            var entry = graph.entries().get(from);
            if (entry == null) {
                continue;
            }
            for (Address to : entry.inputs()) {
                if (to.equals(result)) {
                    var cycle = new ArrayDeque<Address>();
                    cycle.addFirst(result);
                    for (Address cursor = from; cursor != null; cursor = path.get(cursor)) {
                        cycle.addFirst(cursor);
                    }
                    throw new IllegalArgumentException("Derived UI state cycle: "
                            + String.join(" -> ", cycle.stream().map(address -> address.key().toString()).toList()));
                }
                if (!path.containsKey(to)) {
                    path.put(to, from);
                    frontier.add(to);
                }
            }
        }
    }

    Transaction begin(ModuleAccess access) {
        access.requireIssuer().assertOwner();
        return new Transaction(access, published);
    }

    private Prepared prepare(ModuleAccess access, Published base, List<Edit> edits) {
        ensureCurrent(base);
        access.requireIssuer();
        if (edits.isEmpty()) {
            return new Prepared(base, base.values(), Set.of());
        }
        var values = new LinkedHashMap<>(base.values());
        var addresses = new LinkedHashSet<Address>();
        for (Edit edit : edits) {
            var binding = requireKey(base, edit.key());
            var scope = writeScope(access, edit.scope(), binding);
            requireOwner(access, binding);
            Address address = address(binding, scope);
            if (graph.entries().containsKey(address)) {
                throw new IllegalArgumentException("Cannot write derived UI state " + address.key());
            }
            var key = edit.key();
            Object value = key.copy(edit.update().apply(key.copy(valueAt(values, address, key))));
            values.put(address, value);
            addresses.add(address);
        }
        addresses.removeIf(address -> {
            var key = base.keys().get(address.key()).key();
            return Objects.deepEquals(valueAt(base.values(), address, key), valueAt(values, address, key));
        });
        recompute(base, graph, values, addresses, Set.copyOf(addresses));
        ensureCurrent(base);
        access.requireIssuer();
        for (Edit edit : edits) {
            writeScope(access, edit.scope(), requireKey(base, edit.key()));
        }
        return new Prepared(base, values, addresses);
    }

    private ScopeLifecycle.Scope writeScope(ModuleAccess access, ScopeHandle handle, Binding binding) {
        // Global writes are governed by the key owner. A trusted caller may address them at the engine root.
        return binding.key().kind() == StateKey.Kind.GLOBAL && handle == root
                ? access.read(handle) : access.write(handle);
    }

    private void ensureCurrent(Published base) {
        if (published != base) {
            throw new ConcurrentModificationException("Stale UI state version " + base.version());
        }
    }

    private void requireOwner(ModuleAccess access, Binding binding) {
        var issuer = access.requireIssuer();
        if (issuer != binding.owner() && issuer != root) {
            throw new SecurityException("Only the schema owner can write state " + binding.key().id());
        }
    }

    private static void recompute(Published base, Graph graph, Map<Address, Object> values,
                                  Set<Address> changed, Set<Address> sources) {
        if (sources.isEmpty() || graph.dependents().isEmpty()) {
            return;
        }
        // Discover the affected subgraph, then evaluate it in deterministic topological order.
        var pending = new ArrayDeque<>(sources.stream().sorted(ORDER).toList());
        var affected = new LinkedHashSet<Address>();
        while (!pending.isEmpty()) {
            for (Address next : graph.dependents().getOrDefault(pending.removeFirst(), List.of())) {
                if (affected.add(next)) {
                    pending.add(next);
                }
            }
        }
        var dependencies = new HashMap<Address, Integer>();
        var ready = new java.util.PriorityQueue<>(ORDER);
        for (Address node : affected) {
            int count = (int) graph.entries().get(node).inputs().stream().filter(affected::contains).count();
            dependencies.put(node, count);
            if (count == 0) {
                ready.add(node);
            }
        }
        while (!ready.isEmpty()) {
            Address node = ready.remove();
            var inputs = graph.entries().get(node).inputs();
            if (inputs.stream().anyMatch(changed::contains)) {
                evaluate(base, graph, node, values, changed);
            }
            for (Address next : graph.dependents().getOrDefault(node, List.of())) {
                if (affected.contains(next) && dependencies.merge(next, -1, Integer::sum) == 0) {
                    ready.add(next);
                }
            }
        }
    }

    private static void evaluate(Published base, Graph graph, Address address, Map<Address, Object> values,
                                 Set<Address> changed) {
        Derived derived = graph.entries().get(address);
        var arguments = new LinkedHashMap<StateKey<?>, Object>();
        for (Address input : derived.inputs()) {
            var key = base.keys().get(input.key()).key();
            arguments.put(key, valueAt(values, input, key));
        }
        var key = base.keys().get(address.key()).key();
        var next = key.copy(derived.compute().apply(new StateSnapshot(base.version(), arguments)));
        var previous = valueAt(base.values(), address, key);
        values.put(address, next);
        if (!Objects.deepEquals(previous, next)) {
            changed.add(address);
        }
    }

    private long publish(Published base, Map<Address, Object> values, Set<Address> changed, boolean schemaChanged) {
        if (!schemaChanged && changed.isEmpty()) {
            return base.version();
        }
        var next = new Published(base.version() + 1, base.keys(), Map.copyOf(values));
        var changes = new LinkedHashSet<MutationCoordinator.Change>();
        var keys = new LinkedHashSet<UiId>();
        for (Address address : changed) {
            changes.add(new MutationCoordinator.Change(MutationCoordinator.Domain.STATE, address.key(), address.generation()));
            keys.add(address.key());
        }
        mutations.stage(changes, () -> notify(keys, base, next));
        published = next;
        return next.version();
    }

    long publish(Prepared prepared) {
        ensureCurrent(prepared.base());
        return publish(prepared.base(), prepared.values(), prepared.changed(), false);
    }

    private <T> T get(ScopeLifecycle.Scope scope, StateKey<T> key) {
        Published current = published;
        return key.copy(valueAt(current.values(), address(requireKey(current, key), scope), key));
    }

    private StateSnapshot snapshot(ScopeLifecycle.Scope scope) {
        Published current = published;
        var values = new LinkedHashMap<StateKey<?>, Object>();
        for (Binding binding : current.keys().values()) {
            var key = binding.key();
            if (allows(key.kind(), scope.kind())) {
                values.put(key, valueAt(current.values(), address(binding, scope), key));
            }
        }
        return new StateSnapshot(current.version(), values);
    }

    private static Object valueAt(Map<Address, Object> values, Address address, StateKey<?> key) {
        Object value = values.get(address);
        return value == null ? key.initialValue() : value;
    }

    private static Binding requireKey(Published source, StateKey<?> key) {
        var binding = source.keys().get(key.id());
        if (binding == null || binding.key() != key) {
            throw new IllegalArgumentException("Unregistered UI state key: " + key.id());
        }
        return binding;
    }

    private static Address address(Binding binding, ScopeLifecycle.Scope scope) {
        var key = binding.key();
        if (!allows(key.kind(), scope.kind())) {
            throw new IllegalArgumentException(key.kind() + " state " + key.id() + " cannot use a " + scope.kind() + " scope");
        }
        long generation = key.kind() == StateKey.Kind.GLOBAL ? binding.owner().generation() : scope.generation();
        return new Address(key.id(), generation);
    }

    private static boolean allows(StateKey.Kind state, ScopeKind scope) {
        return switch (state) {
            case GLOBAL, EPHEMERAL -> true;
            case SCREEN -> scope == ScopeKind.SCREEN;
            case COMPONENT -> scope == ScopeKind.COMPONENT;
        };
    }

    private void notify(Set<UiId> keys, Published before, Published after) {
        for (UiId key : keys) {
            for (var observer : observers.forKey(key)) {
                if (observer.isActive()) {
                    try {
                        observer.listener().change(before, after);
                    } catch (RuntimeException | LinkageError | AssertionError failure) {
                        failures.accept("State observer of " + key, failure);
                    }
                }
            }
        }
    }

    void remove(List<ScopeLifecycle.Scope> closing) {
        closing.forEach(observers::remove);
        var base = published;
        var generations = new java.util.HashSet<Long>();
        closing.forEach(scope -> generations.add(scope.generation()));
        var keys = new LinkedHashMap<>(base.keys());
        keys.values().removeIf(binding -> generations.contains(binding.owner().generation()));
        var values = new LinkedHashMap<>(base.values());
        values.keySet().removeIf(address -> generations.contains(address.generation()) || !keys.containsKey(address.key()));
        var entries = new LinkedHashMap<>(graph.entries());
        entries.keySet().removeIf(address -> generations.contains(address.generation()) || !keys.containsKey(address.key()));
        if (entries.size() != graph.entries().size()) {
            graph = Graph.of(entries);
        }
        if (keys.size() != base.keys().size() || values.size() != base.values().size()) {
            base.keys().keySet().stream().filter(id -> !keys.containsKey(id)).forEach(observers::removeKey);
            published = new Published(base.version() + 1, Map.copyOf(keys), Map.copyOf(values));
        }
    }

    void permissionsChanged(ModuleAccess access) {
        observers.removeForIssuer(access.defaultScope(), target -> target != root && !access.allowsScope(target));
    }

    private record Binding(StateKey<?> key, ScopeLifecycle.Scope owner) {}

    private record Address(UiId key, long generation) {}

    private record Derived(List<Address> inputs, Function<StateSnapshot, ?> compute) {}

    private record Graph(Map<Address, Derived> entries, Map<Address, List<Address>> dependents) {
        static Graph of(Map<Address, Derived> entries) {
            var index = new HashMap<Address, List<Address>>();
            entries.forEach((result, derived) -> derived.inputs().forEach(input ->
                    index.computeIfAbsent(input, ignored -> new ArrayList<>()).add(result)));
            return new Graph(Map.copyOf(entries), index);
        }
    }

    private record Published(long version, Map<UiId, Binding> keys, Map<Address, Object> values) {}

    private record Edit(ScopeHandle scope, StateKey<?> key, UnaryOperator<Object> update) {}

    record Prepared(Published base, Map<Address, Object> values, Set<Address> changed) {}

    @FunctionalInterface
    private interface Watch {
        void change(Published before, Published after);
    }

    private final class View implements UIStateStore {
        private final ModuleAccess access;

        private View(ModuleAccess access) {
            this.access = access;
        }

        @Override
        public void define(StateKey<?> key) {
            StateStore.this.define(access, key);
        }

        @Override
        public <T> void derive(ScopeHandle scope, StateKey<T> key, Set<StateKey<?>> dependencies,
                               Function<StateSnapshot, T> compute) {
            StateStore.this.derive(access, scope, key, dependencies, compute);
        }

        @Override
        public <T> T get(StateKey<T> key) {
            return get(access.defaultScope(), key);
        }

        @Override
        public <T> T get(ScopeHandle scope, StateKey<T> key) {
            return StateStore.this.get(access.read(scope), key);
        }

        @Override
        public <T> void update(StateKey<T> key, UnaryOperator<T> updater) {
            beginTransaction().update(access.defaultScope(), key, updater).commit();
        }

        @Override
        public StateSnapshot snapshot() {
            return snapshot(access.defaultScope());
        }

        @Override
        public StateSnapshot snapshot(ScopeHandle scope) {
            return StateStore.this.snapshot(access.read(scope));
        }

        @Override
        public StateTransaction beginTransaction() {
            return begin(access);
        }

        @Override
        public <T> Subscription observe(ScopeHandle scope, StateKey<T> key, Consumer<ValueChange<T>> listener) {
            access.assertSideEffectAllowed();
            var issuer = access.requireIssuer();
            issuer.assertOwner();
            var target = access.read(scope);
            var address = address(requireKey(published, key), target);
            Objects.requireNonNull(listener, "listener");
            return observers.add(key.id(), issuer, target, (before, after) -> {
                Object previous = valueAt(before.values(), address, key);
                Object current = valueAt(after.values(), address, key);
                if (!Objects.deepEquals(previous, current)) {
                    listener.accept(new ValueChange<>(key.copy(previous), key.copy(current), after.version()));
                }
            });
        }
    }

    final class Transaction implements StateTransaction {
        private final ModuleAccess access;
        private Published base;
        private final List<Edit> edits = new ArrayList<>();
        private boolean used;

        private Transaction(ModuleAccess access, Published base) {
            this.access = access;
            this.base = base;
        }

        @Override
        public <T> StateTransaction set(ScopeHandle scope, StateKey<T> key, T value) {
            return append(scope, key, ignored -> value);
        }

        @Override
        public <T> StateTransaction update(ScopeHandle scope, StateKey<T> key, UnaryOperator<T> updater) {
            Objects.requireNonNull(updater, "updater");
            return append(scope, key, value -> updater.apply(key.type().cast(value)));
        }

        private StateTransaction append(ScopeHandle scope, StateKey<?> key, UnaryOperator<Object> update) {
            checkOpen();
            edits.add(new Edit(Objects.requireNonNull(scope, "scope"), Objects.requireNonNull(key, "key"), update));
            return this;
        }

        @Override
        public long commit() {
            return mutations.write(() -> publish(prepare()));
        }

        Prepared prepare() {
            checkOpen();
            try {
                return StateStore.this.prepare(access, base, edits);
            } finally {
                discard();
            }
        }

        void discard() {
            used = true;
            base = null;
            edits.clear();
        }

        private void checkOpen() {
            access.requireIssuer().assertOwner();
            if (used) {
                throw new IllegalStateException("State transaction is already closed");
            }
        }
    }
}
