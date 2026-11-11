// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.*;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.*;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Matrix;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class IntelligenceCoordinateInteractionTest {
    @Test void framebufferAndPhysicalPixelsHaveIndependentRatios() {
        var coordinates = new UICoordinates(400, 240, 1200, 720, 1600, 960);
        var gui = new Rect(10, 20, 30, 40, Space.GUI);
        assertEquals(new Rect(30, 60, 90, 120, Space.FRAMEBUFFER), coordinates.convert(gui, Space.FRAMEBUFFER));
        assertEquals(new Rect(40, 80, 120, 160, Space.PHYSICAL), coordinates.convert(gui, Space.PHYSICAL));
        assertEquals(gui, coordinates.convert(coordinates.convert(gui, Space.PHYSICAL), Space.GUI));
        assertThrows(IllegalArgumentException.class, () -> coordinates.convert(new Rect(0, 0, 1, 1, Space.LOCAL), Space.GUI));
    }
    @Test void localBoundsUseExplicitMatrixAndRotatedEnvelope() {
        var converted = UICoordinates.toGui(new Rect(0, 0, 10, 20, Space.LOCAL), new Matrix(0, 1, -1, 0, 100, 50));
        assertEquals(new Rect(80, 50, 20, 10, Space.GUI), converted);
    }
    @Test void interactionReportsStaleHitHiddenInputAndFocusConflict() {
        var a = new UIInteractionSnapshot.Node(UiId.parse("example:a"), 4, 3, true, true, true, false, true, false);
        var b = new UIInteractionSnapshot.Node(UiId.parse("example:b"), 4, 4, false, true, true, true, true, true);
        var report = new UIInteractionSnapshot(4, List.of(a, b), false).validate();
        assertTrue(report.issues().stream().anyMatch(i -> i.code().equals("STALE_HIT_VERSION")));
        assertTrue(report.issues().stream().anyMatch(i -> i.code().equals("HIDDEN_INPUT")));
        assertTrue(report.issues().stream().anyMatch(i -> i.code().equals("FOCUS_CONFLICT")));
        assertFalse(report.valid());
        assertFalse(report.limitations().isEmpty());
    }
    @Test void responsiveConstraintsDoNotMutateTheBaseDeclaration() {
        var narrow = Constraints.defaults(80, 30);
        var wide = Constraints.defaults(200, 40);
        var node = UIElement.builder("example:responsive").preferredSize(80, 30).breakpoint(600, wide).padding(12).build();
        assertEquals(80, node.constraintsFor(400).preferredW());
        assertEquals(wide, node.constraintsFor(600));
        assertEquals(80, node.constraints().preferredW());
        assertEquals(12, node.padding());
    }
}
