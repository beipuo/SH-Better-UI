// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.dishanhai.sh_better_ui.kernel.api.ConfigLayer.*;

final class ConfigCenter {
    private final ScopeLifecycle.Scope root;
    private final UILifecycle lifecycle;
    private final MutationCoordinator mutations;
    private final BiConsumer<String, Throwable> failures;
    private final ScopedSubscriptions<UiId, Watch> observers;
    private volatile Published published = new Published(0, Map.of(), Map.of());

    ConfigCenter(ScopeLifecycle.Scope root, UILifecycle lifecycle, MutationCoordinator mutations,
                 BiConsumer<String, Throwable> failures) {
        this.root = root;
        this.lifecycle = lifecycle;
        this.mutations = mutations;
        this.failures = failures;
        observers = new ScopedSubscriptions<>(mutations::assertOutsideValidation);
    }

    UIConfigCenter view(ModuleAccess access) {
        return new View(access);
    }

    private void define(ModuleAccess access, ConfigKey<?> key) {
        Objects.requireNonNull(key, "key");
        mutations.write(() -> {
            access.requireSchemaOwner(key.id());
            var base = published;
            var keys = new LinkedHashMap<>(base.keys());
            if (keys.putIfAbsent(key.id(), new Binding(key, access.requireIssuer())) != null) {
                throw new IllegalArgumentException("Duplicate UI configuration key: " + key.id());
            }
            published = new Published(base.version() + 1, Map.copyOf(keys), base.values());
            return null;
        });
    }

    private <T> T get(ScopeLifecycle.Scope scope, ConfigKey<T> key) {
        Published current = published;
        requireKey(current, key);
        return key.copy(resolve(current, scope, key));
    }

    private ConfigSnapshot snapshot(ScopeLifecycle.Scope scope) {
        Published current = published;
        var values = new LinkedHashMap<ConfigKey<?>, Object>();
        current.keys().values().forEach(binding -> values.put(binding.key(), resolve(current, scope, binding.key())));
        return new ConfigSnapshot(current.version(), values);
    }

    private long commit(ModuleAccess access, Published base, List<Edit> edits) {
        return mutations.write(() -> {
            if (base != published) {
                throw new ConcurrentModificationException("Stale UI configuration version " + base.version());
            }
            access.requireIssuer();
            var candidate = new LinkedHashMap<>(base.values());
            var addresses = new LinkedHashSet<Address>();
            for (Edit edit : edits) {
                var binding = requireKey(base, edit.key());
                var address = validate(access, binding, edit.scope());
                if (edit.clear()) {
                    candidate.remove(address);
                } else {
                    Object value = edit.storedVersion() == null ? edit.key().validate(edit.value())
                            : migrate(edit);
                    candidate.put(address, value);
                }
                addresses.add(address);
            }
            if (base != published) {
                throw new ConcurrentModificationException("UI configuration changed during validation");
            }
            // Validation can invoke module code. Recheck capabilities and lifetimes before publication.
            access.requireIssuer();
            for (Edit edit : edits) {
                validate(access, requireKey(base, edit.key()), edit.scope());
            }

            var changed = new LinkedHashSet<MutationCoordinator.Change>();
            var keys = new LinkedHashSet<UiId>();
            for (Address address : addresses) {
                if (!Objects.deepEquals(base.values().get(address), candidate.get(address))) {
                    changed.add(new MutationCoordinator.Change(MutationCoordinator.Domain.CONFIG,
                            address.key(), address.generation()));
                    keys.add(address.key());
                }
            }
            if (changed.isEmpty()) {
                return base.version();
            }
            var next = new Published(base.version() + 1, base.keys(), Map.copyOf(candidate));
            mutations.stage(changed, () -> notify(keys, base, next));
            published = next;
            return next.version();
        });
    }

    private Address validate(ModuleAccess access, Binding binding, ConfigScope scope) {
        ConfigLayer layer = scope.layer();
        ConfigKey<?> key = binding.key();
        if (!key.layers().contains(layer) || key.mutability() == ConfigKey.Mutability.STARTUP_ONLY
                && lifecycle.state() != EngineState.INITIALIZING) {
            throw new IllegalArgumentException("Configuration " + key.id() + " cannot be written at " + layer + " now");
        }
        var target = access.read(scope.lifetime());
        switch (layer) {
            case MODULE_DEFAULTS -> {
                requireKind(target, ScopeKind.ENGINE);
                var issuer = access.requireIssuer();
                if (issuer != binding.owner() && issuer != root) {
                    throw new SecurityException("Only the schema owner can set module defaults for " + key.id());
                }
            }
            case RESOURCE_THEME -> {
                requireKind(target, ScopeKind.ENGINE);
                access.require(UIPermission.MODIFY_GLOBAL_CONFIG, target);
                access.require(UIPermission.MODIFY_GLOBAL_THEME, target);
            }
            case USER_GLOBAL -> {
                requireKind(target, ScopeKind.ENGINE);
                access.require(UIPermission.MODIFY_GLOBAL_CONFIG, target);
            }
            case SCREEN -> {
                requireKind(target, ScopeKind.SCREEN);
                access.write(target);
            }
            case COMPONENT -> {
                requireKind(target, ScopeKind.COMPONENT);
                access.write(target);
            }
            case RUNTIME_OVERRIDE -> {
                if (target == root) {
                    access.require(UIPermission.MODIFY_GLOBAL_CONFIG, target);
                } else {
                    access.write(target);
                }
            }
            case ENGINE_DEFAULTS -> throw new IllegalArgumentException("Engine defaults are part of the schema");
        }
        return new Address(key.id(), layer, target.generation());
    }

    private static Object migrate(Edit edit) {
        if (edit.scope().layer() != USER_GLOBAL || edit.key().persistence() != ConfigKey.Persistence.USER_GLOBAL) {
            throw new IllegalArgumentException("Configuration is not persisted in User Global: " + edit.key().id());
        }
        return edit.key().migrate(edit.storedVersion(), edit.value());
    }

    private static void requireKind(ScopeLifecycle.Scope scope, ScopeKind kind) {
        if (scope.kind() != kind) {
            throw new IllegalArgumentException("Configuration layer requires scope kind " + kind);
        }
    }

    private Object resolve(Published source, ScopeLifecycle.Scope scope, ConfigKey<?> key) {
        Object value = inherited(source, scope, key.id(), RUNTIME_OVERRIDE, null);
        if (value == null) {
            value = inherited(source, scope, key.id(), COMPONENT, ScopeKind.COMPONENT);
        }
        if (value == null) {
            value = inherited(source, scope, key.id(), SCREEN, ScopeKind.SCREEN);
        }
        if (value == null) {
            value = source.values().get(new Address(key.id(), USER_GLOBAL, root.generation()));
        }
        if (value == null) {
            value = source.values().get(new Address(key.id(), RESOURCE_THEME, root.generation()));
        }
        if (value == null) {
            value = source.values().get(new Address(key.id(), MODULE_DEFAULTS, root.generation()));
        }
        return value == null ? key.defaultValue() : value;
    }

    private static Object inherited(Published source, ScopeLifecycle.Scope scope, UiId key,
                                    ConfigLayer layer, ScopeKind kind) {
        for (var current = scope; current != null; current = current.parent()) {
            if (kind == null || current.kind() == kind) {
                Object value = source.values().get(new Address(key, layer, current.generation()));
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static Binding requireKey(Published current, ConfigKey<?> key) {
        var binding = current.keys().get(key.id());
        if (binding == null || binding.key() != key) {
            throw new IllegalArgumentException("Unregistered UI configuration key: " + key.id());
        }
        return binding;
    }

    private void notify(Set<UiId> keys, Published before, Published after) {
        for (UiId key : keys) {
            for (var observer : observers.forKey(key)) {
                if (observer.isActive()) {
                    try {
                        observer.listener().change(before, after);
                    } catch (RuntimeException | LinkageError | AssertionError failure) {
                        failures.accept("Configuration observer of " + key, failure);
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
        if (keys.size() != base.keys().size() || values.size() != base.values().size()) {
            base.keys().keySet().stream().filter(id -> !keys.containsKey(id)).forEach(observers::removeKey);
            published = new Published(base.version() + 1, Map.copyOf(keys), Map.copyOf(values));
        }
    }

    void permissionsChanged(ModuleAccess access) {
        observers.removeForIssuer(access.defaultScope(), target -> target != root && !access.allowsScope(target));
    }

    private record Binding(ConfigKey<?> key, ScopeLifecycle.Scope owner) {}

    private record Address(UiId key, ConfigLayer layer, long generation) {}

    private record Published(long version, Map<UiId, Binding> keys, Map<Address, Object> values) {}

    private record Edit(ConfigScope scope, ConfigKey<?> key, Object value, Integer storedVersion, boolean clear) {
        Edit {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(key, "key");
        }
    }

    @FunctionalInterface
    private interface Watch {
        void change(Published before, Published after);
    }

    private final class View implements UIConfigCenter {
        private final ModuleAccess access;

        private View(ModuleAccess access) {
            this.access = access;
        }

        @Override
        public void define(ConfigKey<?> key) {
            ConfigCenter.this.define(access, key);
        }

        @Override
        public <T> T get(ConfigKey<T> key) {
            return get(access.defaultScope(), key);
        }

        @Override
        public <T> T get(ScopeHandle scope, ConfigKey<T> key) {
            return ConfigCenter.this.get(access.read(scope), key);
        }

        @Override
        public <T> void set(ConfigKey<T> key, T value) {
            beginTransaction().set(new ConfigScope(access.defaultScope(), RUNTIME_OVERRIDE), key, value).commit();
        }

        @Override
        public ConfigSnapshot snapshot() {
            return snapshot(access.defaultScope());
        }

        @Override
        public ConfigSnapshot snapshot(ScopeHandle scope) {
            return ConfigCenter.this.snapshot(access.read(scope));
        }

        @Override
        public ConfigTransaction beginTransaction() {
            access.requireIssuer().assertOwner();
            return new Transaction(access, published);
        }

        @Override
        public <T> Subscription observe(ScopeHandle scope, ConfigKey<T> key, Consumer<ValueChange<T>> listener) {
            access.assertSideEffectAllowed();
            var issuer = access.requireIssuer();
            issuer.assertOwner();
            var target = access.read(scope);
            requireKey(published, key);
            Objects.requireNonNull(listener, "listener");
            return observers.add(key.id(), issuer, target, (before, after) -> {
                Object previous = resolve(before, target, key);
                Object current = resolve(after, target, key);
                if (!Objects.deepEquals(previous, current)) {
                    listener.accept(new ValueChange<>(key.copy(previous), key.copy(current), after.version()));
                }
            });
        }
    }

    private final class Transaction implements ConfigTransaction {
        private final ModuleAccess access;
        private Published base;
        private final List<Edit> edits = new ArrayList<>();
        private boolean used;

        private Transaction(ModuleAccess access, Published base) {
            this.access = access;
            this.base = base;
        }

        @Override
        public <T> ConfigTransaction set(ConfigScope scope, ConfigKey<T> key, T value) {
            return append(new Edit(scope, key, value, null, false));
        }

        @Override
        public ConfigTransaction clear(ConfigScope scope, ConfigKey<?> key) {
            return append(new Edit(scope, key, null, null, true));
        }

        @Override
        public <T> ConfigTransaction load(ConfigScope scope, ConfigKey<T> key, int storedVersion, T value) {
            return append(new Edit(scope, key, value, storedVersion, false));
        }

        private ConfigTransaction append(Edit edit) {
            checkOpen();
            edits.add(edit);
            return this;
        }

        @Override
        public long commit() {
            checkOpen();
            used = true;
            try {
                return ConfigCenter.this.commit(access, base, edits);
            } finally {
                base = null;
                edits.clear();
            }
        }

        private void checkOpen() {
            access.requireIssuer().assertOwner();
            if (used) {
                throw new IllegalStateException("Configuration transaction is already closed");
            }
        }
    }
}
