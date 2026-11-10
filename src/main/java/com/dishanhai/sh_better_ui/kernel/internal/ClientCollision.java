// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.UiCollisionLayout;
import com.dishanhai.sh_better_ui.client.gui.collision.CollisionBody;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.ToIntFunction;

final class ClientCollision implements CollisionService {
    private static final Set<UIDirtyFlag> GEOMETRY = Set.of(UIDirtyFlag.HIT_TEST);
    private final KernelHost host;
    private final ClientInput input;
    private final Map<ScopeLifecycle.Scope, CollisionGeometry> worlds = new HashMap<>();

    ClientCollision(KernelHost host, ClientInput input) {
        this.host = host;
        this.input = input;
    }

    @Override
    public Geometry mount(ModuleContext module, ScopeHandle node, UiId writer, Bounds viewport,
                          BooleanSupplier reducedMotion) {
        var target = host.ownedNode(module, node);
        var world = new CollisionGeometry(target, Objects.requireNonNull(viewport), Objects.requireNonNull(reducedMotion));
        var claim = new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.LAYOUT_MUTATION, writer);
        world.lease = host.acquire(host.moduleAccess(module),
                new ResourceRequest(claim, target, 0, 0, ConflictPolicy.REJECT), world::release);
        worlds.put(target, world);
        world.invalidate(viewport);
        return world;
    }

    @Override
    public Subscription target(ModuleContext module, ScopeHandle node, Geometry geometry, CollisionBody body,
                               int layer, int hitSlop) {
        var target = host.ownedNode(module, node);
        if (!(geometry instanceof CollisionGeometry world) || worlds.get(world.scope) != world
                || !target.isWithin(world.scope) || !world.layout.world().contains(body)) {
            throw new IllegalArgumentException("Collision target must belong to the mounted geometry");
        }
        if (hitSlop < 0 || hitSlop > UiCollisionLayout.MAX_HIT_SLOP) {
            throw new IllegalArgumentException("Invalid hit slop");
        }
        var existing = world.targets.get(body);
        if (existing != null && existing.slop != hitSlop) {
            throw new IllegalArgumentException("Collision target registrations for one body must share its hit slop");
        }
        var subscription = input.pickedTarget(module, target, layer, world.picker, body,
                detached -> world.detach(body, detached));
        world.targets.computeIfAbsent(body, ignored -> new BodyTargets(hitSlop)).subscriptions.add(subscription);
        return subscription;
    }

    void update() {
        long nowMs = host.nowNanos() / 1_000_000L;
        for (var node : host.dirty().drain(UIDirtyFlag.HIT_TEST)) {
            var world = worlds.get(node);
            if (world == null || !world.isActive()) continue;
            try {
                boolean reduce = world.reducedMotion.getAsBoolean();
                if (!world.isActive()) continue;
                world.layout.update(world.viewport, nowMs, reduce);
                if (world.isActive() && world.layout.world().isAnimating()) world.invalidate(world.viewport);
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                host.report("UI collision geometry " + node.id(), failure);
                world.close();
            }
        }
    }

    private final class CollisionGeometry implements Geometry {
        private final ScopeLifecycle.Scope scope;
        private final UiCollisionLayout layout;
        private final Map<CollisionBody, BodyTargets> targets = new LinkedHashMap<>();
        private final Function<NativeInput, CollisionBody> picker = this::pick;
        private final ToIntFunction<CollisionBody> hitSlop = this::hitSlop;
        private BooleanSupplier reducedMotion;
        private Bounds viewport;
        private OwnershipLease lease;

        CollisionGeometry(ScopeLifecycle.Scope scope, Bounds viewport, BooleanSupplier reducedMotion) {
            this.scope = scope;
            this.viewport = viewport;
            this.reducedMotion = reducedMotion;
            layout = new UiCollisionLayout(viewport);
        }

        @Override public UiCollisionLayout layout() { return layout; }
        @Override public boolean isActive() { return lease.isActive(); }
        @Override public void close() { lease.close(); }

        private CollisionBody pick(NativeInput event) {
            if (!(event instanceof NativeInput.Pointer pointer) || !isActive()) return null;
            return layout.hitTestExpanded(pointer.x(), pointer.y(), hitSlop);
        }

        private int hitSlop(CollisionBody body) {
            var registered = targets.get(body);
            return registered == null ? 0 : registered.slop;
        }

        private void detach(CollisionBody body, Subscription subscription) {
            var registered = targets.get(body);
            if (registered != null && registered.subscriptions.remove(subscription) && registered.subscriptions.isEmpty()) {
                targets.remove(body);
            }
        }

        @Override public void invalidate(Bounds next) {
            host.assertOwner();
            if (!isActive()) throw new IllegalStateException("UI geometry writer was released");
            viewport = Objects.requireNonNull(next);
            host.dirty().invalidate(scope, GEOMETRY);
        }

        void release() {
            worlds.remove(scope, this);
            reducedMotion = null;
            for (var registered : List.copyOf(targets.values())) {
                for (var subscription : List.copyOf(registered.subscriptions)) subscription.close();
            }
            layout.close();
        }
    }

    private static final class BodyTargets {
        final int slop;
        final Set<Subscription> subscriptions = new LinkedHashSet<>();

        BodyTargets(int slop) { this.slop = slop; }
    }
}
