// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;

import java.util.List;
import java.util.function.Supplier;

/** Live read-only facets. Each query uses one immutable publication. */
final class EngineAccess implements UIEngine {
    record Published(ServiceRegistry.Catalog services, CapabilityIndex capabilities) {}

    record Facets(UIScheduler scheduler, UIInvalidation invalidations, UIConfigCenter configs, UIStateStore states,
                  UIPermissionManager permissions, UICommandBus commands, UIEventBus events,
                  UIResourceOwnership ownership) {}

    private final UILifecycle lifecycle;
    private final Supplier<Published> source;
    private final APIRegistry registry;
    private final Facets facets;
    private final CapabilityResolver capabilities = new CapabilityResolver() {
        @Override
        public CapabilityStatus status(CapabilityKey key) {
            return current().capabilities().status(key);
        }

        @Override
        public List<CapabilityDescriptor> available() {
            return current().capabilities().available();
        }

        @Override
        public CapabilityResolution resolve(CapabilityRequest request) {
            return current().capabilities().resolve(request);
        }
    };

    EngineAccess(UILifecycle lifecycle, ModuleDiscovery.Module viewer, Supplier<Published> source, Facets facets) {
        this.lifecycle = lifecycle;
        this.source = source;
        this.facets = facets;
        registry = ServiceRegistry.view(() -> current().services(), viewer);
    }

    @Override
    public APIRegistry registry() {
        return registry;
    }

    @Override
    public CapabilityResolver capabilities() {
        return capabilities;
    }

    @Override
    public UILifecycle lifecycle() {
        return lifecycle;
    }

    @Override
    public UIScheduler scheduler() {
        current();
        return facets.scheduler();
    }

    @Override
    public UIInvalidation invalidations() {
        current();
        return facets.invalidations();
    }

    @Override
    public UIConfigCenter configs() {
        current();
        return facets.configs();
    }

    @Override
    public UIStateStore states() {
        current();
        return facets.states();
    }

    @Override
    public UIPermissionManager permissions() {
        current();
        return facets.permissions();
    }

    @Override
    public UICommandBus commands() {
        current();
        return facets.commands();
    }

    @Override
    public UIEventBus events() {
        current();
        return facets.events();
    }

    @Override
    public UIResourceOwnership ownership() {
        current();
        return facets.ownership();
    }

    private Published current() {
        Published view = source.get();
        if (view == null) {
            throw new IllegalStateException("UI services are unavailable in the current engine or module lifetime");
        }
        return view;
    }
}
