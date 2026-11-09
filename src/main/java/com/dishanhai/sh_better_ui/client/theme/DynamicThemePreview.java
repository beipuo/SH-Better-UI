// Author: dishanhai
package com.dishanhai.sh_better_ui.client.theme;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.theme.BuiltinThemes;
import com.dishanhai.sh_better_ui.theme.ThemeScope;
import com.dishanhai.sh_better_ui.theme.accessibility.ThemePreferences;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Interactive sample shared by Forge and NeoForge. */
public final class DynamicThemePreview {
    private static final String KEY = "sh_better_ui.theme.dynamic.";
    private static final String AURORA = "sh_better_ui:aurora";

    private DynamicThemePreview() {}

    /** Creates an opt-in page with the caller's return target and focus entry. */
    public static UiScreen screen(Screen parent, Supplier<? extends GuiEventListener> focus) {
        var manager = MinecraftThemes.manager();
        var state = new PreviewState();
        return ShBetterUiApi.screen(KEY + "title").parent(parent).returnFocus(focus).dynamicTheme()
                .onInit(screen -> {
                    if (state.original == null) state.original = manager.preferences();
                    state.contrast = null;
                    state.motion = null;
                    state.aurora = null;
                    state.materialRow = null;
                    var area = screen.viewport();
                    int width = Math.max(0, Math.min(360, area.width() - 12));
                    int row = Math.min(26, Math.max(0, (area.height() - 24) / 5));
                    if (width < 100 || row < 12) return;
                    int left = (screen.width - width) / 2;
                    int gap = 6, column = (width - gap) / 2, top = area.y() + 22;
                    var choices = List.of(BuiltinThemes.DARK, BuiltinThemes.LIGHT_ID, BuiltinThemes.NETHER,
                            BuiltinThemes.END, AURORA);
                    for (int index = 0; index < choices.size(); index++) {
                        String id = choices.get(index);
                        String path = id.substring(id.indexOf(':') + 1);
                        var button = screen.button(left + index % 2 * (column + gap), top + index / 2 * row,
                                column, row - 3, KEY + path, () -> {
                                    if (manager.registry().find(id).isPresent()) manager.transitionTo(id);
                                });
                        button.active = manager.registry().find(id).isPresent();
                        if (id.equals(AURORA)) state.aurora = button;
                    }
                    screen.button(left + column + gap, top + 2 * row, column, row - 3,
                            KEY + "follow", () -> manager.preferences(manager.preferences()
                                    .withMode(ThemePreferences.Mode.FOLLOW_ENVIRONMENT)));
                    state.contrast = screen.button(left, top + 3 * row, column, row - 3,
                            KEY + "high_contrast", () -> manager.preferences(manager.preferences()
                                    .withHighContrast(!manager.preferences().highContrast())));
                    state.motion = screen.button(left + column + gap, top + 3 * row, column, row - 3,
                            KEY + "reduced_motion", () -> manager.preferences(manager.preferences()
                                    .withReducedMotion(!manager.preferences().reducedMotion())));
                    var sample = screen.widgetThemeScope().child("preview-local");
                    var styles = UiStyle.values();
                    int sampleWidth = (width - 2 * gap) / styles.length;
                    state.materialRow = new Bounds(left, top + 4 * row, width, row - 3);
                    for (int i = 0; i < styles.length; i++) {
                        var style = styles[i];
                        var button = new UiButton(left + i * (sampleWidth + gap), top + 4 * row, sampleWidth, row - 3,
                                Component.translatable(KEY + "material." + style.name().toLowerCase(Locale.ROOT)),
                                ignored -> {
                                    state.local = !state.local;
                                    setLocalStyle(sample, state.local);
                                }, screen.context().preferences(), false, style);
                        button.setPreview(Component.translatable(KEY + "local_scope"));
                        button.bindTheme(sample);
                        screen.add(button);
                    }
                    if (state.local) setLocalStyle(sample, true);
                })
                .onTick(screen -> {
                    if (state.aurora != null) state.aurora.active = manager.registry().find(AURORA).isPresent();
                    if (state.contrast != null) state.contrast.setSelected(manager.preferences().highContrast());
                    if (state.motion != null) state.motion.setSelected(manager.preferences().reducedMotion());
                })
                .onRender((screen, frame) -> {
                    var graphics = frame.graphics();
                    DarkModeTheme.background(graphics, 0, 0, screen.width, screen.height);
                    var materials = state.materialRow;
                    if (materials != null) {
                        int stripe = Math.max(4, materials.width() / 36);
                        int color = DarkModeTheme.borderColor();
                        for (int x = materials.x(); x < materials.right(); x += 2 * stripe) {
                            graphics.fill(x, materials.y() - 3, Math.min(x + stripe, materials.right()),
                                    materials.bottom() + 3, color);
                        }
                    }
                    var font = Minecraft.getInstance().font;
                    var active = manager.snapshot().themeIds();
                    var title = manager.registry().find(active.get(active.size() - 1))
                            .map(theme -> Component.translatable(theme.nameKey())).orElse(Component.empty());
                    var label = Component.translatable(KEY + "active", title);
                    var line = Language.getInstance().getVisualOrder(font.substrByWidth(label, Math.max(1, screen.width - 12)));
                    graphics.drawString(font, line,
                            6, frame.viewport().y() + 7, DarkModeTheme.textColor(), false);
                })
                .onRemoved(screen -> {
                    state.contrast = null;
                    state.motion = null;
                    state.aurora = null;
                    state.materialRow = null;
                    if (state.original != null && !manager.isClosed()) {
                        manager.preferences(state.original);
                    }
                    state.original = null;
                }).build();
    }

    private static void setLocalStyle(ThemeScope sample, boolean enabled) {
        sample.configure(override -> {
            if (enabled) override.override(ThemeTokens.SURFACE, ColorValue.argb(0xff685c8d))
                    .override(ThemeTokens.RADIUS_SMALL, 8f).override(ThemeTokens.RADIUS_LARGE, 5f);
            else override.clear(ThemeTokens.SURFACE).clear(ThemeTokens.RADIUS_SMALL).clear(ThemeTokens.RADIUS_LARGE);
        });
    }

    private static final class PreviewState {
        ThemePreferences original;
        UiButton contrast;
        UiButton motion;
        UiButton aurora;
        Bounds materialRow;
        boolean local;
    }
}
