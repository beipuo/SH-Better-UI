// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.ElectricLogoProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.DotMatrixProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.PatternWavesProfile;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.DynamicThemeSettings;
import com.dishanhai.sh_better_ui.kernel.api.CapabilityStatus;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.theme.BuiltinThemes;
import com.dishanhai.sh_better_ui.theme.ThemeManager;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import com.dishanhai.sh_better_ui.theme.transition.TransitionSpec;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ClientUiKernelTest {
    @Test
    void scopedThemeOverridesInheritAndExpireWithTheirKernelNodes() {
        try (var fixture = new ClientServiceFixture(Set.of(), Set.of())) {
            var service = fixture.publication.current().require(UIApis.THEME);
            var root = fixture.tree();
            var node = root.openChild(ScopeKind.COMPONENT, UiId.parse("example:theme_node"));
            var child = service.scope(fixture.module, node);
            var parent = service.scope(fixture.module, root);
            var token = ThemeTokens.SPACING_SM;
            var global = service.snapshot().resolve(token);
            parent.configure(builder -> builder.override(token, global + 3));
            assertEquals(global + 3, child.resolve(token));
            child.configure(builder -> builder.override(token, global + 1));
            assertEquals(global + 1, child.resolve(token));
            assertEquals(global, service.snapshot().resolve(token));
            root.close();
            assertFalse(child.isOpen());
            assertThrows(IllegalStateException.class, () -> child.resolve(token));
        }
    }

    @Test
    void builtInServiceUsesTheOriginalManagerAndExpiresWithTheHost() {
        for (var platform : ModuleDescriptor.Platform.values()) {
            var publication = new EnginePublication();
            var manager = new ThemeManager();
            var host = ClientUiKernel.install(platform, publication, manager, manager::close);
            var engine = publication.current();
            var service = engine.require(UIApis.THEME);
            try (host) {
                assertNotNull(engine.require(UIApis.APPEARANCE));
                assertEquals(CapabilityStatus.AVAILABLE, engine.capabilities().status(UIApis.DYNAMIC_THEME));
                assertTrue(service.availableThemes().contains(BuiltinThemes.LIGHT_ID));
                assertSame(manager.snapshot(), service.snapshot());
                manager.transitionTo(BuiltinThemes.LIGHT_ID, TransitionSpec.immediate());
                assertSame(manager.snapshot(), service.snapshot());
                assertEquals(BuiltinThemes.LIGHT_ID, service.snapshot().themeIds().get(0));
                host.runFrame();
                assertEquals(1, engine.scheduler().snapshot().frame());
            }
            assertNull(publication.current());
            assertTrue(manager.isClosed());
            assertThrows(IllegalStateException.class, service::snapshot);
            assertThrows(IllegalStateException.class, service::availableThemes);
        }
    }

    @Test
    void appearanceAndMaterialCatalogResolveTheSameNativeEffectsAndPresets() {
        var publication = new EnginePublication();
        var manager = new ThemeManager();
        try (var host = ClientUiKernel.install(ModuleDescriptor.Platform.FORGE_1_20_1, publication, manager, manager::close)) {
            var engine = publication.current();
            var appearance = engine.require(UIApis.APPEARANCE);
            assertSame(ShBetterUiApi.backgrounds(), appearance.backgrounds());
            for (var background : appearance.backgrounds().entries()) {
                assertTrue(appearance.groups().contains(background.settingsGroup()));
                assertTrue(appearance.template(background.settingsGroup()).size() > 0);
            }
            var catalog = engine.require(UIApis.MATERIAL_CATALOG);
            for (String id : List.of("dot_matrix", "electric_logo", "pattern_waves", "ascii_field", "shape_waves",
                    "visual_reveal", "antigravity", "light_rays", "laser_flow", "code_rain")) {
                var material = catalog.find("sh_better_ui:" + id).orElseThrow();
                assertEquals("sh_better_ui:" + id, material.nativePreviewId());
                assertTrue(ShBetterUiApi.developerPreviews().find(material.nativePreviewId()).isPresent());
            }

            var settings = ClientUiSettings.DEFAULT;
            var empty = new JsonObject();
            assertEquals(4, appearance.presets("dotMatrix").size());
            assertEquals(4, appearance.presets("electricLogo").size());
            assertEquals(6, appearance.presets("patternWaves").size());
            var rainbow = appearance.applyPreset(settings, "dotMatrix", "rainbow", empty, empty);
            var storm = appearance.applyPreset(settings, "electricLogo", "storm", empty, empty);
            var pond = appearance.applyPreset(settings, "patternWaves", "pond", empty, empty);
            var draft = appearance.draft(settings, "dotMatrix", empty, rainbow);
            draft = appearance.draft(draft, "electricLogo", empty, storm);
            draft = appearance.draft(draft, "patternWaves", empty, pond);
            appearance.preview(draft, () -> {
                assertEquals(DotMatrixProfile.Gradient.RAINBOW, appearance.profile("dotMatrix", DotMatrixProfile.VIOLET).gradient());
                assertEquals(ElectricLogoProfile.Preset.STORM.profile(), appearance.profile("electricLogo", ElectricLogoProfile.DEFAULT));
                assertEquals(PatternWavesProfile.Preset.POND.profile(),
                        appearance.profile("patternWaves", PatternWavesProfile.Preset.SILK.profile()));
            });
            assertEquals(0, empty.size());
            assertThrows(IllegalArgumentException.class, () -> appearance.applyPreset(settings, "electricLogo", "rainbow", empty, empty));
        }
    }

    @Test
    void mirrorsTheCommittedSettingsAndOwnsThePageThemeLifetime() {
        var previous = ClientUiSettings.current();
        var next = previous.withDynamicTheme(new DynamicThemeSettings("example:theme", 125, true));
        var publication = new EnginePublication();
        var manager = new ThemeManager();
        try (var host = ClientUiKernel.install(ModuleDescriptor.Platform.FORGE_1_20_1, publication, manager, manager::close)) {
            var configs = publication.current().configs();
            assertEquals(previous, configs.get(UIApis.CLIENT_SETTINGS));
            assertThrows(SecurityException.class, () -> configs.set(UIApis.CLIENT_SETTINGS, next));
            ClientUiSettings.update(next);
            host.runFrame();
            assertEquals(next, configs.get(UIApis.CLIENT_SETTINGS));
            var page = manager.global().child("screen");
            var lifetime = host.attachPageTheme(page);
            lifetime.close();
            assertTrue(page.isClosed());
            var remount = host.attachPageTheme(manager.global().child("screen"));
            assertNotEquals(lifetime.generation(), remount.generation());
        } finally {
            ClientUiSettings.update(previous);
        }
    }
}
