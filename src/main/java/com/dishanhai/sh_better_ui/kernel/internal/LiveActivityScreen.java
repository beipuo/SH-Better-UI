// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Cursor-enabled activity entry point for gameplay. Native Escape returns to the game. */
final class LiveActivityScreen extends Screen {
    private boolean initialFocus = true;

    LiveActivityScreen() { super(Component.translatable("sh_better_ui.live.title")); }

    boolean takeInitialFocus() {
        boolean take = initialFocus;
        initialFocus = false;
        return take;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        int buttonWidth = Math.min(72, Math.max(20, width - 12));
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds((width - buttonWidth) / 2, Math.max(6, height - 68), buttonWidth, 20).build());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var hint = font.split(Component.translatable("sh_better_ui.live.hint"), Math.max(1, width - 12));
        int y = Math.max(4, height - 73 - hint.size() * font.lineHeight);
        for (var line : hint) {
            graphics.drawCenteredString(font, line, width / 2, y, 0xffeeeeee);
            y += font.lineHeight;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
