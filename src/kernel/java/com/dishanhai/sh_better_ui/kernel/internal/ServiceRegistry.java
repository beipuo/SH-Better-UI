// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owner-thread registration and immutable, separately published query views. */
final class ServiceRegistry {
    private static final Comparator<RegistrySnapshot.Entry> PREFERENCE = Comparator
            .<RegistrySnapshot.Entry>comparingInt(entry -> entry.descriptor().priority()).reversed()
            .thenComparing(entry -> entry.descriptor().providerVersion(), Comparator.reverseOrder())
            .thenComparing(entry -> entry.origin().moduleId());

    private final Thread ownerThread = Thread.currentThread();
    private final Map<UiId, Entry> entries = new TreeMap<>();
    private Transaction active;
    private boolean frozen;
    private long version;

    void register(ModuleDiscovery.Module module, Consumer<ServiceRegistrar> callback) {
        register(module, permission -> {
            if (!module.permissions().contains(permission)) {
                throw new SecurityException("UI module " + module.id() + " lacks " + permission);
            }
        }, callback);
    }

    void register(ModuleDiscovery.Module module, Consumer<UIPermission> authorize, Consumer<ServiceRegistrar> callback) {
        assertOwner();
        if (frozen || active != null) {
            throw new IllegalStateException("The UI registration window is unavailable");
        }
        var transaction = new Transaction(module, authorize);
        active = transaction;
        boolean committed = false;
        try {
            callback.accept(transaction);
            committed = true;
        } finally {
            active = null;
            if (!committed) {
                transaction.rollback();
            }
            transaction.originals.clear();
        }
    }

    void freeze() {
        assertOwner();
        if (active != null || frozen) {
            throw new IllegalStateException("Cannot freeze the UI registry in its current phase");
        }
        frozen = true;
        version = 1;
    }

    static APIRegistry view(Supplier<Catalog> catalog, ModuleDiscovery.Module viewer) {
        return new View(catalog, viewer);
    }

    Catalog capture(Set<UiId> started) {
        assertOwner();
        if (!frozen) {
            throw new IllegalStateException("UI services cannot be queried before registration freezes");
        }
        var visible = new HashMap<UiId, Entry>();
        var ordered = new ArrayList<Entry>();
        entries.forEach((id, entry) -> {
            if (started.contains(entry.metadata().origin().moduleId())) {
                visible.put(id, entry);
                ordered.add(entry);
            }
        });
        return new Catalog(Map.copyOf(visible), List.copyOf(ordered), version);
    }

    void remove(UiId module) {
        assertOwner();
        if (active != null) {
            throw new IllegalStateException("Lifecycle removal cannot interrupt a UI registration transaction");
        }
        boolean changed = entries.values().removeIf(entry -> {
            if (!entry.metadata().origin().moduleId().equals(module)) {
                return false;
            }
            entry.handle().status = RegistrationHandle.Status.REVOKED;
            return true;
        });
        if (changed) {
            version++;
        }
    }

    private void assertOwner() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("UI service registration requires the owner thread");
        }
    }

    private record Entry(RegistrySnapshot.Entry metadata, UIService service, Handle handle) {
        boolean visibleTo(ModuleDiscovery.Module viewer) {
            return switch (metadata.descriptor().visibility()) {
                case PUBLIC -> true;
                case MODULE_PRIVATE -> viewer != null && metadata.origin().moduleId().equals(viewer.id());
                case KERNEL_PRIVATE -> viewer != null && viewer.core();
            };
        }
    }

    static final class Catalog {
        private final Map<UiId, Entry> services;
        private final List<Entry> ordered;
        private final long version;

        private Catalog(Map<UiId, Entry> services, List<Entry> ordered, long version) {
            this.services = services;
            this.ordered = ordered;
            this.version = version;
        }

        <T extends UIService> Optional<T> find(ServiceKey<T> key, ModuleDiscovery.Module viewer) {
            Entry entry = services.get(Objects.requireNonNull(key, "key").id());
            if (entry == null || !entry.metadata().descriptor().key().equals(key) || !entry.visibleTo(viewer)) {
                return Optional.empty();
            }
            return Optional.of(key.type().cast(entry.service()));
        }

        RegistrySnapshot snapshot(ModuleDiscovery.Module viewer) {
            var metadata = ordered.stream().filter(entry -> entry.visibleTo(viewer)).map(Entry::metadata).toList();
            return new RegistrySnapshot(version, metadata);
        }
    }

    private record View(Supplier<Catalog> catalog, ModuleDiscovery.Module viewer) implements APIRegistry {
        @Override
        public <T extends UIService> Optional<T> find(ServiceKey<T> key) {
            return catalog.get().find(key, viewer);
        }

        @Override
        public RegistrySnapshot snapshot() {
            return catalog.get().snapshot(viewer);
        }
    }

    private final class Handle implements RegistrationHandle {
        private final UiId id;
        private volatile Status status = Status.ACTIVE;

        private Handle(UiId id) {
            this.id = id;
        }

        @Override
        public Status status() {
            return status;
        }

        @Override
        public void close() {
            if (status != Status.ACTIVE) {
                return;
            }
            assertOwner();
            Entry entry = entries.get(id);
            if (active == null || entry == null || entry.handle() != this
                    || !entry.metadata().origin().moduleId().equals(active.module.id())) {
                throw new IllegalStateException("UI service can only be withdrawn in its owner's registration window: " + id);
            }
            active.remember(id);
            entries.remove(id);
            status = Status.REVOKED;
        }
    }

    private enum Skipped implements RegistrationHandle {
        INSTANCE;

        @Override
        public Status status() {
            return Status.SKIPPED;
        }

        @Override
        public void close() {
            // No registration was acquired.
        }
    }

    private final class Transaction implements ServiceRegistrar {
        private final ModuleDiscovery.Module module;
        private final Consumer<UIPermission> authorize;
        private final Map<UiId, Entry> originals = new HashMap<>();

        private Transaction(ModuleDiscovery.Module module, Consumer<UIPermission> authorize) {
            this.module = module;
            this.authorize = authorize;
        }

        @Override
        public <T extends UIService> RegistrationHandle register(ServiceDescriptor<T> descriptor, T service,
                                                                  RegistrationConflictPolicy policy) {
            assertOwner();
            if (active != this) {
                throw new IllegalStateException("Expired UI registrar for " + module.id());
            }
            authorize.accept(UIPermission.REGISTER_SERVICE);
            Objects.requireNonNull(policy, "policy");
            var key = Objects.requireNonNull(descriptor, "descriptor").key();
            UiId id = key.id();
            if (!id.namespace().equals(module.id().namespace())
                    || descriptor.visibility() == ServiceDescriptor.Visibility.KERNEL_PRIVATE && !module.core()) {
                throw new SecurityException("UI module " + module.id() + " may not register " + id);
            }
            if (!key.type().isInstance(Objects.requireNonNull(service, "service"))) {
                throw new IllegalArgumentException("UI service " + id + " must implement " + key.type().getName());
            }
            var metadata = new RegistrySnapshot.Entry(descriptor, module.origin());
            var previous = entries.get(id);
            if (previous != null && !replace(previous.metadata(), metadata, policy)) {
                return Skipped.INSTANCE;
            }

            remember(id);
            var handle = new Handle(id);
            entries.put(id, new Entry(metadata, service, handle));
            if (previous != null) {
                previous.handle().status = RegistrationHandle.Status.REVOKED;
            }
            return handle;
        }

        private boolean replace(RegistrySnapshot.Entry previous, RegistrySnapshot.Entry next,
                                RegistrationConflictPolicy policy) {
            var key = next.descriptor().key();
            if (!key.equals(previous.descriptor().key())) {
                throw new IllegalArgumentException("Incompatible Java type or contract version for UI service " + key.id());
            }
            return switch (policy) {
                case REJECT -> throw new IllegalStateException("Duplicate UI service: " + key.id());
                case KEEP_EXISTING -> false;
                case SELECT_BY_PRIORITY, REPLACE_IF_ALLOWED -> {
                    authorize.accept(UIPermission.REPLACE_SERVICE);
                    var visibility = previous.descriptor().visibility();
                    if (visibility != next.descriptor().visibility()
                            || (visibility == ServiceDescriptor.Visibility.MODULE_PRIVATE
                            && !previous.origin().moduleId().equals(next.origin().moduleId()))) {
                        throw new SecurityException("Replacement cannot change the visibility of UI service " + key.id());
                    }
                    boolean prioritySelection = policy == RegistrationConflictPolicy.SELECT_BY_PRIORITY;
                    if (!previous.descriptor().replaceable() || (prioritySelection && !next.descriptor().replaceable())) {
                        throw new IllegalStateException("UI service cannot be replaced: " + key.id());
                    }
                    yield !prioritySelection || PREFERENCE.compare(next, previous) < 0;
                }
            };
        }

        private void remember(UiId id) {
            if (!originals.containsKey(id)) {
                originals.put(id, entries.get(id));
            }
        }

        private void rollback() {
            originals.forEach((id, previous) -> {
                var current = entries.remove(id);
                if (current != null) {
                    current.handle().status = RegistrationHandle.Status.REVOKED;
                }
                if (previous != null) {
                    previous.handle().status = RegistrationHandle.Status.ACTIVE;
                    entries.put(id, previous);
                }
            });
        }
    }
}
