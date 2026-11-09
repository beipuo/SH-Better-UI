package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 山海：内置预览的共用按钮、布局和绘制。 */
final class GalleryPreviewLayout {
    static final int DEFAULT_HEIGHT = 46;
    static final int BUTTON_HEIGHT = 22;

    private GalleryPreviewLayout() {}

    static Bounds centeredControl(Bounds area) {
        int width = Math.min(164, area.width() - 16);
        return new Bounds(area.x() + (area.width() - width) / 2,
                area.y() + (area.height() - BUTTON_HEIGHT) / 2, width, BUTTON_HEIGHT);
    }

    static UiButton addButton(PreviewContext host, Bounds bounds, Component text, GraphicIcon icon, Runnable action) {
        return host.add(DeveloperPanelScreen.button(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                text, icon, host.preferences(), action));
    }

    static void drawCentered(GuiGraphics graphics, Font font, Component text, int x, int y, int width) {
        var lines = font.split(text, width);
        int count = Math.min(2, lines.size());
        int textY = y + (BUTTON_HEIGHT - count * font.lineHeight) / 2;
        for (int i = 0; i < count; i++) {
            var line = lines.get(i);
            graphics.drawString(font, line, x + (width - font.width(line)) / 2, textY,
                    LiquidGlassTheme.textColor(), false);
            textY += font.lineHeight;
        }
    }

    static void drawBackdrop(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fillGradient(x, y, x + width, y + height, 0xFF153F79, 0xFF40264F);
        graphics.fill(x + width / 5, y + 3, x + width * 2 / 5, y + height - 3, 0xFF85C9D7);
        graphics.fill(x + width * 3 / 5, y + 3, x + width * 4 / 5, y + height - 3, 0xFFD79D66);
    }
}
