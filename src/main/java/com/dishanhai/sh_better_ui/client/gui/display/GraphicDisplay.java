package com.dishanhai.sh_better_ui.client.gui.display;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Result;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.text.UiFormattedText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;

/** 山海：原生 Screen 可嵌入的状态图文及翻页绘制。标题、底板和页码状态由调用方管理。 */
public final class GraphicDisplay {
    private static final int PADDING = 4;
    private static final int ICON_GAP = 4;

    private GraphicDisplay() {}

    public record Entry(GraphicIcon icon, Component label, int iconColor) {
        public Entry {
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(label, "label");
        }
    }

    /** 使用 Screen 坐标绘制当前页并裁切超出的内容。设 rowColor 为 0 可省略行底色。 */
    public static void drawRows(GuiGraphics graphics, Font font, Result layout, List<Entry> entries,
                                int iconScale, int textColor, int rowColor) {
        drawRows(graphics, font, layout, entries, iconScale, textColor, rowColor, UiPreferences.DEFAULT);
    }

    /** 使用宿主画面的动效偏好绘制图标。 */
    public static void drawRows(GuiGraphics graphics, Font font, Result layout, List<Entry> entries,
                                int iconScale, int textColor, int rowColor, UiPreferences preferences) {
        int iconSize = GraphicIcon.size(iconScale);
        int visible = Math.min(layout.cells().size(), entries.size());
        if (visible == 0) return;
        Bounds content = layout.content();
        graphics.enableScissor(content.x(), content.y(), content.right(), content.bottom());
        try {
            for (int i = 0; i < visible; i++) {
                Bounds cell = layout.cells().get(i);
                Entry entry = entries.get(i);
                if ((rowColor >>> 24) != 0) {
                    graphics.fill(cell.x(), cell.y(), cell.right(), cell.bottom(), rowColor);
                }
                drawEntry(graphics, font, cell, entry, iconScale, iconSize, textColor, preferences);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private static void drawEntry(GuiGraphics graphics, Font font, Bounds cell, Entry entry,
                                  int iconScale, int iconSize, int textColor, UiPreferences preferences) {
        int textX = cell.x() + PADDING;
        if (iconSize <= cell.width() - 2 * PADDING && iconSize <= cell.height() - 2 * PADDING) {
            entry.icon().draw(graphics, textX, cell.y() + (cell.height() - iconSize) / 2,
                    iconScale, entry.iconColor(), preferences);
            textX += iconSize + ICON_GAP;
        }
        int textWidth = cell.right() - PADDING - textX;
        int maxLines = (cell.height() - 2 * PADDING) / font.lineHeight;
        if (textWidth <= 0 || maxLines <= 0) return;

        var lines = font.split(entry.label(), textWidth);
        int count = Math.min(lines.size(), maxLines);
        int y = cell.y() + (cell.height() - count * font.lineHeight) / 2;
        for (int i = 0; i < count; i++) {
            var line = lines.get(i);
            if (font.width(line) <= textWidth) {
                UiFormattedText.draw(graphics, font, line, textX, y + i * font.lineHeight,
                        textColor, false, preferences);
            }
        }
    }

    /** 在宿主画面的控件绘制结束后调用，可悬停查看未截短的标签。 */
    public static void drawRowTooltip(GuiGraphics graphics, Font font, Result layout, List<Entry> entries,
                                      int mouseX, int mouseY) {
        int index = layout.hitTest(mouseX, mouseY);
        if (index >= 0 && index < entries.size()) {
            int width = Math.max(1, graphics.guiWidth() - 24);
            var lines = font.split(entries.get(index).label(), width);
            graphics.renderTooltip(font, lines, mouseX, mouseY);
        }
    }

    /** 页码采用从 1 开始的编号，空间足够时显示在两个翻页按钮之间。 */
    public static void drawPageNumber(GuiGraphics graphics, Font font, Result layout,
                                      int pageNumber, int pageCount, int color) {
        Bounds previous = layout.previous();
        Bounds next = layout.next();
        if (previous.width() == 0 || next.width() == 0 || pageCount < 1) return;
        Component label = Component.translatable("sh_better_ui.display.page", pageNumber, pageCount);
        int width = font.width(label);
        int available = next.x() - previous.right();
        if (width + 2 * PADDING > available) return;
        graphics.drawString(font, label, previous.right() + (available - width) / 2,
                previous.y() + (previous.height() - font.lineHeight) / 2, color, false);
    }

    /** 用布局返回的边界创建原生翻页控件。宿主注册控件并按实际页码设置 active。 */
    public static UiButton pageButton(Bounds bounds, boolean next, UiPreferences preferences, Button.OnPress onPress) {
        Component label = Component.translatable(next ? "sh_better_ui.display.next" : "sh_better_ui.display.previous");
        UiButton button = new UiButton(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                label, onPress, preferences, false);
        button.setIcon(next ? GraphicIcon.NEXT : GraphicIcon.PREVIOUS);
        button.visible = bounds.width() > 0 && bounds.height() > 0;
        button.active = button.visible;
        return button;
    }
}
