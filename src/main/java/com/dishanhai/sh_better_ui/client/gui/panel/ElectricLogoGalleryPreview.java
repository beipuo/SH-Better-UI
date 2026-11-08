package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.ElectricLogo;
import com.dishanhai.sh_better_ui.client.gui.display.ElectricLogoProfile;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

import static com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen.label;

/** 山海：用可聚焦的画布演示悬停、点击波纹和贴图过渡。 */
final class ElectricLogoGalleryPreview {
    static final int PREVIEW_EXTRA = 68;
    private static final String TEXT = "gallery.electric_logo.";
    private static final int GAP = 4;
    private static final int BUTTON_HEIGHT = 20;
    private static final List<SampleImage> IMAGES = List.of(
            new SampleImage("bolt", null),
            new SampleImage("star", GraphicIcon.FAVORITE.texture()),
            new SampleImage("creeper", GraphicIcon.MINECRAFT.texture()));

    private ElectricLogo logo;
    private int selected;

    List<AbstractWidget> widgets(Bounds area, UiPreferences preferences) {
        if (logo == null) logo = new ElectricLogo(IMAGES.get(selected).texture());
        boolean stacked = area.height() >= 82;
        int buttonWidth = stacked ? Math.min(190, area.width() - 2 * GAP) : area.width() * 45 / 100;
        int buttonX = stacked ? area.x() + (area.width() - buttonWidth) / 2 : area.right() - GAP - buttonWidth;
        int buttonY = stacked ? area.bottom() - GAP - BUTTON_HEIGHT : area.y() + (area.height() - BUTTON_HEIGHT) / 2;
        int canvasRight = stacked ? area.right() - GAP : buttonX - GAP;
        int canvasBottom = stacked ? buttonY - GAP : area.bottom() - GAP;
        var canvas = new Bounds(area.x() + GAP, area.y() + GAP,
                canvasRight - area.x() - GAP, canvasBottom - area.y() - GAP);

        var change = DeveloperPanelScreen.button(buttonX, buttonY, buttonWidth, BUTTON_HEIGHT,
                changeLabel(), GraphicIcon.REFRESH, preferences, pressed -> {
                    selected = (selected + 1) % IMAGES.size();
                    logo.setTexture(IMAGES.get(selected).texture());
                    pressed.setMessage(changeLabel());
                });
        return List.of(new Canvas(canvas, logo, preferences), change);
    }

    private Component changeLabel() {
        return label(TEXT + "change", label(TEXT + IMAGES.get(selected).name()));
    }

    void release() {
        if (logo == null) return;
        logo.close();
        logo = null;
    }

    private record SampleImage(String name, ResourceLocation texture) {}

    private static final class Canvas extends Button {
        private final Bounds area;
        private final ElectricLogo logo;
        private final UiPreferences preferences;

        Canvas(Bounds area, ElectricLogo logo, UiPreferences preferences) {
            super(area.x(), area.y(), area.width(), area.height(), label(TEXT + "canvas"),
                    ignored -> logo.pulse(area.x() + area.width() * 0.5, area.y() + area.height() * 0.5),
                    DEFAULT_NARRATION);
            this.area = area;
            this.logo = logo;
            this.preferences = preferences;
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            super.updateWidgetNarration(output);
            if (narrationPriority() != NarratableEntry.NarrationPriority.NONE) {
                output.add(NarratedElementType.HINT,
                        label(TEXT + (preferences.reducedMotion() ? "reduced_motion" : "hint")));
            }
        }

        @Override
        public void onClick(double x, double y) {
            if (!preferences.reducedMotion()) logo.pulse(x, y);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(area.x(), area.y(), area.right(), area.bottom(),
                    preferences.highContrast() ? 0xFF000000 : 0xFF0D101D);
            if (!logo.draw(graphics, area.x(), area.y(), area.width(), area.height(), mouseX, mouseY,
                    ElectricLogoProfile.DEFAULT, preferences)) {
                var font = Minecraft.getInstance().font;
                var lines = font.split(label(TEXT + "unavailable"), area.width() - 2 * GAP);
                int y = area.y() + (area.height() - lines.size() * font.lineHeight) / 2;
                for (var line : lines) {
                    graphics.drawCenteredString(font, line, area.x() + area.width() / 2, y, DarkModeTheme.TEXT);
                    y += font.lineHeight;
                }
            } else {
                GraphicIcon.INTERACTION.draw(graphics, area.right() - GraphicIcon.PIXELS - GAP,
                        area.bottom() - GraphicIcon.PIXELS - GAP, 1,
                        isHoveredOrFocused() ? DarkModeTheme.TEXT : DarkModeTheme.TEXT_MUTED, preferences);
            }
            int border = isFocused() || preferences.highContrast() ? DarkModeTheme.TEXT
                    : isHovered() ? DarkModeTheme.BORDER_HOVER : DarkModeTheme.BORDER;
            graphics.renderOutline(area.x(), area.y(), area.width(), area.height(), border);
            if (isFocused()) {
                graphics.renderOutline(area.x() + 1, area.y() + 1, area.width() - 2, area.height() - 2, border);
            }
        }
    }
}
