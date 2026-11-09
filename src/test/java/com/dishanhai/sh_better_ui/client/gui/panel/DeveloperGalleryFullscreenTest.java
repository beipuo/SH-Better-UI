package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：保证控件与材质图库的所有示例都能通过公开 ID 打开。 */
class DeveloperGalleryFullscreenTest {
    @Test
    void everyGallerySampleIdOpensItsOwnFullscreenScreen() {
        var settings = ShBetterUiApi.settings();
        var registry = ShBetterUiApi.developerPreviews();
        var ids = settings.developerSampleIds();
        assertEquals(List.of("underlined_tabs", "stepper"), ids.subList(0, 2));
        assertTrue(ids.containsAll(List.of("bottom_sheet", "interruptible_animation", "electric_logo", "status_icon",
                "code_rain", "laser_flow")));
        assertEquals(registry.ids(), ids);
        assertEquals(ids.size(), new HashSet<>(ids).size());

        for (String id : ids) {
            var screen = settings.developerSampleFullscreen(null, id, UiPreferences.DEFAULT);
            var title = assertInstanceOf(TranslatableContents.class, screen.getTitle().getContents());
            assertEquals(registry.find(id).orElseThrow().titleKey(), title.getKey());
        }
        assertThrows(IllegalArgumentException.class,
                () -> settings.developerSampleFullscreen(null, "missing", UiPreferences.DEFAULT));
    }

    @Test
    void previewHeightTakesPriorityOverTheCodeDrawer() {
        var tight = DeveloperGalleryScreen.fitPreview(300, 244, 130, true, true);
        assertEquals(130, tight.previewHeight());
        assertFalse(tight.inlineCode());
        assertFalse(tight.needsFullscreen());

        var roomy = DeveloperGalleryScreen.fitPreview(300, 360, 130, true, true);
        assertEquals(130, roomy.previewHeight());
        assertTrue(roomy.inlineCode());
        assertFalse(roomy.needsFullscreen());

        var tooShort = DeveloperGalleryScreen.fitPreview(300, 244, 166, true, true);
        assertTrue(tooShort.needsFullscreen());
        assertTrue(tooShort.previewHeight() >= 46);
        assertTrue(tooShort.top() >= 4);

        var noCode = DeveloperGalleryScreen.fitPreview(300, 360, 46, false, true);
        assertFalse(noCode.inlineCode());
        assertTrue(noCode.top() > roomy.top());
    }

    @Test
    void newRegistrationUsesTheExistingCatalogueAndFullscreenEntryPoint() {
        var settings = ShBetterUiApi.settings();
        var creations = new AtomicInteger();
        var spec = new PreviewSpec("test_addon:extra_widget", PreviewSpec.Category.MATERIALS, 1000,
                "gui.done", () -> {
                    creations.incrementAndGet();
                    return host -> {};
                });
        try (var registration = ShBetterUiApi.client().registerDeveloperPreview(spec)) {
            assertTrue(settings.developerSampleIds().contains(spec.id()));
            var screen = settings.developerSampleFullscreen(null, spec.id(), UiPreferences.DEFAULT);
            assertEquals("gui.done", assertInstanceOf(TranslatableContents.class, screen.getTitle().getContents()).getKey());
            assertEquals(0, creations.get());
        }
        assertFalse(settings.developerSampleIds().contains(spec.id()));
        assertThrows(IllegalArgumentException.class,
                () -> settings.developerSampleFullscreen(null, spec.id(), UiPreferences.DEFAULT));
    }
}
