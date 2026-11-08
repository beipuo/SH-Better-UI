package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.Antigravity;
import com.dishanhai.sh_better_ui.client.gui.scaled.AntigravityProfile.ColorMode;
import com.dishanhai.sh_better_ui.client.gui.scaled.AntigravityProfile.Shape;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMouseState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import static com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen.label;

/** 山海：反重力粒子画布；形状、配色和暂停状态随窗口重建保留。 */
final class AntigravityGalleryPreview {
    static final int PREVIEW_EXTRA = 112;
    private static final String TEXT = "gallery.antigravity.";
    private static final int GAP = 4;
    private static final int BUTTON_HEIGHT = 22;
    private static final Shape[] SHAPES = Shape.values();
    private static final ColorMode[] COLOR_MODES = ColorMode.values();
    private final Antigravity field = ShBetterUiApi.graphics().antigravity();

    List<AbstractWidget> widgets(Bounds area, UiPreferences preferences, BooleanSupplier inputAllowed) {
        int x = area.x() + GAP;
        int y = area.y() + GAP;
        int width = Math.max(1, area.width() - 2 * GAP);
        int height = Math.max(1, area.height() - 2 * GAP);
        boolean controlsAside = height < 92;
        int buttonHeight = Math.min(BUTTON_HEIGHT, Math.max(1, (height - GAP) / 2));
        int controlWidth = controlsAside ? Math.min(122, Math.max(1, (width - Math.min(80, width / 3) - 2 * GAP) / 2))
                : Math.min(122, Math.max(1, (width - GAP) / 2));
        int controlsWidth = 2 * controlWidth + GAP;
        int controlsHeight = 2 * buttonHeight + GAP;
        int controlsX = controlsAside ? x + width - controlsWidth : x + (width - controlsWidth) / 2;
        int controlsY = controlsAside ? y + (height - controlsHeight) / 2 : y + height - controlsHeight;
        var canvas = new Bounds(x, y, controlsAside ? Math.max(1, controlsX - GAP - x) : width,
                controlsAside ? height : Math.max(1, controlsY - GAP - y));

        var shape = DeveloperPanelScreen.button(controlsX, controlsY, controlWidth, buttonHeight,
                shapeLabel(), null, preferences, pressed -> {
                    Shape next = SHAPES[(field.profile().particleShape().ordinal() + 1) % SHAPES.length];
                    field.setProfile(field.profile().toBuilder().particleShape(next).build());
                    pressed.setMessage(shapeLabel());
                    ((UiButton) pressed).setModeIndicators(SHAPES.length, next.ordinal());
                });
        shape.setModeIndicators(SHAPES.length, field.profile().particleShape().ordinal());
        var color = DeveloperPanelScreen.button(controlsX + controlWidth + GAP, controlsY, controlWidth, buttonHeight,
                colorLabel(), null, preferences, pressed -> {
                    ColorMode next = COLOR_MODES[(field.profile().colorMode().ordinal() + 1) % COLOR_MODES.length];
                    field.setProfile(field.profile().toBuilder().colorMode(next).build());
                    pressed.setMessage(colorLabel());
                    ((UiButton) pressed).setModeIndicators(COLOR_MODES.length, next.ordinal());
                });
        color.setModeIndicators(COLOR_MODES.length, field.profile().colorMode().ordinal());
        var pause = DeveloperPanelScreen.button(controlsX, controlsY + buttonHeight + GAP, controlWidth, buttonHeight,
                pauseLabel(), pauseIcon(), preferences, pressed -> {
                    field.setPaused(!field.isPaused());
                    pressed.setMessage(pauseLabel());
                    var control = (UiButton) pressed;
                    control.setLeadingIcon(pauseIcon());
                    control.setSelected(field.isPaused());
                });
        pause.setSelected(field.isPaused());
        var restart = DeveloperPanelScreen.button(controlsX + controlWidth + GAP, controlsY + buttonHeight + GAP,
                controlWidth, buttonHeight, label(TEXT + "restart"), GraphicIcon.REFRESH, preferences,
                ignored -> {
                    field.restart();
                    pause.setMessage(pauseLabel());
                    pause.setLeadingIcon(pauseIcon());
                    pause.setSelected(false);
                });
        pause.active = restart.active = !preferences.reducedMotion() && !preferences.highContrast();
        if (!pause.active) {
            var hint = label(TEXT + (preferences.highContrast() ? "contrast" : "reduced_motion"));
            pause.setPreview(hint);
            restart.setPreview(hint);
        }
        return List.of(new Canvas(canvas, field, preferences, inputAllowed), shape, color, pause, restart);
    }

    void suspend() { field.suspend(); }

    private Component shapeLabel() {
        return label(TEXT + "shape", label(TEXT + field.profile().particleShape().name().toLowerCase(Locale.ROOT)));
    }

    private Component colorLabel() {
        return label(TEXT + "color_mode", label(TEXT + field.profile().colorMode().name().toLowerCase(Locale.ROOT)));
    }

    private Component pauseLabel() { return label(TEXT + (field.isPaused() ? "resume" : "pause")); }
    private GraphicIcon pauseIcon() { return field.isPaused() ? GraphicIcon.RESUME : GraphicIcon.PAUSE; }

    private static final class Canvas extends Button {
        private final Bounds area;
        private final Antigravity field;
        private final UiPreferences preferences;
        private final BooleanSupplier inputAllowed;

        Canvas(Bounds area, Antigravity field, UiPreferences preferences, BooleanSupplier inputAllowed) {
            super(area.x(), area.y(), area.width(), area.height(), label(TEXT + "canvas"), ignored -> {
                if (inputAllowed.getAsBoolean() && !field.isPaused()
                        && !preferences.reducedMotion() && !preferences.highContrast()) field.burst(1);
            }, DEFAULT_NARRATION);
            this.area = area;
            this.field = field;
            this.preferences = preferences;
            this.inputAllowed = inputAllowed;
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            return inputAllowed.getAsBoolean() && super.mouseClicked(x, y, button);
        }

        @Override
        public void onClick(double x, double y) {
            // 鼠标按住与释放由只读快照驱动；键盘仍走 Button.onPress。
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            super.updateWidgetNarration(output);
            String hint = preferences.highContrast() ? "contrast" : preferences.reducedMotion() ? "reduced_motion"
                    : field.isPaused() ? "paused_hint" : "hint";
            output.add(NarratedElementType.HINT, label(TEXT + hint));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean motion = !preferences.reducedMotion() && !preferences.highContrast();
            UiMouseState mouse = active && motion && !field.isPaused() && inputAllowed.getAsBoolean()
                    ? UiMouseState.read() : null;
            graphics.fill(area.x(), area.y(), area.right(), area.bottom(), 0xFF0D101D);
            boolean available = field.draw(graphics, area, mouse, preferences);
            if (preferences.highContrast() || !available) {
                var font = Minecraft.getInstance().font;
                var lines = font.split(label(TEXT + (preferences.highContrast() ? "contrast" : "unavailable")),
                        Math.max(1, area.width() - 2 * GAP));
                int y = area.y() + Math.max(0, (area.height() - lines.size() * font.lineHeight) / 2);
                graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
                try {
                    for (var line : lines) {
                        graphics.drawCenteredString(font, line, area.x() + area.width() / 2, y, DarkModeTheme.TEXT);
                        y += font.lineHeight;
                    }
                } finally {
                    graphics.disableScissor();
                }
            } else if (motion && !field.isPaused() && area.width() >= GraphicIcon.PIXELS + 2 * GAP
                    && area.height() >= GraphicIcon.PIXELS + 2 * GAP) {
                GraphicIcon.INTERACTION.draw(graphics, area.right() - GraphicIcon.PIXELS - GAP,
                        area.bottom() - GraphicIcon.PIXELS - GAP, 1,
                        isHoveredOrFocused() ? DarkModeTheme.TEXT : DarkModeTheme.TEXT_MUTED, preferences);
            }
            int border = isFocused() || preferences.highContrast() ? DarkModeTheme.TEXT
                    : isHovered() ? DarkModeTheme.BORDER_HOVER : DarkModeTheme.BORDER;
            graphics.renderOutline(area.x(), area.y(), area.width(), area.height(), border);
            if (isFocused() && area.width() > 2 && area.height() > 2) {
                graphics.renderOutline(area.x() + 1, area.y() + 1, area.width() - 2, area.height() - 2, border);
            }
        }
    }
}
