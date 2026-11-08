package com.dishanhai.sh_better_ui.client.gui.display;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.UiLayout.Horizontal;
import com.dishanhai.sh_better_ui.client.gui.display.UiLayout.Size;
import com.dishanhai.sh_better_ui.client.gui.display.UiLayout.Vertical;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.text.UiFormattedText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 山海：以原生字体测量和绘制单行本地化文字。 */
public final class UiText {
    private UiText() {}

    public static void drawLeft(GuiGraphics graphics, Font font, Component text, Bounds area,
                                int color, boolean shadow) {
        draw(graphics, font, text, area, Horizontal.LEFT, color, shadow);
    }

    public static void drawCentered(GuiGraphics graphics, Font font, Component text, Bounds area,
                                    int color, boolean shadow) {
        draw(graphics, font, text, area, Horizontal.CENTER, color, shadow);
    }

    public static void drawRight(GuiGraphics graphics, Font font, Component text, Bounds area,
                                 int color, boolean shadow) {
        draw(graphics, font, text, area, Horizontal.RIGHT, color, shadow);
    }

    /** 按指定方向水平对齐、垂直居中。区域用于定位，换行和裁切由调用方处理。 */
    public static void draw(GuiGraphics graphics, Font font, Component text, Bounds area,
                            Horizontal horizontal, int color, boolean shadow) {
        draw(graphics, font, text, area, horizontal, color, shadow, UiPreferences.DEFAULT);
    }

    /** 绘制带格式效果的文字时沿用画面的高对比及减弱动态效果偏好。 */
    public static void draw(GuiGraphics graphics, Font font, Component text, Bounds area,
                            Horizontal horizontal, int color, boolean shadow, UiPreferences preferences) {
        var bounds = UiLayout.align(area, new Size(font.width(text), font.lineHeight), horizontal, Vertical.CENTER);
        UiFormattedText.draw(graphics, font, text, bounds.x(), bounds.y(), color, shadow, preferences);
    }
}
