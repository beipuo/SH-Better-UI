package com.dishanhai.sh_better_ui.client.glass;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：字形柔光沿用 Minecraft 的低 alpha 规则，不在淡出时突然变成不透明描边。 */
class GlassTextGlowTest {
    @Test
    void glowIsSubtleWhiteAndScalesWithExplicitTextAlpha() {
        int full = GlassSurface.textGlowColor(0xFF334455);
        int half = GlassSurface.textGlowColor(0x80334455);
        assertEquals(0xFFFFFF, full & 0xFFFFFF);
        assertTrue((full >>> 24) >= 4 && (full >>> 24) <= 16);
        assertTrue((half >>> 24) >= 4 && (half >>> 24) < (full >>> 24));
        assertEquals(full, GlassSurface.textGlowColor(0x00334455),
                "RGB-only text colors follow vanilla's opaque-alpha fallback");
    }

    @Test
    void nearTransparentTextNeverGetsVanillasOpaqueOutlineFallback() {
        for (int alpha = 4; alpha <= 255; alpha++) {
            int glow = GlassSurface.textGlowColor(alpha << 24 | 0x334455);
            assertTrue(glow == 0 || (glow >>> 24) >= 4,
                    "Font.adjustColor promotes alpha 1..3 to 255, so skip that outline");
        }
    }

    @Test
    void standaloneControlsKeepTheirOwnTextBackingWhileOverlaysStayThin() {
        for (float hover : new float[]{0f, 0.5f, 1f}) {
            int standalone = GlassSurface.clearControlFill(hover, true);
            int overlay = GlassSurface.clearControlFill(hover, false);
            assertEquals(GlassColors.CLEAR.PANEL_FILL >>> 24, standalone >>> 24);
            assertEquals(standalone & 0xFFFFFF, overlay & 0xFFFFFF);
            assertTrue((standalone >>> 24) > (overlay >>> 24));
        }
    }
}
