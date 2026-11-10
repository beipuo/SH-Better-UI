// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientBackgroundSamplerTest {
    @Test void unallocatedLifecycleDoesNotRequireANativeRenderThread() {
        var sampler = new ClientBackgroundSampler(16, 100_000_000);
        assertDoesNotThrow(sampler::reset);
        assertDoesNotThrow(sampler::close);
        assertDoesNotThrow(sampler::close);
    }

    @Test void sampleBudgetKeepsAspectAndExtremeRegionsBounded() {
        assertArrayEquals(new int[]{16, 4}, ClientBackgroundSampler.sampleSize(new PixelRegion(0, 0, 800, 200), 16));
        assertArrayEquals(new int[]{1, 16}, ClientBackgroundSampler.sampleSize(new PixelRegion(0, 0, 1, Integer.MAX_VALUE), 16));
        assertArrayEquals(new int[]{8, 4}, ClientBackgroundSampler.sampleSize(new PixelRegion(0, 0, 8, 4), 64));
        assertThrows(IllegalArgumentException.class, () -> new ClientBackgroundSampler(65, 0));
    }

    @Test void completedPixelsCannotCrossStateVersionRegionOrStage() {
        var region = new PixelRegion(2, 8, 80, 20);
        var sample = BackgroundSample.pixels(region, 1, 1, new int[]{0xff334455}, Stage.POST_MATERIAL_PRE_TEXT,
                Source.GPU_DOWNSAMPLED_PIXELS, 1, 1, 7);
        assertTrue(ClientBackgroundSampler.matches(sample, region, Stage.POST_MATERIAL_PRE_TEXT, 7));
        assertFalse(ClientBackgroundSampler.matches(sample, region, Stage.POST_MATERIAL_PRE_TEXT, 8));
        assertFalse(ClientBackgroundSampler.matches(sample, new PixelRegion(3, 8, 80, 20), Stage.POST_MATERIAL_PRE_TEXT, 7));
        assertFalse(ClientBackgroundSampler.matches(sample, region, Stage.FINAL_COMPOSITE, 7));
    }

    @Test void equalBrightnessHistogramsDoNotHideAnimatedPixelRearrangement() {
        var region = new PixelRegion(0, 0, 40, 20);
        var first = BackgroundSample.pixels(region, 2, 1, new int[]{0xff000000, 0xffffffff},
                Stage.POST_MATERIAL_PRE_TEXT, Source.GPU_DOWNSAMPLED_PIXELS, 1, 1, 1);
        var next = BackgroundSample.pixels(region, 2, 1, new int[]{0xffffffff, 0xff000000},
                Stage.POST_MATERIAL_PRE_TEXT, Source.GPU_DOWNSAMPLED_PIXELS, 2, 2, 1);
        assertEquals(first.statistics().mean(), next.statistics().mean());
        assertFalse(ClientBackgroundSampler.temporallyStable(first, next));
        assertTrue(ClientBackgroundSampler.temporallyStable(first, first));
    }
}
