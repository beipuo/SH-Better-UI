package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiDisclosure;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiDynamicStatus;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLoading;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScrollableSectionBar;
import com.dishanhai.sh_better_ui.config.TabScrollSettings;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSkeleton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTextDetails;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/** 山海：原生控件工厂、实时状态与加载提示。创建的控件由宿主注册和更新。 */
public final class ControlsApi {
    ControlsApi() {}

    public UiButton button(Bounds area, Component label, Button.OnPress onPress) {
        return new UiButton(area.x(), area.y(), area.width(), area.height(), label, onPress);
    }

    public UiButton button(Bounds area, Component label, Button.OnPress onPress, UiPreferences preferences,
                           boolean primary, UiStyle style) {
        return new UiButton(area.x(), area.y(), area.width(), area.height(), label, onPress, preferences, primary, style);
    }

    public UiSlider slider(Bounds area, int min, int max, int step, int value, UiPreferences preferences,
                           IntFunction<Component> label, IntConsumer onChange) {
        return new UiSlider(area, min, max, step, value, preferences, label, onChange);
    }

    public UiToggleButton toggle(Bounds area, boolean value, UiPreferences preferences, UiStyle style,
                                 Function<Boolean, Component> label, Consumer<Boolean> onChange) {
        return new UiToggleButton(area, value, preferences, style, label, onChange);
    }

    public UiSectionBar sectionBar(Bounds area, UiPreferences preferences, List<UiSectionBar.Section> sections,
                                   int selectedIndex, IntConsumer onSelect) {
        return new UiSectionBar(area.x(), area.y(), area.width(), area.height(), preferences, sections, selectedIndex, onSelect);
    }

    /** 横向滚动标签组，作为单个原生控件注册；默认读取客户端标签栏设置。 */
    public UiScrollableSectionBar scrollableSectionBar(Bounds area, UiPreferences preferences,
            List<UiSectionBar.Section> sections, int selectedIndex, IntConsumer onSelect) {
        return new UiScrollableSectionBar(area, preferences, sections, selectedIndex, onSelect);
    }

    public UiScrollableSectionBar scrollableSectionBar(Bounds area, UiPreferences preferences,
            List<UiSectionBar.Section> sections, int selectedIndex, IntConsumer onSelect,
            TabScrollSettings settings, int tabWidth) {
        return new UiScrollableSectionBar(area, preferences, sections, selectedIndex, onSelect, settings, tabWidth);
    }

    public UiDisclosure disclosure(boolean expanded) {
        return new UiDisclosure(expanded);
    }

    public UiTextDetails textDetails(Component title, Component details, int collapsedLines) {
        return new UiTextDetails(title, details, collapsedLines);
    }

    public UiDynamicStatus dynamicStatus(Supplier<UiDynamicStatus.Snapshot> source) {
        return new UiDynamicStatus(source);
    }

    public void drawStatus(GuiGraphics graphics, Font font, Bounds area, UiDynamicStatus.Snapshot snapshot,
                           UiPreferences preferences) {
        UiDynamicStatus.draw(graphics, font, area, snapshot, preferences);
    }

    public void skeletonCard(GuiGraphics graphics, Bounds area, long nowMs, UiPreferences preferences) {
        UiSkeleton.card(graphics, area, nowMs, preferences);
    }

    public void skeleton(GuiGraphics graphics, Bounds viewport, List<UiSkeleton.Block> blocks, long nowMs,
                         UiPreferences preferences, float opacity) {
        UiSkeleton.draw(graphics, viewport, blocks, nowMs, preferences, opacity);
    }

    public void spinner(GuiGraphics graphics, Bounds area, long nowMs, UiPreferences preferences) {
        UiLoading.spinner(graphics, area, nowMs, preferences);
    }

    public void progressRing(GuiGraphics graphics, Bounds area, float progress, UiPreferences preferences) {
        UiLoading.spinner(graphics, area, progress, preferences);
    }

    public void edgeSpinner(GuiGraphics graphics, Bounds viewport, UiLoading.Edge edge, long nowMs,
                            UiPreferences preferences) {
        UiLoading.edgeSpinner(graphics, viewport, edge, nowMs, preferences);
    }

    public void progressBar(GuiGraphics graphics, Bounds area, float progress, UiPreferences preferences) {
        UiLoading.progressBar(graphics, area, progress, preferences);
    }

    public void topLoadingBar(GuiGraphics graphics, Bounds viewport, long nowMs, UiPreferences preferences) {
        UiLoading.topBar(graphics, viewport, nowMs, preferences);
    }

    public void topProgressBar(GuiGraphics graphics, Bounds viewport, float progress, UiPreferences preferences) {
        UiLoading.topBar(graphics, viewport, progress, preferences);
    }
}
