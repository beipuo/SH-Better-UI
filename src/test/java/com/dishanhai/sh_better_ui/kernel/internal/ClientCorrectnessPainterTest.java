// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Rect;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Space;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.PixelRegion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientCorrectnessPainterTest {
    @Test void roiUsesFramebufferSizeRatherThanPhysicalWindowPixels() {
        assertEquals(new PixelRegion(20, 10, 40, 20), ClientCorrectnessPainter.pixelRegion(
                new Rect(10, 5, 20, 10, Space.GUI), 160, 90, 320, 180));
        assertEquals(new PixelRegion(15, 7, 30, 16), ClientCorrectnessPainter.pixelRegion(
                new Rect(10, 5, 20, 10, Space.GUI), 160, 90, 240, 135));
    }

    @Test void roiClipsToFramebufferAndRejectsMixedSpacesAndEmptyTargets() {
        assertEquals(new PixelRegion(0, 0, 20, 20), ClientCorrectnessPainter.pixelRegion(
                new Rect(-10, -10, 20, 20, Space.GUI), 100, 50, 200, 100));
        assertNull(ClientCorrectnessPainter.pixelRegion(new Rect(500, 500, 1, 1, Space.GUI), 100, 50, 200, 100));
        assertNull(ClientCorrectnessPainter.pixelRegion(new Rect(0, 0, 1, 1, Space.GUI), 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ClientCorrectnessPainter.pixelRegion(
                new Rect(0, 0, 10, 10, Space.FRAMEBUFFER), 100, 50, 200, 100));
    }
}
