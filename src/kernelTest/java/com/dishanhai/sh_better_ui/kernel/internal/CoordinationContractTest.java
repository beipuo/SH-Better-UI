// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceClaim;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.dishanhai.sh_better_ui.kernel.api.UIPermission.*;
import static com.dishanhai.sh_better_ui.kernel.internal.TestModules.*;
import static org.junit.jupiter.api.Assertions.*;

class CoordinationContractTest {
    private static final ResourceClaim POINTER = claim(ResourceKind.POINTER_CAPTURE, "pointer");
    private static final ResourceClaim ALTERNATE_POINTER = claim(ResourceKind.POINTER_CAPTURE, "alternate_pointer");
    private static final ResourceClaim FOCUS = claim(ResourceKind.KEYBOARD_FOCUS, "focus");
    private static final ResourceClaim OTHER_FOCUS = claim(ResourceKind.KEYBOARD_FOCUS, "other_focus");
    private static final ResourceClaim ANIMATION = claim(ResourceKind.ANIMATION_PROPERTY, "opacity");

    @Test
    void permissionIsInheritedFromItsBoundaryAndRevocationCancelsQueuedWorkAndLeases() {
        try (var fixture = new Fixture(SchedulerBudget.DEFAULT, Set.of(CAPTURE_INPUT, REGISTER_RENDER_PASS))) {
            var left = fixture.screen("left");
            var right = fixture.screen("right");
            var child = fixture.component(left);
            var ui = fixture.ui();
            fixture.host.restrictPermission(fixture.id(), CAPTURE_INPUT, left);
            assertTrue(ui.permissions().check(new PermissionRequest(CAPTURE_INPUT, child)).allowed());
            assertFalse(ui.permissions().check(new PermissionRequest(CAPTURE_INPUT, right)).allowed());
            assertFalse(fixture.publication.current().permissions().check(new PermissionRequest(CAPTURE_INPUT, left)).allowed());

            var released = new AtomicInteger();
            var lease = fixture.lease(POINTER, left, 0, 0, ConflictPolicy.REJECT, released::incrementAndGet);
            assertThrows(SecurityException.class,
                    () -> fixture.lease(POINTER, right, Integer.MAX_VALUE, 0, ConflictPolicy.REPLACE, () -> {}));
            var task = ui.scheduler().submit(UITask.ui(left, UIPhase.RENDER, UIPriority.CRITICAL, () -> fail("revoked")));
            fixture.host.revokePermission(fixture.id(), REGISTER_RENDER_PASS);
            fixture.host.revokePermission(fixture.id(), CAPTURE_INPUT);
            assertEquals(TaskHandle.Status.CANCELLED, task.status());
            assertFalse(lease.isActive());
            assertEquals(1, released.get());
            lease.close();
            assertEquals(1, released.get());
            assertThrows(SecurityException.class, () -> left.claim(POINTER, () -> {}));
            assertTrue(fixture.host.lifecycle().snapshot().diagnostics().stream()
                    .anyMatch(message -> message.contains("test-mod.jar") && message.contains("CAPTURE_INPUT")));
        }
    }

    @Test
    void focusIsExclusivePerScreenAndStaleReplacementHandlesCannotReleaseNewOwners() {
        try (var fixture = new Fixture()) {
            var first = fixture.screen("one");
            var second = fixture.screen("two");
            var release = new ArrayList<String>();
            var original = fixture.lease(FOCUS, fixture.component(first), 1, 0, ConflictPolicy.REJECT,
                    () -> release.add("first"));
            assertThrows(IllegalArgumentException.class,
                    () -> fixture.lease(FOCUS, first, 10, 0, ConflictPolicy.MERGE, () -> {}));
            assertThrows(IllegalStateException.class,
                    () -> fixture.lease(OTHER_FOCUS, first, 0, 0, ConflictPolicy.REPLACE, () -> {}));
            var unrelated = fixture.lease(FOCUS, second, 0, 0, ConflictPolicy.REJECT, () -> release.add("second"));
            var replacement = fixture.lease(OTHER_FOCUS, first, 2, 0, ConflictPolicy.REPLACE, () -> release.add("new"));
            original.close();
            assertTrue(replacement.isActive());
            assertTrue(unrelated.isActive());
            first.close();
            assertEquals(List.of("first", "new"), release);
        }
    }

    @Test
    void pointerCaptureUsesOneNativeSlotRegardlessOfClaimName() {
        try (var fixture = new Fixture(SchedulerBudget.DEFAULT, Set.of(CAPTURE_INPUT))) {
            var first = fixture.screen("primary_pointer");
            var second = fixture.screen("secondary_pointer");
            var active = fixture.lease(POINTER, first, 0, 0, ConflictPolicy.REJECT, () -> {});
            assertThrows(IllegalStateException.class, () ->
                    fixture.lease(ALTERNATE_POINTER, second, 0, 0, ConflictPolicy.REJECT, () -> {}));
            assertTrue(active.isActive());
        }
    }

    @Test
    void duplicateCleanupCallbackCannotDetachTheExistingLease() {
        try (var fixture = new Fixture(SchedulerBudget.DEFAULT, Set.of(CAPTURE_INPUT))) {
            var screen = fixture.screen("same_callback");
            var releases = new AtomicInteger();
            AutoCloseable release = releases::incrementAndGet;
            var original = fixture.lease(POINTER, screen, 0, 0, ConflictPolicy.REJECT, release);
            assertThrows(IllegalStateException.class,
                    () -> fixture.lease(FOCUS, screen, 1, 0, ConflictPolicy.REJECT, release));
            assertTrue(original.isActive());
            screen.close();
            assertEquals(1, releases.get());
        }
    }

    @Test
    void internalDiagnosticsRequireAGrantAndPreviouslyAcquiredViewsHonorRevocation() {
        try (var fixture = new Fixture(SchedulerBudget.DEFAULT, Set.of(ACCESS_INTERNAL_DIAGNOSTICS))) {
            var context = fixture.ui();
            var lifecycle = context.lifecycle();
            var global = fixture.publication.current().lifecycle();
            assertThrows(SecurityException.class, () -> context.permissions().require(
                    new PermissionRequest(BLOCK_VANILLA_INPUT, fixture.screen("denied"))));
            assertFalse(lifecycle.snapshot().diagnostics().isEmpty());
            assertTrue(global.snapshot().diagnostics().isEmpty());
            fixture.host.revokePermission(fixture.id(), ACCESS_INTERNAL_DIAGNOSTICS);
            assertTrue(lifecycle.snapshot().diagnostics().isEmpty());
            assertEquals(EngineState.RUNNING, lifecycle.state());
        }
    }

    @Test
    void animationRetargetKeepsItsTrackAndTimedLeasePromotesTheBestQueuedRequest() {
        try (var fixture = new Fixture()) {
            var component = fixture.component(fixture.screen("animation"));
            var release = new ArrayList<String>();
            var active = fixture.lease(ANIMATION, component, 0, 10, ConflictPolicy.REJECT, () -> release.add("original"));
            var low = fixture.lease(ANIMATION, component, 1, 0, ConflictPolicy.QUEUE, () -> release.add("low"));
            var high = fixture.lease(ANIMATION, component, 2, 0, ConflictPolicy.QUEUE, () -> release.add("high"));
            assertEquals(OwnershipLease.Status.QUEUED, high.status());
            var retarget = fixture.lease(ANIMATION, component, 0, 10, ConflictPolicy.RETARGET, () -> fail("new track"));
            assertSame(active, retarget);
            fixture.clock.set(10);
            fixture.host.runFrame();
            assertEquals(List.of("original"), release);
            assertTrue(high.isActive());
            assertFalse(low.isActive());
            high.close();
            assertTrue(low.isActive());
            component.close();
            assertEquals(List.of("original", "high", "low"), release);
        }
    }

    @Test
    void narrowedForeignScopePermissionIsInheritedAndCancellationRechecksTheGrant() {
        try (var fixture = new Fixture(SchedulerBudget.DEFAULT, Set.of(MODIFY_FOREIGN_SCOPE))) {
            var hostScreen = fixture.host.rootScope().openChild(ScopeKind.SCREEN, UiId.parse("sh_better_ui:shared"));
            var child = hostScreen.openChild(ScopeKind.UI_TREE, UiId.parse("sh_better_ui:tree"));
            fixture.host.restrictPermission(fixture.id(), MODIFY_FOREIGN_SCOPE, child);
            assertTrue(fixture.ui().permissions().check(new PermissionRequest(MODIFY_FOREIGN_SCOPE, child)).allowed());
            assertFalse(fixture.ui().permissions().check(new PermissionRequest(MODIFY_FOREIGN_SCOPE, hostScreen)).allowed());
            var impostor = new CopiedScope(child);
            assertEquals(PermissionDecision.INVALID_SCOPE,
                    fixture.ui().permissions().check(new PermissionRequest(MODIFY_FOREIGN_SCOPE, impostor)));
            var task = fixture.ui().scheduler().submit(UITask.ui(child, UIPhase.STATE_MUTATION,
                    UIPriority.CRITICAL, () -> fail("permission revoked")));
            fixture.host.revokePermission(fixture.id(), MODIFY_FOREIGN_SCOPE);
            assertEquals(TaskHandle.Status.CANCELLED, task.status());
        }
    }

    @Test
    void commandValidatesStateDirtyWorkAndEffectCapacityBeforePublishing() {
        try (var fixture = new Fixture(new SchedulerBudget(1, 2, 1, 8, 8, 8, 100), Set.of())) {
            var ui = fixture.ui();
            var screen = fixture.screen("command");
            var component = fixture.component(screen);
            var key = StateKey.of(UiId.parse("example:clicks"), Integer.class, 0, StateKey.Kind.SCREEN);
            ui.states().define(key);
            var blocked = ui.scheduler().submit(UITask.ui(screen, UIPhase.ANIMATION, UIPriority.NORMAL, () -> {}));
            var applied = new AtomicInteger();
            assertThrows(IllegalStateException.class, () -> ui.commands().transaction(tx -> {
                tx.set(screen, key, 1).invalidate(component, Set.of(UIDirtyFlag.PAINT));
                tx.requestAnimation(component, applied::incrementAndGet);
            }));
            assertEquals(0, ui.states().get(screen, key));
            assertTrue(fixture.host.dirty().drain(UIDirtyFlag.PAINT).isEmpty());
            blocked.cancel();

            ui.states().observe(screen, key, change -> {
                assertEquals(List.of(component.parent(), component), fixture.host.dirty().drain(UIDirtyFlag.PAINT));
                assertEquals(1, ui.scheduler().snapshot().queued());
            });
            var result = ui.commands().transaction(tx -> tx.set(screen, key, 4)
                    .invalidate(component, Set.of(UIDirtyFlag.PAINT))
                    .requestAnimation(component, applied::incrementAndGet));
            assertEquals(4, ui.states().get(screen, key));
            assertEquals(ui.states().snapshot(screen).version(), result.stateVersion());
            assertEquals(0, applied.get());
            fixture.host.runFrame();
            assertEquals(1, applied.get());
            assertEquals(TaskHandle.Status.SUCCEEDED, result.effects().get(0).status());
        }
    }

    @Test
    void typedAsyncCommandsHaveReceiptsAndEffectFailureDoesNotUndoCommittedState() {
        try (var fixture = new Fixture()) {
            var ui = fixture.ui();
            var screen = fixture.screen("typed");
            var value = StateKey.of(UiId.parse("example:value"), Integer.class, 0, StateKey.Kind.SCREEN);
            ui.states().define(value);
            var command = new CommandKey<>(UiId.parse("example:set_value"), Integer.class);
            ui.commands().register(command, (scope, payload, tx) -> {
                assertThrows(SecurityException.class, scope::close);
                tx.set(scope, value, payload).afterCommit(UITask.ui(scope, UIPhase.ANIMATION, UIPriority.NORMAL,
                        () -> { throw new IllegalArgumentException("effect failed"); }));
            });
            var receipt = ui.commands().submit(new UICommand<>(command, screen, 12), 20);
            assertEquals(TaskHandle.Status.PENDING, receipt.status());
            fixture.host.runFrame();
            assertEquals(TaskHandle.Status.SUCCEEDED, receipt.status());
            assertEquals(12, ui.states().get(screen, value));
            assertEquals(TaskHandle.Status.FAILED, receipt.result().orElseThrow().effects().get(0).status());
            var expires = ui.commands().submit(new UICommand<>(command, screen, 13), 5);
            fixture.clock.set(5);
            fixture.host.runFrame();
            assertEquals(TaskHandle.Status.TIMED_OUT, expires.status());
            assertEquals(12, ui.states().get(screen, value));
        }
    }

    @Test
    void commandPreparationRejectsReentrantMutationsAndDirectSideEffects() {
        try (var fixture = new Fixture()) {
            var ui = fixture.ui();
            var screen = fixture.screen("prepare");
            var value = StateKey.of(UiId.parse("example:prepare_value"), Integer.class, 0, StateKey.Kind.SCREEN);
            ui.states().define(value);
            assertThrows(IllegalStateException.class, () -> ui.commands().transaction(tx -> {
                tx.set(screen, value, 1);
                ui.scheduler().submit(UITask.ui(screen, UIPhase.STATE_MUTATION, UIPriority.NORMAL, () -> fail("side effect")));
            }));
            assertThrows(IllegalStateException.class, () -> ui.commands().transaction(tx -> {
                tx.set(screen, value, 1);
                screen.close();
            }));
            assertTrue(screen.isOpen());
            assertEquals(0, ui.states().get(screen, value));
            assertEquals(0, ui.scheduler().snapshot().queued());
        }
    }

    @Test
    void routedEventsKeepFlagsSeparateAndReleaseSubscriptionsOnScopeClose() {
        try (var fixture = new Fixture()) {
            var ui = fixture.ui();
            var screen = fixture.screen("events");
            var component = fixture.component(screen);
            var event = new UIEventType<>(UiId.parse("example:event"), Integer.class, true);
            var calls = new ArrayList<String>();
            ui.events().subscribe(event, screen, UIEventPhase.CAPTURE, 0, seen -> calls.add("capture"));
            ui.events().subscribe(event, component, UIEventPhase.TARGET, 1, seen -> {
                assertEquals(fixture.id(), seen.source());
                assertEquals(component.generation(), seen.currentTarget().generation());
                calls.add("target");
                seen.markHandled();
                seen.preventDefault();
                seen.stopPropagation();
            });
            var subscription = ui.events().subscribe(event, component, UIEventPhase.TARGET, 0,
                    seen -> calls.add("same target"));
            ui.events().subscribe(event, screen, UIEventPhase.BUBBLE, 100, seen -> fail("stopped bubble"));
            var result = ui.events().emit(event, component, 42);
            assertEquals(List.of("capture", "target", "same target"), calls);
            assertTrue(result.handled());
            assertTrue(result.defaultPrevented());
            assertTrue(result.propagationStopped());
            assertFalse(result.immediatePropagationStopped());
            assertFalse(result.consumed());
            component.close();
            assertFalse(subscription.isActive());
        }
    }

    @Test
    void dispatchUsesStablePriorityOrderDuringReentrantSubscriptionChanges() {
        try (var fixture = new Fixture()) {
            var events = fixture.ui().events();
            var screen = fixture.screen("subscription_order");
            var child = fixture.component(screen);
            var type = new UIEventType<>(UiId.parse("example:subscription_order"), String.class, false);
            var calls = new ArrayList<String>();
            var removed = events.subscribe(type, child, UIEventPhase.TARGET, 0,
                    event -> calls.add("closed/" + event.payload()));
            events.subscribe(type, child, UIEventPhase.TARGET, 10, event -> {
                calls.add("first/" + event.payload());
                if (event.payload().equals("outer")) {
                    removed.close();
                    events.subscribe(type, child, UIEventPhase.TARGET, 20, next -> calls.add("new/" + next.payload()));
                    events.emit(type, child, "nested");
                }
            });
            events.subscribe(type, child, UIEventPhase.TARGET, 10, event -> calls.add("second/" + event.payload()));
            events.emit(type, child, "outer");
            events.emit(type, child, "next");
            assertEquals(List.of("first/outer", "new/nested", "first/nested", "second/nested", "second/outer",
                    "new/next", "first/next", "second/next"), calls);
        }
    }

    @Test
    void subscriptionSnapshotsAreReusedUntilTheirKeyChanges() {
        var scope = new ScopeLifecycle((owner, failure) -> fail(failure)).root();
        var subscriptions = new ScopedSubscriptions<String, String>(() -> {});
        subscriptions.add("key", scope, scope, "first");
        var snapshot = subscriptions.forKey("key");
        assertSame(snapshot, subscriptions.forKey("key"));
        subscriptions.add("unrelated", scope, scope, "other");
        assertSame(snapshot, subscriptions.forKey("key"));
        var added = subscriptions.add("key", scope, scope, "second");
        var updated = subscriptions.forKey("key");
        assertNotSame(snapshot, updated);
        assertEquals(2, updated.size());
        assertThrows(UnsupportedOperationException.class, updated::clear);
        added.close();
        assertEquals(snapshot, subscriptions.forKey("key"));
        assertNotSame(updated, subscriptions.forKey("key"));
        subscriptions.remove(scope);
        assertTrue(subscriptions.forKey("key").isEmpty());
    }

    @Test
    void onlyAuthorizedNativeInputCanBlockVanillaAndConsumeStopsTheCurrentNode() {
        try (var fixture = new Fixture(SchedulerBudget.DEFAULT, Set.of(BLOCK_VANILLA_INPUT))) {
            var ui = fixture.ui();
            var screen = fixture.screen("native");
            var child = fixture.component(screen);
            var type = new UIEventType<>(UiId.parse("example:native"), String.class, true);
            var other = new AtomicInteger();
            ui.events().subscribe(type, child, UIEventPhase.TARGET, 10, UIEvent::consume);
            ui.events().subscribe(type, child, UIEventPhase.TARGET, 0, event -> other.incrementAndGet());
            assertFalse(ui.events().emit(type, child, "normal").blockVanilla());
            var result = new AtomicReference<EventResult>();
            ui.scheduler().submit(UITask.ui(screen, UIPhase.INPUT, UIPriority.NORMAL,
                    () -> result.set(fixture.host.routeInput(type, screen, child, "native"))));
            fixture.host.runFrame();
            assertTrue(result.get().consumed());
            assertTrue(result.get().blockVanilla());
            assertTrue(result.get().immediatePropagationStopped());
            assertFalse(result.get().handled());
            assertEquals(0, other.get());
            fixture.host.revokePermission(fixture.id(), BLOCK_VANILLA_INPUT);
            ui.scheduler().submit(UITask.ui(screen, UIPhase.INPUT, UIPriority.NORMAL,
                    () -> result.set(fixture.host.routeInput(type, screen, child, "revoked"))));
            fixture.host.runFrame();
            assertFalse(result.get().blockVanilla());
            assertEquals(1, other.get());
        }
    }

    @Test
    void eventRecursionAndListenerFailuresAreIsolated() {
        try (var fixture = new Fixture()) {
            var events = fixture.ui().events();
            var screen = fixture.screen("recursive");
            var type = new UIEventType<>(UiId.parse("example:recursive"), Integer.class, false);
            var delivered = new AtomicInteger();
            events.subscribe(type, screen, UIEventPhase.TARGET, 1, event -> {
                event.preventDefault();
                events.emit(type, screen, event.payload() + 1);
            });
            events.subscribe(type, screen, UIEventPhase.TARGET, 0, event -> delivered.incrementAndGet());
            assertFalse(events.emit(type, screen, 0).defaultPrevented());
            assertTrue(delivered.get() > 1 && delivered.get() <= 32);
            assertTrue(fixture.host.lifecycle().snapshot().diagnostics().stream()
                    .anyMatch(message -> message.contains("event dispatch limit")));
        }
    }

    private static ResourceClaim claim(ResourceKind kind, String path) {
        return new ResourceClaim(kind, new UiId("example", path));
    }

    private record CopiedScope(ScopeHandle actual) implements ScopeHandle {
        @Override public UiId id() { return actual.id(); }
        @Override public UiId owner() { return actual.owner(); }
        @Override public ScopeKind kind() { return actual.kind(); }
        @Override public long generation() { return actual.generation(); }
        @Override public boolean isOpen() { return actual.isOpen(); }
        @Override public ScopeHandle openChild(ScopeKind kind, UiId id) { throw new UnsupportedOperationException(); }
        @Override public void track(AutoCloseable resource) { throw new UnsupportedOperationException(); }
        @Override public OwnershipLease claim(ResourceClaim resource, AutoCloseable release) { throw new UnsupportedOperationException(); }
        @Override public void close() { throw new UnsupportedOperationException(); }
    }

    private static final class Fixture implements AutoCloseable {
        final AtomicLong clock = new AtomicLong();
        final AtomicReference<ModuleContext> context = new AtomicReference<>();
        final EnginePublication publication = new EnginePublication();
        final KernelHost host;

        Fixture() {
            this(SchedulerBudget.DEFAULT, Set.of());
        }

        Fixture(SchedulerBudget budget, Set<UIPermission> grants) {
            host = new KernelHost(ModuleDescriptor.Platform.FORGE_1_20_1, publication, clock::get,
                    runnable -> fail("unexpected worker"), budget);
            var module = module("coordination").claimResource(POINTER).claimResource(ALTERNATE_POINTER).claimResource(FOCUS)
                    .claimResource(OTHER_FOCUS).claimResource(ANIMATION);
            grants.forEach(module::requestPermission);
            host.start(List.of(candidate(module.build(), grants, false, ctx -> {}, context::set, ctx -> {})));
        }

        UIEngine ui() {
            return context.get().engine();
        }

        UiId id() {
            return context.get().descriptor().id();
        }

        ScopeHandle screen(String path) {
            return context.get().scope().openChild(ScopeKind.SCREEN, new UiId("example", path));
        }

        ScopeLifecycle.Scope component(ScopeHandle screen) {
            return (ScopeLifecycle.Scope) screen.openChild(ScopeKind.UI_TREE, UiId.parse("example:tree"))
                    .openChild(ScopeKind.COMPONENT, UiId.parse("example:child"));
        }

        OwnershipLease lease(ResourceClaim claim, ScopeHandle scope, int priority, long ttl,
                             ConflictPolicy policy, AutoCloseable release) {
            return ui().ownership().acquire(new ResourceRequest(claim, scope, priority, ttl, policy), release);
        }

        @Override
        public void close() {
            host.close();
        }
    }
}
