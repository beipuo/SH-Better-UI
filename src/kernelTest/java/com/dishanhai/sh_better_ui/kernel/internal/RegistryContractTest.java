// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static com.dishanhai.sh_better_ui.kernel.api.RegistrationConflictPolicy.*;
import static com.dishanhai.sh_better_ui.kernel.api.ServiceDescriptor.Visibility.*;
import static com.dishanhai.sh_better_ui.kernel.internal.TestModules.*;
import static org.junit.jupiter.api.Assertions.*;

class RegistryContractTest {
    private record TestService(String name) implements UIService {}
    private record WrongService() implements UIService {}

    private static final UiId ID = UiId.parse("example:service");
    private static final ServiceKey<TestService> KEY = new ServiceKey<>(ID, TestService.class, 1);
    private static final ServiceDescriptor<TestService> PUBLIC_SERVICE =
            new ServiceDescriptor<>(KEY, new UiVersion(1, 0, 0), PUBLIC, 0, true);

    @Test
    void freezePublishesTypedImmutableViewsWithVerifiedOrigins() {
        var owner = discover(candidate(module("owner").build())).get(0);
        var registry = new ServiceRegistry();
        var service = new TestService("service");
        var registrar = new AtomicReference<ServiceRegistrar>();
        var handle = new AtomicReference<RegistrationHandle>();
        registry.register(owner, services -> {
            registrar.set(services);
            handle.set(services.register(PUBLIC_SERVICE, service));
            assertThrows(IllegalStateException.class, () -> registry.capture(Set.of(owner.id())));
        });
        registry.freeze();
        APIRegistry view = view(registry, Set.of(owner.id()), null);
        assertSame(service, view.require(KEY));
        assertTrue(view.find(new ServiceKey<>(ID, TestService.class, 2)).isEmpty());
        assertTrue(view.find(new ServiceKey<>(ID, WrongService.class, 1)).isEmpty());
        assertEquals(owner.origin(), view.snapshot().entries().get(0).origin());
        assertEquals(java.util.List.of(PUBLIC_SERVICE), view.descriptors());
        assertThrows(UnsupportedOperationException.class, view.snapshot().entries()::clear);
        assertThrows(IllegalStateException.class, () -> registrar.get().register(PUBLIC_SERVICE, service));
        assertThrows(IllegalStateException.class, handle.get()::close);

        registry.remove(owner.id());
        assertTrue(view(registry, Set.of(owner.id()), null).find(KEY).isEmpty());
        assertTrue(view(registry, Set.of(owner.id()), null).snapshot().version() > view.snapshot().version());
        assertEquals(RegistrationHandle.Status.REVOKED, handle.get().status());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rejectsRawServiceMismatchForeignNamespaceAndIncompatibleDuplicates() {
        var owner = discover(candidate(module("owner").build())).get(0);
        var registry = new ServiceRegistry();
        registry.register(owner, services -> {
            var service = new TestService("valid");
            assertThrows(IllegalArgumentException.class,
                    () -> services.register((ServiceDescriptor) PUBLIC_SERVICE, new WrongService()));
            var foreign = new ServiceKey<>(UiId.parse("other:service"), TestService.class, 1);
            assertThrows(SecurityException.class, () -> services.register(
                    new ServiceDescriptor<>(foreign, new UiVersion(1, 0, 0), PUBLIC, 0, false), service));
            services.register(PUBLIC_SERVICE, service);
            assertThrows(IllegalStateException.class, () -> services.register(PUBLIC_SERVICE, service));
            var v2 = new ServiceKey<>(ID, TestService.class, 2);
            assertThrows(IllegalArgumentException.class, () -> services.register(
                    new ServiceDescriptor<>(v2, new UiVersion(1, 0, 0), PUBLIC, 0, false), service, KEEP_EXISTING));
            assertEquals(RegistrationHandle.Status.SKIPPED,
                    services.register(PUBLIC_SERVICE, service, KEEP_EXISTING).status());
            assertThrows(SecurityException.class, () -> services.register(PUBLIC_SERVICE, service, REPLACE_IF_ALLOWED));
        });
    }

    @Test
    void replacementRollsBackOnRegistrationFailureAndStaleHandlesAreInert() {
        var original = discover(candidate(module("owner").build())).get(0);
        var replacement = replacementModule("replacement");
        var registry = new ServiceRegistry();
        var old = new AtomicReference<RegistrationHandle>();
        var next = new AtomicReference<RegistrationHandle>();
        var originalService = new TestService("original");
        registry.register(original, services -> old.set(services.register(PUBLIC_SERVICE, originalService)));
        assertThrows(IllegalStateException.class, () -> registry.register(replacement, services -> {
            next.set(services.register(PUBLIC_SERVICE, new TestService("failed"), REPLACE_IF_ALLOWED));
            throw new IllegalStateException("registration failed");
        }));
        assertEquals(RegistrationHandle.Status.ACTIVE, old.get().status());
        assertEquals(RegistrationHandle.Status.REVOKED, next.get().status());
        var newService = new TestService("replacement");
        registry.register(replacement, services -> {
            services.register(PUBLIC_SERVICE, newService, REPLACE_IF_ALLOWED);
            old.get().close();
        });
        registry.freeze();
        assertEquals(RegistrationHandle.Status.REVOKED, old.get().status());
        assertSame(newService, view(registry, Set.of(original.id(), replacement.id()), null).require(KEY));
    }

    @Test
    void priorityTiesUseStableModuleIdentityAndWithdrawalIsIdempotent() {
        var a = replacementModule("a");
        var b = replacementModule("b");
        for (var modules : java.util.List.of(java.util.List.of(a, b), java.util.List.of(b, a))) {
            var registry = new ServiceRegistry();
            for (var module : modules) {
                registry.register(module, services -> services.register(PUBLIC_SERVICE,
                        new TestService(module.id().path()), SELECT_BY_PRIORITY));
            }
            registry.freeze();
            assertEquals("a", view(registry, Set.of(a.id(), b.id()), null).require(KEY).name());
        }

        var registry = new ServiceRegistry();
        registry.register(a, services -> {
            var handle = services.register(PUBLIC_SERVICE, new TestService("withdrawn"));
            handle.close();
            handle.close();
        });
        registry.freeze();
        assertTrue(view(registry, Set.of(a.id()), null).find(KEY).isEmpty());
    }

    @Test
    void modulePrivateServicesAreOnlyVisibleToTheirOwner() {
        var owner = discover(candidate(module("owner").build())).get(0);
        var peer = discover(candidate(module("peer").build())).get(0);
        var registry = new ServiceRegistry();
        registry.register(owner, services -> services.register(
                new ServiceDescriptor<>(KEY, new UiVersion(1, 0, 0), MODULE_PRIVATE, 0, false),
                new TestService("private")));
        registry.freeze();
        Set<UiId> active = Set.of(owner.id(), peer.id());
        assertTrue(view(registry, active, null).find(KEY).isEmpty());
        assertTrue(view(registry, active, peer).find(KEY).isEmpty());
        assertEquals("private", view(registry, active, owner).require(KEY).name());
    }

    @Test
    void priorityCannotOverrideAnIncumbentThatDeclinesReplacement() {
        var a = replacementModule("a");
        var b = replacementModule("b");
        var registry = new ServiceRegistry();
        var sealed = new ServiceDescriptor<>(KEY, new UiVersion(1, 0, 0), PUBLIC, 0, false);
        registry.register(a, services -> services.register(sealed, new TestService("protected")));
        registry.register(b, services -> assertThrows(IllegalStateException.class, () ->
                services.register(PUBLIC_SERVICE, new TestService("ignored"), SELECT_BY_PRIORITY)));
        registry.freeze();
        assertEquals("protected", view(registry, Set.of(a.id(), b.id()), null).require(KEY).name());
    }

    private static APIRegistry view(ServiceRegistry registry, Set<UiId> active, ModuleDiscovery.Module viewer) {
        var catalog = registry.capture(active);
        return ServiceRegistry.view(() -> catalog, viewer);
    }

    private static ModuleDiscovery.Module replacementModule(String path) {
        var permission = UIPermission.REPLACE_SERVICE;
        var descriptor = module(path).requestPermission(permission).build();
        return discover(candidate(descriptor, Set.of(permission), false, ctx -> {}, ctx -> {}, ctx -> {})).get(0);
    }
}
