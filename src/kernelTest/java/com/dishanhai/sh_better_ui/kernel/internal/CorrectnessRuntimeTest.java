// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.*;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class CorrectnessRuntimeTest {
    private KernelHost host;
    private CorrectnessRuntime engine;
    private ModuleContext module, other;
    private static final Rect VIEW = new Rect(0, 0, 400, 240, Space.GUI);

    @BeforeEach void start() {
        host = new KernelHost(ModuleDescriptor.Platform.FORGE_1_20_1, new EnginePublication());
        engine = new CorrectnessRuntime(host);
        host.start(List.of(
                TestModules.candidate(TestModules.module("owner").build(), Set.of(), false,
                        ignored -> {}, context -> module = context, ignored -> {}),
                TestModules.candidate(TestModules.module("other").build(), Set.of(), false,
                        ignored -> {}, context -> other = context, ignored -> {})));
    }

    @AfterEach void stop() { host.close(); }

    private UIElement element(String id) {
        return UIElement.builder("example:" + id).preferredSize(100, 30).minSize(40, 20)
                .anchor(Anchor.TOP_CENTER).interactive(true).build();
    }

    private void prepare(long frame) {
        engine.prepare(VIEW, List.of(), frame, frame * 16_000_000, 1, 1, false);
    }

    @Test void idleFrameReusesCommittedSnapshotAndScopeCloseRevokesPresentation() {
        var screen = host.rootScope().openChild(ScopeKind.SCREEN, UiId.parse("sh_better_ui:test_screen"));
        var scope = host.mountTree(module, screen, UiId.parse("example:tree"));
        var binding = engine.register(module, scope, element("first"));
        prepare(1);
        var first = binding.committed().orElseThrow();
        assertTrue(first.renderable());
        assertFalse(binding.hitTest(first.resolved().x() + 1, first.resolved().y() + 1));
        assertTrue(engine.present(binding, first.version()));
        assertTrue(binding.hitTest(first.resolved().x() + 1, first.resolved().y() + 1));
        prepare(2);
        assertSame(first, binding.committed().orElseThrow());
        assertEquals(1, engine.metrics().revalidations());
        scope.close();
        assertFalse(binding.isActive());
        assertTrue(binding.presented().isEmpty());
        assertFalse(binding.hitTest(200, 1));
    }

    @Test void lateUpdatesAndPresentationReceiptsCannotReplaceNewState() {
        var binding = engine.register(module, module.scope(), element("first"));
        prepare(1);
        var old = binding.committed().orElseThrow();
        assertTrue(binding.update(1, element("first").toBuilder().preferredSize(150, 30).build()));
        assertFalse(binding.update(1, element("first")));
        prepare(2);
        assertFalse(engine.present(binding, old.version()));
        assertTrue(binding.presented().isEmpty());
        assertEquals(150, binding.committed().orElseThrow().resolved().width());
    }

    @Test void registeredCriticalControlsNeverReceiveInputThroughAnUnresolvedHardConflict() {
        var binding = engine.register(module, module.scope(), element("critical").toBuilder()
                .minSize(500, 30).preferredSize(500, 30).maxSize(600, 60).build());
        prepare(1);
        var node = binding.committed().orElseThrow();
        assertFalse(node.renderable());
        assertFalse(node.interactiveEnabled());
        assertFalse(engine.report(module, module.scope()).unresolvedHard().isEmpty());
        assertFalse(engine.present(binding, node.version()));
    }

    @Test void safeAreaCorrectionsHaveEvidenceAndDeterministicResults() {
        var binding = engine.register(module, module.scope(), element("first"));
        var reserved = new UISnapshot.ReservedArea(UiId.parse("minecraft:boss"),
                new Rect(100, 0, 200, 45, Space.GUI), ValidationReport.Evidence.OBSERVED, 1);
        engine.prepare(VIEW, List.of(reserved), 1, 0, 1, 1, false);
        var node = binding.committed().orElseThrow();
        assertFalse(node.resolved().intersects(reserved.bounds()));
        var report = engine.report(module, module.scope());
        assertTrue(report.corrections().stream().anyMatch(c -> c.nodeId().equals(node.id())));
        assertTrue(engine.exportJson(module, module.scope()).contains("declarationVersion"));
    }

    @Test void snapshotCollectionsAndDeclarationsAreDetached() {
        var binding = engine.register(module, module.scope(), element("first"));
        prepare(1);
        var snapshot = engine.snapshot(module, module.scope());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.nodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.report().issues().clear());
        assertEquals(binding.committed().orElseThrow().version(), snapshot.version());
    }

    @Test void foreignScopeAndOffThreadMutationsAreRejected() {
        assertThrows(SecurityException.class, () -> engine.register(other, module.scope(), element("foreign")));
        var binding = engine.register(module, module.scope(), element("first"));
        var failure = CompletableFuture.supplyAsync(() -> assertThrows(IllegalStateException.class,
                () -> binding.update(1, element("first")))).join();
        assertNotNull(failure);
    }

    @Test void staleCorrectionPlansAndRollbackCannotBypassHardConstraints() {
        var binding = engine.register(module, module.scope(), element("first"));
        prepare(1);
        var snapshot = engine.snapshot(module, module.scope());
        var plan = engine.resolve(snapshot, engine.validate(snapshot));
        assertTrue(binding.update(1, element("first").toBuilder().preferredSize(140, 30).build()));
        prepare(2);
        assertFalse(engine.apply(module, module.scope(), plan).applied());
    }

    @Test void aDetachedPlanCannotExpandTheHitAreaToInvisiblePixels() {
        var binding = engine.register(module, module.scope(), element("first"));
        prepare(1);
        var source = engine.snapshot(module, module.scope());
        var n = binding.committed().orElseThrow();
        var forged = new UISnapshot.Node(n.id(), n.version(), n.revision(), n.scopeGeneration(), n.declaration(),
                n.preferred(), n.resolved(), n.predicted(), n.swept(), n.clipped(), n.visible(), VIEW, n.occlusion(), n.shadow(),
                n.transform(), n.text(), n.material(), n.legibility(), n.motion(), true, true, n.measurement(), n.background(), n.materialObservation());
        var proposed = new UISnapshot(source.version(), source.declarationVersion(), source.resourceVersion(), source.themeVersion(),
                source.frameId(), source.timestampNanos(), source.viewport(), source.safeAreas(), Map.of(n.id(), forged), source.report(), source.status());
        var plan = new CorrectionPlan(source, proposed, List.of());
        assertFalse(engine.apply(module, module.scope(), plan).applied());
    }
}
