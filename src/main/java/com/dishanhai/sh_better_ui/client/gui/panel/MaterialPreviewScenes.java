package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.glass.GlassBlur;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import static com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen.label;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.BUTTON_HEIGHT;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.centeredControl;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.drawBackdrop;
import static com.dishanhai.sh_better_ui.client.gui.panel.GalleryPreviewLayout.drawCentered;

/** 山海：共享不同按钮样式、玻璃配方和资源图标的演示。 */
final class MaterialPreviewScenes {
    private MaterialPreviewScenes() {}

    static final class StyledButton implements PreviewScene {
        private final UiStyle style;
        private boolean selected;

        StyledButton(UiStyle style) {
            this.style = style;
        }

        @Override
        public void layout(PreviewContext host) {
            var area = centeredControl(host.viewport());
            var button = new UiButton(area.x(), area.y(), area.width(), area.height(), Component.translatable("gui.done"),
                    pressed -> {
                        selected = !selected;
                        ((UiButton) pressed).setSelected(selected);
                    }, host.preferences(), false, style);
            button.setLeadingIcon(GraphicIcon.RESET);
            button.setSelected(selected);
            host.add(button);
        }
    }

    static final class Glass implements PreviewScene {
        private final GlassStyle style;
        private Font font;

        Glass(GlassStyle style) {
            this.style = style;
        }

        @Override
        public void layout(PreviewContext host) {
            font = host.font();
        }

        @Override
        public void render(UiFrame frame) {
            var preview = frame.viewport();
            var graphics = frame.graphics();
            int x = preview.x() + 8;
            int y = preview.y() + 5;
            int width = preview.width() - 16;
            int height = preview.height() - 10;
            drawBackdrop(graphics, x, y, width, height);
            boolean sampled = frame.preferences().useBackdropBlur() && GlassBlur.tryBackdrop(graphics,
                    new GuiRect(x, y, width, height), 1f, style, 12f, 4f);
            if (!sampled) {
                graphics.fill(x, y, x + width, y + height, 0x9020252E);
                drawCentered(graphics, font, label("gallery.blur_unavailable"), x + 3, y + 7, width - 6);
            }
        }
    }

    static final class Icon implements PreviewScene {
        private final GraphicIcon icon;
        private Font font;

        Icon(GraphicIcon icon) {
            this.icon = icon;
        }

        @Override
        public void layout(PreviewContext host) {
            font = host.font();
        }

        @Override
        public void render(UiFrame frame) {
            var preview = frame.viewport();
            var graphics = frame.graphics();
            int x = preview.x() + 10;
            int size = GraphicIcon.size(2);
            icon.draw(graphics, x, preview.y() + (preview.height() - size) / 2, 2, 0xFFFFFFFF, frame.preferences());
            int pathX = x + size + 8;
            var lines = font.split(Component.literal(icon.texture().toString()), preview.right() - pathX - 8);
            int y = preview.y() + (preview.height() - lines.size() * font.lineHeight) / 2;
            for (var line : lines) {
                graphics.drawString(font, line, pathX, y, LiquidGlassTheme.textColor(), false);
                y += font.lineHeight;
            }
        }
    }

    static final class GradientCard implements PreviewScene {
        private final DarkModeTheme.Interaction motion = DarkModeTheme.Interaction.card();
        private Font font;

        @Override
        public void layout(PreviewContext host) {
            font = host.font();
        }

        @Override
        public void render(UiFrame frame) {
            var preview = frame.viewport();
            var graphics = frame.graphics();
            int width = Math.min(164, preview.width() - 32);
            int x = preview.x() + (preview.width() - width) / 2;
            int y = preview.y() + 6;
            var state = motion.sample(false, false, false, true, frame.preferences(), frame.nowMs());
            DarkModeTheme.card(graphics, x, y, width, BUTTON_HEIGHT, state,
                    () -> drawCentered(graphics, font, Component.translatable("gui.stats"), x + 6, y, width - 12));
        }
    }
}
