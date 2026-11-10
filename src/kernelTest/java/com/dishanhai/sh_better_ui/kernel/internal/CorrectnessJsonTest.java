// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Rect;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Space;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.UISnapshot;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.UIElement;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.ValidationReport;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CorrectnessJsonTest {
    @Test void exportsSnapshotVersionsBoundsAndUnresolvedEvidence() {
        var issue = new ValidationReport.Issue("contrast\"\n", List.of(UiId.parse("example:label")), "LEGIBILITY",
                ValidationReport.Severity.HARD, ValidationReport.Evidence.PREDICTED,
                Map.of("ratio", Double.NaN, "infinite", Double.POSITIVE_INFINITY, "finite", 4.5),
                List.of(ValidationReport.Strategy.MATERIAL), "sh_better_ui.correctness.legibility", false);
        var report = new ValidationReport(7, List.of(issue), List.of(), List.of("quote\" slash\\ tab\t control\u0001"), 2);
        var snapshot = new UISnapshot(7, 3, 2, 1, 42, 100,
                new Rect(0, 0, 400, 240, Space.GUI), List.of(), Map.of(), report, UISnapshot.Status.SAFE_DEGRADED);
        String json = CorrectnessJson.export(snapshot);
        assertTrue(json.startsWith("{\"schemaVersion\":1,\"snapshot\":{"));
        assertTrue(json.contains("\"declarationVersion\":3"));
        assertTrue(json.contains("\"frameId\":42"));
        assertTrue(json.contains("\"space\":\"GUI\""));
        assertTrue(json.contains("\"evidence\":\"PREDICTED\""));
        assertTrue(json.contains("\"ratio\":null"));
        assertTrue(json.contains("\"infinite\":null"));
        assertTrue(json.contains("\"finite\":4.5"));
        assertTrue(json.contains("contrast\\\"\\n"));
        assertTrue(json.contains("quote\\\" slash\\\\ tab\\t control\\u0001"));
        assertFalse(json.contains("NaN"));
        assertFalse(json.contains("Infinity"));
        assertTrue(json.contains("\"unresolvedHard\":[{"));
    }

    @Test void exportOrderingIsStableRegardlessOfMapInsertionOrder() {
        var first = new java.util.LinkedHashMap<String, Double>();
        first.put("z", 1d); first.put("a", 2d);
        var second = new java.util.LinkedHashMap<String, Double>();
        second.put("a", 2d); second.put("z", 1d);
        assertEquals(CorrectnessJson.write(first), CorrectnessJson.write(second));
        assertEquals("{\"a\":2.0,\"z\":1.0}", CorrectnessJson.write(first));
    }

    @Test void supplementaryCharactersAndControlCharactersRemainValidJsonStrings() {
        assertEquals("\"中文🙂\\r\\n\\b\\f\\t\\u0000\"", CorrectnessJson.write("中文🙂\r\n\b\f\t\u0000"));
        assertEquals("\"example:node\"", CorrectnessJson.write(UiId.parse("example:node")));
    }

    @Test void exportsCompleteNodeAndMeasuredSampleProvenanceWithoutDroppingPredictiveIntervals() {
        String json = CorrectnessJson.export(richSnapshot());
        for (String field : List.of("preferred", "resolved", "predicted", "swept", "clipped", "visible",
                "interactive", "occlusion", "shadow", "constraints", "motion", "measurement", "background", "statistics", "collisions")) {
            assertTrue(json.contains("\"" + field + "\":"), field);
        }
        assertTrue(json.contains("\"source\":\"CPU_PIXELS\""));
        assertTrue(json.contains("\"stage\":\"POST_MATERIAL_PRE_TEXT\""));
        assertTrue(json.contains("\"fromSeconds\":0.1"));
        assertTrue(json.contains("\"stateVersion\":3"));
        assertTrue(json.contains("\"dynamicStability\":null"));
    }

    static UISnapshot richSnapshot() {
        var declaration = UIElement.builder("example:node").text("example.title").build();
        var rect = new Rect(10, 20, 120, 36, Space.GUI);
        var pixels = VisualIntelligence.BackgroundSample.pixels(new VisualIntelligence.PixelRegion(20, 40, 240, 72),
                2, 1, new int[] {0xff000000, 0xffffffff}, VisualIntelligence.Stage.POST_MATERIAL_PRE_TEXT,
                VisualIntelligence.Source.CPU_PIXELS, 42, 100, 3);
        var material = VisualIntelligence.MaterialSnapshot.of(declaration.visualPolicy().glass());
        var motion = new SpatialIntelligence.Trajectory(List.of(
                new SpatialIntelligence.Keyframe(0, new SpatialIntelligence.MotionState(SpatialGeometry.Shape.rectangle(120, 36),
                        new SpatialGeometry.Transform(10, 20, 1, 1, 0, 0, 0))),
                new SpatialIntelligence.Keyframe(1, new SpatialIntelligence.MotionState(SpatialGeometry.Shape.rectangle(120, 36),
                        new SpatialGeometry.Transform(50, 20, 1, 1, 0, 0, 0)))), "declared deterministic keyframes");
        var prediction = SpatialIntelligence.predict(declaration.id(), motion, 1, 8, 100_000_000);
        var node = new UISnapshot.Node(declaration.id(), 7, 3, 1, declaration, rect, rect, rect, rect, rect, rect, rect,
                rect, rect, SpatialGeometry.Matrix.IDENTITY, declaration.visualPolicy().text(), material,
                VisualIntelligence.evaluate(declaration.visualPolicy().text(), pixels, material), prediction,
                true, false, new UISnapshot.TextMeasurement(60, 9, 2, true), pixels,
                VisualIntelligence.BackgroundSample.unavailable(pixels.region(), VisualIntelligence.Stage.POST_MATERIAL_PRE_TEXT, 42, 100, 3));
        var collision = new SpatialIntelligence.CollisionPrediction(declaration.id(), UiId.parse("example:obstacle"),
                List.of(new SpatialIntelligence.Interval(.1, .2, rect)), true, -1, "declared path");
        return new UISnapshot(7, 3, 2, 1, 42, 100, new Rect(0, 0, 400, 240, Space.GUI), List.of(),
                Map.of(declaration.id(), node), new ValidationReport(7, List.of(), List.of(), List.of(), 1),
                UISnapshot.Status.VALID, List.of(collision));
    }
}
