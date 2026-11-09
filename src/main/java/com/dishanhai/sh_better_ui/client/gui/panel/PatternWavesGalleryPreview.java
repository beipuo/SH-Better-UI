package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.PatternWaves;
import com.dishanhai.sh_better_ui.client.gui.scaled.PatternWavesProfile.Preset;
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
import java.util.function.Supplier;

import static com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen.label;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

/** 山海：可切换预设和暂停的波面画布。窗口重建时保留预设与暂停状态。 */
final class PatternWavesGalleryPreview implements PreviewScene {
    static final int PREVIEW_EXTRA = 68;
    private static final String TEXT = "gallery.pattern_waves.";
    private static final int GAP = 4;
    private static final int BUTTON_HEIGHT = 22;
    private static final Preset[] PRESETS = Preset.values();

    private Preset preset;
    private final PatternWaves waves;

    PatternWavesGalleryPreview() { this(Preset.SILK, false); }

    private PatternWavesGalleryPreview(Preset preset, boolean paused) {
        this.preset = preset;
        waves = new PatternWaves(preset.profile());
        waves.setPaused(paused);
    }

    @Override
    public void layout(PreviewContext host) {
        widgets(host.viewport(), host.preferences(), host::interactive).forEach(host::add);
    }

    @Override
    public Supplier<? extends PreviewScene> saveForRemount() {
        Preset saved = preset;
        boolean paused = waves.isPaused();
        return () -> new PatternWavesGalleryPreview(saved, paused);
    }

    List<AbstractWidget> widgets(Bounds area, UiPreferences preferences, BooleanSupplier inputAllowed) {
        int x = area.x() + GAP;
        int y = area.y() + GAP;
        int width = area.width() - 2 * GAP;
        int height = area.height() - 2 * GAP;
        boolean controlsAside = height < 74;
        int controlWidth = Math.min(122, (width - GAP) / 2);
        int controlsX = controlsAside ? area.right() - GAP - controlWidth
                : area.x() + (area.width() - 2 * controlWidth - GAP) / 2;
        int controlsY = controlsAside ? area.y() + (area.height() - 2 * BUTTON_HEIGHT - GAP) / 2
                : area.bottom() - GAP - BUTTON_HEIGHT;
        Bounds canvas = new Bounds(x, y, controlsAside ? controlsX - GAP - x : width,
                controlsAside ? height : controlsY - GAP - y);

        var change = DeveloperPanelScreen.button(controlsX, controlsY, controlWidth, BUTTON_HEIGHT,
                presetLabel(), null, preferences, pressed -> {
                    preset = PRESETS[(preset.ordinal() + 1) % PRESETS.length];
                    waves.setProfile(preset.profile());
                    pressed.setMessage(presetLabel());
                    ((UiButton) pressed).setModeIndicators(PRESETS.length, preset.ordinal());
                });
        change.setModeIndicators(PRESETS.length, preset.ordinal());

        int pauseX = controlsAside ? controlsX : controlsX + controlWidth + GAP;
        int pauseY = controlsAside ? controlsY + BUTTON_HEIGHT + GAP : controlsY;
        var pause = DeveloperPanelScreen.button(pauseX, pauseY, controlWidth, BUTTON_HEIGHT,
                pauseLabel(), pauseIcon(), preferences, pressed -> {
                    waves.setPaused(!waves.isPaused());
                    pressed.setMessage(pauseLabel());
                    var control = (UiButton) pressed;
                    control.setLeadingIcon(pauseIcon());
                    control.setSelected(waves.isPaused());
                });
        pause.setSelected(waves.isPaused());
        pause.active = !preferences.reducedMotion() && !preferences.highContrast();
        if (!pause.active) pause.setPreview(label(TEXT + (preferences.highContrast() ? "contrast" : "reduced_motion")));
        return List.of(new Canvas(canvas, waves, preferences, inputAllowed), change, pause);
    }

    private Component presetLabel() {
        return label(TEXT + "preset", label(TEXT + preset.name().toLowerCase(Locale.ROOT)));
    }

    private Component pauseLabel() {
        return label(TEXT + (waves.isPaused() ? "resume" : "pause"));
    }

    private GraphicIcon pauseIcon() {
        return waves.isPaused() ? GraphicIcon.RESUME : GraphicIcon.PAUSE;
    }

    private static final class Canvas extends Button {
        private final Bounds area;
        private final PatternWaves waves;
        private final UiPreferences preferences;
        private final BooleanSupplier inputAllowed;

        Canvas(Bounds area, PatternWaves waves, UiPreferences preferences, BooleanSupplier inputAllowed) {
            super(area.x(), area.y(), area.width(), area.height(), label(TEXT + "canvas"), ignored -> {
                if (inputAllowed.getAsBoolean()) {
                    waves.mousePressed(area.x() + area.width() * 0.5, area.y() + area.height() * 0.5,
                            GLFW_MOUSE_BUTTON_LEFT);
                }
            }, DEFAULT_NARRATION);
            this.area = area;
            this.waves = waves;
            this.preferences = preferences;
            this.inputAllowed = inputAllowed;
            active = !preferences.reducedMotion() && !preferences.highContrast();
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            if (button != GLFW_MOUSE_BUTTON_LEFT && isMouseOver(x, y) && inputAllowed.getAsBoolean()) {
                waves.mousePressed(x, y, button);
            }
            return super.mouseClicked(x, y, button);
        }

        @Override
        public void onClick(double x, double y) {
            if (inputAllowed.getAsBoolean()) waves.mousePressed(x, y, GLFW_MOUSE_BUTTON_LEFT);
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            super.updateWidgetNarration(output);
            if (active) output.add(NarratedElementType.HINT, label(TEXT + "hint"));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            UiMouseState mouse = active && inputAllowed.getAsBoolean() ? UiMouseState.read() : null;
            waves.draw(graphics, area, mouse, preferences);
            if (preferences.highContrast()) {
                var font = Minecraft.getInstance().font;
                var lines = font.split(label(TEXT + "contrast"), area.width() - 2 * GAP);
                int y = area.y() + (area.height() - lines.size() * font.lineHeight) / 2;
                for (var line : lines) {
                    graphics.drawCenteredString(font, line, area.x() + area.width() / 2, y, DarkModeTheme.textColor());
                    y += font.lineHeight;
                }
            } else if (active) {
                GraphicIcon.INTERACTION.draw(graphics, area.right() - GraphicIcon.PIXELS - GAP,
                        area.bottom() - GraphicIcon.PIXELS - GAP, 1,
                        isHoveredOrFocused() ? DarkModeTheme.textColor() : DarkModeTheme.mutedTextColor(), preferences);
            }
            int border = isFocused() || preferences.highContrast() ? DarkModeTheme.textColor()
                    : isHovered() ? DarkModeTheme.hoverBorderColor() : DarkModeTheme.borderColor();
            graphics.renderOutline(area.x(), area.y(), area.width(), area.height(), border);
            if (isFocused()) {
                graphics.renderOutline(area.x() + 1, area.y() + 1, area.width() - 2, area.height() - 2, border);
            }
        }
    }
}
