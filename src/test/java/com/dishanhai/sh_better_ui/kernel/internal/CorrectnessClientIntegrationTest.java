// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.*;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Anchor;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CorrectnessClientIntegrationTest {
    @Test void serviceDiscoveryThemeBindingAndLifecycleUseTheSameKernel() {
        try (var fixture = new ClientServiceFixture(Set.of(), Set.of())) {
            var engine = fixture.module.engine();
            assertSame(engine.correctness(), engine.require(UIApis.CORRECTNESS));
            assertEquals(CapabilityStatus.AVAILABLE, engine.capabilities().status(UIApis.UI_CORRECTNESS));
            var scope = fixture.tree();
            var theme = engine.require(UIApis.THEME).scope(fixture.module, scope);
            theme.configure(builder -> builder.override(ThemeTokens.TEXT, ColorValue.argb(0xff000000)));
            var correctness = (ClientCorrectness) engine.correctness();
            var binding = correctness.register(fixture.module, scope, UIElement.builder("example:declarative")
                    .anchor(Anchor.TOP_CENTER).preferredSize(100, 30).build());
            correctness.prepare(new UIPlatform.Viewport(400, 240, 2, true), List.of(), 1, UICorrectnessEngine.Settings.DEFAULT);
            var node = binding.committed().orElseThrow();
            assertTrue(node.renderable());
            assertEquals(0xff000000, node.text().argb());
            assertEquals(com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial.Foreground.DARK, node.material().glass().foreground());
            assertEquals(0xff000000, theme.resolve(ThemeTokens.TEXT).argb());
            assertFalse(binding.hitTest(node.resolved().x() + 1, node.resolved().y() + 1));
            scope.close();
            assertFalse(binding.isActive());
            assertEquals(0, correctness.metrics().registeredNodes());
        }
    }

    @Test void disablingCorrectnessWithdrawsPresentationWithoutDestroyingOwnedDeclarations() {
        try (var fixture = new ClientServiceFixture(Set.of(), Set.of())) {
            var correctness = (ClientCorrectness) fixture.module.engine().correctness();
            var binding = correctness.register(fixture.module, fixture.module.scope(), UIElement.builder("example:toggle").build());
            var view = new UIPlatform.Viewport(400, 240, 1, true);
            correctness.prepare(view, List.of(), 1, UICorrectnessEngine.Settings.DEFAULT);
            assertTrue(binding.committed().orElseThrow().renderable());
            long enabledVersion = binding.committed().orElseThrow().version();
            var off = new UICorrectnessEngine.Settings(false, true, false, 256, 256, 4_000_000, 100_000_000);
            correctness.prepare(view, List.of(), 1, off);
            assertTrue(binding.isActive());
            assertFalse(binding.committed().orElseThrow().renderable());
            assertTrue(binding.committed().orElseThrow().version() > enabledVersion);
            correctness.prepare(view, List.of(), 1, UICorrectnessEngine.Settings.DEFAULT);
            assertTrue(binding.committed().orElseThrow().renderable());
        }
    }
}
