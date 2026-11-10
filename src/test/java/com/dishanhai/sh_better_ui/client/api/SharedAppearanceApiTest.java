// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.scaled.ThemeEffects;
import com.dishanhai.sh_better_ui.theme.BuiltinThemes;
import com.dishanhai.sh_better_ui.theme.Theme;
import com.dishanhai.sh_better_ui.theme.ThemeManager;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SharedAppearanceApiTest {
    @Test
    void registeredAndPackThemesAreSelectableAndResolvedWithoutChangingGlobalTheme() {
        try (var manager = new ThemeManager()) {
            var materials = new MaterialsApi(() -> manager);
            assertEquals(10, materials.themeChoices().stream().filter(choice -> !choice.id().contains(":")).count());
            manager.register(Theme.builder("example:custom").parent(BuiltinThemes.DARK)
                    .token(ThemeTokens.ACCENT, ColorValue.argb(0xff946afa))
                    .token(ThemeTokens.TEXT, ColorValue.argb(0xfff9e9d3)).build());
            assertTrue(materials.themeChoices().stream().anyMatch(choice -> choice.id().equals("example:custom")));
            assertEquals(0xff946afa, materials.findTheme("example:custom").orElseThrow().frame);
            assertEquals(0xfff9e9d3, materials.findTheme("example:custom").orElseThrow().colors.TEXT);
            assertEquals(java.util.List.of(BuiltinThemes.DARK), manager.snapshot().themeIds());

            manager.replaceResourceThemes(java.util.List.of(Theme.builder("example:pack").build()), java.util.List.of());
            assertTrue(materials.findTheme("example:pack").isPresent());
            manager.replaceResourceThemes(java.util.List.of(), java.util.List.of());
            assertTrue(materials.findTheme("example:pack").isEmpty());
        }
    }

    public record Profile(float strength) {
        public Profile {
            if (!(strength >= 0 && strength <= 1)) throw new IllegalArgumentException();
        }
    }

    @Test
    void oneBackgroundRegistrationExposesItsProfileAndRemoval() {
        var registry = new BackgroundRegistry();
        var spec = new BackgroundSpec("example:test_background", "example.background", new Profile(0.4f),
                logo -> (graphics, font, area, mouse, x, y, now, prefs) -> {});
        try (var registration = registry.register(spec)) {
            assertEquals(spec, registry.find(spec.id()).orElseThrow());
            assertEquals(0.4f, ThemeEffects.template(spec.settingsGroup()).get("strength").getAsFloat());
            var values = new JsonObject();
            values.addProperty("strength", 2f);
            assertThrows(IllegalArgumentException.class, () -> ThemeEffects.validate(spec.settingsGroup(), values));
            values.addProperty("strength", 0.8f);
            ThemeEffects.validate(spec.settingsGroup(), values);
        }
        assertTrue(registry.find(spec.id()).isEmpty());
        assertFalse(ThemeEffects.groups().contains(spec.settingsGroup()));
    }
}
