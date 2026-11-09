package com.dishanhai.sh_better_ui.client.glass;

import com.dishanhai.sh_better_ui.client.gui.scaled.ThemeEffects;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：共享配色贯穿结构色，复制和高对比不改变操作语义。 */
class GlassTintThemeTest {
    @Test
    void namedPalettesHaveDistinctSurfacesAndAccents() throws Exception {
        var accents = new java.util.HashSet<Integer>();
        var surfaces = new java.util.HashSet<Integer>();
        for (String name : new String[]{"AURORA", "AMETHYST", "ROSE", "EMBER"}) {
            var theme = (GlassTheme) GlassTheme.class.getField(name).get(null);
            assertTrue(theme.glass);
            assertEquals("clearGlass", theme.materialGroup());
            assertTrue(accents.add(theme.frame), name);
            assertTrue(surfaces.add(theme.panelBg), name);
            assertEquals(theme.colors.ACCENT, theme.frame);
            assertEquals(theme.colors.BUTTON_FILL, theme.buttonBg);
            assertEquals(theme.colors.DISPLAY_FILL, theme.panelInner);
            var copy = new GlassTheme(theme);
            assertEquals(theme.frame, copy.frame);
            assertEquals(theme.colors.TEXT, copy.ink("normal", 0));
            ThemeEffects.preview(ClientUiSettings.DEFAULT.withDisplay(true, false, false), () -> {
                assertEquals(0xFFFFFFFF, copy.ink("§8disabled", 0));
                assertNotEquals(copy.ink("§asuccess", 0), copy.ink("§cfailure", 0));
            });
        }
    }
}
