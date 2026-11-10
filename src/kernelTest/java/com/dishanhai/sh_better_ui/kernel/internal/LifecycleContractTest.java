// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.Platform.FORGE_1_20_1;
import static com.dishanhai.sh_better_ui.kernel.internal.TestModules.*;
import static org.junit.jupiter.api.Assertions.*;

class LifecycleContractTest {
    private record ExampleService(String value) implements UIService {}

    private static final UiId PROVIDER = UiId.parse("example:provider");
    private static final CapabilityKey CAPABILITY = new CapabilityKey(UiId.parse("example:layout"));
    private static final UiVersion RELEASE = UiVersion.parse("1.0.0");
    private static final ServiceKey<ExampleService> KEY = new ServiceKey<>(UiId.parse("example:service"),
            ExampleService.class, 1);
    private static final ServiceDescriptor<ExampleService> SERVICE =
            new ServiceDescriptor<>(KEY, RELEASE, ServiceDescriptor.Visibility.PUBLIC, 0, false);

    @Test
    void publishesOnlyAfterOrderedInitializationAndRevokesViewsBeforeReverseShutdown() {
        var slot = new EnginePublication();
        var host = new KernelHost(FORGE_1_20_1, slot);
        var log = new ArrayList<String>();
        var provider = candidate(module("provider").providedCapability(CAPABILITY, RELEASE).build(), Set.of(), false,
                ctx -> {
                    assertNull(slot.current());
                    assertEquals(EngineState.REGISTERING, host.lifecycle().state());
                    assertThrows(IllegalStateException.class, ctx::engine);
                    ctx.services().register(SERVICE, new ExampleService("ready"));
                    ctx.scope().track(() -> log.add("provider cleanup"));
                    log.add("provider register");
                },
                ctx -> {
                    assertNull(slot.current());
                    assertEquals(EngineState.INITIALIZING, ctx.engine().lifecycle().state());
                    log.add("provider start");
                }, ctx -> log.add("provider stop"));
        var consumer = candidate(module("consumer").dependency(PROVIDER, VersionRange.ANY, true).build(),
                Set.of(), false, ctx -> log.add("consumer register"),
                ctx -> {
                    assertEquals("ready", ctx.engine().require(KEY).value());
                    assertTrue(ctx.engine().capabilities().supports(CAPABILITY));
                    assertThrows(IllegalStateException.class, ctx::services);
                    ctx.scope().track(() -> log.add("consumer cleanup"));
                    log.add("consumer start");
                },
                ctx -> {
                    assertNull(slot.current());
                    assertFalse(ctx.scope().isOpen());
                    assertThrows(IllegalStateException.class, ctx::engine);
                    log.add("consumer stop");
                });

        host.start(List.of(consumer, provider));
        UIEngine engine = slot.current();
        assertEquals(EngineState.RUNNING, engine.lifecycle().state());
        assertEquals(ModuleLifecycle.RUNNING, engine.lifecycle().snapshot().modules().get(PROVIDER));
        assertEquals("ready", engine.require(KEY).value());
        assertTrue(engine.capabilities().supports(CAPABILITY));
        var registry = engine.registry();
        var capabilities = engine.capabilities();
        var snapshot = registry.snapshot();
        assertThrows(IllegalStateException.class, () -> host.start(List.of()));
        host.close();
        host.close();

        assertNull(slot.current());
        assertEquals(EngineState.TERMINATED, engine.lifecycle().state());
        assertThrows(IllegalStateException.class, () -> registry.find(KEY));
        assertThrows(IllegalStateException.class, () -> capabilities.supports(CAPABILITY));
        assertEquals(1, snapshot.entries().size());
        assertEquals(List.of("provider register", "consumer register", "provider start", "consumer start",
                "consumer stop", "consumer cleanup", "provider stop", "provider cleanup"), log);
    }

    @Test
    void failedModuleIsCleanedAndHardDependentsAreBlockedWhileOptionalConsumersDegrade() {
        var slot = new EnginePublication();
        var host = new KernelHost(FORGE_1_20_1, slot);
        var log = new ArrayList<String>();
        var broken = candidate(module("a_broken").providedCapability(CAPABILITY, RELEASE).build(), Set.of(), false,
                ctx -> {
                    ctx.services().register(SERVICE, new ExampleService("failed"));
                    ctx.scope().track(() -> log.add("broken cleanup"));
                },
                ctx -> { throw new IllegalStateException("init broken"); },
                ctx -> log.add("broken stop"));
        var fallback = candidate(module("b_fallback").providedCapability(CAPABILITY,
                new ModuleDescriptor.CapabilityOffer(RELEASE, 0, true)).build());
        var required = candidate(module("c_required").requiredCapability(CAPABILITY, VersionRange.ANY).build(),
                Set.of(), false, ctx -> ctx.scope().track(() -> log.add("blocked cleanup")),
                ctx -> fail("hard dependent started"), ctx -> fail("unstarted dependent stopped"));
        var optional = candidate(module("d_optional").optionalCapability(CAPABILITY, VersionRange.ANY).build(),
                Set.of(), false, ctx -> {}, ctx -> {
                    assertTrue(ctx.engine().find(KEY).isEmpty());
                    assertEquals(CapabilityStatus.DEGRADED, ctx.engine().capabilities().status(CAPABILITY));
                    log.add("optional start");
                }, ctx -> {});

        host.start(List.of(required, optional, broken, fallback));
        var state = slot.current().lifecycle().snapshot();
        assertEquals(ModuleLifecycle.FAILED, state.modules().get(broken.module().descriptor().id()));
        assertEquals(ModuleLifecycle.BLOCKED, state.modules().get(required.module().descriptor().id()));
        assertEquals(ModuleLifecycle.RUNNING, state.modules().get(optional.module().descriptor().id()));
        assertTrue(state.diagnostics().isEmpty());
        assertTrue(host.lifecycle().snapshot().diagnostics().stream().anyMatch(line -> line.contains("init broken")));
        assertEquals(List.of("broken stop", "broken cleanup", "blocked cleanup", "optional start"), log);
        host.close();
    }

    @Test
    void replacementCannotHideAStartedServiceUntilItRunsAndRestoresItsPredecessorOnRemoval() {
        var slot = new EnginePublication();
        var replaceable = new ServiceDescriptor<>(KEY, RELEASE, ServiceDescriptor.Visibility.PUBLIC, 0, true);
        var originalHandle = new AtomicReference<RegistrationHandle>();
        var backupHandle = new AtomicReference<RegistrationHandle>();
        var brokenHandle = new AtomicReference<RegistrationHandle>();
        var original = candidate(module("a_original").build(), Set.of(), false,
                ctx -> originalHandle.set(ctx.services().register(replaceable, new ExampleService("original"))),
                ctx -> assertEquals("original", ctx.engine().require(KEY).value()), ctx -> {});
        var replace = UIPermission.REPLACE_SERVICE;
        var policy = RegistrationConflictPolicy.REPLACE_IF_ALLOWED;
        var backup = candidate(module("b_backup").requestPermission(replace).build(), Set.of(replace), false,
                ctx -> backupHandle.set(ctx.services().register(replaceable, new ExampleService("backup"), policy)),
                ctx -> assertEquals("backup", ctx.engine().require(KEY).value()), ctx -> {});
        var broken = candidate(module("c_broken").requestPermission(replace).build(), Set.of(replace), false,
                ctx -> brokenHandle.set(ctx.services().register(replaceable, new ExampleService("broken"), policy)),
                ctx -> { throw new IllegalStateException("Replacement failed to initialize"); }, ctx -> {});

        try (var host = new KernelHost(FORGE_1_20_1, slot)) {
            host.startNative(List.of(broken, backup, original));
            var registry = slot.current().registry();
            var before = registry.snapshot();
            assertEquals("backup", registry.require(KEY).value());
            assertEquals(backup.module().descriptor().id(), before.entries().get(0).origin().moduleId());
            assertEquals(RegistrationHandle.Status.ACTIVE, backupHandle.get().status());
            assertEquals(RegistrationHandle.Status.REVOKED, brokenHandle.get().status());

            host.stopModule(backup.module().descriptor().id());
            var after = registry.snapshot();
            assertEquals("original", registry.require(KEY).value());
            assertEquals(original.module().descriptor().id(), after.entries().get(0).origin().moduleId());
            assertEquals(RegistrationHandle.Status.ACTIVE, originalHandle.get().status());
            assertEquals(RegistrationHandle.Status.REVOKED, backupHandle.get().status());
            assertTrue(after.version() > before.version());
            host.stopModule(original.module().descriptor().id());
            assertTrue(registry.find(KEY).isEmpty());
        }
    }

    @Test
    void runtimeStopRemovesTheTransitiveModuleSetInOneSnapshot() throws Exception {
        var slot = new EnginePublication();
        var host = new KernelHost(FORGE_1_20_1, slot);
        var consumerKey = new ServiceKey<>(UiId.parse("example:consumer_service"), ExampleService.class, 1);
        var ctxRef = new AtomicReference<ModuleContext>();
        var log = new ArrayList<String>();
        var provider = candidate(module("provider").providedCapability(CAPABILITY, RELEASE).build(), Set.of(), false,
                ctx -> ctx.services().register(SERVICE, new ExampleService("provider")), ctx -> {},
                ctx -> log.add("provider stop"));
        var consumer = candidate(module("consumer").dependency(PROVIDER, VersionRange.ANY, true).build(),
                Set.of(), false, ctx -> ctx.services().register(
                        new ServiceDescriptor<>(consumerKey, RELEASE, ServiceDescriptor.Visibility.PUBLIC, 0, false),
                        new ExampleService("consumer")), ctxRef::set, ctx -> log.add("consumer stop"));
        host.start(List.of(consumer, provider));
        var engine = slot.current();
        var moduleEngine = ctxRef.get().engine();
        var registry = engine.registry();
        var readerReady = new CountDownLatch(1);
        var publicationDone = new CountDownLatch(1);
        var reader = new FutureTask<>(() -> {
            assertEquals(2, registry.snapshot().entries().size());
            readerReady.countDown();
            do {
                int size = registry.snapshot().entries().size();
                assertTrue(size == 2 || size == 0, "observed a partial module stop");
            } while (publicationDone.getCount() > 0 && !Thread.currentThread().isInterrupted());
            assertEquals(0, registry.snapshot().entries().size());
            return null;
        });
        var thread = new Thread(reader, "kernel-snapshot-reader");
        thread.start();
        try {
            assertTrue(readerReady.await(5, TimeUnit.SECONDS));
            host.stopModule(PROVIDER);
            publicationDone.countDown();
            reader.get(5, TimeUnit.SECONDS);
        } finally {
            publicationDone.countDown();
            reader.cancel(true);
            host.close();
        }
        assertEquals(List.of("consumer stop", "provider stop"), log);
        assertThrows(IllegalStateException.class, moduleEngine.registry()::snapshot);
    }

    @Test
    void coreFailureAndDuplicateHostsCannotPublishOrLeakResources() {
        var slot = new EnginePublication();
        var first = new KernelHost(FORGE_1_20_1, slot);
        var second = new KernelHost(FORGE_1_20_1, slot);
        first.start(List.of());
        var running = slot.current();
        assertThrows(IllegalStateException.class, () -> second.start(List.of()));
        assertEquals(EngineState.FAILED, second.lifecycle().state());
        assertSame(running, slot.current());
        second.close();
        first.close();

        var failures = new ArrayList<String>();
        var broken = new KernelHost(FORGE_1_20_1, slot);
        var core = candidate(module("core").initializationPhase(ModuleDescriptor.InitializationPhase.CORE).build(),
                Set.of(), true, ctx -> ctx.scope().track(() -> failures.add("core cleanup")),
                ctx -> { throw new IllegalStateException("core failed"); }, ctx -> failures.add("core stop"));
        assertThrows(IllegalStateException.class, () -> broken.start(List.of(core)));
        assertNull(slot.current());
        assertEquals(EngineState.FAILED, broken.lifecycle().state());
        assertEquals(List.of("core stop", "core cleanup"), failures);
        broken.close();
        assertEquals(EngineState.TERMINATED, broken.lifecycle().state());
    }

    @Test
    void runtimeCoreFailureWithdrawsTheEngineAndReleasesEveryScope() {
        var slot = new EnginePublication();
        var host = new KernelHost(FORGE_1_20_1, slot);
        var log = new ArrayList<String>();
        var core = candidate(module("core").initializationPhase(ModuleDescriptor.InitializationPhase.CORE).build(),
                Set.of(), true, ctx -> ctx.scope().track(() -> log.add("core cleanup")), ctx -> {},
                ctx -> log.add("core stop"));
        host.start(List.of(core));
        UIEngine engine = slot.current();
        var reload = host.rootScope().openChild(ScopeKind.RESOURCE_RELOAD, UiId.parse("sh_better_ui:reload"));
        reload.track(() -> log.add("reload cleanup"));
        UiId coreId = core.module().descriptor().id();
        host.failModule(coreId, new IllegalStateException("core runtime failure"));
        assertNull(slot.current());
        assertFalse(reload.isOpen());
        assertEquals(EngineState.FAILED, engine.lifecycle().state());
        assertEquals(ModuleLifecycle.FAILED, engine.lifecycle().snapshot().modules().get(coreId));
        assertEquals(List.of("core stop", "core cleanup", "reload cleanup"), log);
        host.close();
    }
}
