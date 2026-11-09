package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LightRays;
import com.dishanhai.sh_better_ui.client.gui.scaled.LightRaysProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.LightRaysProfile.Builder;
import com.dishanhai.sh_better_ui.client.gui.scaled.LightRaysProfile.Origin;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMouseState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import static com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen.label;

/** 山海：光束画布和实时调参。预览重建时保留当前底板和参数。 */
final class LightRaysGalleryPreview implements PreviewScene {
    static final int PREVIEW_EXTRA = 65;
    private static final String TEXT = "gallery.light_rays.";
    private static final int PAD = 2;
    private static final int GAP = 2;
    private static final int REPLAY_WIDTH = 24;
    private static final Origin[] ORIGINS = Origin.values();
    private static final Parameter[] PARAMETERS = Parameter.values();

    private final LightRays rays;
    private final Builder settings;
    private int selected;
    private boolean edited;

    LightRaysGalleryPreview() {
        this(null, 0);
    }

    private LightRaysGalleryPreview(LightRaysProfile profile, int selected) {
        rays = profile == null ? ShBetterUiApi.graphics().lightRays() : ShBetterUiApi.graphics().lightRays(profile);
        this.selected = selected;
        settings = LightRaysProfile.builder();
        edited = profile != null;
        if (edited) {
            settings.lightMode(profile.lightMode());
            for (Parameter parameter : PARAMETERS) {
                parameter.write.accept(settings, (float) parameter.read.applyAsDouble(profile));
            }
        }
    }

    @Override
    public void layout(PreviewContext host) {
        suspend();
        widgets(host.viewport(), host.preferences(), host::interactive).forEach(host::add);
    }

    List<AbstractWidget> widgets(Bounds area, UiPreferences preferences, BooleanSupplier inputAllowed) {
        int width = area.width() - 2 * PAD;
        int controlsWidth = Math.min(196, Math.max(116, width / 2));
        int x = area.right() - PAD - controlsWidth;
        int rowHeight = Math.min(22, (area.height() - 2 * PAD - 2 * GAP) / 3);
        int rowPitch = rowHeight + GAP;
        int y = area.y() + (area.height() - 3 * rowHeight - 2 * GAP) / 2;
        var canvas = new Bounds(area.x() + PAD, area.y() + PAD,
                width - controlsWidth - GAP, area.height() - 2 * PAD);

        var board = DeveloperPanelScreen.button(x, y, controlsWidth - REPLAY_WIDTH - GAP, rowHeight,
                boardLabel(), null, preferences, pressed -> {
                    settings.lightMode(!profile().lightMode());
                    applySettings();
                    pressed.setMessage(boardLabel());
                    ((UiButton) pressed).setSelected(profile().lightMode());
                });
        board.setSelected(profile().lightMode());
        board.setPreview(label(TEXT + "board_hint"));

        var replay = DeveloperPanelScreen.button(area.right() - PAD - REPLAY_WIDTH, y, REPLAY_WIDTH, rowHeight,
                label(TEXT + "replay"), null, preferences, rays::restart);
        replay.setIcon(GraphicIcon.REFRESH);
        replay.active = !preferences.reducedMotion() && !preferences.highContrast();

        var sliderArea = new Bounds(x, y + 2 * rowPitch, controlsWidth, rowHeight);
        var sliders = new ArrayList<UiSlider>(PARAMETERS.length);
        for (Parameter parameter : PARAMETERS) {
            var slider = new UiSlider(sliderArea, parameter.min, parameter.max, parameter.step,
                    parameter.currentValue(profile()), preferences, parameter::message, value -> {
                        parameter.write.accept(settings, value / (float) parameter.scale);
                        applySettings();
                    });
            slider.visible = parameter.ordinal() == selected;
            sliders.add(slider);
        }

        int previousWidth = (controlsWidth - GAP) / 2;
        var previous = DeveloperPanelScreen.button(x, y + rowPitch, previousWidth, rowHeight,
                label(TEXT + "previous"), null, preferences, () -> select(sliders, -1));
        var next = DeveloperPanelScreen.button(x + previousWidth + GAP, y + rowPitch,
                controlsWidth - previousWidth - GAP, rowHeight, label(TEXT + "next"), null, preferences,
                () -> select(sliders, 1));
        var widgets = new ArrayList<AbstractWidget>(List.of(new Canvas(canvas, preferences, inputAllowed),
                board, replay, previous, next));
        widgets.addAll(sliders);
        return widgets;
    }

    private void select(List<UiSlider> sliders, int direction) {
        sliders.get(selected).visible = false;
        selected = Math.floorMod(selected + direction, sliders.size());
        sliders.get(selected).visible = true;
    }

    LightRaysProfile profile() { return rays.profile(); }

    private void applySettings() {
        edited = true;
        rays.setProfile(settings.build());
    }

    @Override
    public void suspend() { rays.suspend(); }

    @Override
    public Supplier<? extends PreviewScene> saveForRemount() {
        var profile = edited ? rays.profile() : null;
        int parameter = selected;
        return () -> new LightRaysGalleryPreview(profile, parameter);
    }

    private Component boardLabel() {
        return label(TEXT + (profile().lightMode() ? "light_board" : "dark_board"));
    }

    private enum Parameter {
        SPEED("speed", 0, 300, 5, LightRaysProfile::raysSpeed, Builder::raysSpeed),
        SPREAD("spread", 5, 200, 5, LightRaysProfile::lightSpread, Builder::lightSpread),
        DENSITY("density", 10, 600, 5, LightRaysProfile::rayDensity, Builder::rayDensity),
        INTENSITY("intensity", 0, 400, 5, LightRaysProfile::intensity, Builder::intensity),
        ORIGIN("origin", 0, ORIGINS.length - 1, 1, 1, profile -> profile.raysOrigin().ordinal(),
                (builder, value) -> builder.raysOrigin(ORIGINS[value.intValue()])),
        TILT("tilt", -90, 90, 1, 1, LightRaysProfile::tilt, Builder::tilt),
        LENGTH("length", 10, 500, 5, LightRaysProfile::rayLength, Builder::rayLength),
        SHARPNESS("sharpness", 0, 100, 5, LightRaysProfile::raySharpness, Builder::raySharpness),
        GLOW("glow", 0, 200, 5, LightRaysProfile::glow, Builder::glow),
        HAZE("haze", 0, 100, 5, LightRaysProfile::haze, Builder::haze),
        OPACITY("opacity", 0, 100, 5, LightRaysProfile::opacity, Builder::opacity),
        FOLLOW("follow", 0, 100, 5, LightRaysProfile::mouseInfluence, Builder::mouseInfluence);

        final String key;
        final int min;
        final int max;
        final int step;
        final int scale;
        final ToDoubleFunction<LightRaysProfile> read;
        final BiConsumer<Builder, Float> write;

        Parameter(String key, int min, int max, int step,
                  ToDoubleFunction<LightRaysProfile> read, BiConsumer<Builder, Float> write) {
            this(key, min, max, step, 100, read, write);
        }

        Parameter(String key, int min, int max, int step, int scale,
                  ToDoubleFunction<LightRaysProfile> read, BiConsumer<Builder, Float> write) {
            this.key = key;
            this.min = min;
            this.max = max;
            this.step = step;
            this.scale = scale;
            this.read = read;
            this.write = write;
        }

        int currentValue(LightRaysProfile profile) {
            return (int) Math.round(read.applyAsDouble(profile) * scale);
        }

        Component message(int value) {
            if (this == ORIGIN) return label(TEXT + key, label(TEXT + ORIGINS[value].name().toLowerCase(Locale.ROOT)));
            if (this == TILT) return label(TEXT + key, value);
            return label(TEXT + "percent", label(TEXT + key), value);
        }
    }

    private final class Canvas extends Button {
        private final Bounds area;
        private final UiPreferences preferences;
        private final BooleanSupplier inputAllowed;
        private boolean available = true;

        Canvas(Bounds area, UiPreferences preferences, BooleanSupplier inputAllowed) {
            super(area.x(), area.y(), area.width(), area.height(), label(TEXT + "canvas"), ignored -> {},
                    DEFAULT_NARRATION);
            this.area = area;
            this.preferences = preferences;
            this.inputAllowed = inputAllowed;
            setTooltip(Tooltip.create(label(TEXT + (preferences.highContrast() ? "contrast"
                    : preferences.reducedMotion() ? "reduced_motion" : "hint"))));
        }

        @Override
        public void onPress() {
            if (inputAllowed.getAsBoolean()) rays.pulse();
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            return inputAllowed.getAsBoolean() && super.mouseClicked(x, y, button);
        }

        @Override
        public void onClick(double x, double y) {
            // 鼠标点击由 LightRays 自己采样；键盘走 onPress。
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            super.updateWidgetNarration(output);
            String hint = preferences.highContrast() ? "contrast" : !available ? "unavailable"
                    : preferences.reducedMotion() ? "reduced_motion" : "hint";
            output.add(NarratedElementType.HINT, label(TEXT + hint));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean light = profile().lightMode();
            int textColor = light ? 0xFF22344E : DarkModeTheme.textColor();
            graphics.fill(area.x(), area.y(), area.right(), area.bottom(), light ? 0xFFF0F4FF : 0xFF0B1220);
            UiMouseState mouse = inputAllowed.getAsBoolean() && !preferences.reducedMotion() && !preferences.highContrast()
                    ? UiMouseState.read() : null;
            available = rays.draw(graphics, area, mouse, preferences);
            if (!available) renderFallback(graphics, textColor);
            int border = isFocused() || preferences.highContrast() ? textColor
                    : light ? 0xFF778299 : DarkModeTheme.borderColor();
            graphics.renderOutline(area.x(), area.y(), area.width(), area.height(), border);
            if (isFocused()) {
                graphics.renderOutline(area.x() + 1, area.y() + 1, area.width() - 2, area.height() - 2, border);
            }
        }

        private void renderFallback(GuiGraphics graphics, int color) {
            var font = Minecraft.getInstance().font;
            var lines = font.split(label(TEXT + (preferences.highContrast() ? "contrast" : "unavailable")),
                    area.width() - 2 * PAD);
            int y = area.y() + Math.max(0, (area.height() - lines.size() * font.lineHeight) / 2);
            graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
            try {
                for (var line : lines) {
                    graphics.drawCenteredString(font, line, area.x() + area.width() / 2, y, color);
                    y += font.lineHeight;
                }
            } finally {
                graphics.disableScissor();
            }
        }
    }
}
