// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.intelligence;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.*;

/** Immutable publication consumed by rendering, hit testing and inspectors. All bounds identify their space. */
public record UISnapshot(long version, long declarationVersion, long resourceVersion, long themeVersion,
                         long frameId, long timestampNanos, Rect viewport, List<ReservedArea> safeAreas,
                         Map<UiId, Node> nodes, ValidationReport report, Status status,
                         List<CollisionPrediction> collisions) {
    public enum Status { VALID, SAFE_DEGRADED }
    public record TextMeasurement(double width, double height, long resourceVersion, boolean measured) {
        public TextMeasurement {
            if (!Double.isFinite(width) || !Double.isFinite(height) || width < 0 || height < 0) {
                throw new IllegalArgumentException("Invalid measured text size");
            }
        }
    }
    public record ReservedArea(UiId id, Rect bounds, ValidationReport.Evidence evidence, long frameId) {
        public ReservedArea { Objects.requireNonNull(id); Objects.requireNonNull(bounds); Objects.requireNonNull(evidence); }
    }
    public record Node(UiId id, long version, long revision, long scopeGeneration, UIElement declaration,
                       Rect preferred, Rect resolved, Rect predicted, Rect swept, Rect clipped, Rect visible,
                       Rect interactive, Rect occlusion, Rect shadow, SpatialGeometry.Matrix transform,
                       VisualIntelligence.TextStyle text, VisualIntelligence.MaterialSnapshot material,
                       VisualIntelligence.LegibilityReport legibility, Prediction motion,
                       boolean renderable, boolean interactiveEnabled, TextMeasurement measurement,
                       VisualIntelligence.BackgroundSample background,
                       VisualIntelligence.BackgroundSample materialObservation, double requiredContrast) {
        public Node {
            Objects.requireNonNull(id); Objects.requireNonNull(declaration); Objects.requireNonNull(preferred);
            Objects.requireNonNull(resolved); Objects.requireNonNull(predicted); Objects.requireNonNull(swept);
            Objects.requireNonNull(clipped); Objects.requireNonNull(visible); Objects.requireNonNull(interactive);
            Objects.requireNonNull(occlusion); Objects.requireNonNull(shadow); Objects.requireNonNull(transform);
            Objects.requireNonNull(text); Objects.requireNonNull(material); Objects.requireNonNull(legibility);
            Objects.requireNonNull(measurement); Objects.requireNonNull(background);
            Objects.requireNonNull(materialObservation);
            if (!Double.isFinite(requiredContrast) || requiredContrast < 1 || requiredContrast > 21) throw new IllegalArgumentException("Invalid effective contrast target");
        }
        public Node(UiId id, long version, long revision, long scopeGeneration, UIElement declaration,
                    Rect preferred, Rect resolved, Rect predicted, Rect swept, Rect clipped, Rect visible,
                    Rect interactive, Rect occlusion, Rect shadow, SpatialGeometry.Matrix transform,
                    VisualIntelligence.TextStyle text, VisualIntelligence.MaterialSnapshot material,
                    VisualIntelligence.LegibilityReport legibility, Prediction motion,
                    boolean renderable, boolean interactiveEnabled, TextMeasurement measurement,
                    VisualIntelligence.BackgroundSample background, VisualIntelligence.BackgroundSample materialObservation) {
            this(id, version, revision, scopeGeneration, declaration, preferred, resolved, predicted, swept, clipped, visible,
                    interactive, occlusion, shadow, transform, text, material, legibility, motion, renderable, interactiveEnabled,
                    measurement, background, materialObservation, declaration.visualPolicy().minimumContrast());
        }
        public Node availability(boolean draw, boolean input) {
            return new Node(id, version, revision, scopeGeneration, declaration, preferred, resolved, predicted, swept,
                    clipped, visible, interactive, occlusion, shadow, transform, text, material, legibility, motion, draw, input,
                    measurement, background, materialObservation, requiredContrast);
        }
    }
    public UISnapshot {
        if (version < 0 || declarationVersion < 0 || resourceVersion < 0 || themeVersion < 0 || frameId < 0) {
            throw new IllegalArgumentException("Negative snapshot version");
        }
        Objects.requireNonNull(viewport); Objects.requireNonNull(report); Objects.requireNonNull(status);
        safeAreas = List.copyOf(safeAreas); nodes = Map.copyOf(nodes); collisions = List.copyOf(collisions);
        if (nodes.size() > 256 || safeAreas.size() > 128 || collisions.size() > 1024) {
            throw new IllegalArgumentException("Snapshot exceeds the bounded analysis capacity");
        }
    }
    public UISnapshot(long version, long declarationVersion, long resourceVersion, long themeVersion,
                      long frameId, long timestampNanos, Rect viewport, List<ReservedArea> safeAreas,
                      Map<UiId, Node> nodes, ValidationReport report, Status status) {
        this(version, declarationVersion, resourceVersion, themeVersion, frameId, timestampNanos,
                viewport, safeAreas, nodes, report, status, List.of());
    }
}
