// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceClaim;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceKind;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.LongSupplier;

import static com.dishanhai.sh_better_ui.kernel.api.OwnershipLease.Status.*;

/** One owner-thread authority for legacy claims and scoped leases. */
final class ResourceOwnership {
    private static final int QUEUE_LIMIT = 16;
    private static final int SHARED_LIMIT = 64;
    private static final Comparator<Lease> WAIT_ORDER = Comparator.<Lease>comparingInt(Lease::priority).reversed()
            .thenComparingLong(lease -> lease.sequence);

    private final Map<ConflictResolver.SlotKey, Slot> slots = new HashMap<>();
    private final Map<ResourceClaim, Set<Lease>> byClaim = new HashMap<>();
    private final Map<ModuleAccess, Set<Lease>> byIssuer = new HashMap<>();
    private final Set<Lease> expiring = new LinkedHashSet<>();
    private final CleanupManager cleanup;
    private final LongSupplier clock;
    private final Runnable validationGuard;
    private long sequence;

    ResourceOwnership(CleanupManager cleanup, LongSupplier clock, Runnable validationGuard) {
        this.cleanup = cleanup;
        this.clock = clock;
        this.validationGuard = validationGuard;
    }

    UIResourceOwnership view(ModuleAccess access) {
        return (request, release) -> acquireScoped(access, request, release, null);
    }

    OwnershipLease acquireNativePointer(ModuleAccess access, ResourceRequest request, AutoCloseable release) {
        if (request.resource().kind() != ResourceKind.POINTER_CAPTURE || request.policy() != ConflictPolicy.REJECT) {
            throw new IllegalArgumentException("Native pointer acquisition requires an exclusive pointer request");
        }
        return acquireScoped(access, request, release, UIPermission.BLOCK_VANILLA_INPUT);
    }

    private OwnershipLease acquireScoped(ModuleAccess access, ResourceRequest request, AutoCloseable release,
                                         UIPermission extra) {
        var issuer = access.requireIssuer();
        issuer.assertOwner();
        access.assertSideEffectAllowed();
        var target = access.write(request.scope());
        access.requireClaim(request.resource());
        requirePermissions(access, target, request.resource().kind(), extra);
        return acquire(access, target, request, release, false, extra);
    }

    OwnershipLease acquireLegacy(ScopeLifecycle.Scope target, ModuleAccess access, ResourceClaim resource,
                                 AutoCloseable release) {
        if (access != null) {
            requirePermissions(access, target, resource.kind(), null);
        }
        return acquire(access, target, new ResourceRequest(resource, target, 0, 0, ConflictPolicy.REJECT),
                release, true, null);
    }

    boolean pointerAvailable(ScopeLifecycle.Scope target, ResourceClaim claim) {
        var slot = slots.get(ConflictResolver.slot(claim, target, false));
        if (slot == null) return true;
        if (slot.busy) return false;
        maintain(slot, clock.getAsLong());
        return slot.active.isEmpty() && slot.waiting.isEmpty();
    }

    private OwnershipLease acquire(ModuleAccess access, ScopeLifecycle.Scope target, ResourceRequest request,
                                   AutoCloseable release, boolean legacy, UIPermission extra) {
        Objects.requireNonNull(release, "release");
        var claim = request.resource();
        var policy = request.policy();
        ConflictResolver.validate(claim.kind(), policy);
        var key = ConflictResolver.slot(claim, target, legacy);
        long now = clock.getAsLong();
        Slot existing = slots.get(key);
        if (existing != null) {
            if (existing.busy) {
                throw new IllegalStateException("Reentrant UI resource acquisition: " + claim);
            }
            maintain(existing, now);
            if (access != null) {
                access.write(target);
                requirePermissions(access, target, claim.kind(), extra);
            } else if (!target.isOpen()) {
                throw new IllegalStateException("UI resource scope is closed: " + target.id());
            }
        }
        if (byClaim.getOrDefault(claim, Set.of()).stream().anyMatch(lease -> legacy || lease.legacy)) {
            throw new IllegalStateException("UI resource already has an engine-wide claim: " + claim);
        }
        var slot = slots.computeIfAbsent(key, Slot::new);
        var issuer = access == null ? target : access.requireIssuer();
        var current = slot.active.isEmpty() ? null : slot.active.iterator().next();
        if (current != null && policy == ConflictPolicy.RETARGET) {
            if (current.issuer != issuer || current.target != target
                    || !ConflictResolver.canReplace(current.priority, request.priority(), true)) {
                throw new IllegalStateException("Only the active animation owner can retarget " + claim);
            }
            current.priority = request.priority();
            current.renew(now, request.lifetimeNanos());
            return current;
        }

        boolean queued = current != null && policy == ConflictPolicy.QUEUE;
        if (current != null) {
            switch (policy) {
                case REJECT, RETARGET, MERGE -> throw new IllegalStateException("UI resource is held: " + claim);
                case QUEUE -> {
                    if (slot.waiting.size() >= QUEUE_LIMIT) {
                        throw new IllegalStateException("UI resource wait queue is full: " + claim);
                    }
                }
                case COEXIST -> {
                    if (slot.active.size() >= SHARED_LIMIT
                            || slot.active.stream().anyMatch(lease -> lease.policy != ConflictPolicy.COEXIST)) {
                        throw new IllegalStateException("UI resource has no shared slot: " + claim);
                    }
                }
                case REPLACE -> {
                    if (current.issuer != issuer) {
                        if (access == null) {
                            throw new SecurityException("UI resource replacement requires a module context");
                        }
                        access.require(UIPermission.MODIFY_FOREIGN_SCOPE, current.target);
                    }
                    if (slot.active.size() != 1
                            || !ConflictResolver.canReplace(current.priority, request.priority(), current.issuer == issuer)) {
                        throw new IllegalStateException("UI resource replacement lost priority: " + claim);
                    }
                }
            }
        }

        var lease = new Lease(slot, access, issuer, target, request, release, legacy, extra, ++sequence, now);
        boolean tracked = false;
        try {
            cleanup.track(target, release, lease);
            tracked = true;
            if (issuer != target) {
                cleanup.track(issuer, lease, lease);
            }
        } catch (RuntimeException failure) {
            if (tracked) {
                cleanup.forget(target, release);
            }
            discardEmpty(slot);
            throw failure;
        }
        byClaim.computeIfAbsent(claim, ignored -> new LinkedHashSet<>()).add(lease);
        if (access != null) {
            byIssuer.computeIfAbsent(access, ignored -> new LinkedHashSet<>()).add(lease);
        }
        lease.renew(now, request.lifetimeNanos());
        if (queued) {
            slot.waiting.add(lease);
        } else if (current != null && policy == ConflictPolicy.REPLACE) {
            slot.busy = true;
            try {
                drop(current);
                if (lease.status != RELEASED) {
                    if (lease.valid(clock.getAsLong())) {
                        activate(lease);
                    } else {
                        drop(lease);
                    }
                }
            } finally {
                slot.busy = false;
                maintain(slot, clock.getAsLong());
            }
        } else {
            activate(lease);
        }
        return lease;
    }

    void sweep() {
        if (!expiring.isEmpty()) {
            long now = clock.getAsLong();
            for (Lease lease : List.copyOf(expiring)) {
                if (!lease.valid(now)) {
                    maintain(lease.slot, now);
                }
            }
        }
    }

    void permissionsChanged(ModuleAccess access) {
        long now = clock.getAsLong();
        for (Lease lease : List.copyOf(byIssuer.getOrDefault(access, Set.of()))) {
            if (!lease.valid(now)) {
                maintain(lease.slot, now);
            }
        }
    }

    private void maintain(Slot slot, long now) {
        if (slot.busy) {
            return;
        }
        slot.busy = true;
        try {
            for (Lease lease : List.copyOf(slot.active)) {
                if (!lease.valid(now)) {
                    drop(lease);
                }
            }
            for (Lease lease : List.copyOf(slot.waiting)) {
                if (!lease.valid(now)) {
                    drop(lease);
                }
            }
            if (slot.active.isEmpty() && !slot.waiting.isEmpty()) {
                activate(slot.waiting.remove());
            }
        } finally {
            slot.busy = false;
            discardEmpty(slot);
        }
    }

    private static void activate(Lease lease) {
        lease.slot.active.add(lease);
        lease.status = ACTIVE;
    }

    private void close(Lease lease) {
        if (lease.status == RELEASED) {
            return;
        }
        lease.issuer.assertOwner();
        validationGuard.run();
        Slot slot = lease.slot;
        boolean wasBusy = slot.busy;
        slot.busy = true;
        try {
            drop(lease);
        } finally {
            slot.busy = wasBusy;
            if (!wasBusy) {
                maintain(slot, clock.getAsLong());
            }
        }
    }

    private void drop(Lease lease) {
        if (lease.status == RELEASED) {
            return;
        }
        lease.status = RELEASED;
        lease.slot.active.remove(lease);
        lease.slot.waiting.remove(lease);
        expiring.remove(lease);
        if (lease.access != null) {
            remove(byIssuer, lease.access, lease);
        }
        AutoCloseable release = lease.release;
        lease.release = null;
        try {
            cleanup.release(lease.target, release);
        } finally {
            remove(byClaim, lease.resource, lease);
            cleanup.forget(lease.target, release);
            if (lease.issuer != lease.target) {
                cleanup.forget(lease.issuer, lease);
            }
        }
    }

    private void discardEmpty(Slot slot) {
        if (slot.active.isEmpty() && slot.waiting.isEmpty()) {
            slots.remove(slot.key, slot);
        }
    }

    private static <K> void remove(Map<K, Set<Lease>> index, K key, Lease lease) {
        Set<Lease> entries = index.get(key);
        entries.remove(lease);
        if (entries.isEmpty()) {
            index.remove(key);
        }
    }

    private static UIPermission kindPermission(ResourceKind kind) {
        return switch (kind) {
            case POINTER_CAPTURE -> UIPermission.CAPTURE_INPUT;
            case RENDER_TARGET -> UIPermission.REGISTER_RENDER_PASS;
            default -> null;
        };
    }

    private static void requirePermissions(ModuleAccess access, ScopeLifecycle.Scope target, ResourceKind kind,
                                           UIPermission extra) {
        var permission = kindPermission(kind);
        if (permission != null) {
            access.require(permission, target);
        }
        if (extra != null) access.require(extra, target);
    }

    private static final class Slot {
        final ConflictResolver.SlotKey key;
        final Set<Lease> active = new LinkedHashSet<>();
        final PriorityQueue<Lease> waiting = new PriorityQueue<>(WAIT_ORDER);
        boolean busy;

        Slot(ConflictResolver.SlotKey key) {
            this.key = key;
        }
    }

    private final class Lease implements OwnershipLease {
        private final Slot slot;
        private final ModuleAccess access;
        private final ScopeLifecycle.Scope issuer;
        private final ScopeLifecycle.Scope target;
        private final ResourceClaim resource;
        private final ConflictPolicy policy;
        private final boolean legacy;
        private final UIPermission extra;
        private final long sequence;
        private volatile Status status = QUEUED;
        private volatile int priority;
        private volatile Timing timing;
        private AutoCloseable release;

        Lease(Slot slot, ModuleAccess access, ScopeLifecycle.Scope issuer, ScopeLifecycle.Scope target,
              ResourceRequest request, AutoCloseable release, boolean legacy, UIPermission extra, long sequence, long now) {
            this.slot = slot;
            this.access = access;
            this.issuer = issuer;
            this.target = target;
            resource = request.resource();
            policy = request.policy();
            this.legacy = legacy;
            this.extra = extra;
            this.sequence = sequence;
            priority = request.priority();
            timing = new Timing(now, request.lifetimeNanos());
            this.release = release;
        }

        @Override public ResourceClaim resource() { return resource; }
        @Override public UiId owner() { return issuer.owner(); }
        @Override public long scopeGeneration() { return target.generation(); }
        @Override public Status status() { return status; }
        @Override public int priority() { return priority; }
        @Override public long lifetimeNanos() { return timing.lifetime(); }

        @Override
        public boolean isActive() {
            return status == ACTIVE && valid(clock.getAsLong());
        }

        private boolean valid(long now) {
            Timing current = timing;
            if (status == RELEASED || !target.isOpen() || !issuer.isOpen()
                    || current.lifetime() != 0 && now - current.started() >= current.lifetime()) {
                return false;
            }
            var permission = kindPermission(resource.kind());
            return access == null || access.allowsScope(target)
                    && (permission == null || access.decision(permission, target).allowed())
                    && (extra == null || access.decision(extra, target).allowed());
        }

        void renew(long now, long lifetime) {
            timing = new Timing(now, lifetime);
            if (lifetime == 0) {
                expiring.remove(this);
            } else {
                expiring.add(this);
            }
        }

        @Override
        public void close() {
            ResourceOwnership.this.close(this);
        }
    }

    private record Timing(long started, long lifetime) {}
}
