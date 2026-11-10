// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.*;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.*;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CorrectnessSafetyRegressionTest {
    private static final Rect VIEW = new Rect(0, 0, 400, 240, Space.GUI);
    private KernelHost host;
    private CorrectnessRuntime engine;
    private ModuleContext module;

    @BeforeEach void start() {
        host = new KernelHost(ModuleDescriptor.Platform.FORGE_1_20_1, new EnginePublication());
        engine = new CorrectnessRuntime(host);
        host.start(List.of(TestModules.candidate(TestModules.module("owner").build(), Set.of(), false,
                ignored -> {}, context -> module = context, ignored -> {})));
        engine.budget(256, 256, 100_000_000);
    }
    @AfterEach void stop() { host.close(); }

    private static UIElement element(String id) {
        return UIElement.builder("example:" + id).preferredSize(100, 30).minSize(40, 20)
                .anchor(Anchor.TOP_CENTER).interactive(true).build();
    }
    private void prepare(long frame) {
        engine.prepare(VIEW, List.of(), frame, frame * 16_000_000, 1, 1, false);
    }
    private VisualIntelligence.BackgroundSample sample(UISnapshot snapshot, long capturedNanos) {
        return VisualIntelligence.BackgroundSample.pixels(new VisualIntelligence.PixelRegion(150, 4, 100, 30), 1, 1,
                new int[] {0xffffffff}, VisualIntelligence.Stage.BACKDROP_PRE_MATERIAL,
                VisualIntelligence.Source.CPU_PIXELS, snapshot.frameId(), capturedNanos, snapshot.declarationVersion());
    }

    @Test void aNewHardSafetyGateImmediatelyRevokesAnOldPaintedHitTarget() {
        var binding = engine.register(module, module.scope(), element("first"));
        prepare(1);
        var painted = binding.committed().orElseThrow();
        assertTrue(engine.present(binding, painted.version()));
        double x = painted.interactive().x() + 1, y = painted.interactive().y() + 1;
        assertTrue(binding.hitTest(x, y));
        assertTrue(binding.update(binding.revision(), element("first").toBuilder()
                .minSize(500, 30).preferredSize(500, 30).maxSize(600, 60).build()));
        prepare(2);
        assertFalse(binding.committed().orElseThrow().renderable());
        assertFalse(binding.hitTest(x, y), "A previously painted target must obey the new safety gate");
    }

    @Test void closingAParentImmediatelyRevokesItsDescendantsPresentation() {
        var parent = engine.register(module, module.scope(), element("parent").toBuilder()
                .preferredSize(200, 100).minSize(200, 100).build());
        var child = engine.register(module, module.scope(), element("child").toBuilder()
                .parent(parent.declaration().id()).preferredSize(60, 25).minSize(60, 25).build());
        prepare(1);
        var painted = child.committed().orElseThrow();
        assertTrue(engine.present(child, painted.version()));
        double x = painted.interactive().x() + 1, y = painted.interactive().y() + 1;
        assertTrue(child.hitTest(x, y));
        parent.close();
        assertFalse(child.hitTest(x, y), "A missing parent must not leave an orphaned interactive child");
        assertTrue(child.presented().isEmpty());
    }

    @Test void futureDatedPixelReceiptsAreRejected() {
        var binding = engine.register(module, module.scope(), element("future"));
        prepare(1);
        var snapshot = engine.snapshot(module, module.scope());
        assertFalse(engine.observe(binding, snapshot.version(), sample(snapshot, snapshot.timestampNanos() + 1)),
                "Samples cannot claim a capture time later than the current native frame");
    }

    @Test void movingTheActualGeometryInvalidatesPixelsFromTheOldLocation() {
        engine = new CorrectnessRuntime(host, (node, bounds, resources) -> new UISnapshot.TextMeasurement(0, 0, resources, false),
                new CorrectnessRuntime.MotionAdapter() {
                    @Override public CorrectnessRuntime.MotionFrame sample(long generation, UIElement declaration,
                            Rect target, long now, boolean reduced) {
                        return new CorrectnessRuntime.MotionFrame(new Rect(now < 32_000_000 ? 20 : 120, 20, 100, 30, Space.GUI), null, true);
                    }
                    @Override public void forget(long generation, UiId id) {}
                });
        var binding = engine.register(module, module.scope(), element("moving"));
        prepare(1);
        var first = engine.snapshot(module, module.scope());
        assertTrue(engine.observe(binding, first.version(), sample(first, first.timestampNanos())));
        prepare(2);
        assertNotEquals(VisualIntelligence.Validity.VALID, binding.committed().orElseThrow().background().validity(),
                "An old region must not be treated as the backdrop behind newly moved text");
    }

    @Test void disablingPositionCorrectionDoesNotSilentlyClampTheDeclaredPosition() {
        var policy = new UICorrectnessContract.CorrectionPolicy(false, false, false, false, true, 1, 1_000_000);
        var standard = UICorrectnessContract.ACCESSIBLE;
        var contract = new UICorrectnessContract(standard.hard(), standard.soft(), policy);
        var declaration = element("disabled").toBuilder().anchor(Anchor.ABSOLUTE)
                .preferredBounds(new Rect(380, 20, 100, 30, Space.GUI)).correctness(contract).build();
        var binding = engine.register(module, module.scope(), declaration);
        prepare(1);
        assertFalse(binding.committed().orElseThrow().renderable(),
                "Preserve the forbidden correction and withhold unsafe output instead of moving it anyway");
        assertTrue(engine.report(module, module.scope()).corrections().stream()
                .noneMatch(c -> c.strategy() == ValidationReport.Strategy.POSITION));
    }

    @Test void aRollbackCannotOverwriteALaterSuccessfulPlan() {
        engine.register(module, module.scope(), element("rollback"));
        prepare(1);
        var first = engine.snapshot(module, module.scope());
        var oldPlan = engine.resolve(first, engine.validate(first));
        assertTrue(engine.apply(module, module.scope(), oldPlan).applied());
        var second = engine.snapshot(module, module.scope());
        var newerPlan = engine.resolve(second, engine.validate(second));
        assertTrue(engine.apply(module, module.scope(), newerPlan).applied());
        assertFalse(engine.rollback(module, module.scope(), oldPlan).applied(),
                "Undo needs an exact application receipt, not just the same declaration/resource versions");
    }

    @Test void nodeVersionsCannotDifferFromTheirPublication() {
        engine.register(module, module.scope(), element("version"));
        prepare(1);
        var source = engine.snapshot(module, module.scope());
        var detached = new UISnapshot(source.version() + 1, source.declarationVersion(), source.resourceVersion(),
                source.themeVersion(), source.frameId(), source.timestampNanos(), source.viewport(), source.safeAreas(),
                source.nodes(), source.report(), source.status(), source.collisions());
        assertFalse(engine.validate(detached).valid(), "A mixed render/hit publication is a hard contract violation");
    }

    @Test void extremeFiniteRowsCannotOverflowIntoAnUnboundedIndexLoop() {
        var binding = engine.register(module, module.scope(), element("huge_row"));
        prepare(1);
        var source = engine.snapshot(module, module.scope());
        var original = binding.committed().orElseThrow();
        var huge = new Rect(0, ((double) Integer.MAX_VALUE) * 32 + 100, 100, 30, Space.GUI);
        var forged = replaceBounds(original, huge);
        var detached = new UISnapshot(source.version(), source.declarationVersion(), source.resourceVersion(), source.themeVersion(),
                source.frameId(), source.timestampNanos(), source.viewport(), source.safeAreas(), Map.of(forged.id(), forged),
                source.report(), source.status(), source.collisions());
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertFalse(engine.validate(detached).valid()));
    }

    private static UISnapshot.Node replaceBounds(UISnapshot.Node n, Rect bounds) {
        return new UISnapshot.Node(n.id(), n.version(), n.revision(), n.scopeGeneration(), n.declaration(), n.preferred(),
                bounds, bounds, bounds, bounds, bounds, bounds, bounds, bounds, SpatialGeometry.Matrix.IDENTITY,
                n.text(), n.material(), n.legibility(), null, true, true, n.measurement(), n.background(), n.materialObservation());
    }
}
