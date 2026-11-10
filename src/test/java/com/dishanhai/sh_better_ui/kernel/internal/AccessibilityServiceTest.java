// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.theme.accessibility.ThemePreferences;
import net.minecraft.client.gui.narration.ScreenNarrationCollector;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AccessibilityServiceTest {
    @Test
    void reusesThemePreferencesAndWritesToTheNativeNarrationCollector() {
        try (var fixture = new ClientServiceFixture(Set.of(), Set.of())) {
            var engine = fixture.publication.current();
            var service = engine.require(UIApis.ACCESSIBILITY);
            fixture.themes.preferences(ThemePreferences.DEFAULT.withReducedMotion(true).withHighContrast(true));
            assertTrue(service.preferences(fixture.themes.global()).reducedMotion());
            assertTrue(service.preferences(fixture.themes.global()).highContrast());
            assertTrue(engine.capabilities().supports(UIApis.MINECRAFT_NARRATION));
            assertEquals(CapabilityStatus.UNAVAILABLE, engine.capabilities().status(UIApis.SYSTEM_SCREEN_READER));

            var node = fixture.tree();
            var role = AccessibilityService.Role.BUTTON;
            var semantics = service.describe(fixture.module, node,
                    () -> new AccessibilityService.Semantics(role, Component.literal("Open"), Component.literal("Ready")));
            var collector = new ScreenNarrationCollector();
            collector.update(output -> assertTrue(service.narrate(fixture.module, node, output)));
            var speech = collector.collectNarrationText(true);
            assertTrue(speech.contains("Open"));
            assertTrue(speech.contains("Ready"));
            assertEquals(role, service.semantics(fixture.module, node).orElseThrow().role());
            node.close();
            assertFalse(semantics.isActive());
        }
    }
}
