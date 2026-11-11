// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel;

import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class VisualIntelligenceTest {
    private static final PixelRegion REGION = new PixelRegion(4, 8, 80, 20);
    private static final MaterialSnapshot GLASS = MaterialSnapshot.of(GlassMaterial.preset(GlassVariant.CLEAR));

    private static BackgroundSample pixels(int... colors) {
        return BackgroundSample.pixels(REGION, colors.length, 1, colors, Stage.POST_MATERIAL_PRE_TEXT,
                Source.CPU_PIXELS, 7, 100, 3);
    }

    @Test void measuredBackgroundEvaluatesAlphaAndWorstPixelInsteadOfMeanOnly() {
        var report = VisualIntelligence.evaluate(TextStyle.of(0xffffffff), pixels(0xff000000, 0xffffffff), GLASS);
        assertTrue(report.valid());
        assertTrue(report.pixelObserved());
        assertFalse(report.finalCompositeObserved());
        assertEquals(1, report.worstRatio(), 1e-9);
        assertTrue(report.computedRatio() > 1);
        assertTrue(report.textureInterference() > 0);
        var alpha = VisualIntelligence.evaluate(TextStyle.of(0x80ffffff), pixels(0xff000000), GLASS);
        assertTrue(alpha.worstRatio() < 6);
    }

    @Test void quantilesAreBoundedImmutableAndFinalGlyphPixelsAreNotBackgroundEvidence() {
        int[] colors = {0xff000000, 0xff444444, 0xffaaaaaa, 0xffffffff};
        var sample = pixels(colors);
        Arrays.fill(colors, 0xff000000);
        assertEquals(1, sample.statistics().maximum(), 1e-9);
        assertEquals(4, sample.statistics().count());
        assertEquals(4, sample.statistics().colorDistribution().stream().mapToInt(Integer::intValue).sum());
        var composite = BackgroundSample.pixels(REGION, 1, 1, new int[]{0xffffffff}, Stage.FINAL_COMPOSITE,
                Source.CPU_PIXELS, 8, 200, 3);
        var report = VisualIntelligence.evaluate(TextStyle.of(0xffffffff), composite, GLASS);
        assertFalse(report.valid());
        assertTrue(report.finalCompositeObserved());
        assertTrue(report.limitations().stream().anyMatch(s -> s.contains("glyph")));
        assertFalse(VisualIntelligence.evaluate(TextStyle.of(0xffffffff),
                BackgroundSample.unavailable(REGION, Stage.POST_MATERIAL_PRE_TEXT, Validity.PENDING, 8, 200, 3), GLASS).valid());
    }

    @Test void repairFirstChangesForegroundAndDoesNotModifyRequestedMaterial() {
        var resolution = VisualIntelligence.resolve(TextStyle.of(0xffffffff), pixels(0xffffffff), GLASS, 4.5);
        assertEquals(0xff000000, resolution.text().argb());
        assertEquals(java.util.List.of(Strategy.FOREGROUND), resolution.strategies());
        assertSame(GLASS, resolution.material());
        assertTrue(resolution.after().worstRatio() >= 4.5);
        assertEquals(GlassMaterial.Foreground.LIGHT, GLASS.glass().foreground());
    }

    @Test void mixedPixelsRequireLocalProtectionAndRetainPredictionProvenance() {
        var resolution = VisualIntelligence.resolve(TextStyle.of(0xffffffff), pixels(0xff000000, 0xffffffff), GLASS, 7);
        assertTrue(resolution.material().contrastMask() > 0 || resolution.material().opacity() > 0);
        assertTrue(resolution.after().worstRatio() >= 7);
        assertFalse(resolution.after().pixelObserved());
        assertTrue(resolution.after().limitations().stream().anyMatch(s -> s.contains("prediction")));
        assertTrue(resolution.strategies().contains(Strategy.HIGH_READABILITY)
                || resolution.strategies().contains(Strategy.OPACITY)
                || resolution.strategies().contains(Strategy.ISOLATION));
    }

    @Test void stabilizerEntersSafetyImmediatelyAndRequiresStableExitEvidence() {
        var safe = VisualIntelligence.resolve(TextStyle.of(0xffffffff), pixels(0xff000000), GLASS, 4.5);
        var risk = VisualIntelligence.resolve(TextStyle.of(0xffffffff), pixels(0xffffffff), GLASS, 4.5);
        var stabilizer = new Stabilizer(new StabilityPolicy(500, 100, 150, 0.3, 0.8));
        assertEquals(safe.text().argb(), stabilizer.update(safe, 0).text().argb());
        assertEquals(risk.text().argb(), stabilizer.update(risk, 10).text().argb());
        assertEquals(risk.text().argb(), stabilizer.update(safe, 300).text().argb());
        assertEquals(risk.text().argb(), stabilizer.update(safe, 600).text().argb());
        assertEquals(safe.text().argb(), stabilizer.update(safe, 900).text().argb());
    }

    @Test void newBackgroundCanMakePreviouslyCorrectedPolarityUnsafe() {
        var bright = pixels(0xffffffff);
        var dark = pixels(0xff000000);
        var white = TextStyle.of(0xffffffff);
        var stabilizer = new Stabilizer();
        var onBright = VisualIntelligence.resolve(white, bright, GLASS, 4.5);
        assertEquals(0xff000000, stabilizer.update(onBright, bright, 0).text().argb());
        var onDark = VisualIntelligence.resolve(white, dark, GLASS, 4.5);
        var stabilized = stabilizer.update(onDark, dark, 10);
        assertEquals(0xff000000, stabilized.text().argb());
        assertTrue(stabilized.satisfied());
        assertTrue(stabilized.material().opacity() > 0);
        assertFalse(stabilized.after().pixelObserved());
    }

    @Test void malformedSamplesAndPoliciesFailBeforeAnalysis() {
        assertThrows(IllegalArgumentException.class, () -> new PixelRegion(0, 0, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> BackgroundSample.pixels(REGION, 2, 2,
                new int[]{1}, Stage.POST_MATERIAL_PRE_TEXT, Source.CPU_PIXELS, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> VisualIntelligence.resolve(TextStyle.of(-1), pixels(-1), GLASS, 22));
        assertThrows(IllegalArgumentException.class, () -> new TextStyle(-1, Float.NaN, 400, 0, 0, 0, 0));
    }
}
