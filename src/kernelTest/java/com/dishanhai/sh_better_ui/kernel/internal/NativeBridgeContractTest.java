// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.Platform.FORGE_1_20_1;
import static com.dishanhai.sh_better_ui.kernel.internal.TestModules.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeBridgeContractTest {
    @Test
    void rendersOnlyInTheMatchingNativeSurfaceAndStillCleansUpSkippedFrames() {
        try (var fixture = new Fixture()) {
            var calls = new ArrayList<String>();
            var scheduler = fixture.context.get().engine().scheduler();
            var screen = fixture.screen();
            var tree = fixture.host.mountTree(fixture.context.get(), screen, UiId.parse("example:tree"));
            scheduler.submit(UITask.ui(tree, UIPhase.RENDER_PREPARATION, UIPriority.NORMAL, () -> calls.add("prepare")));
            var draw = scheduler.submit(UITask.ui(tree, UIPhase.RENDER, UIPriority.NORMAL, () -> calls.add("screen")));
            var hud = scheduler.submit(UITask.ui(fixture.context.get().scope(), UIPhase.RENDER,
                    UIPriority.NORMAL, () -> calls.add("hud")));
            var timeout = scheduler.submit(UITask.ui(tree, UIPhase.RENDER, UIPriority.NORMAL,
                    () -> fail("skipped native render ran")).withTimeout(10));
            scheduler.submit(UITask.ui(tree, UIPhase.CLEANUP, UIPriority.NORMAL, () -> calls.add("cleanup")));
            assertFalse(fixture.host.nativeInput(fixture.host::canBeginNativeFrame));
            assertTrue(fixture.host.canBeginNativeFrame());
            fixture.host.beginNativeFrame(() -> {}, phase -> {});
            assertEquals(List.of("prepare"), calls);
            fixture.clock.set(10);
            fixture.host.finishNativeFrame();
            assertEquals(List.of("prepare", "cleanup"), calls);
            assertEquals(TaskHandle.Status.TIMED_OUT, timeout.status());
            assertEquals(TaskHandle.Status.PENDING, draw.status());

            fixture.host.beginNativeFrame(() -> {}, phase -> {});
            assertTrue(fixture.host.renderNativeFrame(screen));
            assertFalse(fixture.host.renderNativeFrame(screen));
            fixture.host.finishNativeFrame();
            assertEquals(List.of("prepare", "cleanup", "screen"), calls);
            assertEquals(TaskHandle.Status.PENDING, hud.status());

            fixture.host.beginNativeFrame(() -> {}, phase -> {});
            assertTrue(fixture.host.renderNativeFrame(null));
            fixture.host.finishNativeFrame();
            assertEquals(List.of("prepare", "cleanup", "screen", "hud"), calls);
        }
    }

    @Test
    void disconnectCancelsModuleOwnedNativeSessionWork() {
        try (var fixture = new Fixture()) {
            var physical = fixture.host.rootScope().openChild(ScopeKind.CLIENT_SESSION, UiId.parse("sh_better_ui:session"));
            var module = fixture.context.get();
            var session = fixture.host.mountSession(module, physical, UiId.parse("example:session"));
            var task = module.engine().scheduler().submit(UITask.ui(session, UIPhase.RENDER,
                    UIPriority.NORMAL, () -> fail("Disconnected HUD work ran")).delayed(10));
            physical.close();
            assertFalse(session.isOpen());
            assertEquals(TaskHandle.Status.CANCELLED, task.status());
            assertTrue(module.scope().isOpen());
        }
    }

    @Test
    void closesMountedTreeWithEitherItsPhysicalScreenOrModule() {
        try (var fixture = new Fixture()) {
            var screen = fixture.screen();
            var context = fixture.context.get();
            var id = UiId.parse("example:tree");
            var first = fixture.host.mountTree(context, screen, id);
            var child = first.openChild(ScopeKind.COMPONENT, UiId.parse("example:child"));
            var scheduler = context.engine().scheduler();
            var pending = scheduler.submit(UITask.ui(child, UIPhase.RENDER, UIPriority.NORMAL,
                    () -> fail("closed mount ran")));
            var cleanup = new ArrayList<String>();
            screen.track(() -> cleanup.add("screen"));
            first.track(() -> cleanup.add("tree"));
            child.track(() -> cleanup.add("child"));
            screen.close();
            assertEquals(List.of("child", "tree", "screen"), cleanup);
            assertEquals(TaskHandle.Status.CANCELLED, pending.status());
            var remount = fixture.host.mountTree(context, fixture.screen(), id);
            assertNotEquals(first.generation(), remount.generation());
            fixture.host.stopModule(context.descriptor().id());
            assertFalse(remount.isOpen());
        }
    }

    @Test
    void nativeStartupRejectsBrokenExtensionsAndKeepsIndependentModules() {
        var bad = candidate(module("bad").apiContractVersion(99).build());
        var missingId = UiId.parse("example:missing");
        var missing = candidate(module("missing").dependency(UiId.parse("example:absent"), VersionRange.ANY, true).build(),
                Set.of(), false, ctx -> fail("rejected module registered"), ctx -> fail("rejected module started"),
                ctx -> fail("rejected module stopped"));
        var dependent = candidate(module("dependent").dependency(missingId, VersionRange.ANY, true).build());
        var good = candidate(module("good").build());
        try (var host = new KernelHost(FORGE_1_20_1, new EnginePublication())) {
            host.startNative(List.of(bad, missing, dependent, good));
            assertEquals(EngineState.RUNNING, host.lifecycle().state());
            var snapshot = host.lifecycle().snapshot();
            assertEquals(ModuleLifecycle.RUNNING, snapshot.modules().get(UiId.parse("example:good")));
            assertEquals(ModuleLifecycle.BLOCKED, snapshot.modules().get(missingId));
            assertEquals(ModuleLifecycle.BLOCKED, snapshot.modules().get(dependent.module().descriptor().id()));
            assertTrue(snapshot.diagnostics().stream().anyMatch(value -> value.contains("example:bad")));
            assertTrue(snapshot.diagnostics().stream().anyMatch(value -> value.contains("example:missing")));
        }
    }

    @Test
    void nativeCaptureRequiresAndRetainsVanillaBlockingAuthority() {
        var claim = new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.POINTER_CAPTURE,
                UiId.parse("example:pointer"));
        var grants = Set.of(UIPermission.CAPTURE_INPUT, UIPermission.BLOCK_VANILLA_INPUT);
        var module = new AtomicReference<ModuleContext>();
        var released = new ArrayList<String>();
        try (var host = new KernelHost(FORGE_1_20_1, new EnginePublication())) {
            var builder = module("pointer").claimResource(claim);
            grants.forEach(builder::requestPermission);
            host.start(List.of(candidate(builder.build(), grants, false, ctx -> {}, module::set, ctx -> {})));
            var screen = host.rootScope().openChild(ScopeKind.SCREEN, UiId.parse("sh_better_ui:screen"));
            var ctx = module.get();
            var tree = host.mountTree(ctx, screen, UiId.parse("example:tree"));
            var request = new ResourceRequest(claim, tree, 0, 0, ConflictPolicy.REJECT);
            var lease = host.acquireNativePointer(host.moduleAccess(ctx), request, () -> released.add("released"));
            host.revokePermission(ctx.descriptor().id(), UIPermission.BLOCK_VANILLA_INPUT);
            assertEquals(OwnershipLease.Status.RELEASED, lease.status());
            assertEquals(List.of("released"), released);
            assertThrows(SecurityException.class,
                    () -> host.acquireNativePointer(host.moduleAccess(ctx), request, () -> fail("unauthorized capture")));
            try (var reservation = ctx.engine().ownership().acquire(request, () -> {})) {
                assertTrue(reservation.isActive());
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final AtomicReference<ModuleContext> context = new AtomicReference<>();
        final AtomicLong clock = new AtomicLong();
        final KernelHost host = new KernelHost(FORGE_1_20_1, new EnginePublication(), clock::get,
                Runnable::run, SchedulerBudget.DEFAULT);

        Fixture() {
            var permissions = Set.of(UIPermission.REGISTER_RENDER_PASS);
            host.start(List.of(candidate(module("native").requestPermission(UIPermission.REGISTER_RENDER_PASS).build(),
                    permissions, false, ctx -> {}, context::set, ctx -> {})));
        }

        ScopeHandle screen() {
            return host.rootScope().openChild(ScopeKind.SCREEN, UiId.parse("sh_better_ui:screen"));
        }

        @Override public void close() { host.close(); }
    }
}
