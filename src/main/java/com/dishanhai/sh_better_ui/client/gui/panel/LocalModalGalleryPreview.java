package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiExpansionController;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLocalModal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlideConfirm;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import static com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen.label;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.BUTTON_HEIGHT;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.drawCentered;

/** 山海：展示重叠请求、底层控件状态和区域外的原生输入。 */
final class LocalModalGalleryPreview implements PreviewScene {
    static final int HEIGHT = 86;
    private static final int GAP = 4;
    private static final Component SLIDE = Component.translatable("sh_better_ui.slide_confirm.label");
    private final boolean[] selected = new boolean[2];
    private Font font;
    private UiLocalModal local;
    private int confirmed;
    private int outsideClicks;
    private Component status = label("gallery.local_modal_count", 0);

    @Override
    public void layout(PreviewContext host) {
        suspend();
        font = host.font();
        local = ShBetterUiApi.interactions().localModal(host.screen(), host.preferences());
        Bounds viewport = host.viewport();
        int cell = Math.min(70, (viewport.width() - 16 - 3 * GAP) / 4);
        if (cell <= 0) return;
        int rowWidth = 4 * cell + 3 * GAP;
        int x = viewport.x() + (viewport.width() - rowWidth) / 2;
        int y = viewport.y() + (viewport.height() >= HEIGHT ? 24 : 2);
        int expandedWidth = 3 * cell + 2 * GAP;
        addSource(host, x, y, cell, expandedWidth, false);
        for (int i = 0; i < selected.length; i++) {
            int index = i;
            var button = host.add(new UiButton(x + (i + 1) * (cell + GAP), y, cell, BUTTON_HEIGHT,
                    label("gallery.local_modal_neighbor", i + 1), pressed -> {
                        selected[index] = !selected[index];
                        ((UiButton) pressed).setSelected(selected[index]);
                    }, host.preferences(), false, UiStyle.LIQUID_GLASS));
            button.setSelected(selected[i]);
        }
        addSource(host, x + 3 * (cell + GAP), y, cell, expandedWidth, true);
        int outsideX = viewport.height() >= HEIGHT ? x + rowWidth / 2 - 55 : viewport.right() - 114;
        host.add(new UiButton(outsideX, y + BUTTON_HEIGHT + GAP, 110, BUTTON_HEIGHT,
                label("gallery.local_modal_outside", outsideClicks),
                pressed -> pressed.setMessage(label("gallery.local_modal_outside", ++outsideClicks)),
                host.preferences(), false, UiStyle.LIQUID_GLASS));
    }

    private void addSource(PreviewContext host, int x, int y, int width, int expandedWidth, boolean right) {
        var placement = UiExpansionController.anchored(expandedWidth, BUTTON_HEIGHT, right ? 1 : 0, 0);
        host.add(new UiButton(x, y, width, BUTTON_HEIGHT,
                label(right ? "gallery.local_modal_right" : "gallery.local_modal_left"), pressed -> {
                    boolean opened = local.openSlide((UiButton) pressed, host.viewport(), placement,
                            UiExpansionController.Config.DEFAULT, UiSlideConfirm.LocalConfig.DEFAULT, SLIDE,
                            () -> status = label("gallery.local_modal_count", ++confirmed));
                    status = opened ? label("gallery.local_modal_count", confirmed) : label("gallery.local_modal_busy");
                }, host.preferences(), true, UiStyle.LIQUID_GLASS));
    }

    @Override
    public void render(UiFrame frame) {
        var viewport = frame.viewport();
        if (viewport.height() >= HEIGHT) {
            drawCentered(frame.graphics(), font, label("gallery.local_modal_hint"), viewport.x() + 5,
                    viewport.y(), viewport.width() - 10);
            drawCentered(frame.graphics(), font, status, viewport.x() + 5, viewport.y() + 65, viewport.width() - 10);
        } else {
            drawCentered(frame.graphics(), font, status, viewport.x() + 4, viewport.y() + 28, viewport.width() - 122);
        }
    }

    @Override
    public void suspend() {
        if (local != null) local.interrupt();
    }
}
