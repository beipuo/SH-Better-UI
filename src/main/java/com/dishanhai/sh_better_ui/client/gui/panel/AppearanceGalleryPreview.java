// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.kernel.api.AppearanceService;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Compares the native effect and its shared UUK settings in a gallery-owned draft. */
final class AppearanceGalleryPreview implements PreviewScene {
    private static final String KEYS = "sh_better_ui.theme_editor.";
    private final String fixedGroup;
    private final Map<String, JsonObject> drafts;
    private String group;
    private AppearanceService appearance;
    private ThemeEffectPreview preview;
    private ClientUiSettings settings;
    private ClientUiSettings draft;
    private PreviewContext host;
    private Bounds canvas;

    private AppearanceGalleryPreview(String group) {
        this(group, group == null ? "dotMatrix" : group, new HashMap<>());
    }

    private AppearanceGalleryPreview(String fixedGroup, String group, Map<String, JsonObject> drafts) {
        this.fixedGroup = fixedGroup;
        this.group = group;
        this.drafts = drafts;
    }

    static void registerInto(PreviewRegistry registry) {
        register(registry, "effect_appearance", null, KEYS + "gallery.title", 19);
        register(registry, "ascii_field", "ascii", "sh_better_ui.panel.theme.group.ascii", 20);
        register(registry, "shape_waves", "shapeWaves", "sh_better_ui.panel.theme.group.shapeWaves", 21);
        register(registry, "visual_reveal", "reveal", "sh_better_ui.panel.theme.group.reveal", 22);
    }

    private static void register(PreviewRegistry registry, String id, String group, String titleKey, int order) {
        registry.register(new PreviewSpec("sh_better_ui:" + id, PreviewSpec.Category.MATERIALS, order, titleKey,
                96, 0, example(group == null ? "dotMatrix" : group), () -> new AppearanceGalleryPreview(group)));
    }

    @Override
    public void layout(PreviewContext host) {
        this.host = host;
        appearance = UnifiedUI.get().require(UIApis.APPEARANCE);
        if (preview == null) preview = new ThemeEffectPreview(appearance);
        settings = ShBetterUiApi.settings().current();
        var groups = appearance.groups().stream().filter(ThemeEffectPreview::supports).toList();
        if (!groups.contains(group)) group = groups.get(0);
        refresh();

        var area = host.viewport();
        int controlsY = area.bottom() - 26;
        int left = area.x() + 6;
        int width = area.width() - 12;
        canvas = new Bounds(left, area.y() + 6, width, Math.max(0, area.height() - 38));
        int split = fixedGroup == null ? width / 2 : 0;
        if (fixedGroup == null) {
            host.add(CycleButton.<String>builder(AppearanceGalleryPreview::name)
                    .withValues(groups).withInitialValue(group).displayOnlyValue().create(left, controlsY,
                            split - 4, 20, Component.translatable(KEYS + "gallery.effect"), (button, selected) -> {
                                group = selected;
                                refresh();
                            }));
        }
        host.add(DeveloperPanelScreen.button(left + split, controlsY, width - split, 20,
                Component.translatable(KEYS + "gallery.configure"), GraphicIcon.SETTINGS, host.preferences(),
                () -> configure(host)));
    }

    private void configure(PreviewContext host) {
        if (!host.interactive() || !appearance.groups().contains(group)) return;
        String editing = group;
        Minecraft.getInstance().setScreen(appearance.edit(host.screen(), settings, editing, inherited(editing),
                drafts.getOrDefault(editing, new JsonObject()), next -> drafts.put(editing, next)));
    }

    private void refresh() {
        draft = appearance.draft(settings, group, inherited(group), drafts.getOrDefault(group, new JsonObject()));
    }

    private JsonObject inherited(String id) {
        return settings.theme().mode() == ThemeSettings.Mode.CUSTOM
                ? settings.theme().effects().group(id) : new JsonObject();
    }

    @Override
    public void render(UiFrame frame) {
        if (canvas.height() <= 0) return;
        var mouse = frame.mouse();
        boolean pointer = mouse != null && mouse.available() && host.interactive();
        appearance.preview(draft, () -> preview.draw(group, frame.graphics(), host.font(), canvas, name(group),
                pointer ? (int) mouse.x() : -1, pointer ? (int) mouse.y() : -1, frame.preferences()));
    }

    @Override
    public Supplier<? extends PreviewScene> saveForRemount() {
        String selected = group;
        // The settings form may write its result while this gallery scene is detached.
        return () -> new AppearanceGalleryPreview(fixedGroup, selected, drafts);
    }

    @Override
    public void suspend() {
        if (preview != null) preview.close();
    }

    private static Component name(String group) {
        var spec = ThemeEffectPreview.background(group);
        return spec == null ? Component.translatableWithFallback("sh_better_ui.panel.theme.group." + group, group)
                : Component.translatable(spec.titleKey());
    }

    private static String example(String group) {
        return """
                var appearance = UnifiedUI.get().require(UIApis.APPEARANCE);
                var template = appearance.template("%s");
                var presets = appearance.presets("%s");

                // Hold one layer per view and close it when the view is removed.
                var background = appearance.backgrounds().layer(null);
                background.draw("%s", graphics, font, bounds, mouse, x, y, now, preferences);
                """.formatted(group, group, group);
    }
}
