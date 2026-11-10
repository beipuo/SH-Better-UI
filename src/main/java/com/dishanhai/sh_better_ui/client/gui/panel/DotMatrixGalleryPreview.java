package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.DotMatrixProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.DotMatrixProfile.ColorStop;
import com.dishanhai.sh_better_ui.client.gui.scaled.DotMatrixProfile.Fade;
import com.dishanhai.sh_better_ui.client.gui.scaled.DotMatrixProfile.Flow;
import com.dishanhai.sh_better_ui.client.gui.scaled.DotMatrixProfile.Gradient;
import com.dishanhai.sh_better_ui.client.gui.scaled.DotMatrixProfile.PointerWave;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiDotMatrix;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import static com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen.label;

/** 山海：材质图库中的实时点阵预览，重建控件时保留调节值。 */
final class DotMatrixGalleryPreview implements PreviewScene {
    static final int PREVIEW_EXTRA = 68;
    static final int SETTINGS_WIDTH = 72;
    private static final int GAP = 4;
    private static final int WAVE_TOGGLE_WIDTH = 70;
    static final int TOOLBAR_INSET = SETTINGS_WIDTH + GAP + WAVE_TOGGLE_WIDTH;
    private static final int MAX_FLOATING = 128;
    private static final int INITIAL_HUE = 280;
    private static final String TEXT = "gallery.dot_matrix.";
    private static final DotMatrixProfile BASE = DotMatrixProfile.VIOLET.withGrid(
            new DotMatrixProfile.Grid(6, 2, 0.42f, DotMatrixProfile.VIOLET.grid().seed()));

    private int hue = INITIAL_HUE;
    private int chance = Math.round(BASE.motion().floatProbability() * 100f);
    private int maxFloating = BASE.motion().maxFloatingDots();
    private Mode mode = Mode.TWINKLE;
    private int fadeChance = 28;
    private int seconds = Gradient.RAINBOW.periodMs() / 1000;
    private Colorway colorway = Colorway.RAINBOW;
    private Direction direction = Direction.RIGHT;
    private boolean motionSettings;
    private boolean pointerWave = true;
    private final UiDotMatrix matrix = new UiDotMatrix(profile());

    DotMatrixGalleryPreview() {}

    private DotMatrixGalleryPreview(Settings saved) {
        hue = saved.hue();
        chance = saved.chance();
        maxFloating = saved.maxFloating();
        mode = saved.mode();
        fadeChance = saved.fadeChance();
        seconds = saved.seconds();
        colorway = saved.colorway();
        direction = saved.direction();
        motionSettings = saved.motionSettings();
        pointerWave = saved.pointerWave();
        matrix.setProfile(profile());
    }

    @Override
    public void layout(PreviewContext host) {
        var toolbar = host.toolbar();
        var settings = new Bounds(toolbar.right() - SETTINGS_WIDTH, toolbar.y(), SETTINGS_WIDTH, toolbar.height());
        int waveWidth = Math.min(WAVE_TOGGLE_WIDTH, toolbar.width() - SETTINGS_WIDTH - GAP);
        widgets(host.viewport(), settings, host.preferences(), host::interactive, waveWidth).forEach(host::add);
    }

    @Override
    public void suspend() { matrix.clearPulse(); }

    @Override
    public Supplier<? extends PreviewScene> saveForRemount() {
        var saved = new Settings(hue, chance, maxFloating, mode, fadeChance, seconds, colorway, direction,
                motionSettings, pointerWave);
        return () -> new DotMatrixGalleryPreview(saved);
    }

    private record Settings(int hue, int chance, int maxFloating, Mode mode, int fadeChance, int seconds,
                            Colorway colorway, Direction direction, boolean motionSettings, boolean pointerWave) {}

    List<AbstractWidget> widgets(Bounds area, Bounds settingsArea, UiPreferences preferences,
                                 BooleanSupplier pointerEnabled) {
        return widgets(area, settingsArea, preferences, pointerEnabled, WAVE_TOGGLE_WIDTH);
    }

    private List<AbstractWidget> widgets(Bounds area, Bounds settingsArea, UiPreferences preferences,
                                         BooleanSupplier pointerEnabled, int waveWidth) {
        matrix.clearPulse();
        int gap = area.height() < 80 ? 2 : GAP;
        int controlHeight = area.height() < 80 ? 16 : area.height() < 100 ? 18 : 22;
        int x = area.x() + gap;
        int y = area.y() + gap;
        int width = area.width() - 2 * gap;
        int leftWidth = (width - gap) / 2;
        int rightWidth = width - leftWidth - gap;
        int rightX = x + leftWidth + gap;
        int secondRow = area.bottom() - gap - controlHeight;
        int firstRow = secondRow - gap - controlHeight;
        Bounds canvas = new Bounds(x, y, width, firstRow - gap - y);
        Bounds upperLeft = new Bounds(x, firstRow, leftWidth, controlHeight);
        Bounds upperRight = new Bounds(rightX, firstRow, rightWidth, controlHeight);
        Bounds lowerLeft = new Bounds(x, secondRow, leftWidth, controlHeight);
        Bounds lowerRight = new Bounds(rightX, secondRow, rightWidth, controlHeight);

        List<AbstractWidget> basic = List.of(
                slider(upperLeft, 0, 359, hue, "hue", preferences, value -> hue = value),
                cycleButton(upperRight, "effect", () -> mode.key, preferences, () -> mode = next(mode, Mode.values())),
                slider(lowerLeft, 0, 100, chance, "chance", preferences, value -> chance = value),
                slider(lowerRight, 0, MAX_FLOATING, maxFloating, "max", preferences, value -> maxFloating = value));
        var flow = cycleButton(lowerLeft, "flow", () -> direction.key, preferences,
                () -> direction = next(direction, Direction.values()));
        var period = slider(lowerRight, 1, 12, seconds, "period", preferences, value -> seconds = value);
        var palette = cycleButton(upperRight, "gradient", () -> colorway.key, preferences, () -> {
            colorway = next(colorway, Colorway.values());
            flow.active = period.active = !colorway.stops.isEmpty();
        });
        flow.active = period.active = !colorway.stops.isEmpty();
        List<AbstractWidget> advanced = List.of(
                slider(upperLeft, 0, 100, fadeChance, "fade_chance", preferences, value -> fadeChance = value),
                palette, flow, period);
        Runnable showSettings = () -> {
            basic.forEach(widget -> widget.visible = !motionSettings);
            advanced.forEach(widget -> widget.visible = motionSettings);
        };
        var settings = DeveloperPanelScreen.button(settingsArea.x(), settingsArea.y(), settingsArea.width(),
                settingsArea.height(), settingsLabel(), GraphicIcon.SETTINGS, preferences, pressed -> {
                    motionSettings = !motionSettings;
                    showSettings.run();
                    pressed.setMessage(settingsLabel());
                });
        var waveToggle = DeveloperPanelScreen.button(settingsArea.x() - GAP - waveWidth, settingsArea.y(),
                waveWidth, settingsArea.height(), pointerWaveLabel(), null, preferences, pressed -> {
                    pointerWave = !pointerWave;
                    matrix.setProfile(profile());
                    pressed.setMessage(pointerWaveLabel());
                    ((UiButton) pressed).setSelected(pointerWave);
                });
        waveToggle.setSelected(pointerWave);
        waveToggle.setTooltip(Tooltip.create(label(TEXT + "pointer_wave.tooltip")));
        showSettings.run();
        List<AbstractWidget> widgets = new ArrayList<>();
        widgets.add(waveToggle);
        widgets.add(settings);
        widgets.add(new Canvas(canvas, matrix, preferences, pointerEnabled));
        widgets.addAll(basic);
        widgets.addAll(advanced);
        return widgets;
    }

    private UiSlider slider(Bounds area, int min, int max, int value, String key, UiPreferences preferences,
                            IntConsumer update) {
        return new UiSlider(area, min, max, 1, value, preferences, current -> label(TEXT + key, current), current -> {
            update.accept(current);
            matrix.setProfile(profile());
        });
    }

    private UiButton cycleButton(Bounds area, String key, Supplier<String> value, UiPreferences preferences,
                                 Runnable advance) {
        return DeveloperPanelScreen.button(area.x(), area.y(), area.width(), area.height(),
                label(TEXT + key, label(TEXT + value.get())), null, preferences, pressed -> {
                    advance.run();
                    matrix.setProfile(profile());
                    pressed.setMessage(label(TEXT + key, label(TEXT + value.get())));
                });
    }

    private Component settingsLabel() {
        return label(TEXT + (motionSettings ? "settings_basic" : "settings_motion"));
    }

    private Component pointerWaveLabel() {
        return label(TEXT + "pointer_wave", Component.translatable(pointerWave ? "options.on" : "options.off"));
    }

    DotMatrixProfile profile() {
        DotMatrixProfile.Palette palette = BASE.palette();
        DotMatrixProfile.Motion motion = BASE.motion();
        float normalized = hue / 360f;
        DotMatrixProfile profile = BASE.withPalette(new DotMatrixProfile.Palette(
                        tint(palette.backgroundArgb(), normalized), tint(palette.dotArgb(), normalized),
                        tint(palette.highlightArgb(), normalized), palette.glow()))
                .withMotion(new DotMatrixProfile.Motion(mode.effect, motion.periodMs(), chance / 100f,
                        maxFloating, motion.floatDistance()))
                .withFade(new Fade(fadeChance / 100f, 2600))
                .withPointerWave(pointerWave ? PointerWave.DEFAULT : PointerWave.NONE);
        if (colorway.stops.isEmpty()) return profile;
        List<ColorStop> stops = colorway.stops;
        if (hue != INITIAL_HUE) {
            float offset = (hue - INITIAL_HUE) / 360f;
            stops = stops.stream().map(stop -> new ColorStop(stop.position(), shiftHue(stop.argb(), offset))).toList();
        }
        return profile.withGradient(new Gradient(stops, seconds * 1000, direction.flow));
    }

    private static int tint(int argb, float hue) {
        int red = argb >>> 16 & 255, green = argb >>> 8 & 255, blue = argb & 255;
        int brightest = Math.max(red, Math.max(green, blue));
        if (brightest == 0) return argb;
        int darkest = Math.min(red, Math.min(green, blue));
        float saturation = (brightest - darkest) / (float) brightest;
        return (argb & 0xFF00_0000) | Mth.hsvToRgb(hue, saturation, brightest / 255f);
    }

    private static int shiftHue(int argb, float offset) {
        float[] hsv = Color.RGBtoHSB(argb >>> 16 & 255, argb >>> 8 & 255, argb & 255, null);
        float shifted = hsv[0] + offset;
        float hue = shifted - (float) Math.floor(shifted);
        return (argb & 0xFF00_0000) | Mth.hsvToRgb(hue, hsv[1], hsv[2]);
    }

    private static <T extends Enum<T>> T next(T current, T[] values) {
        return values[(current.ordinal() + 1) % values.length];
    }

    private enum Mode {
        TWINKLE("twinkle", DotMatrixProfile.Effect.TWINKLE),
        WAVE("wave", DotMatrixProfile.Effect.WAVE),
        STEADY("steady", DotMatrixProfile.Effect.STEADY);

        final String key;
        final DotMatrixProfile.Effect effect;

        Mode(String key, DotMatrixProfile.Effect effect) {
            this.key = key;
            this.effect = effect;
        }

    }

    private enum Colorway {
        RAINBOW("rainbow", Gradient.RAINBOW.stops()),
        REVERSE("reverse", Gradient.REVERSE_RAINBOW.stops()),
        AURORA("aurora", Gradient.AURORA.stops()),
        SOLID("solid", Gradient.NONE.stops());

        final String key;
        final List<ColorStop> stops;

        Colorway(String key, List<ColorStop> stops) {
            this.key = key;
            this.stops = stops;
        }
    }

    private enum Direction {
        RIGHT("right", Flow.RIGHT), DOWN("down", Flow.DOWN),
        DIAGONAL("diagonal", Flow.DIAGONAL), OUTWARD("outward", Flow.OUTWARD);

        final String key;
        final Flow flow;

        Direction(String key, Flow flow) {
            this.key = key;
            this.flow = flow;
        }
    }

    private static final class Canvas extends Button {
        private final Bounds area;
        private final UiDotMatrix matrix;
        private final UiPreferences preferences;
        private final BooleanSupplier pointerEnabled;

        Canvas(Bounds area, UiDotMatrix matrix, UiPreferences preferences, BooleanSupplier pointerEnabled) {
            super(area.x(), area.y(), area.width(), area.height(), label(TEXT + "pulse"),
                    ignored -> matrix.pulse(area, area.x() + area.width() * 0.5, area.y() + area.height() * 0.5),
                    DEFAULT_NARRATION);
            this.area = area;
            this.matrix = matrix;
            this.preferences = preferences;
            this.pointerEnabled = pointerEnabled;
            active = !preferences.highContrast();
        }

        @Override
        public void onClick(double x, double y) {
            matrix.pulse(area, x, y);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean available = pointerEnabled.getAsBoolean() && Minecraft.getInstance().isWindowActive();
            matrix.render(graphics, area, available ? mouseX : Double.NaN, available ? mouseY : Double.NaN,
                    PageTransition.nowMs(), preferences);
            int edge = isFocused() || preferences.highContrast() ? DarkModeTheme.textColor()
                    : isHovered() ? DarkModeTheme.hoverBorderColor() : DarkModeTheme.borderColor();
            graphics.renderOutline(area.x(), area.y(), area.width(), area.height(), edge);
            if (preferences.highContrast()) {
                var font = Minecraft.getInstance().font;
                graphics.drawCenteredString(font, label(TEXT + "contrast"), area.x() + area.width() / 2,
                        area.y() + (area.height() - font.lineHeight) / 2, DarkModeTheme.textColor());
            } else {
                int inset = Math.min(GAP, Math.max(0, (area.height() - GraphicIcon.PIXELS) / 2));
                GraphicIcon.INTERACTION.draw(graphics, area.right() - GraphicIcon.PIXELS - GAP,
                        area.bottom() - GraphicIcon.PIXELS - inset, 1,
                        isHoveredOrFocused() ? DarkModeTheme.textColor() : DarkModeTheme.mutedTextColor(), preferences);
            }
        }
    }
}
