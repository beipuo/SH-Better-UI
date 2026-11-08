package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.ElectricLogo;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplay;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Grid;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Result;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIconShader;
import com.dishanhai.sh_better_ui.client.gui.display.NetworkLayout;
import com.dishanhai.sh_better_ui.client.gui.display.NetworkScreen;
import com.dishanhai.sh_better_ui.client.gui.display.UiLayout;
import com.dishanhai.sh_better_ui.client.gui.display.UiLayout.Horizontal;
import com.dishanhai.sh_better_ui.client.gui.display.UiLayout.Size;
import com.dishanhai.sh_better_ui.client.gui.display.UiLayout.Vertical;
import com.dishanhai.sh_better_ui.client.gui.display.UiText;
import com.dishanhai.sh_better_ui.client.gui.scaled.NetworkTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.NetworkToggle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.text.UiFormattedText;
import com.dishanhai.sh_better_ui.client.gui.text.UiTextEffects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** 山海：图标、图文排版、逐字效果与网络风格界面的共用入口。 */
public final class GraphicsApi {
    GraphicsApi() {}

    public Bounds bounds(int x, int y, int width, int height) {
        return new Bounds(x, y, width, height);
    }

    public GraphicIcon icon(ResourceLocation texture) {
        return new GraphicIcon(texture);
    }

    public GraphicIcon icon(ResourceLocation texture, GraphicIconShader shader) {
        return new GraphicIcon(texture, shader);
    }

    public int iconSize(int scale) {
        return GraphicIcon.size(scale);
    }

    public GraphicIconShader iconShader(ResourceLocation id) {
        return new GraphicIconShader(id);
    }

    public GraphicIconShader iconShader(ResourceLocation id,
                                        BiConsumer<ShaderInstance, GraphicIconShader.Frame> uniforms) {
        return new GraphicIconShader(id, uniforms);
    }

    /** 为每个视图创建独立实例，移除视图时在渲染线程关闭。 */
    public ElectricLogo electricLogo() {
        return new ElectricLogo();
    }

    /** 使用完整的图片资源路径，传 null 时使用内置闪电。 */
    public ElectricLogo electricLogo(ResourceLocation texture) {
        return new ElectricLogo(texture);
    }

    public Result displayLayout(Bounds area, int count, int rowHeight, boolean pagination) {
        return GraphicDisplayLayout.arrange(area, count, rowHeight, pagination);
    }

    public Result displayLayout(Bounds area, int count, int rowHeight, boolean pagination, Grid grid) {
        return GraphicDisplayLayout.arrange(area, count, rowHeight, pagination, grid);
    }

    public int pageCapacity(Bounds area, int rowHeight, boolean pagination, Grid grid) {
        return GraphicDisplayLayout.pageCapacity(area, rowHeight, pagination, grid);
    }

    public void drawRows(GuiGraphics graphics, Font font, Result layout, List<GraphicDisplay.Entry> entries,
                         int iconScale, int textColor, int rowColor) {
        GraphicDisplay.drawRows(graphics, font, layout, entries, iconScale, textColor, rowColor);
    }

    public void drawRows(GuiGraphics graphics, Font font, Result layout, List<GraphicDisplay.Entry> entries,
                         int iconScale, int textColor, int rowColor, UiPreferences preferences) {
        GraphicDisplay.drawRows(graphics, font, layout, entries, iconScale, textColor, rowColor, preferences);
    }

    /** 在宿主控件绘制结束后提交完整标签的悬停提示。 */
    public void drawRowTooltip(GuiGraphics graphics, Font font, Result layout, List<GraphicDisplay.Entry> entries,
                               int mouseX, int mouseY) {
        GraphicDisplay.drawRowTooltip(graphics, font, layout, entries, mouseX, mouseY);
    }

    public void drawPageNumber(GuiGraphics graphics, Font font, Result layout, int pageNumber, int pageCount,
                               int color) {
        GraphicDisplay.drawPageNumber(graphics, font, layout, pageNumber, pageCount, color);
    }

    public UiButton pageButton(Bounds area, boolean next, UiPreferences preferences, Button.OnPress onPress) {
        return GraphicDisplay.pageButton(area, next, preferences, onPress);
    }

    public Bounds align(Bounds area, Size size, Horizontal horizontal, Vertical vertical) {
        return UiLayout.align(area, size, horizontal, vertical);
    }

    public List<Bounds> row(Bounds area, List<Size> sizes, int gap, Horizontal horizontal, Vertical vertical) {
        return UiLayout.row(area, sizes, gap, horizontal, vertical);
    }

    public List<Bounds> column(Bounds area, List<Size> sizes, int gap, Horizontal horizontal, Vertical vertical) {
        return UiLayout.column(area, sizes, gap, horizontal, vertical);
    }

    public void drawText(GuiGraphics graphics, Font font, Component text, Bounds area, Horizontal horizontal,
                         int color, boolean shadow) {
        UiText.draw(graphics, font, text, area, horizontal, color, shadow);
    }

    public void drawText(GuiGraphics graphics, Font font, Component text, Bounds area, Horizontal horizontal,
                         int color, boolean shadow, UiPreferences preferences) {
        UiText.draw(graphics, font, text, area, horizontal, color, shadow, preferences);
    }

    public int drawFormattedText(GuiGraphics graphics, Font font, Component text, float x, float y,
                                 int argb, boolean shadow, UiPreferences preferences) {
        return UiFormattedText.draw(graphics, font, text, x, y, argb, shadow, preferences);
    }

    public int drawFormattedText(GuiGraphics graphics, Font font, FormattedCharSequence text, float x, float y,
                                 int argb, boolean shadow, UiPreferences preferences) {
        return UiFormattedText.draw(graphics, font, text, x, y, argb, shadow, preferences);
    }

    public FormattedCharSequence colorizeText(FormattedCharSequence text, int argb,
                                             long timeMillis, UiPreferences preferences) {
        return UiFormattedText.colorize(text, argb, timeMillis, preferences);
    }

    public Component applyTextEffect(Component text, ResourceLocation id) {
        return UiTextEffects.apply(text, id);
    }

    public Component applyTextEffect(Component text, ResourceLocation id, String symbols) {
        return UiTextEffects.apply(text, id, symbols);
    }

    public Component applyTextEffect(Component text, String symbols) {
        return UiTextEffects.apply(text, symbols);
    }

    public Component parseText(String raw) {
        return UiTextEffects.parse(raw);
    }

    public Component parseText(String raw, Style style) {
        return UiTextEffects.parse(raw, style);
    }

    public Component normalizeText(Component text) {
        return UiTextEffects.normalize(text);
    }

    public void registerTextPalette(ResourceLocation id, int... rgb) {
        UiTextEffects.registerPalette(id, rgb);
    }

    public void registerTextPalette(ResourceLocation id, boolean body, int... rgb) {
        UiTextEffects.registerPalette(id, body, rgb);
    }

    public void registerTextEffect(ResourceLocation id, UiTextEffects.Effect effect) {
        UiTextEffects.registerEffect(id, effect);
    }

    /** 构建网络风格界面，由宿主在客户端打开。 */
    public NetworkScreen networkScreen(Screen parent, Component title, UiPreferences preferences,
                                       List<NetworkScreen.Tab> tabs, NetworkScreen.AddAction addAction) {
        return new NetworkScreen(parent, title, preferences, tabs, addAction);
    }

    public Optional<NetworkLayout.Result> networkLayout(int width, int height, int tabCount, boolean hasAction,
                                                        List<NetworkLayout.RowKind> rows, int lineHeight, int pageIndex) {
        return NetworkLayout.arrange(width, height, tabCount, hasAction, rows, lineHeight, pageIndex);
    }

    public void drawNetworkPanel(GuiGraphics graphics, Bounds area, UiPreferences preferences) {
        NetworkTheme.drawPanel(graphics, area, preferences);
    }

    public void drawNetworkField(GuiGraphics graphics, Font font, Bounds area, Component text,
                                 UiPreferences preferences) {
        NetworkTheme.drawField(graphics, font, area, text, preferences);
    }

    public UiButton networkTab(Bounds area, Component label, GraphicIcon icon, UiPreferences preferences,
                               Button.OnPress onPress) {
        return NetworkTheme.tab(area, label, icon, preferences, onPress);
    }

    public EditBox networkEditBox(Font font, Bounds area, Component label, UiPreferences preferences) {
        return NetworkTheme.editBox(font, area, label, preferences);
    }

    public NetworkToggle networkToggle(Bounds area, Component label, boolean value, UiPreferences preferences,
                                       Consumer<Boolean> onChange) {
        return new NetworkToggle(area, label, value, preferences, onChange);
    }

    public int networkLabelColor(UiPreferences preferences) {
        return NetworkTheme.labelColor(preferences);
    }
}
