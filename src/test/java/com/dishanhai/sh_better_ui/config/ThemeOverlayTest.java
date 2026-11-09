package com.dishanhai.sh_better_ui.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：局部主题只能覆盖明确提供的参数，不修改全局快照或启用休眠配置。 */
class ThemeOverlayTest {
    @Test
    void nestedFieldsAreMergedWithoutMutatingEitherInput() {
        var base = new ThemeOverrides("{\"dotMatrix\":{\"grid\":{\"spacing\":12,\"dotSize\":2}},\"feedback\":{\"hoverAnimation\":false}}");
        var local = new ThemeOverrides("{\"dotMatrix\":{\"grid\":{\"spacing\":16}}}");
        var merged = base.overlay(local);
        assertEquals(16, merged.group("dotMatrix").getAsJsonObject("grid").get("spacing").getAsInt());
        assertEquals(2, merged.group("dotMatrix").getAsJsonObject("grid").get("dotSize").getAsInt());
        assertTrue(merged.contains("feedback"));
        assertEquals(12, base.group("dotMatrix").getAsJsonObject("grid").get("spacing").getAsInt());
        assertFalse(local.contains("feedback"));
    }

    @Test
    void overlayKeepsActiveCustomColorsAndLeavesAnEmptyOverlayAlone() {
        var base = new ThemeSettings(ThemeSettings.Mode.CUSTOM, ThemeSettings.Base.LIGHT,
                "#112233", "#445566", "", "", "", ThemeOverrides.EMPTY);
        assertSame(base, base.overlayEffects(ThemeOverrides.EMPTY));
        var local = new ThemeOverrides("{\"feedback\":{\"pressAnimation\":false}}");
        var merged = base.overlayEffects(local);
        assertEquals(base.customBase(), merged.customBase());
        assertEquals(base.surface(), merged.surface());
        assertEquals(base.background(), merged.background());
        assertEquals(local, merged.effects());
    }

    @Test
    void overlayDoesNotWakeDormantCustomColorsOrEffectsInLightMode() {
        var dormant = new ThemeSettings(ThemeSettings.Mode.LIGHT, ThemeSettings.Base.DARK,
                "#112233", "#445566", "", "", "", new ThemeOverrides("{\"feedback\":{\"hoverAnimation\":false}}"));
        var local = new ThemeOverrides("{\"liquidGlass\":{\"opacity\":0.8}}");
        var merged = dormant.overlayEffects(local);
        assertEquals(ThemeSettings.Mode.CUSTOM, merged.mode());
        assertEquals(ThemeSettings.Base.LIGHT, merged.customBase());
        assertEquals("", merged.surface());
        assertEquals(local, merged.effects());
    }
}
