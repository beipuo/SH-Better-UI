package com.dishanhai.sh_better_ui.client.glass;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：验证玻璃叠色的亮度与文字对比，不代表游戏画面验收。 */
class GlassColorsTest {
    @Test
    void darkModeRetainsExistingPaletteAndClearModeUsesDarkInk() {
        assertEquals(GlassPalette.TEXT, GlassColors.DARK.TEXT);
        assertEquals(GlassPalette.BUTTON_FILL, GlassColors.DARK.BUTTON_FILL);
        assertTrue(luma(GlassColors.CLEAR.TEXT) < 0.22);
        assertTrue(luma(GlassColors.CLEAR.TEXT_MUTED) < 0.35);
        assertTrue(luma(GlassColors.CLEAR.ROW_FILL) < 0.85);
        assertTrue(luma(GlassColors.CLEAR.BUTTON_FILL) < 0.85);
    }

    @Test
    void clearGlassKeepsDarkInkReadableWithoutStackingIntoWhite() {
        for (int scene : new int[]{0xFF000000, 0xFFFFFFFF, 0xFF405C30, 0xFFC09B73}) {
            int background = over(GlassColors.CLEAR.PANEL_FILL, scene);
            for (int layer = 0; layer < 5; layer++) {
                assertTrue(luma(background) < 0.86, "Layered clear glass must stay below near-white");
                assertTrue(contrast(GlassColors.CLEAR.TEXT, background) >= 4.5,
                        "Clear glass must retain dark-text contrast on bright and dark scenes");
                background = over(GlassColors.CLEAR.BUTTON_FILL_HOVER, background);
            }
        }
        assertTrue((GlassColors.CLEAR.BUTTON_FILL >>> 24) <= 80,
                "Inner controls should overlay a thin tint rather than another opaque white plate");
        assertTrue((GlassColors.CLEAR.LIT >>> 24) <= 48, "Highlights must not overpower the surface");
    }

    private static double luma(int argb) {
        double r = ((argb >>> 16) & 255) / 255.0;
        double g = ((argb >>> 8) & 255) / 255.0;
        double b = (argb & 255) / 255.0;
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    @Test
    void smokeGlassStaysDimAcrossStackedControlsEvenOverAWhiteWorld() {
        int background = over(GlassColors.SMOKE.PANEL_FILL, 0xFFFFFFFF);
        for (int layer = 0; layer < 4; layer++) {
            assertTrue(luma(background) < 0.36, "Layered glass must not become a white sheet");
            assertTrue(contrast(GlassColors.SMOKE.TEXT, background) >= 4.5,
                    "Dim glass must keep its text readable");
            background = over(GlassColors.SMOKE.BUTTON_FILL_HOVER, background);
        }
        assertTrue((GlassColors.SMOKE.BUTTON_FILL >>> 24) <= 64,
                "Inner controls must not hide the root glass");
        int rgb = GlassColors.SMOKE.PANEL_FILL;
        assertTrue(Math.abs(((rgb >>> 16) & 255) - (rgb & 255)) <= 12,
                "Smoke glass should remain neutral rather than saturated blue");
    }

    private static int over(int foreground, int background) {
        double alpha = (foreground >>> 24) / 255.0;
        int result = 0xFF000000;
        for (int shift : new int[]{16, 8, 0}) {
            int value = (int) Math.round(((foreground >>> shift) & 255) * alpha
                    + ((background >>> shift) & 255) * (1.0 - alpha));
            result |= value << shift;
        }
        return result;
    }

    private static double contrast(int foreground, int background) {
        double a = luminance(foreground), b = luminance(background);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(int color) {
        double result = 0;
        double[] weights = {0.2126, 0.7152, 0.0722};
        for (int i = 0; i < 3; i++) {
            double channel = ((color >>> (16 - 8 * i)) & 255) / 255.0;
            result += weights[i] * (channel <= 0.04045 ? channel / 12.92
                    : Math.pow((channel + 0.055) / 1.055, 2.4));
        }
        return result;
    }
}
