package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.glass.GlassCell;
import com.dishanhai.sh_better_ui.client.glass.GlassColors;
import com.dishanhai.sh_better_ui.client.glass.GlassSurface;
import com.dishanhai.sh_better_ui.client.glass.GlassTheme;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiAttention;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import com.dishanhai.sh_better_ui.theme.ThemeManager;
import com.dishanhai.sh_better_ui.theme.ThemeRegistry;
import com.dishanhai.sh_better_ui.theme.ThemeResolver;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** 山海：原生画面的玻璃、Slate 和液态玻璃材质。自绘入口沿用 GUI 逻辑坐标。 */
public final class MaterialsApi {
    public record ThemeChoice(String id, String nameKey) {}
    private record Preset(ThemeChoice choice, GlassTheme theme) {}

    private static final List<Preset> PRESETS = List.of(
            preset("classic", "classic", GlassTheme.CLASSIC),
            preset("glass", "clearGlass", GlassTheme.GLASS),
            preset("smoke", "smokeGlass", GlassTheme.SMOKE),
            preset("slate", "slate", GlassTheme.SLATE),
            preset("liquidGlass", "liquidGlass", GlassTheme.LIQUID_GLASS),
            preset("network", "network", GlassTheme.NETWORK),
            preset("aurora", "aurora", GlassTheme.AURORA),
            preset("amethyst", "amethyst", GlassTheme.AMETHYST),
            preset("rose", "rose", GlassTheme.ROSE),
            preset("ember", "ember", GlassTheme.EMBER));

    private final Supplier<ThemeManager> themes;
    private final ThemeResolver resolver = new ThemeResolver();
    private final Map<String, GlassTheme> resolved = new HashMap<>();
    private ThemeRegistry registry;
    private Map<?, ?> tokenCatalog;
    private List<ThemeChoice> choices;

    MaterialsApi() { this(ShBetterUiApi::themes); }

    MaterialsApi(Supplier<ThemeManager> themes) { this.themes = themes; }

    private static Preset preset(String id, String label, GlassTheme theme) {
        return new Preset(new ThemeChoice(id, "sh_better_ui.panel.theme.group." + label), theme);
    }

    /** Stable snapshot of all material presets and registered Java or resource themes. Client thread only. */
    public List<ThemeChoice> themeChoices() {
        var manager = themes.get();
        refresh(manager);
        return choices;
    }

    /** Resolves the named theme for this screen without changing the global selection. Client thread only. */
    public Optional<GlassTheme> findTheme(String id) {
        for (var preset : PRESETS) {
            if (preset.choice().id().equals(id)) return Optional.of(preset.theme());
        }
        if (id == null || !id.contains(":")) return Optional.empty();
        var manager = themes.get();
        refresh(manager);
        if (registry.find(id).isEmpty()) return Optional.empty();
        return Optional.of(resolved.computeIfAbsent(id, key -> GlassTheme.fromSnapshot(
                resolver.resolve(registry, manager.tokenCatalog(), List.of(key), null))));
    }

    private void refresh(ThemeManager manager) {
        var next = manager.registry();
        var tokens = manager.tokenCatalog();
        if (registry != next) {
            var entries = new ArrayList<ThemeChoice>();
            PRESETS.forEach(preset -> entries.add(preset.choice()));
            next.themes().values().forEach(theme -> entries.add(new ThemeChoice(theme.id(), theme.nameKey())));
            choices = List.copyOf(entries);
        }
        if (registry != next || tokenCatalog != tokens) {
            resolved.clear();
            registry = next;
            tokenCatalog = tokens;
        }
    }

    public UiTheme theme() {
        return UiTheme.current();
    }

    public UiTheme resolveTheme(ThemeSettings settings) {
        return UiTheme.resolve(settings);
    }

    public <T extends Record> T themedProfile(String group, T fallback) {
        return com.dishanhai.sh_better_ui.client.gui.scaled.ThemeEffects.profile(group, fallback);
    }

    public GraphicIcon themeIcon(ThemeSettings.Mode mode) {
        return UiTheme.icon(mode);
    }

    public GlassTheme classicTheme() {
        return GlassTheme.CLASSIC;
    }

    public GlassTheme glassTheme() {
        return GlassTheme.GLASS;
    }

    public GlassTheme smokeTheme() {
        return GlassTheme.SMOKE;
    }

    public GlassColors glassColors(boolean clear) {
        return GlassColors.select(clear);
    }

    public void clearPanel(GuiGraphics graphics, Bounds area) {
        GlassSurface.panel(graphics, area.x(), area.y(), area.width(), area.height());
    }

    public void clearControl(GuiGraphics graphics, Bounds area, float hover, boolean sampleBackground) {
        GlassSurface.control(graphics, area.x(), area.y(), area.width(), area.height(), hover, sampleBackground);
    }

    public void smokePanel(GuiGraphics graphics, Bounds area) {
        GlassSurface.smokePanel(graphics, area.x(), area.y(), area.width(), area.height());
    }

    public void smokeControl(GuiGraphics graphics, Bounds area, float hover) {
        GlassSurface.smokeControl(graphics, area.x(), area.y(), area.width(), area.height(), hover);
    }

    public void drawGlassText(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        GlassSurface.drawString(graphics, font, text, x, y, color);
    }

    public void drawGlassText(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color) {
        GlassSurface.drawString(graphics, font, text, x, y, color);
    }

    public void glassCell(GuiGraphics graphics, int x, int y, int size, int frame, float selection, boolean hovered,
                          GlassColors colors) {
        GlassCell.draw(graphics, x, y, size, frame, selection, hovered, colors);
    }

    public DarkModeTheme.Interaction slateCardInteraction() {
        return DarkModeTheme.Interaction.card();
    }

    public DarkModeTheme.Interaction slateControlInteraction() {
        return DarkModeTheme.Interaction.control();
    }

    public void slateBackground(GuiGraphics graphics, Bounds area) {
        DarkModeTheme.background(graphics, area.x(), area.y(), area.width(), area.height());
    }

    public void slateCard(GuiGraphics graphics, Bounds area, DarkModeTheme.State state, Runnable content) {
        DarkModeTheme.card(graphics, area.x(), area.y(), area.width(), area.height(), state, content);
    }

    public void slateButton(GuiGraphics graphics, Bounds area, DarkModeTheme.State state, boolean primary,
                            boolean selected, float emphasis, Runnable content) {
        DarkModeTheme.button(graphics, area.x(), area.y(), area.width(), area.height(), state, primary, selected,
                emphasis, content);
    }

    public void slateInput(GuiGraphics graphics, Bounds area, DarkModeTheme.State state) {
        DarkModeTheme.input(graphics, area.x(), area.y(), area.width(), area.height(), state);
    }

    public void slateDragHandle(GuiGraphics graphics, Bounds area, DarkModeTheme.State state) {
        DarkModeTheme.dragHandle(graphics, area.x(), area.y(), area.width(), area.height(), state);
    }

    public void slateDropTarget(GuiGraphics graphics, Bounds area) {
        DarkModeTheme.dropTarget(graphics, area.x(), area.y(), area.width(), area.height());
    }

    public void slateSelectionIndicator(GuiGraphics graphics, UiAttention.Frame frame, UiPreferences preferences) {
        DarkModeTheme.selectionIndicator(graphics, frame, preferences);
    }

    public void slateAttentionTarget(GuiGraphics graphics, Bounds area, UiAttention.Frame frame, UiPreferences preferences) {
        DarkModeTheme.attentionTarget(graphics, area.x(), area.y(), area.width(), area.height(), frame, preferences);
    }

    public void liquidToolbar(GuiGraphics graphics, Bounds area, UiPreferences preferences) {
        LiquidGlassTheme.toolbar(graphics, area.x(), area.y(), area.width(), area.height(), preferences);
    }

    public void liquidPopover(GuiGraphics graphics, Bounds area, UiPreferences preferences) {
        LiquidGlassTheme.popover(graphics, area.x(), area.y(), area.width(), area.height(), preferences);
    }

    public void liquidLayeredPopover(GuiGraphics graphics, Bounds area, UiPreferences preferences, float blurStrength) {
        LiquidGlassTheme.layeredPopover(graphics, area.x(), area.y(), area.width(), area.height(), preferences, blurStrength);
    }

    public void liquidInput(GuiGraphics graphics, Bounds area, UiPreferences preferences, boolean sampleBackground,
                            boolean focused) {
        LiquidGlassTheme.input(graphics, area.x(), area.y(), area.width(), area.height(), preferences, sampleBackground,
                focused);
    }

    public void liquidSuggestionRow(GuiGraphics graphics, Bounds area, boolean selected, boolean hovered,
                                    UiPreferences preferences) {
        LiquidGlassTheme.suggestionRow(graphics, area.x(), area.y(), area.width(), area.height(), selected, hovered,
                preferences);
    }

    public void liquidButton(GuiGraphics graphics, Bounds area, DarkModeTheme.State state, UiPreferences preferences,
                             boolean sampleBackground, float blurRadius, boolean primary, boolean selected,
                             float emphasis, Runnable content) {
        LiquidGlassTheme.button(graphics, area.x(), area.y(), area.width(), area.height(), state, preferences,
                sampleBackground, blurRadius, primary, selected, emphasis, content);
    }

    public int liquidControlText(boolean enabled) {
        return LiquidGlassTheme.controlText(enabled);
    }

    public void roundedRect(GuiGraphics graphics, float x, float y, float width, float height, float radius,
                            float stroke, int edge, int fill) {
        RoundedCorners.draw(graphics, x, y, width, height, radius, stroke, edge, fill);
    }
}
