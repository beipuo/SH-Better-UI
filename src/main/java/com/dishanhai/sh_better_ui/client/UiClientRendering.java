package com.dishanhai.sh_better_ui.client;

import com.dishanhai.sh_better_ui.client.blur.GaussBlur;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** 山海：两个加载器共用的叠层绘制与清理顺序。 */
final class UiClientRendering {
    private UiClientRendering() {}

    static void renderScreenOverlays(Screen owner, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (owner != Minecraft.getInstance().screen) return;
        UiPointerCapture.validate(owner);
        UiOverlayHost overlay = UiOverlayHost.current(owner);
        if (overlay != null) overlay.render(graphics, mouseX, mouseY, partialTick);
        UiToast.renderOverlay(graphics);
    }

    static void renderHudOverlay(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && minecraft.getOverlay() == null && !minecraft.options.hideGui) {
            UiToast.renderOverlay(graphics);
        }
    }

    static void screenClosing(Screen owner) {
        UiFrameScheduler.ownerClosing(owner, () -> {
            try {
                UiPointerCapture.ownerClosed(owner);
                UiOverlayHost overlay = UiOverlayHost.current(owner);
                if (overlay != null) overlay.ownerClosed();
            } finally {
                clearBlur();
            }
        });
    }

    static void endFrame() {
        // Screen Pre 被取消时没有 Screen Post，未消费的绘制请求仍应在帧尾清理。
        clearBlur();
        UiFrameScheduler.endFrame();
    }

    private static void clearBlur() {
        GaussBlur.clear();
        GaussBlur.clearTopLayer();
    }
}
