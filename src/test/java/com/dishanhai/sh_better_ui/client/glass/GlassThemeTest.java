package com.dishanhai.sh_better_ui.client.glass;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：消费端主题类型沿用原有 token 与语义，不复制绘制实现。 */
class GlassThemeTest {
    @Test
    void smokeThemeAndConsumerCopyUseStellarSmokeTokens() throws Exception {
        GlassTheme smoke = (GlassTheme) GlassTheme.class.getField("SMOKE").get(null);
        GlassTheme copy = new GlassTheme(smoke);
        assertAll(
                () -> assertTrue(copy.glass),
                () -> assertEquals(GlassColors.SMOKE.PANEL_FILL, copy.panelBg),
                () -> assertEquals(GlassColors.SMOKE.DISPLAY_FILL, copy.panelInner),
                () -> assertEquals(GlassColors.SMOKE.ROW_FILL, copy.rowBg),
                () -> assertEquals(GlassColors.SMOKE.BUTTON_FILL, copy.buttonBg),
                () -> assertEquals(GlassColors.SMOKE.BUTTON_FILL_HOVER, copy.buttonHover),
                () -> assertEquals(GlassColors.SMOKE.TEXT_MUTED, copy.tabIdle()),
                () -> assertEquals(GlassColors.SMOKE.ACCENT, copy.tabActive()),
                () -> assertEquals(GlassColors.SMOKE.TEXT_DIM, copy.ink("§8disabled", 0)));
    }

    @Test
    void consumerThemeCopiesAllTokensAndKeepsGlassTextLegible() {
        GlassTheme copy = new GlassTheme(GlassTheme.GLASS);
        assertTrue(copy.glass);
        assertEquals(GlassTheme.GLASS.panelBg, copy.panelBg);
        assertEquals(GlassTheme.GLASS.cellEdge, copy.cellEdge);
        assertEquals(GlassTheme.GLASS.buttonHover, copy.buttonHover);
        assertEquals("disabled", copy.label("§8disabled"));
        assertEquals(GlassPalette.TEXT_DIM, copy.ink("§8disabled", 0));
        assertEquals(GlassPalette.OK, copy.ink("§aenabled", 0));
    }

    @Test
    void classicThemePreservesCallerTextAndColor() {
        GlassTheme copy = new GlassTheme(GlassTheme.CLASSIC);
        assertFalse(copy.glass);
        assertEquals("§8disabled", copy.label("§8disabled"));
        assertEquals(0xFF123456, copy.ink("§8disabled", 0xFF123456));
        assertEquals(GlassTheme.CLASSIC.frame, copy.frame);
    }
}
