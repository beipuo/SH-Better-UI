// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlassSceneCaptureTest {
    @Test
    void fullResolutionIsReusedWhenItFitsAndLargeViewportsDownsampleUniformly() {
        var full = GlassSceneCapture.Size.fit(3840, 2160, 16384, 32L << 20);
        assertEquals(new GlassSceneCapture.Size(3840, 2160), full);
        var reduced = GlassSceneCapture.Size.fit(7681, 4321, 4096, 32L << 20);
        assertEquals(new GlassSceneCapture.Size(3841, 2161), reduced);
        assertTrue(reduced.bytes() <= 32L << 20);
    }

    @Test
    void budgetAndDriverLimitsAlsoApplyToExtremeOrEmptyViewports() {
        assertEquals(new GlassSceneCapture.Size(1, 16), GlassSceneCapture.Size.fit(1, 64, 16, 1024));
        assertEquals(new GlassSceneCapture.Size(1, 1),
                GlassSceneCapture.Size.fit(Integer.MAX_VALUE, Integer.MAX_VALUE, 1, 4));
        assertThrows(IllegalArgumentException.class, () -> GlassSceneCapture.Size.fit(0, 9, 16, 1024));
        assertThrows(IllegalArgumentException.class, () -> GlassSceneCapture.Size.fit(9, 9, 0, 1024));
        assertThrows(IllegalArgumentException.class, () -> GlassSceneCapture.Size.fit(9, 9, 16, 3));
    }
}
