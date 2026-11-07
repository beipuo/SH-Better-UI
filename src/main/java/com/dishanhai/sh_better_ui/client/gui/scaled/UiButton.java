package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** 山海：保留原生输入、焦点和旁白的暗色按钮。 */
final class UiButton extends Button {
    private final UiPreferences preferences;
    private final boolean primary;
    private final DarkModeTheme.Interaction motion = DarkModeTheme.Interaction.control();
    private boolean justPressed;

    UiButton(int x, int y, int width, int height, Component label, OnPress onPress,
             UiPreferences preferences, boolean primary) {
        super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
        this.preferences = preferences;
        this.primary = primary;
    }

    @Override
    public void onPress() {
        justPressed = true;
        super.onPress();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var state = motion.sample(isHovered(), justPressed, isFocused(), active, preferences, PageTransition.nowMs());
        justPressed = false;
        int x = getX(), y = getY(), width = getWidth(), height = getHeight();
        DarkModeTheme.button(graphics, x, y, width, height, state, primary, () -> {
            var font = Minecraft.getInstance().font;
            var lines = font.split(getMessage(), Math.max(1, width - 12));
            int count = Math.min(lines.size(), Math.max(1, (height - 4) / font.lineHeight));
            int textY = y + (height - count * font.lineHeight) / 2;
            for (int i = 0; i < count; i++) {
                var line = lines.get(i);
                graphics.drawString(font, line, x + (width - font.width(line)) / 2,
                        textY + i * font.lineHeight, state.controlText(), false);
            }
        });
    }
}
