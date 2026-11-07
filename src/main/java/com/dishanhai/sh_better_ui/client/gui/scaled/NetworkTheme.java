package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** 山海：带蓝色描边和半透明底板的网络风格，供原生 Screen 组合使用。 */
public final class NetworkTheme {
    private static final int PANEL = 0xCC090E16;
    private static final int FIELD = 0xD0111720;
    private static final int EDGE = 0xFF316E9B;
    private static final int LABEL = 0xFF86C5F4;
    private static final int FOCUS = 0xFFA7DBFF;
    private static final int CONTRAST_FILL = 0xFF080C13;
    private static final int PADDING = 5;

    private NetworkTheme() {}

    /** 在控件之前绘制面板。高对比模式使用不透明底色。 */
    public static void drawPanel(GuiGraphics graphics, Bounds area, UiPreferences preferences) {
        if (area.width() == 0 || area.height() == 0) return;
        DarkModeTheme.rounded(graphics, area.x(), area.y(), area.width(), area.height(), 11, 2,
                outline(preferences.highContrast()), surface(preferences.highContrast(), PANEL));
    }

    /** 绘制标题或只读设置行。可在 Component 上设置标签与数值各自的颜色。 */
    public static void drawField(GuiGraphics graphics, Font font, Bounds area, Component text,
                                 UiPreferences preferences) {
        if (area.width() == 0 || area.height() == 0) return;
        fieldFrame(graphics, area.x(), area.y(), area.width(), area.height(), preferences, false);
        if (area.width() <= 2 * PADDING || area.height() < font.lineHeight + 4) return;
        graphics.enableScissor(area.x() + PADDING, area.y() + 2, area.right() - PADDING, area.bottom() - 2);
        try {
            graphics.drawString(font, text, area.x() + PADDING,
                    area.y() + (area.height() - font.lineHeight) / 2, DarkModeTheme.TEXT, false);
        } finally {
            graphics.disableScissor();
        }
    }

    /** 创建可注册到 Screen 的图标页签或操作按钮。用 setSelected 同步当前页。 */
    public static UiButton tab(Bounds area, Component name, GraphicIcon icon, UiPreferences preferences,
                               Button.OnPress onPress) {
        var button = new UiButton(area.x(), area.y(), area.width(), area.height(), name, onPress,
                preferences, false, UiStyle.NETWORK);
        button.setIcon(icon);
        button.visible = area.width() > 2 && area.height() > 2;
        button.active = button.visible;
        return button;
    }

    /** 创建原生输入框。宿主可用 setFilter、setMaxLength 与 setResponder 配置编辑规则。 */
    public static EditBox editBox(Font font, Bounds area, Component name, UiPreferences preferences) {
        if (area.width() < 12 || area.height() < font.lineHeight + 4) {
            throw new IllegalArgumentException("Not enough room for an edit box");
        }
        return new EditBox(font, area.x(), area.y(), area.width(), area.height(), name) {
            private boolean bordered = true;
            private boolean drawingText;

            @Override
            public void setBordered(boolean bordered) {
                super.setBordered(bordered);
                this.bordered = bordered;
            }

            @Override
            public int getInnerWidth() {
                return drawingText ? getWidth() - 8 : super.getInnerWidth();
            }

            @Override
            public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                if (!bordered) {
                    super.renderWidget(graphics, mouseX, mouseY, partialTick);
                    return;
                }
                fieldFrame(graphics, getX(), getY(), getWidth(), getHeight(), preferences, isFocused());
                graphics.pose().pushPose();
                try {
                    // 保留原生带边框时的文字起点与点击定位，只替换外框。
                    graphics.pose().translate(4f, (getHeight() - 8) / 2, 0f);
                    drawingText = true;
                    super.setBordered(false);
                    super.renderWidget(graphics, mouseX, mouseY, partialTick);
                } finally {
                    super.setBordered(true);
                    drawingText = false;
                    graphics.pose().popPose();
                }
            }
        };
    }

    private static void fieldFrame(GuiGraphics graphics, int x, int y, int width, int height,
                                   UiPreferences preferences, boolean focused) {
        boolean contrast = preferences.highContrast();
        DarkModeTheme.rounded(graphics, x, y, width, height, 4, focused ? 2 : 1,
                focused ? FOCUS : outline(contrast), surface(contrast, FIELD));
    }

    static void drawTab(GuiGraphics graphics, int x, int y, int width, int height,
                        DarkModeTheme.State state, boolean selected) {
        boolean focused = state.focused();
        int edge = focused ? FOCUS : controlText(state, selected);
        int fill = surface(state.highContrast(), selected ? 0xE51B3549 : FIELD);
        if (state.press() > 0f) fill = GuiMotion.lerpColor(fill, 0xFF234C68, state.press());
        DarkModeTheme.rounded(graphics, x, y, width, height, 4, focused || selected ? 2 : 1, edge,
                fill);
    }

    static int controlText(DarkModeTheme.State state, boolean selected) {
        if (!state.enabled()) return DarkModeTheme.TEXT_MUTED;
        if (selected || state.focused() || state.highContrast()) return 0xFFFFFFFF;
        return GuiMotion.lerpColor(DarkModeTheme.TEXT_SECONDARY, 0xFFFFFFFF, state.hover());
    }

    static int outline(boolean contrast) {
        return contrast ? FOCUS : EDGE;
    }

    static int surface(boolean contrast, int fill) {
        return contrast ? CONTRAST_FILL : fill;
    }

    /** 获取设置项标签色，可与数值颜色分别应用于 Component。 */
    public static int labelColor(UiPreferences preferences) {
        return preferences.highContrast() ? DarkModeTheme.TEXT : LABEL;
    }
}
