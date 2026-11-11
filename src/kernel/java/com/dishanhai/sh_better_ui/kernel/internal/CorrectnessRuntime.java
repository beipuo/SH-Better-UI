// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.*;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;

import java.util.*;

import static com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.*;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.BackgroundSample;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.PixelRegion;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.Stage;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.Validity;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.Source;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.MaterialSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.Stabilizer;
import static com.dishanhai.sh_better_ui.kernel.api.intelligence.ValidationReport.*;

/** One scope-owned pipeline in the existing host. Native code supplies observations, never global GL state. */
final class CorrectnessRuntime implements UICorrectnessEngine {
    interface TextMeasurer {
        UISnapshot.TextMeasurement measure(UIElement declaration, Rect bounds, long resourceVersion);
    }
    interface VisualAdapter {
        UIElement.VisualPolicy resolve(long scopeGeneration, UIElement declaration);
    }
    interface MotionAdapter {
        MotionFrame sample(long scopeGeneration, UIElement declaration, Rect target, long nowNanos, boolean reducedMotion);
        void forget(long scopeGeneration, UiId id);
    }
    interface AvoidingMotionAdapter extends MotionAdapter {
        void environment(long scopeGeneration, UiId id, Rect viewport, List<Rect> obstacles);
    }
    record MotionFrame(Rect actual, Trajectory future, boolean moving) {}

    private final KernelHost host;
    private final Map<SceneKey, Scene> scenes = new LinkedHashMap<>();
    private final Set<Entry> entries = new LinkedHashSet<>();
    private final Map<CorrectionPlan, Long> appliedPlans = new IdentityHashMap<>();
    private final TextMeasurer measurer;
    private final MotionAdapter motionAdapter;
    private final VisualAdapter visualAdapter;
    private Rect viewport = new Rect(0, 0, 0, 0, Space.GUI);
    private List<UISnapshot.ReservedArea> safeAreas = List.of();
    private long version, frameId, nowNanos, resourceVersion, themeVersion;
    private long layoutNanos, motionNanos, contrastNanos, correctionNanos, preparationNanos, cacheHits, revalidations, rejected;
    private int lastIterations;
    private boolean reducedMotion;
    private int maxNodes = 256, placementBudget = 256;
    private long layoutBudgetNanos = 4_000_000;
    private boolean automatic = true, enabled = true;

    CorrectnessRuntime(KernelHost host) {
        this(host, (node, bounds, resources) -> new UISnapshot.TextMeasurement(node.text().measuredWidth(),
                node.text().measuredHeight(), resources, false), null);
    }

    CorrectnessRuntime(KernelHost host, TextMeasurer measurer, MotionAdapter motionAdapter) {
        this(host, measurer, motionAdapter, (scope, node) -> node.visualPolicy());
    }
    CorrectnessRuntime(KernelHost host, TextMeasurer measurer, MotionAdapter motionAdapter, VisualAdapter visualAdapter) {
        this.host = Objects.requireNonNull(host); this.measurer = Objects.requireNonNull(measurer);
        this.motionAdapter = motionAdapter; this.visualAdapter = Objects.requireNonNull(visualAdapter);
    }

    void settings(Settings settings) {
        host.assertOwner();
        budget(settings.maxNodes(), settings.maxPlacements(), settings.layoutBudgetNanos());
        if (automatic != settings.automaticCorrection() || enabled != settings.enabled()) {
            automatic = settings.automaticCorrection(); enabled = settings.enabled();
            for (var scene : scenes.values()) {
                scene.changed(true);
                if (!enabled && scene.committed != null) {
                    long disabledVersion = ++version;
                    var hidden = new LinkedHashMap<>(scene.committed.nodes());
                    hidden.replaceAll((id, node) -> stamp(node.availability(false, false), disabledVersion));
                    var report = scene.committed.report();
                    scene.committed = new UISnapshot(disabledVersion, scene.declarationVersion, resourceVersion, themeVersion,
                            frameId, nowNanos, viewport, safeAreas, hidden,
                            new ValidationReport(disabledVersion, report.issues(), report.corrections(), report.limitations(), report.revalidationCount()),
                            UISnapshot.Status.SAFE_DEGRADED);
                    revokeUnsafeReceipts(scene);
                }
            }
        }
    }

    void budget(int nodes, int placements, long nanos) {
        host.assertOwner();
        if (nodes < 1 || nodes > 256 || placements < 1 || placements > 256 || nanos < 1 || nanos > 100_000_000) {
            throw new IllegalArgumentException("Invalid correctness performance budget");
        }
        if (maxNodes == nodes && placementBudget == placements && layoutBudgetNanos == nanos) return;
        maxNodes = nodes; placementBudget = placements; layoutBudgetNanos = nanos;
        for (var scene : scenes.values()) scene.layoutDirty = true;
    }

    @Override public Binding register(ModuleContext module, ScopeHandle scope, UIElement declaration) {
        var target = host.ownedScope(module, scope);
        host.moduleAccess(module).assertSideEffectAllowed();
        Objects.requireNonNull(declaration);
        if (!declaration.id().namespace().equals(target.owner().namespace())) throw new SecurityException("Foreign UI namespace");
        if (entries.size() >= maxNodes) throw new IllegalStateException("Correctness node capacity reached");
        var scene = scenes.computeIfAbsent(key(target), ignored -> new Scene());
        if (scene.entries.containsKey(declaration.id())) throw new IllegalArgumentException("Duplicate UI node " + declaration.id());
        if (declaration.parent() != null) {
            var parent = scene.entries.get(declaration.parent());
            if (parent == null || !parent.scope.owner().equals(target.owner())) {
                throw new IllegalArgumentException("A parent must be registered by this owner before its child");
            }
        }
        var entry = new Entry(target, scene, declaration);
        entry.lifetime = host.onScopeClose(target, entry::release);
        entries.add(entry); scene.entries.put(declaration.id(), entry);
        scene.changed(true);
        return entry;
    }

    /** Called at the existing RENDER_PREPARATION phase. Idle scenes keep their exact immutable publication. */
    void prepare(Rect viewport, List<UISnapshot.ReservedArea> areas, long frameId, long nowNanos,
                 long resources, long theme, boolean reducedMotion) {
        host.assertOwner();
        if (viewport.space() != Space.GUI || areas.size() > 128) throw new IllegalArgumentException("Invalid GUI observation");
        for (var area : areas) if (area.bounds().space() != Space.GUI) throw new IllegalArgumentException("Mixed safe-area space");
        long start = System.nanoTime();
        boolean geometryChanged = !this.viewport.equals(viewport) || !sameAreas(safeAreas, areas);
        boolean resourcesChanged = resourceVersion != resources, themeChanged = themeVersion != theme;
        this.viewport = viewport; safeAreas = List.copyOf(areas); this.frameId = frameId;
        this.nowNanos = nowNanos; resourceVersion = resources; themeVersion = theme; this.reducedMotion = reducedMotion;
        if (!enabled) { preparationNanos = System.nanoTime() - start; return; }
        motionNanos = 0; contrastNanos = 0;
        for (var scene : scenes.values()) {
            if (geometryChanged || resourcesChanged) scene.changed(true, resourcesChanged ? InvalidationReason.RESOURCE_RELOAD : InvalidationReason.VIEWPORT);
            else if (themeChanged) scene.changed(false, InvalidationReason.THEME);
            if (scene.layoutDirty || scene.visualDirty || scene.moving || scene.committed == null) commit(scene);
            else cacheHits++;
        }
        preparationNanos = System.nanoTime() - start;
    }

    private void commit(Scene scene) {
        long nextVersion = ++version;
        var changes = new ArrayList<Correction>();
        var conflicts = new ArrayList<Issue>();
        long started = System.nanoTime();
        if (scene.layoutDirty) {
            var declarations = new ArrayList<SpatialIntelligence.Node>();
            for (var entry : scene.entries.values()) {
                var declaration = entry.declaration;
                if (!declaration.state().visible()) continue;
                var c = declaration.constraintsFor(viewport.width());
                if (declaration.parent() != null) {
                    var parent = scene.entries.get(declaration.parent());
                    double padding = parent == null ? 0 : parent.declaration.padding();
                    c = new Constraints(c.anchor(), c.minW(), c.minH(), c.preferredW(), c.preferredH(), c.maxW(), c.maxH(),
                            c.margin() + padding, c.priority(), c.respectSafeArea(), c.avoidOverlap());
                }
                var policy = declaration.contract().correction();
                if (!automatic || !policy.enabled() || !policy.move()) {
                    c = new Constraints(Anchor.ABSOLUTE, c.minW(), c.minH(), c.preferredW(), c.preferredH(),
                            c.maxW(), c.maxH(), c.margin(), c.priority(), false, false);
                }
                if (!policy.resize()) {
                    c = new Constraints(c.anchor(), c.preferredW(), c.preferredH(), c.preferredW(), c.preferredH(),
                            c.preferredW(), c.preferredH(), c.margin(), c.priority(), c.respectSafeArea(), c.avoidOverlap());
                }
                if (automatic && policy.enabled() && policy.resize() && !declaration.text().translationKey().isEmpty()) {
                    double width = Math.min(c.preferredW(), Math.max(c.minW(), viewport.width() - 2 * c.margin()));
                    var measure = measurer.measure(declaration, new Rect(0, 0, width, c.preferredH(), Space.GUI), resourceVersion);
                    double height = Math.min(c.maxH(), Math.max(c.preferredH(), measure.height() + 2 * declaration.padding()));
                    c = new Constraints(c.anchor(), c.minW(), c.minH(), c.preferredW(), height, c.maxW(), c.maxH(),
                            c.margin(), c.priority(), c.respectSafeArea(), c.avoidOverlap());
                }
                declarations.add(new SpatialIntelligence.Node(declaration.id(), declaration.parent(), declaration.preferred(), c));
            }
            var solution = SpatialIntelligence.solve(declarations, viewport, safeAreas.stream().map(UISnapshot.ReservedArea::bounds).toList(),
                    placementBudget, layoutBudgetNanos);
            var targets = new LinkedHashMap<>(solution.bounds());
            for (var entry : scene.entries.values()) {
                var declaration = entry.declaration;
                var policy = declaration.contract().correction();
                if (declaration.state().visible() && (!automatic || !policy.enabled() || !policy.move())) {
                    var parent = declaration.parent() == null ? null : targets.get(declaration.parent());
                    targets.put(declaration.id(), SpatialIntelligence.preferred(new SpatialIntelligence.Node(declaration.id(),
                            declaration.parent(), declaration.preferred(), declaration.constraintsFor(viewport.width())), viewport, parent));
                }
            }
            scene.targets = Map.copyOf(targets); scene.layoutConflicts = solution.conflicts();
            lastIterations = solution.iterations();
        }
        layoutNanos = System.nanoTime() - started;
        for (var conflict : scene.layoutConflicts) {
            conflicts.add(new Issue(conflict.code() + "/" + conflict.nodeId(), List.of(conflict.nodeId()), conflict.code(),
                    Severity.HARD, Evidence.MODEL, dimensions(conflict.evidence()), List.of(Strategy.POSITION, Strategy.SIZE, Strategy.SAFE_HIDE),
                    "sh_better_ui.correctness.issue." + conflict.code().toLowerCase(Locale.ROOT), false));
        }
        var nodes = new LinkedHashMap<UiId, UISnapshot.Node>();
        scene.moving = false;
        for (var entry : scene.entries.values()) {
            var declaration = entry.declaration;
            var target = scene.targets.get(declaration.id());
            boolean solved = target != null;
            if (target == null) target = new Rect(viewport.x(), viewport.y(), 0, 0, Space.GUI);
            Rect actual = target;
            Trajectory future = declaration.motionPolicy().trajectory();
            started = System.nanoTime();
            if (solved && motionAdapter != null) {
                if (motionAdapter instanceof AvoidingMotionAdapter avoiding) {
                    var obstacles = new ArrayList<Rect>();
                    if (declaration.constraints().respectSafeArea()) for (var area : safeAreas) obstacles.add(area.bounds());
                    if (declaration.constraints().avoidOverlap()) for (var targetEntry : scene.targets.entrySet()) {
                        if (!targetEntry.getKey().equals(declaration.id()) && !Objects.equals(targetEntry.getKey(), declaration.parent())
                                && !declaration.id().equals(scene.entries.get(targetEntry.getKey()).declaration.parent())) {
                            obstacles.add(targetEntry.getValue());
                        }
                    }
                    avoiding.environment(entry.scope.generation(), declaration.id(), viewport, obstacles);
                }
                var sample = motionAdapter.sample(entry.scope.generation(), declaration, target, nowNanos, reducedMotion);
                actual = sample.actual();
                future = sample.future() == null ? future : sample.future();
                scene.moving |= sample.moving();
            }
            Prediction prediction = null;
            if (future != null && !reducedMotion && declaration.motionPolicy().horizonSeconds() > 0) {
                prediction = SpatialIntelligence.predict(declaration.id(), future, declaration.motionPolicy().horizonSeconds(), 64, 1_000_000);
            }
            motionNanos += System.nanoTime() - started;
            Rect swept = prediction == null ? actual : actual.union(prediction.sweptBounds());
            Rect predicted = prediction == null || prediction.path().isEmpty() ? actual : bounds(prediction.path().get(prediction.path().size() - 1).state());
            Rect clip = viewport;
            if (declaration.parent() != null) {
                var parent = nodes.get(declaration.parent());
                if (parent != null) clip = clip.intersection(parent.visible());
            }
            Rect visible = actual.intersection(clip);
            var background = entry.background;
            if (background == null || !near(entry.sampleBounds, actual) || entry.sampleDeclaration != scene.declarationVersion || entry.sampleResources != resourceVersion
                    || nowNanos - background.capturedNanos() > 1_000_000_000L) {
                background = BackgroundSample.unavailable(new PixelRegion(0, 0, 1, 1), Stage.BACKDROP_PRE_MATERIAL,
                        frameId, nowNanos, scene.declarationVersion);
            }
            started = System.nanoTime();
            var visual = visualAdapter.resolve(entry.scope.generation(), declaration);
            var material = MaterialSnapshot.of(visual.glass());
            var text = visual.text();
            var legibility = VisualIntelligence.evaluate(text, background, material);
            if (automatic && visual.adaptive() && declaration.contract().correction().enabled() && declaration.contract().correction().material()) {
                var requested = VisualIntelligence.resolve(text, background, material, visual.minimumContrast());
                var resolved = entry.stabilizer.update(requested, background, nowNanos);
                text = resolved.text(); material = resolved.material(); legibility = resolved.after();
                if (!resolved.strategies().isEmpty()) {
                    changes.add(new Correction(declaration.id(), Strategy.MATERIAL, String.join(",", resolved.strategies().stream().map(Enum::name).toList()),
                            finiteRatio(resolved.before().worstRatio()), finiteRatio(resolved.after().worstRatio()), resolved.satisfied()));
                }
            }
            if ((declaration.interactionPolicy().critical() || declaration.contract().hard().contains(UICorrectnessContract.Rule.LEGIBILITY))
                    && !legibility.valid() && !declaration.text().translationKey().isEmpty()) {
                // Unknown pixels cannot justify transparent critical content. Use a documented opaque local fallback.
                int dark = 0xff101218, light = 0xfff8f8fa;
                double darkRatio = ContrastPolicy.ratio(text.argb(), 0, dark), lightRatio = ContrastPolicy.ratio(text.argb(), 0, light);
                int backing = darkRatio >= lightRatio ? dark : light;
                if (Math.max(darkRatio, lightRatio) < visual.minimumContrast()) {
                    int foregroundColor = VisualIntelligence.luminance(text.argb()) >= .4 ? 0xffffffff : 0xff000000;
                    text = text.foreground(foregroundColor);
                    backing = foregroundColor == 0xffffffff ? 0xff000000 : 0xffffffff;
                }
                material = new MaterialSnapshot(visual.glass(), backing, 1, 0, true);
                var opaqueModel = BackgroundSample.pixels(new PixelRegion(0, 0, 1, 1), 1, 1, new int[] {backing},
                        Stage.POST_MATERIAL_PRE_TEXT, Source.PREDICTED, frameId, nowNanos, scene.declarationVersion);
                legibility = VisualIntelligence.evaluate(text, opaqueModel, material);
                changes.add(new Correction(declaration.id(), Strategy.MATERIAL, "UNOBSERVED_CRITICAL_FALLBACK", Map.of(),
                        Map.of("opacity", 1d, "modeledContrast", legibility.worstRatio()), false));
            }
            var optical = material.glass();
            var foreground = VisualIntelligence.luminance(text.argb()) > .4
                    ? com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial.Foreground.LIGHT
                    : com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial.Foreground.DARK;
            if (optical.foreground() != foreground) {
                material = new MaterialSnapshot(new com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial(
                        optical.variant(), optical.optics(), optical.quality(), foreground), material.tintArgb(),
                        material.opacity(), material.contrastMask(), material.highReadability());
            }
            var materialObservation = entry.materialObservation;
            if (materialObservation != null && entry.materialDeclaration == scene.declarationVersion
                    && near(entry.materialBounds, actual) && entry.materialResources == resourceVersion
                    && material.equals(entry.observedMaterial) && text.equals(entry.observedText)
                    && materialObservation.capturedNanos() <= nowNanos && nowNanos - materialObservation.capturedNanos() <= 1_000_000_000L) {
                legibility = VisualIntelligence.evaluate(text, materialObservation, material);
            } else {
                materialObservation = BackgroundSample.unavailable(new PixelRegion(0, 0, 1, 1), Stage.POST_MATERIAL_PRE_TEXT,
                        frameId, nowNanos, nextVersion);
            }
            contrastNanos += System.nanoTime() - started;
            boolean draw = solved && declaration.state().visible() && !visible.empty();
            boolean input = draw && declaration.state().enabled() && declaration.interactionPolicy().interactive();
            var measurement = measurer.measure(declaration, actual, resourceVersion);
            float extent = visual.shadowExtent();
            Rect shadow = new Rect(actual.x() - extent, actual.y() - extent, actual.width() + 2 * extent, actual.height() + 2 * extent, Space.GUI);
            var node = new UISnapshot.Node(declaration.id(), nextVersion, entry.revision, entry.scope.generation(), declaration,
                    declaration.preferred(), actual, predicted, swept, clip, visible, visible,
                    visible, shadow, SpatialGeometry.Matrix.IDENTITY, text, material, legibility, prediction, draw, input, measurement, background, materialObservation,
                    visual.minimumContrast());
            nodes.put(node.id(), node);
            var reasons = scene.reasons.isEmpty() && scene.moving ? Set.of(InvalidationReason.MOTION) : scene.reasons;
            for (var reason : reasons) conflicts.add(new Issue("INVALIDATED/" + reason + "/" + node.id(), List.of(node.id()),
                    "INVALIDATED_" + reason, Severity.INFORMATION, Evidence.OBSERVED, Map.of(), List.of(),
                    "sh_better_ui.correctness.issue.invalidated_" + reason.name().toLowerCase(Locale.ROOT), true));
            if (solved && scene.layoutDirty && automatic && declaration.contract().correction().enabled()
                    && declaration.contract().correction().move() && !near(entry.lastTarget, target)) {
                changes.add(new Correction(node.id(), Strategy.POSITION, "CONSTRAINT_LAYOUT", dimensions(declaration.preferred()), dimensions(target), true));
            }
            entry.lastTarget = target;
        }
        for (var node : nodes.values()) {
            if (node.motion() != null && node.motion().condition().startsWith("UNRESOLVED")) {
                conflicts.add(new Issue("MOTION_ROUTE/" + node.id(), List.of(node.id()), "MOTION_ROUTE_UNRESOLVED",
                        node.declaration().contract().hard().contains(UICorrectnessContract.Rule.SAFE_AREA) ? Severity.HARD : Severity.SOFT,
                        Evidence.PREDICTED, Map.of(), List.of(Strategy.MOTION_REPLAN, Strategy.SAFE_HIDE),
                        "sh_better_ui.correctness.issue.motion_route_unresolved", false));
            }
        }
        var initial = publication(scene, nextVersion, nodes, new ValidationReport(nextVersion, conflicts, changes, List.of(), 0), List.of());
        var validation = validate(initial);
        var allIssues = new ArrayList<>(conflicts); allIssues.addAll(validation.issues());
        var predictedCollisions = predict(initial, .25);
        for (var collision : predictedCollisions) {
            allIssues.add(new Issue("PREDICTED_COLLISION/" + collision.first() + "/" + collision.second(),
                    List.of(collision.first(), collision.second()), "PREDICTED_COLLISION", Severity.SOFT, Evidence.PREDICTED,
                    Map.of("timeError", collision.timeError(), "conservative", collision.conservative() ? 1d : 0d),
                    List.of(Strategy.MOTION_REPLAN, Strategy.POSITION), "sh_better_ui.correctness.issue.predicted_collision", false));
        }
        initial = publication(scene, nextVersion, nodes, new ValidationReport(nextVersion, allIssues, changes, validation.limitations(), 1), predictedCollisions);
        long correctionStart = System.nanoTime();
        var plan = resolve(initial, initial.report());
        scene.committed = plan.proposed();
        revokeUnsafeReceipts(scene);
        correctionNanos = System.nanoTime() - correctionStart;
        scene.layoutDirty = false; scene.visualDirty = false; scene.reasons.clear(); revalidations++;
    }

    @Override public ValidationReport validate(UISnapshot snapshot) { return CorrectnessValidator.validate(Objects.requireNonNull(snapshot)); }

    @Override public List<CollisionPrediction> predict(UISnapshot snapshot, double horizonSeconds) {
        if (!Double.isFinite(horizonSeconds) || horizonSeconds < 0 || horizonSeconds > 10) throw new IllegalArgumentException("Invalid prediction horizon");
        if (horizonSeconds == 0) return List.of();
        var predictions = new ArrayList<Prediction>();
        for (var node : snapshot.nodes().values().stream().sorted(Comparator.comparing(UISnapshot.Node::id)).toList()) {
            if (!node.renderable()) continue;
            var prediction = node.motion();
            if (prediction == null) {
                var shape = SpatialGeometry.Shape.rectangle(Math.max(.001, node.resolved().width()), Math.max(.001, node.resolved().height()));
                var transform = new SpatialGeometry.Transform(node.resolved().x(), node.resolved().y(), 1, 1, 0, 0, 0);
                var state = new MotionState(shape, transform);
                prediction = SpatialIntelligence.predict(node.id(), new Trajectory(List.of(new Keyframe(0, state), new Keyframe(Math.max(.001, horizonSeconds), state)),
                        "Registered static geometry; future user input is unknown"), horizonSeconds, 2, 1_000_000);
            }
            predictions.add(prediction);
        }
        var collisions = new ArrayList<CollisionPrediction>();
        int pairs = 0;
        for (int a = 0; a < predictions.size(); a++) for (int b = a + 1; b < predictions.size(); b++) {
            var first = predictions.get(a); var second = predictions.get(b);
            if (!first.sweptBounds().intersects(second.sweptBounds())) continue;
            if (++pairs > 1024) return List.copyOf(collisions);
            var collision = SpatialIntelligence.collisions(first, second);
            if (!collision.intervals().isEmpty()) collisions.add(collision);
        }
        return List.copyOf(collisions);
    }

    @Override public CorrectionPlan resolve(UISnapshot snapshot, ValidationReport report) {
        Objects.requireNonNull(snapshot); Objects.requireNonNull(report);
        if (snapshot.version() != report.version()) throw new IllegalArgumentException("Report belongs to a different snapshot");
        var nodes = new LinkedHashMap<>(snapshot.nodes());
        var operations = new ArrayList<>(report.corrections());
        var affected = new HashSet<UiId>();
        for (var issue : report.unresolvedHard()) affected.addAll(issue.nodes());
        for (var id : affected) {
            var node = nodes.get(id);
            if (node == null) continue;
            // This is a safety gate, not a claim that an impossible minimum/focus/visibility constraint was satisfied.
            nodes.put(id, node.availability(false, false));
            operations.add(new Correction(id, Strategy.SAFE_HIDE, "UNSATISFIABLE_HARD_CONSTRAINT", Map.of("renderable", node.renderable() ? 1d : 0d),
                    Map.of("renderable", 0d, "interactive", 0d), true));
        }
        var proposed = new UISnapshot(snapshot.version(), snapshot.declarationVersion(), snapshot.resourceVersion(), snapshot.themeVersion(),
                snapshot.frameId(), snapshot.timestampNanos(), snapshot.viewport(), snapshot.safeAreas(), nodes,
                new ValidationReport(report.version(), report.issues(), operations, report.limitations(), Math.min(8, report.revalidationCount() + 1)),
                affected.isEmpty() ? UISnapshot.Status.VALID : UISnapshot.Status.SAFE_DEGRADED, snapshot.collisions());
        var after = validate(proposed);
        // Geometric violations remain visible in the report even after their unsafe nodes have been withheld.
        var finalIssues = new LinkedHashMap<String, Issue>();
        for (var issue : report.issues()) finalIssues.put(issue.id(), issue);
        for (var issue : after.issues()) finalIssues.putIfAbsent(issue.id(), issue);
        proposed = new UISnapshot(proposed.version(), proposed.declarationVersion(), proposed.resourceVersion(), proposed.themeVersion(),
                proposed.frameId(), proposed.timestampNanos(), proposed.viewport(), proposed.safeAreas(), proposed.nodes(),
                new ValidationReport(proposed.version(), List.copyOf(finalIssues.values()), operations, report.limitations(), proposed.report().revalidationCount()),
                proposed.status(), proposed.collisions());
        return new CorrectionPlan(snapshot, proposed, operations);
    }

    @Override public CorrectionPlan.Result apply(ModuleContext module, ScopeHandle scope, CorrectionPlan plan) {
        return installPlan(module, scope, plan, false);
    }

    @Override public CorrectionPlan.Result rollback(ModuleContext module, ScopeHandle scope, CorrectionPlan plan) {
        return installPlan(module, scope, plan, true);
    }

    private CorrectionPlan.Result installPlan(ModuleContext module, ScopeHandle scope, CorrectionPlan plan, boolean rollback) {
        var target = host.ownedScope(module, scope);
        host.moduleAccess(module).assertSideEffectAllowed();
        Objects.requireNonNull(plan);
        var scene = scenes.get(key(target));
        var current = scene == null ? null : scene.committed;
        if (current == null || current.declarationVersion() != plan.source().declarationVersion()
                || scene.layoutDirty || scene.visualDirty || current.resourceVersion() != plan.source().resourceVersion()
                || current.themeVersion() != plan.source().themeVersion()
                || !rollback && current.version() != plan.source().version()
                || rollback && !Objects.equals(appliedPlans.get(plan), current.version())) {
            rejected++; return new CorrectionPlan.Result(false, "STALE_PLAN", current);
        }
        if (!rollback) {
            for (var node : plan.source().nodes().values()) {
                if (!node.equals(current.nodes().get(node.id()))) {
                    rejected++; return new CorrectionPlan.Result(false, "DETACHED_SOURCE", current);
                }
            }
        }
        // Recompute the proposal from the registered source; callers cannot forge observations,
        // expand hidden hit regions, or substitute a fabricated legibility report in a plan.
        var expected = resolve(plan.source(), validate(plan.source()));
        if (!plan.proposed().nodes().equals(expected.proposed().nodes())) {
            rejected++; return new CorrectionPlan.Result(false, "UNVERIFIED_PROPOSAL", current);
        }
        var replacement = rollback ? plan.source() : expected.proposed();
        var merged = new LinkedHashMap<>(current.nodes());
        for (var node : replacement.nodes().values()) {
            var entry = scene.entries.get(node.id());
            if (entry == null || !within(entry.scope, target) || !entry.declaration.equals(node.declaration())
                    || node.revision() != entry.revision || node.scopeGeneration() != entry.scope.generation()) {
                rejected++; return new CorrectionPlan.Result(false, "INVALID_PLAN_OWNERSHIP_OR_DECLARATION", current);
            }
            merged.put(node.id(), node);
        }
        long next = ++version;
        merged.replaceAll((id, node) -> stamp(node, next));
        var candidate = publication(scene, next, merged, replacement.report(), replacement.collisions());
        var validation = validate(candidate);
        for (var issue : validation.unresolvedHard()) {
            if (issue.nodes().stream().map(merged::get).filter(Objects::nonNull).anyMatch(UISnapshot.Node::renderable)) {
                rejected++; return new CorrectionPlan.Result(false, "HARD_CONSTRAINT_REJECTED", current);
            }
        }
        scene.committed = new UISnapshot(candidate.version(), candidate.declarationVersion(), candidate.resourceVersion(), candidate.themeVersion(),
                candidate.frameId(), candidate.timestampNanos(), candidate.viewport(), candidate.safeAreas(), merged,
                new ValidationReport(next, validation.issues(), expected.operations(), validation.limitations(), 1),
                validation.valid() ? UISnapshot.Status.VALID : UISnapshot.Status.SAFE_DEGRADED, candidate.collisions());
        revokeUnsafeReceipts(scene);
        if (rollback) appliedPlans.remove(plan);
        else {
            if (appliedPlans.size() >= 16) appliedPlans.clear();
            appliedPlans.put(plan, scene.committed.version());
        }
        return new CorrectionPlan.Result(true, rollback ? "ROLLED_BACK" : "APPLIED", scene.committed);
    }

    @Override public UISnapshot snapshot(ModuleContext module, ScopeHandle scope) {
        var target = host.ownedScope(module, scope);
        var scene = scenes.get(key(target));
        if (scene == null || scene.committed == null) throw new IllegalStateException("No committed correctness frame");
        var source = scene.committed;
        var selected = new LinkedHashMap<UiId, UISnapshot.Node>();
        for (var entry : scene.entries.values()) if (within(entry.scope, target)) {
            var node = source.nodes().get(entry.declaration.id());
            if (node != null) selected.put(node.id(), node);
        }
        if (selected.size() == source.nodes().size()) return source;
        var issues = source.report().issues().stream().filter(i -> selected.keySet().containsAll(i.nodes())).toList();
        var corrections = source.report().corrections().stream().filter(c -> selected.containsKey(c.nodeId())).toList();
        return new UISnapshot(source.version(), source.declarationVersion(), source.resourceVersion(), source.themeVersion(), source.frameId(),
                source.timestampNanos(), source.viewport(), source.safeAreas(), selected,
                new ValidationReport(source.version(), issues, corrections, source.report().limitations(), source.report().revalidationCount()),
                source.status(), source.collisions().stream().filter(c -> selected.containsKey(c.first()) && selected.containsKey(c.second())).toList());
    }

    @Override public ValidationReport report(ModuleContext module, ScopeHandle scope) { return snapshot(module, scope).report(); }
    @Override public String exportJson(ModuleContext module, ScopeHandle scope) { return CorrectnessJson.export(snapshot(module, scope)); }
    @Override public Metrics metrics() {
        host.assertOwner();
        return new Metrics(layoutNanos, motionNanos, contrastNanos, correctionNanos, preparationNanos, cacheHits, revalidations, rejected, entries.size(), lastIterations);
    }

    List<Binding> bindings(ScopeHandle nativeScreen) {
        host.assertOwner();
        var key = nativeScreen == null ? SceneKey.HUD : new SceneKey(nativeScreen.generation());
        var scene = scenes.get(key);
        if (scene == null) return List.of();
        return scene.entries.values().stream().sorted(Comparator.comparingInt((Entry e) -> e.declaration.visualPolicy().z())
                .thenComparing(e -> e.declaration.id())).map(e -> (Binding) e).toList();
    }

    /** Native-only receipt, after the material and foreground draw return successfully. */
    boolean present(Binding binding, long committedVersion) {
        host.assertOwner();
        if (!(binding instanceof Entry entry) || !entries.contains(entry) || !entry.isActive()) return false;
        var node = entry.committed().orElse(null);
        if (node == null || node.version() != committedVersion || !node.renderable()) {
            if (node == null || !node.renderable()) entry.presented = null;
            rejected++; return false;
        }
        entry.presented = node;
        return true;
    }

    void presentationFailed(Binding binding) {
        host.assertOwner();
        if (binding instanceof Entry entry && entries.contains(entry)) entry.presented = null;
    }

    void suspendPresentation() {
        host.assertOwner();
        for (var entry : entries) entry.presented = null;
    }

    void invalidateObservations() {
        host.assertOwner();
        for (var entry : entries) {
            entry.background = null; entry.materialObservation = null;
            entry.scene.changed(false, InvalidationReason.BACKGROUND);
        }
    }

    void onRelease(Binding binding, Runnable cleanup) {
        host.assertOwner();
        if (!(binding instanceof Entry entry) || !entries.contains(entry)) throw new IllegalArgumentException("Foreign binding");
        entry.cleanup = Objects.requireNonNull(cleanup);
    }

    boolean observe(Binding binding, long committedVersion, BackgroundSample sample) {
        host.assertOwner(); Objects.requireNonNull(sample);
        if (!(binding instanceof Entry entry) || !entries.contains(entry) || !entry.isActive()) return false;
        var node = entry.committed().orElse(null);
        if (node == null || node.version() != committedVersion || sample.stateVersion() != entry.scene.declarationVersion
                || sample.validity() != Validity.VALID || sample.source() == Source.PREDICTED
                || sample.frameId() > frameId || sample.capturedNanos() > nowNanos || nowNanos - sample.capturedNanos() > 1_000_000_000L) {
            rejected++; return false;
        }
        if (sample.stage() != Stage.BACKDROP_PRE_MATERIAL) return false;
        if (entry.background != null && entry.background.frameId() >= sample.frameId()) return false;
        entry.background = sample; entry.sampleDeclaration = entry.scene.declarationVersion; entry.sampleResources = resourceVersion;
        entry.sampleBounds = node.resolved();
        entry.scene.visualDirty = true; entry.scene.reasons.add(InvalidationReason.BACKGROUND); // No declaration version change and no layout invalidation.
        return true;
    }

    boolean observeMaterial(Binding binding, long committedVersion, BackgroundSample sample) {
        host.assertOwner(); Objects.requireNonNull(sample);
        if (!(binding instanceof Entry entry) || !entries.contains(entry) || !entry.isActive()) return false;
        var node = entry.committed().orElse(null);
        if (node == null || node.version() != committedVersion || sample.stateVersion() != committedVersion
                || sample.stage() != Stage.POST_MATERIAL_PRE_TEXT || sample.validity() != Validity.VALID
                || sample.source() == Source.PREDICTED || sample.frameId() > frameId || sample.capturedNanos() > nowNanos
                || nowNanos - sample.capturedNanos() > 1_000_000_000L) { rejected++; return false; }
        if (entry.materialObservation != null && entry.materialObservation.frameId() >= sample.frameId()) return false;
        entry.materialObservation = sample; entry.materialBounds = node.resolved(); entry.materialResources = resourceVersion;
        entry.materialDeclaration = entry.scene.declarationVersion;
        entry.observedMaterial = node.material(); entry.observedText = node.text();
        entry.scene.visualDirty = true; entry.scene.reasons.add(InvalidationReason.BACKGROUND);
        return true;
    }

    long declarationVersion(Binding binding) {
        host.assertOwner();
        if (!(binding instanceof Entry entry) || !entries.contains(entry)) throw new IllegalArgumentException("Foreign binding");
        return entry.scene.declarationVersion;
    }
    long declarationVersion(ScopeHandle screen) {
        host.assertOwner();
        var scene = scenes.get(screen == null ? SceneKey.HUD : new SceneKey(screen.generation()));
        return scene == null ? -1 : scene.declarationVersion;
    }

    private UISnapshot publication(Scene scene, long version, Map<UiId, UISnapshot.Node> nodes, ValidationReport report, List<CollisionPrediction> collisions) {
        return new UISnapshot(version, scene.declarationVersion, resourceVersion, themeVersion, frameId, nowNanos,
                viewport, safeAreas, nodes, report, report.valid() ? UISnapshot.Status.VALID : UISnapshot.Status.SAFE_DEGRADED, collisions);
    }
    private static UISnapshot.Node stamp(UISnapshot.Node n, long version) {
        return new UISnapshot.Node(n.id(), version, n.revision(), n.scopeGeneration(), n.declaration(), n.preferred(), n.resolved(), n.predicted(),
                n.swept(), n.clipped(), n.visible(), n.interactive(), n.occlusion(), n.shadow(), n.transform(), n.text(), n.material(), n.legibility(),
                n.motion(), n.renderable(), n.interactiveEnabled(), n.measurement(), n.background(), n.materialObservation(), n.requiredContrast());
    }
    private static void revokeUnsafeReceipts(Scene scene) {
        for (var entry : scene.entries.values()) {
            var current = scene.committed.nodes().get(entry.declaration.id());
            if (current == null || !current.renderable() || !current.interactiveEnabled()) entry.presented = null;
        }
    }
    private static Rect bounds(MotionState state) {
        var matrix = state.transform().matrix(); var shape = state.shape();
        var a = matrix.point(0, 0); var b = matrix.point(shape.width(), 0); var c = matrix.point(0, shape.height()); var d = matrix.point(shape.width(), shape.height());
        double x = Math.min(Math.min(a.x(), b.x()), Math.min(c.x(), d.x())), y = Math.min(Math.min(a.y(), b.y()), Math.min(c.y(), d.y()));
        return new Rect(x, y, Math.max(Math.max(a.x(), b.x()), Math.max(c.x(), d.x())) - x,
                Math.max(Math.max(a.y(), b.y()), Math.max(c.y(), d.y())) - y, Space.GUI);
    }
    private static boolean near(Rect a, Rect b) {
        return a != null && a.space() == b.space() && Math.abs(a.x() - b.x()) < .01 && Math.abs(a.y() - b.y()) < .01
                && Math.abs(a.width() - b.width()) < .01 && Math.abs(a.height() - b.height()) < .01;
    }
    private static Map<String, Double> dimensions(Rect r) { return Map.of("x", r.x(), "y", r.y(), "width", r.width(), "height", r.height()); }
    private static Map<String, Double> finiteRatio(double value) { return Double.isFinite(value) ? Map.of("contrast", value) : Map.of(); }
    private static boolean sameAreas(List<UISnapshot.ReservedArea> a, List<UISnapshot.ReservedArea> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!a.get(i).id().equals(b.get(i).id()) || !a.get(i).bounds().equals(b.get(i).bounds())
                || a.get(i).evidence() != b.get(i).evidence()) return false;
        return true;
    }
    private static boolean within(ScopeLifecycle.Scope node, ScopeLifecycle.Scope ancestor) {
        for (var current = node; current != null; current = current.parent()) if (current == ancestor) return true;
        return false;
    }
    private static SceneKey key(ScopeLifecycle.Scope scope) {
        var screen = scope.ancestor(ScopeKind.SCREEN);
        return screen == null ? SceneKey.HUD : new SceneKey(screen.generation());
    }
    private record SceneKey(long screenGeneration) { static final SceneKey HUD = new SceneKey(0); }
    private static final class Scene {
        final Map<UiId, Entry> entries = new LinkedHashMap<>();
        Map<UiId, Rect> targets = Map.of(); List<Conflict> layoutConflicts = List.of();
        long declarationVersion; boolean layoutDirty = true, visualDirty = true, moving;
        final Set<InvalidationReason> reasons = EnumSet.noneOf(InvalidationReason.class);
        UISnapshot committed;
        void changed(boolean layout) { changed(layout, InvalidationReason.DECLARATION); }
        void changed(boolean layout, InvalidationReason reason) {
            declarationVersion++; layoutDirty |= layout; visualDirty = true; reasons.add(reason);
        }
    }

    private final class Entry implements Binding {
        final ScopeLifecycle.Scope scope; final Scene scene; final Stabilizer stabilizer = new Stabilizer();
        UIElement declaration; long revision = 1, sampleDeclaration, sampleResources;
        Subscription lifetime; Rect lastTarget, sampleBounds, materialBounds; BackgroundSample background, materialObservation;
        MaterialSnapshot observedMaterial; VisualIntelligence.TextStyle observedText; long materialResources, materialDeclaration;
        UISnapshot.Node presented;
        Runnable cleanup;
        Entry(ScopeLifecycle.Scope scope, Scene scene, UIElement declaration) { this.scope = scope; this.scene = scene; this.declaration = declaration; }
        @Override public boolean isActive() { return scope.isOpen() && entries.contains(this); }
        @Override public UIElement declaration() { host.assertOwner(); return declaration; }
        @Override public long revision() { host.assertOwner(); return revision; }
        @Override public boolean update(long expected, UIElement next) {
            host.assertOwner(); requireActive(); Objects.requireNonNull(next);
            if (!declaration.id().equals(next.id())) throw new IllegalArgumentException("A binding cannot change identity");
            if (expected != revision) { rejected++; return false; }
            if (declaration.equals(next)) return true;
            if (!Objects.equals(declaration.parent(), next.parent())) throw new IllegalArgumentException("Reparent by closing and registering a new binding");
            boolean layout = !declaration.constraints().equals(next.constraints()) || !declaration.breakpoints().equals(next.breakpoints())
                    || declaration.padding() != next.padding() || !declaration.preferred().equals(next.preferred())
                    || !declaration.text().equals(next.text()) || declaration.state().visible() != next.state().visible();
            declaration = next; revision++; background = null; materialObservation = null; scene.changed(layout); return true;
        }
        @Override public void invalidate(InvalidationReason reason) {
            host.assertOwner(); requireActive(); Objects.requireNonNull(reason);
            boolean layout = reason == InvalidationReason.DECLARATION || reason == InvalidationReason.CONTENT
                    || reason == InvalidationReason.VIEWPORT || reason == InvalidationReason.RESOURCE_RELOAD;
            materialObservation = null;
            if (reason == InvalidationReason.RESOURCE_RELOAD || reason == InvalidationReason.THEME) background = null;
            if (reason == InvalidationReason.RESOURCE_RELOAD) stabilizer.reset();
            scene.changed(layout, reason);
        }
        @Override public Optional<UISnapshot.Node> committed() {
            host.assertOwner(); return !isActive() || scene.committed == null ? Optional.empty() : Optional.ofNullable(scene.committed.nodes().get(declaration.id()));
        }
        @Override public Optional<UISnapshot.Node> presented() {
            host.assertOwner(); return isActive() ? Optional.ofNullable(presented) : Optional.empty();
        }
        @Override public boolean hitTest(double x, double y) {
            host.assertOwner();
            var current = committed().orElse(null);
            if (!isActive() || presented == null || !presented.interactiveEnabled() || !declaration.state().enabled()
                    || current == null || !current.renderable() || !current.interactiveEnabled()
                    || !declaration.state().visible() || !presented.interactive().contains(x, y)) return false;
            for (var entry : scene.entries.values()) {
                var front = entry.presented;
                if (entry == this || !entry.isActive() || front == null || !front.renderable()) continue;
                int z = Integer.compare(front.declaration().visualPolicy().z(), presented.declaration().visualPolicy().z());
                boolean above = z > 0 || z == 0 && front.id().compareTo(presented.id()) > 0;
                if (above && front.interactive().contains(x, y)
                        && (front.interactiveEnabled() || front.declaration().interactionPolicy().blocksInput())) return false;
            }
            return true;
        }
        @Override public void close() { host.assertOwner(); if (lifetime != null && lifetime.isActive()) lifetime.close(); }
        private void requireActive() { if (!isActive()) throw new IllegalStateException("Correctness binding is closed"); }
        void release() {
            for (var child : List.copyOf(scene.entries.values())) {
                if (declaration.id().equals(child.declaration.parent())) child.close();
            }
            entries.remove(this); scene.entries.remove(declaration.id(), this); presented = null; background = null;
            if (cleanup != null) { var action = cleanup; cleanup = null; action.run(); }
            if (motionAdapter != null) motionAdapter.forget(scope.generation(), declaration.id());
            scene.changed(true);
            if (scene.entries.isEmpty()) scenes.remove(key(scope), scene);
        }
    }
}
