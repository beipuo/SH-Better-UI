package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.audio.AudioBand;
import com.dishanhai.sh_better_ui.client.audio.MusicMotion;
import com.dishanhai.sh_better_ui.client.audio.SystemAudioCapture;
import com.dishanhai.sh_better_ui.client.blur.GaussBlur;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.glass.GlassBlur;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.DotMatrixProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.FluidMorph;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiRenderUtil;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiDotMatrix;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiFrameHandoff;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCapsuleReveal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScreenReturn;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiConfirmation;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerTracker;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScrollableSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSkeleton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.GlassBackdropSettings;
import com.dishanhai.sh_better_ui.config.ControlFeedbackSettings;
import com.dishanhai.sh_better_ui.config.TabScrollSettings;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.IntConsumer;

/** 山海：在同一画面编辑 SHBUI 客户端配置和快捷键。 */
public final class ControlPanelScreen extends Screen implements UiScreenReturn.Target {
    private static final int MAX_WIDTH = 624;
    private static final int PADDING = 14;
    private static final int GAP = 8;
    private static final int BUTTON_HEIGHT = 22;
    private static final int HEADER_HEIGHT = 64;
    private static final int CONFIG_HEADER_HEIGHT = 94;
    private static final int FOOTER_HEIGHT = 46;
    private static final int GLASS_STACK_HEIGHT = 190;
    private static final int GLASS_SIDE_HEIGHT = 146;
    private static final int SCROLLBAR_WIDTH = 5;
    private static final float BODY_RADIUS = 5f;
    private static final List<Section> SECTIONS = List.of(Section.values());
    private static final List<UiSectionBar.Section> TABS = SECTIONS.stream().map(section -> section.tab).toList();
    private static final List<Area> AREAS = List.of(Area.values());
    private static final List<UiSectionBar.Section> MAIN_TABS = AREAS.stream().map(area -> area.tab).toList();
    private static final DotMatrixProfile.Motion BACKGROUND_MOTION = DotMatrixProfile.VIOLET.motion();

    static {
        GaussBlur.allowScreen(screen -> screen instanceof ControlPanelScreen);
    }

    private final Screen parent;
    private final KeyMapping keyMapping;
    private final Predicate<ClientUiSettings> save;
    private final Map<AbstractWidget, Integer> rows = new LinkedHashMap<>();
    private final EnumMap<Scale, UiSlider> sliders = new EnumMap<>(Scale.class);
    private final List<ToggleOption> options = new ArrayList<>();
    private final List<SliderOption> optionSliders = new ArrayList<>();
    private final List<ModOverviewGroup> overviewGroups = new ArrayList<>();
    private final int[] sectionScroll = new int[SECTIONS.size()];
    private final int[] areaScroll = new int[AREAS.size()];
    private final MusicMotion musicMotion = ShBetterUiApi.motion().musicMotion(AudioBand.BEAT, 28f, 125f);
    private SystemAudioCapture backgroundAudio;
    private float backgroundBeat;
    private final UiPointerTracker backgroundPointer = ShBetterUiApi.interactions().pointerTracker();
    private final UiDotMatrix background = ShBetterUiApi.graphics().dotMatrix(DotMatrixProfile.VIOLET
            .withPalette(new DotMatrixProfile.Palette(0xFF0B101B, 0x72FFFFFF, 0xFFFFFFFF, 0f))
            .withGrid(new DotMatrixProfile.Grid(8, 2, 1f, 27L))
            .withMotion(new DotMatrixProfile.Motion(this::backgroundBrightness, BACKGROUND_MOTION.periodMs(),
                    0f, 0, 0f))
            .withInteraction(new DotMatrixProfile.Interaction(0, DotMatrixProfile.VIOLET.interaction().rippleRadius()))
            .withPointerLens(DotMatrixProfile.PointerLens.DEFAULT)
            .withGradient(DotMatrixProfile.Gradient.RAINBOW));
    private final UiCapsuleReveal entrance = new UiCapsuleReveal();
    private final UiScreenReturn returning = new UiScreenReturn(this);
    private final UiFrameHandoff pageHandoff = new UiFrameHandoff();
    private final InterruptibleAnimation pageMorph = new InterruptibleAnimation(Spring.FLUID_PANEL, 1f);
    private final InterruptibleAnimation pageOrigin = new InterruptibleAnimation(Spring.INDICATOR);
    private final Draft defaults;
    private Draft saved;
    private Draft draft;
    private Draft beforeReset;
    private UiPreferences preferences;
    private UiOverlayHost overlays;
    private UiScrollableSectionBar sections;
    private UiScrollableSectionBar navigation;
    private UiToggleButton enabled;
    private UiToggleButton sampling;
    private UiToggleButton contrast;
    private UiToggleButton motion;
    private UiToggleButton musicToggle;
    private SystemAudioCapture.Status displayedAudioStatus;
    private UiToggleButton widgetBlur;
    private UiButton shortcut;
    private UiButton clearShortcut;
    private UiButton back;
    private UiButton developer;
    private UiButton reset;
    private UiButton apply;
    private Component status;
    private boolean restoreDeveloperFocus;
    private boolean entered;
    private boolean capturingShortcut;
    private InputConstants.Key modifierKey;
    private Section selectedSection = Section.GLASS;
    private Section requestedSection;
    private Area selectedArea = Area.CONFIGURATION;
    private Area requestedArea;
    private long nextOverviewRefresh;
    private int scroll;
    private int scrollGrab = -1;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int bodyTop;
    private int bodyBottom;
    private int contentWidth;
    private int contentHeight;
    private int glassWidth;
    private Bounds visiblePage;
    private boolean sidePreview;
    private boolean fits;
    private int layoutWidth;
    private int layoutHeight;

    public ControlPanelScreen(Screen parent, KeyMapping keyMapping, Predicate<ClientUiSettings> save) {
        super(label("title"));
        this.parent = parent;
        this.keyMapping = keyMapping;
        this.save = save;
        defaults = new Draft(ClientUiSettings.DEFAULT,
                new Shortcut(keyMapping.getDefaultKey(), keyMapping.getDefaultKeyModifier().name()));
        saved = new Draft(ClientUiSettings.current(), new Shortcut(keyMapping.getKey(), keyMapping.getKeyModifier().name()));
        draft = saved;
        preferences = UiPreferences.fromSettings(draft.settings());
    }

    @Override
    protected void init() {
        if (sections != null) sections.cancelDrag();
        if (navigation != null) navigation.cancelDrag();
        sections = null;
        navigation = null;
        if (width != layoutWidth || height != layoutHeight) {
            pageMorph.snapTo(1f);
            pageHandoff.clear();
            layoutWidth = width;
            layoutHeight = height;
        }
        back = null;
        clearPage();
        fits = width >= 240 && height >= 186;
        if (!fits) {
            stopBackgroundMusic();
            entrance.finish();
            if (width >= 32 && height >= 34) {
                int backWidth = Math.min(width - 8, 96);
                addRenderableWidget(button((width - backWidth) / 2, height - 26, backWidth,
                        Component.translatable("gui.back"), GraphicIcon.BACK, false, this::onClose));
            }
            return;
        }

        panelWidth = Math.min(MAX_WIDTH, width - 12);
        sidePreview = panelWidth >= 388;
        panelHeight = Math.min(height - 12, CONFIG_HEADER_HEIGHT + GLASS_STACK_HEIGHT + FOOTER_HEIGHT);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        if (!entered) {
            entered = true;
            entrance.startAt(width * 0.5f, top + 5f);
        }
        bodyTop = top + (selectedArea == Area.CONFIGURATION ? CONFIG_HEADER_HEIGHT : HEADER_HEIGHT);
        bodyBottom = top + panelHeight - FOOTER_HEIGHT;
        int x = left + PADDING;
        int innerWidth = panelWidth - 2 * PADDING;
        int mainTabWidth = Math.max(80, (innerWidth - 8) / 3);
        navigation = addRenderableWidget(new UiScrollableSectionBar(new Bounds(x, top + 35, innerWidth, BUTTON_HEIGHT),
                preferences, MAIN_TABS, selectedArea.ordinal(), index -> requestedArea = AREAS.get(index),
                draft.settings().tabs(), mainTabWidth));
        sections = addRenderableWidget(new UiScrollableSectionBar(new Bounds(x, top + 65, innerWidth, BUTTON_HEIGHT),
                preferences, TABS, selectedSection.ordinal(), index -> requestedSection = SECTIONS.get(index),
                draft.settings().tabs(), UiScrollableSectionBar.DEFAULT_TAB_WIDTH));
        addFooter(x, innerWidth);
        buildPage();
        if (restoreDeveloperFocus && developer != null && minecraft != null && minecraft.screen == this) {
            setFocused(developer);
            restoreDeveloperFocus = false;
        }
    }

    private void clearPage() {
        rows.keySet().forEach(this::removeWidget);
        rows.clear();
        sliders.clear();
        options.clear();
        optionSliders.clear();
        overviewGroups.clear();
        developer = null;
        enabled = null;
        sampling = null;
        contrast = null;
        motion = null;
        musicToggle = null;
        displayedAudioStatus = null;
        widgetBlur = null;
        shortcut = null;
        clearShortcut = null;
        capturingShortcut = false;
        modifierKey = null;
        scrollGrab = -1;
        visiblePage = null;
    }

    private void buildPage() {
        int x = left + PADDING;
        boolean configuration = selectedArea == Area.CONFIGURATION;
        int retainedScroll = configuration ? sectionScroll[selectedSection.ordinal()] : areaScroll[selectedArea.ordinal()];
        sections.visible = sections.active = configuration;
        bodyTop = top + (configuration ? CONFIG_HEADER_HEIGHT : HEADER_HEIGHT);
        contentHeight = !configuration ? selectedArea == Area.DEVELOPER ? 120 : 0 : switch (selectedSection) {
            case GLASS -> sidePreview ? GLASS_SIDE_HEIGHT : GLASS_STACK_HEIGHT;
            case DISPLAY -> 68;
            case FEEDBACK, TABS -> 120;
            case SHORTCUT -> 42;
        };
        contentWidth = panelWidth - 2 * PADDING
                - (selectedArea != Area.OVERVIEW && maxScroll() == 0 ? 0 : SCROLLBAR_WIDTH + GAP);
        glassWidth = sidePreview ? contentWidth * 5 / 8 : contentWidth;
        if (selectedArea == Area.DEVELOPER) addDeveloperControls(x);
        else if (selectedArea == Area.OVERVIEW) refreshOverview(PageTransition.nowMs());
        else switch (selectedSection) {
            case GLASS -> addGlassControls(x);
            case DISPLAY -> addDisplayControls(x);
            case FEEDBACK -> addFeedbackControls(x);
            case TABS -> addTabControls(x);
            case SHORTCUT -> addShortcutControls(x);
        }
        scrollTo(retainedScroll);
        refreshActions();
    }

    private void addGlassControls(int x) {
        int columnWidth = (glassWidth - GAP) / 2;
        int right = x + columnWidth + GAP;
        int rightWidth = glassWidth - columnWidth - GAP;
        enabled = toggle(x, 16, glassWidth, "enabled", value -> label("enabled", onOff(value)),
                value -> editBackdrop(draft.settings().backdrop().withEnabled(value)));
        enabled.setIcons(GraphicIcon.ENABLE, GraphicIcon.DISABLE);
        Scale[] scales = Scale.values();
        for (int i = 0; i < scales.length; i++) {
            boolean firstColumn = i % 2 == 0;
            Scale scale = scales[i];
            var area = new Bounds(firstColumn ? x : right, 42 + i / 2 * 26,
                    firstColumn ? columnWidth : rightWidth, BUTTON_HEIGHT);
            var slider = new UiSlider(area, 0, scale.maxPercent, 1, scale.percent(draft.settings().backdrop()),
                    preferences, percent -> label("scale", label(scale.key), percent),
                    percent -> editBackdrop(scale.write(draft.settings().backdrop(), percent / 100f)));
            slider.setTooltip(Tooltip.create(label(scale.key + ".tooltip")));
            sliders.put(scale, row(slider));
        }
    }

    private void addDisplayControls(int x) {
        int toggleWidth = (contentWidth - GAP) / 2;
        contrast = toggle(x, 16, toggleWidth, "contrast", value -> label("contrast", onOff(value)), value -> {
            var settings = draft.settings();
            editDisplay(value, settings.reducedMotion(), settings.widgetBackdropBlur());
        });
        motion = toggle(x + toggleWidth + GAP, 16, contentWidth - toggleWidth - GAP, "motion",
                value -> label("motion", onOff(value)), value -> {
                    var settings = draft.settings();
                    editDisplay(settings.highContrast(), value, settings.widgetBackdropBlur());
                });
        musicToggle = toggle(x, 42, contentWidth, "background_music",
                value -> label("background_music", onOff(value)), this::setBackgroundMusic);
        musicToggle.button().setLeadingIcon(GraphicIcon.MUSIC);
    }

    private boolean musicMotionAllowed() {
        return !preferences.reducedMotion() && !preferences.highContrast();
    }

    private void setBackgroundMusic(boolean enabled) {
        stopBackgroundMusic();
        if (enabled && musicMotionAllowed()) {
            backgroundAudio = ShBetterUiApi.audio().systemOutput();
            backgroundAudio.start();
        }
        refreshMusicToggle();
    }

    private void stopBackgroundMusic() {
        if (backgroundAudio != null) {
            backgroundAudio.close();
            backgroundAudio = null;
        }
        backgroundBeat = 0f;
        musicMotion.reset();
    }

    private void refreshMusicToggle() {
        if (musicToggle == null) return;
        var audioStatus = backgroundAudio == null ? SystemAudioCapture.Status.READY : backgroundAudio.status();
        boolean allowed = musicMotionAllowed();
        if (audioStatus == displayedAudioStatus && musicToggle.button().active == allowed) return;
        displayedAudioStatus = audioStatus;
        musicToggle.button().active = allowed;
        musicToggle.setValue(audioStatus == SystemAudioCapture.Status.STARTING
                || audioStatus == SystemAudioCapture.Status.RUNNING);
        musicToggle.button().setPreview(allowed ? Component.translatable(audioStatus.translationKey())
                .append(CommonComponents.NEW_LINE).append(label("background_music.tooltip"))
                : label("background_music.suspended"));
    }

    private void addDeveloperControls(int x) {
        sampling = toggle(x, 16, contentWidth, "sampling",
                value -> label("sampling", label(value ? "sampling.dense" : "sampling.standard")),
                value -> editBackdrop(draft.settings().backdrop().withDenseSampling(value)));
        widgetBlur = toggle(x, 42, contentWidth, "widget_blur",
                value -> label("widget_blur", onOff(value)), value -> {
                    var settings = draft.settings();
                    editDisplay(settings.highContrast(), settings.reducedMotion(), value);
                });
        developer = row(button(x, 94, contentWidth, label("developer.title"), GraphicIcon.CODE, false, () -> {
            restoreDeveloperFocus = true;
            minecraft.setScreen(new DeveloperPanelScreen(this, preferences, () -> developer));
        }));
        developer.setPreview(label("developer_config.tools.tooltip"));
    }

    private void refreshOverview(long now) {
        var groups = ModOverviewData.capture(minecraft);
        int y = 4;
        for (int index = 0; index < groups.size(); index++) {
            ModOverviewGroup widget;
            if (index < overviewGroups.size()) {
                widget = overviewGroups.get(index);
                widget.update(groups.get(index));
            } else {
                widget = new ModOverviewGroup(left + PADDING, y, contentWidth, groups.get(index), font, preferences);
                overviewGroups.add(row(widget));
            }
            rows.put(widget, y);
            y += widget.getHeight() + GAP;
        }
        contentHeight = y;
        nextOverviewRefresh = now + 1_000L;
        scrollTo(scroll);
    }

    private void addShortcutControls(int x) {
        int clearWidth = 52;
        shortcut = row(button(x, 16, contentWidth - clearWidth - GAP, CommonComponents.EMPTY,
                null, false, () -> {
                    capturingShortcut = true;
                    refreshActions();
                }));
        shortcut.setPreview(label("shortcut.tooltip"));
        clearShortcut = row(button(x + contentWidth - clearWidth, 16, clearWidth,
                label("shortcut.clear"), null, false, () -> finishShortcut(new Shortcut(InputConstants.UNKNOWN, "NONE"))));
        clearShortcut.setPreview(label("shortcut.clear.tooltip"));
    }

    private void addFeedbackControls(int x) {
        option(x, 16, "hover_animation", () -> draft.settings().feedback().hoverAnimation(), value -> {
            var current = draft.settings().feedback();
            editFeedback(new ControlFeedbackSettings(value, current.pressAnimation(), current.selectionPulse(),
                    current.tooltipDelayMs()));
        });
        option(x, 42, "press_animation", () -> draft.settings().feedback().pressAnimation(), value -> {
            var current = draft.settings().feedback();
            editFeedback(new ControlFeedbackSettings(current.hoverAnimation(), value, current.selectionPulse(),
                    current.tooltipDelayMs()));
        });
        option(x, 68, "selection_pulse", () -> draft.settings().feedback().selectionPulse(), value -> {
            var current = draft.settings().feedback();
            editFeedback(new ControlFeedbackSettings(current.hoverAnimation(), current.pressAnimation(), value,
                    current.tooltipDelayMs()));
        });
        optionSlider(x, 94, "tooltip_delay", 0, ControlFeedbackSettings.MAX_TOOLTIP_DELAY_MS, 50,
                () -> draft.settings().feedback().tooltipDelayMs(), value -> {
                    var current = draft.settings().feedback();
                    editFeedback(new ControlFeedbackSettings(current.hoverAnimation(), current.pressAnimation(),
                            current.selectionPulse(), value));
                });
    }

    private void addTabControls(int x) {
        option(x, 16, "tab_wheel", () -> draft.settings().tabs().wheelEnabled(), value -> {
            var current = draft.settings().tabs();
            editTabs(new TabScrollSettings(value, current.dragEnabled(), current.autoReveal(), current.scrollStep()));
        });
        option(x, 42, "tab_drag", () -> draft.settings().tabs().dragEnabled(), value -> {
            var current = draft.settings().tabs();
            editTabs(new TabScrollSettings(current.wheelEnabled(), value, current.autoReveal(), current.scrollStep()));
        });
        option(x, 68, "tab_auto_reveal", () -> draft.settings().tabs().autoReveal(), value -> {
            var current = draft.settings().tabs();
            editTabs(new TabScrollSettings(current.wheelEnabled(), current.dragEnabled(), value, current.scrollStep()));
        });
        optionSlider(x, 94, "tab_scroll_step", TabScrollSettings.MIN_SCROLL_STEP, TabScrollSettings.MAX_SCROLL_STEP, 1,
                () -> draft.settings().tabs().scrollStep(), value -> {
                    var current = draft.settings().tabs();
                    editTabs(new TabScrollSettings(current.wheelEnabled(), current.dragEnabled(), current.autoReveal(), value));
                });
    }

    private void option(int x, int y, String key, BooleanSupplier read, Consumer<Boolean> write) {
        var control = toggle(x, y, contentWidth, key, value -> label(key, onOff(value)), write);
        options.add(new ToggleOption(control, read));
    }

    private void optionSlider(int x, int y, String key, int min, int max, int step, IntSupplier read, IntConsumer write) {
        var slider = new UiSlider(new Bounds(x, y, contentWidth, BUTTON_HEIGHT), min, max, step, read.getAsInt(),
                preferences, value -> label(key, value), write);
        slider.setTooltip(Tooltip.create(label(key + ".tooltip")));
        optionSliders.add(new SliderOption(row(slider), read));
    }

    private void addFooter(int x, int width) {
        int y = bodyBottom + 16;
        int column = (width - 2 * GAP) / 3;
        back = addRenderableWidget(button(x, y, column, Component.translatable("gui.back"), GraphicIcon.BACK,
                false, this::onClose));
        reset = addRenderableWidget(button(x + column + GAP, y, column, label("reset"), GraphicIcon.RESET,
                false, this::resetDraft));
        apply = addRenderableWidget(button(x + 2 * (column + GAP), y, width - 2 * (column + GAP),
                label("apply"), GraphicIcon.SUCCESS, true, this::apply));
        apply.setPreview(label("apply.tooltip"));
        back.setTabOrderGroup(2);
        reset.setTabOrderGroup(2);
        apply.setTabOrderGroup(2);
    }

    private UiButton button(int x, int y, int width, Component text, GraphicIcon icon, boolean primary, Runnable action) {
        var widget = new UiButton(x, y, width, BUTTON_HEIGHT, text, button -> action.run(),
                preferences, primary, UiStyle.LIQUID_GLASS);
        widget.setSampleBackground(false);
        widget.setLeadingIcon(icon);
        return widget;
    }

    private <T extends AbstractWidget> T row(T widget) {
        widget.setTabOrderGroup(1);
        rows.put(widget, widget.getY());
        return addWidget(widget);
    }

    private UiToggleButton toggle(int x, int y, int width, String key, Function<Boolean, Component> text,
                                  Consumer<Boolean> onChange) {
        var toggle = new UiToggleButton(new Bounds(x, y, width, BUTTON_HEIGHT), false, preferences,
                UiStyle.LIQUID_GLASS, text, onChange);
        toggle.setModeIndicatorsVisible(true);
        toggle.button().setSampleBackground(false);
        toggle.button().setPreview(label(key + ".tooltip"));
        row(toggle.button());
        return toggle;
    }

    private void editBackdrop(GlassBackdropSettings backdrop) {
        edit(new Draft(draft.settings().withBackdrop(backdrop), draft.shortcut()));
    }

    private void editDisplay(boolean highContrast, boolean reducedMotion, boolean widgetBackdropBlur) {
        edit(new Draft(draft.settings().withDisplay(highContrast, reducedMotion, widgetBackdropBlur),
                draft.shortcut()));
    }

    private void editFeedback(ControlFeedbackSettings feedback) {
        edit(new Draft(draft.settings().withFeedback(feedback), draft.shortcut()));
    }

    private void editTabs(TabScrollSettings tabs) {
        edit(new Draft(draft.settings().withTabs(tabs), draft.shortcut()));
    }

    private void edit(Draft next) {
        if (!next.equals(draft)) {
            draft = next;
            beforeReset = null;
            status = null;
        }
        refreshActions();
    }

    private void resetDraft() {
        if (beforeReset == null) {
            beforeReset = draft;
            draft = defaults;
        } else {
            draft = beforeReset;
            beforeReset = null;
        }
        status = null;
        sliders.forEach((scale, slider) -> slider.setValue(scale.percent(draft.settings().backdrop())));
        optionSliders.forEach(option -> option.control().setValue(option.read().getAsInt()));
        refreshActions();
    }

    private void refreshActions() {
        var settings = draft.settings();
        var nextPreferences = UiPreferences.fromSettings(settings);
        sections.setSettings(settings.tabs());
        navigation.setSettings(settings.tabs());
        if (!preferences.equals(nextPreferences)) {
            preferences = nextPreferences;
            if (!musicMotionAllowed()) stopBackgroundMusic();
            for (GuiEventListener listener : children()) {
                if (listener instanceof UiButton button) button.setPreferences(preferences);
                else if (listener instanceof UiSlider slider) slider.setPreferences(preferences);
                else if (listener instanceof UiScrollableSectionBar bar) bar.setPreferences(preferences);
                else if (listener instanceof ModOverviewGroup group) group.setPreferences(preferences);
            }
        }
        if (selectedArea == Area.DEVELOPER) {
            sampling.setValue(settings.backdrop().denseSampling());
            widgetBlur.setValue(settings.widgetBackdropBlur() && !settings.highContrast());
            widgetBlur.button().active = !settings.highContrast();
            if (settings.highContrast()) widgetBlur.button().setMessage(label("widget_blur.suspended"));
        } else if (selectedArea == Area.CONFIGURATION) switch (selectedSection) {
            case GLASS -> {
                var backdrop = settings.backdrop();
                enabled.setValue(backdrop.enabled());
            }
            case DISPLAY -> {
                contrast.setValue(settings.highContrast());
                motion.setValue(settings.reducedMotion());
                refreshMusicToggle();
            }
            case SHORTCUT -> {
                shortcut.setMessage(capturingShortcut ? label("shortcut.capture") : label("shortcut.binding",
                        draft.shortcut().displayName(keyMapping)));
                shortcut.setSelected(capturingShortcut);
                clearShortcut.active = !capturingShortcut && !draft.shortcut().key().equals(InputConstants.UNKNOWN);
            }
            case FEEDBACK, TABS -> options.forEach(option -> option.control().setValue(option.read().getAsBoolean()));
        }
        boolean undo = beforeReset != null;
        reset.setMessage(label(undo ? "reset.undo" : "reset"));
        reset.setLeadingIcon(undo ? GraphicIcon.BACK : GraphicIcon.RESET);
        reset.setPreview(label(undo ? "reset.undo.tooltip" : "reset.tooltip"));
        reset.active = !capturingShortcut && (undo || !draft.equals(defaults));
        apply.active = !capturingShortcut && !draft.equals(saved);
    }

    private static Component onOff(boolean selected) {
        return Component.translatable(selected ? "options.on" : "options.off");
    }

    private void apply() {
        if (!draft.settings().equals(saved.settings()) && !save.test(draft.settings())) {
            status = label("save_failed");
            return;
        }
        if (!draft.shortcut().equals(saved.shortcut())) {
            draft.shortcut().assign(keyMapping);
            minecraft.options.save();
        }
        saved = draft;
        beforeReset = null;
        status = label("saved");
        refreshActions();
    }

    private void finishShortcut(Shortcut binding) {
        capturingShortcut = false;
        modifierKey = null;
        edit(new Draft(draft.settings(), binding));
    }

    private static String modifierName(boolean control, boolean shift, boolean alt) {
        if (control) return "CONTROL";
        if (shift) return "SHIFT";
        return alt ? "ALT" : "NONE";
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (entrance.closing()) {
            reopenFromKey(key, scanCode, modifiers);
            return true;
        }
        if (entrance.active() && !capturingShortcut && key != GLFW.GLFW_KEY_ESCAPE
                && matchesPanelKey(key, scanCode, modifiers)) return true;
        if (!entrance.allowsActivation(key, getFocused())) return true;
        if (capturingShortcut) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                finishShortcut(draft.shortcut());
            } else {
                var input = InputConstants.getKey(key, scanCode);
                switch (key) {
                    case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL, GLFW.GLFW_KEY_LEFT_SHIFT,
                         GLFW.GLFW_KEY_RIGHT_SHIFT, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> modifierKey = input;
                    default -> finishShortcut(new Shortcut(input, modifierName((modifiers & GLFW.GLFW_MOD_CONTROL) != 0,
                            (modifiers & GLFW.GLFW_MOD_SHIFT) != 0, (modifiers & GLFW.GLFW_MOD_ALT) != 0)));
                }
            }
            return true;
        }
        if (pageHandoff.active() && rows.containsKey(getFocused()) && (key == GLFW.GLFW_KEY_ENTER
                || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE)) return true;
        if (fits && key == GLFW.GLFW_KEY_ESCAPE && (navigation.keyPressed(key, scanCode, modifiers)
                || sections.visible && sections.keyPressed(key, scanCode, modifiers))) {
            setDragging(false);
            return true;
        }
        if (fits && maxScroll() > 0 && (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN)) {
            scrollTo(scroll + (key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1) * (bodyBottom - bodyTop - GAP));
            return true;
        }
        if (key != GLFW.GLFW_KEY_ESCAPE) revealFocus(getFocused());
        boolean handled = super.keyPressed(key, scanCode, modifiers);
        applyRequestedSection();
        return handled;
    }

    /** 模态确认也在收起时，允许平台把明确的重开意图交给主控面板。 */
    public boolean reopenFromKey(int key, int scanCode, int modifiers) {
        return entrance.closing() && matchesPanelKey(key, scanCode, modifiers) && entrance.reopen();
    }

    private boolean matchesPanelKey(int key, int scanCode, int modifiers) {
        return saved.shortcut().matches(InputConstants.getKey(key, scanCode),
                modifierName((modifiers & GLFW.GLFW_MOD_CONTROL) != 0, (modifiers & GLFW.GLFW_MOD_SHIFT) != 0,
                        (modifiers & GLFW.GLFW_MOD_ALT) != 0));
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (capturingShortcut && modifierKey != null && modifierKey.equals(InputConstants.getKey(key, scanCode))) {
            finishShortcut(new Shortcut(modifierKey, "NONE"));
            return true;
        }
        return super.keyReleased(key, scanCode, modifiers);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (bodyBottom - bodyTop));
    }

    private void scrollTo(int offset) {
        scroll = Math.clamp(offset, 0, maxScroll());
        if (selectedArea == Area.CONFIGURATION) sectionScroll[selectedSection.ordinal()] = scroll;
        else areaScroll[selectedArea.ordinal()] = scroll;
        rows.forEach((widget, y) -> widget.setY(bodyTop + y - scroll));
    }

    private void applyRequestedSection() {
        Area nextArea = requestedArea;
        requestedArea = null;
        if (nextArea != null && nextArea != selectedArea) {
            boolean continuing = capturePage();
            selectedArea = nextArea;
            requestedSection = null;
            setDragging(false);
            clearPage();
            buildPage();
            startPageMorph(navigation.buttons().get(nextArea.ordinal()), PageTransition.nowMs(), continuing);
            if (getFocused() != navigation) setFocused(navigation);
            return;
        }
        Section next = requestedSection;
        requestedSection = null;
        if (selectedArea != Area.CONFIGURATION || next == null || next == selectedSection) return;
        boolean continuing = capturePage();
        selectedSection = next;
        setDragging(false);
        clearPage();
        buildPage();
        var tab = sections.buttons().get(next.ordinal());
        startPageMorph(tab, PageTransition.nowMs(), continuing);
        if (getFocused() != sections) setFocused(sections);
    }

    private boolean capturePage() {
        boolean continuing = pageHandoff.active();
        if (fits && minecraft != null && minecraft.screen == this) {
            int captureTop = top + HEADER_HEIGHT;
            pageHandoff.capture(new Bounds(left + PADDING - 3, captureTop, panelWidth - 2 * PADDING + 6,
                    bodyBottom - captureTop), preferences);
        }
        return continuing;
    }

    private void startPageMorph(UiButton tab, long now, boolean continuing) {
        float center = tab.getX() + tab.getWidth() * 0.5f;
        pageMorph.sample(now, preferences);
        if (preferences.reducedMotion()) {
            pageOrigin.snapTo(center);
        } else if (!pageMorph.running() && !continuing) {
            pageOrigin.snapTo(center);
            pageMorph.snapTo(0f);
            pageMorph.setOpen(true, now);
        } else {
            pageOrigin.animateTo(center, now);
        }
    }

    @Override
    public void setFocused(GuiEventListener listener) {
        super.setFocused(listener);
        revealFocus(listener);
    }

    private void revealFocus(GuiEventListener listener) {
        if (!fits || !(listener instanceof AbstractWidget widget)) return;
        Integer offset = rows.get(widget);
        if (offset == null) return;
        if (widget.getHeight() > bodyBottom - bodyTop - 6) {
            // 长信息组不能完整塞进视口；仍有可见内容时保留阅读位置。
            if (widget.getY() >= bodyBottom || widget.getY() + widget.getHeight() <= bodyTop) scrollTo(offset - 3);
            return;
        }
        if (widget.getY() < bodyTop + 3) scrollTo(offset - 3);
        else if (widget.getY() + widget.getHeight() > bodyBottom - 3) {
            scrollTo(offset + widget.getHeight() - (bodyBottom - bodyTop) + 3);
        }
    }

    private boolean inBody(double x, double y) {
        return fits && x >= left + PADDING - 3 && x < left + panelWidth - PADDING + 3
                && y >= bodyTop && y < bodyBottom;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (entrance.closing()) {
            reopenFromMouse(button);
            return true;
        }
        if (fits && !entrance.allowsPointer(x, y)) return false;
        background.pulse(new Bounds(0, 0, width, height), x, y);
        if (capturingShortcut) {
            finishShortcut(new Shortcut(InputConstants.Type.MOUSE.getOrCreate(button),
                    modifierName(hasControlDown(), hasShiftDown(), hasAltDown())));
            return true;
        }
        boolean inBody = inBody(x, y);
        if (button == 0 && inBody && !pageHandoff.active() && visiblePage != null && visiblePage.contains(x, y)
                && maxScroll() > 0 && x >= scrollbarX() - 3) {
            int thumbTop = thumbTop();
            scrollGrab = y >= thumbTop && y < thumbTop + thumbHeight() ? (int) y - thumbTop : thumbHeight() / 2;
            dragScrollbar(y);
            return true;
        }
        GuiEventListener clicked = null;
        for (GuiEventListener child : children()) {
            if (rows.containsKey(child) && (pageHandoff.active() || visiblePage == null
                    || !visiblePage.contains(x, y))) continue;
            if (child.mouseClicked(x, y, button)) {
                clicked = child;
                break;
            }
        }
        if (clicked == null) return false;
        setFocused(clicked);
        if (button == 0) setDragging(true);
        applyRequestedSection();
        return true;
    }

    /** 鼠标绑定沿用已保存的按键和修饰键。 */
    public boolean reopenFromMouse(int button) {
        if (!entrance.closing()) return false;
        Shortcut binding = saved.shortcut();
        var input = InputConstants.Type.MOUSE.getOrCreate(button);
        return (binding.matches(input, "NONE") || binding.key().equals(input)
                && binding.matches(input, modifierName(hasControlDown(), hasShiftDown(), hasAltDown())))
                && entrance.reopen();
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && scrollGrab >= 0) {
            dragScrollbar(y);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && scrollGrab >= 0) {
            scrollGrab = -1;
            return true;
        }
        if (button == 0 && isDragging() && getFocused() instanceof UiScrollableSectionBar bar) {
            setDragging(false);
            boolean handled = bar.mouseReleased(x, y, button);
            applyRequestedSection();
            return handled;
        }
        if (button == 0 && isDragging() && getFocused() instanceof UiSlider slider) {
            setDragging(false);
            return slider.mouseReleased(x, y, button);
        }
        boolean handled = super.mouseReleased(x, y, button);
        applyRequestedSection();
        return handled;
    }

    // Forge 与 NeoForge 分别使用单轴和双轴滚轮签名。
    public boolean mouseScrolled(double x, double y, double delta) {
        if (scrollTabs(x, y, 0, delta)) return true;
        return scrollBody(x, y, delta);
    }

    private boolean scrollBody(double x, double y, double delta) {
        if (!entrance.allowsPointer(x, y) || pageHandoff.active() || !inBody(x, y)
                || maxScroll() <= 0 || delta == 0 || isDragging() || scrollGrab >= 0) {
            return false;
        }
        scrollTo(scroll - (int) Math.round(delta * 26));
        return true;
    }

    public boolean mouseScrolled(double x, double y, double deltaX, double deltaY) {
        if (scrollTabs(x, y, deltaX, deltaY)) return true;
        return scrollBody(x, y, deltaY);
    }

    private boolean scrollTabs(double x, double y, double deltaX, double deltaY) {
        return fits && entrance.allowsPointer(x, y) && !capturingShortcut
                && (navigation.mouseScrolled(x, y, deltaX, deltaY)
                    || sections.visible && sections.mouseScrolled(x, y, deltaX, deltaY));
    }

    private int scrollbarX() {
        return left + PADDING + contentWidth + GAP;
    }

    private int thumbHeight() {
        int visible = bodyBottom - bodyTop;
        return Math.max(12, visible * visible / contentHeight);
    }

    private int thumbTravel() {
        return bodyBottom - bodyTop - thumbHeight();
    }

    private int thumbTop() {
        return bodyTop + Math.round((float) scroll * thumbTravel() / maxScroll());
    }

    private void dragScrollbar(double y) {
        if (thumbTravel() > 0) scrollTo((int) Math.round((y - bodyTop - scrollGrab) * maxScroll() / thumbTravel()));
    }

    @Override
    public void onClose() {
        if (entrance.closing()) return;
        if (draft.equals(saved)) {
            closeToParent();
            return;
        }
        if (overlays != null && overlays.isModalOpen()) return;
        overlays = new UiOverlayHost(this, preferences);
        var prompt = new UiConfirmation.Prompt(label("discard.title"), label("discard.details"),
                label("discard.action"), CommonComponents.GUI_CANCEL);
        if (back == null) overlays.showDialog(prompt, this::closeToParent);
        else overlays.showDialogFrom(back, prompt, this::closeToParent);
    }

    private void closeToParent() {
        setDragging(false);
        scrollGrab = -1;
        if (sections != null) sections.cancelDrag();
        if (navigation != null) navigation.cancelDrag();
        entrance.close(preferences, () -> minecraft.setScreen(parent));
        if (!fits) entrance.finish();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        pageHandoff.clear();
        stopBackgroundMusic();
        background.clearPulse();
        super.removed();
    }

    @Override
    public void prepareReturnLayer() {
        restoreDeveloperFocus = true;
        if (fits && developer != null) setFocused(developer);
    }

    // NeoForge 由 Screen.render 调用，Forge 1.20.1 使用不同签名。
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!fits) {
            renderDotBackground(graphics, true);
            var lines = font.split(Component.translatable("sh_better_ui.picker.too_small"), Math.max(1, width - 8));
            int y = 6;
            for (var line : lines) {
                if (y + font.lineHeight > height - 27) break;
                graphics.drawCenteredString(font, line, width / 2, y, LiquidGlassTheme.TEXT);
                y += font.lineHeight;
            }
        } else {
            var panel = new Bounds(left, top, panelWidth, panelHeight);
            if (entrance.render(graphics, panel, preferences,
                    progress -> renderTransitionBackground(graphics, partialTick, progress),
                    () -> renderContent(graphics, -1, -1, partialTick))) return;
            renderDotBackground(graphics, true);
            LiquidGlassTheme.popover(graphics, left, top, panelWidth, panelHeight, preferences);
            renderContent(graphics, mouseX, mouseY, partialTick);
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTransitionBackground(GuiGraphics graphics, float partialTick, float progress) {
        returning.prepare(parent, width, height);
        returning.renderBackdrop(graphics, width, height, partialTick);
        if (progress > 0f) {
            GuiRenderUtil.withAttentionContent(graphics, GuiRenderUtil.easeOut(progress), 0f,
                    () -> renderDotBackground(graphics, false));
        }
    }

    private void renderDotBackground(GuiGraphics graphics, boolean allowPointer) {
        long now = PageTransition.nowMs();
        var overlay = UiOverlayHost.current(this);
        boolean interactive = minecraft != null && minecraft.screen == this && minecraft.isWindowActive()
                && (overlay == null || !overlay.isModalOpen());
        if (!interactive) background.clearPulse();
        backgroundBeat = backgroundAudio == null ? 0f
                : musicMotion.sample(backgroundAudio.sample(), now, !interactive || !musicMotionAllowed());
        refreshMusicToggle();
        var bounds = new Bounds(0, 0, width, height);
        var mouse = interactive && allowPointer && !preferences.highContrast()
                ? ShBetterUiApi.interactions().mouseState() : null;
        UiPointerTracker.Frame pointer = null;
        if (mouse != null && mouse.focused() && !mouse.grabbed()) {
            pointer = backgroundPointer.update(bounds, graphics.pose().last().pose(), mouse, true, now);
        } else {
            backgroundPointer.reset();
        }
        background.render(graphics, bounds, pointer, now, preferences);
    }

    private float backgroundBrightness(float x, float y, float phase, float variation) {
        float ambient = BACKGROUND_MOTION.effect().brightness(x, y, phase, variation);
        if (backgroundBeat == 0f) return ambient;
        return ambient + backgroundBeat * (0.28f + DotMatrixProfile.Effect.WAVE.brightness(x, y, phase, variation));
    }

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = left + PADDING;
        long now = PageTransition.nowMs();
        if (selectedArea == Area.OVERVIEW && now >= nextOverviewRefresh) refreshOverview(now);
        graphics.drawString(font, title, x, top + 15, LiquidGlassTheme.TEXT, false);
        renderBody(graphics, x, mouseX, mouseY, partialTick);
        pageHandoff.render(graphics, preferences);
        Component message = status != null ? status : !draft.equals(saved) ? label("unsaved") : null;
        if (message != null) graphics.drawString(font, message, x, bodyBottom + 3, LiquidGlassTheme.TEXT_MUTED, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderBody(GuiGraphics graphics, int x, int mouseX, int mouseY, float partialTick) {
        long now = PageTransition.nowMs();
        float progress = GuiMotion.clamp01(pageMorph.sample(now, preferences));
        var viewport = new Bounds(x - 3, bodyTop, panelWidth - 2 * PADDING + 6, bodyBottom - bodyTop);
        int surface = preferences.highContrast() ? DarkModeTheme.BACKGROUND : DarkModeTheme.SURFACE;
        var panel = new GuiRect(viewport.x(), viewport.y(), viewport.width(), viewport.height());
        float center = pageOrigin.sample(now, preferences);
        var capsule = FluidMorph.seed(center, bodyTop + 5f);
        var frame = FluidMorph.between(capsule, panel, BODY_RADIUS, progress);
        var rect = frame.rect();
        int edge = preferences.highContrast() ? LiquidGlassTheme.TEXT
                : GuiMotion.lerpColor(0xA0000000 | (LiquidGlassTheme.ACCENT & 0xFFFFFF), DarkModeTheme.BORDER, progress);

        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        try {
            if (progress < 1f) {
                RoundedCorners.draw(graphics, viewport.x(), viewport.y(), viewport.width(), viewport.height(),
                        BODY_RADIUS, 0f, 0, surface);
                float opacity = GuiRenderUtil.easeOut((progress - 0.54f) / 0.44f);
                renderSkeleton(graphics, viewport, x, now, (1f - opacity) * 0.42f);
            }
            RoundedCorners.draw(graphics, rect.getX(), rect.getY(), rect.getW(), rect.getH(),
                    frame.radius(), 1f, edge, surface);
            renderMorphContents(graphics, viewport, frame, x, mouseX, mouseY, partialTick, now, progress, surface);
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderMorphContents(GuiGraphics graphics, Bounds viewport, FluidMorph frame, int x, int mouseX,
                                     int mouseY, float partialTick, long now, float progress, int surface) {
        var rect = frame.rect();
        float inset = frame.radius() * 0.4f * (1f - progress);
        int left = Math.max(viewport.x(), Math.round(rect.getX() + inset));
        int top = Math.max(viewport.y(), Math.round(rect.getY() + inset));
        int right = Math.min(viewport.right(), Math.round(rect.getX() + rect.getW() - inset));
        int bottom = Math.min(viewport.bottom(), Math.round(rect.getY() + rect.getH() - inset));
        var clip = new Bounds(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
        float opacity = GuiRenderUtil.easeOut((progress - 0.54f) / 0.44f);
        visiblePage = opacity >= 0.9f ? clip : null;
        if (clip.width() == 0 || clip.height() == 0) return;
        graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        try {
            if (opacity > 0f) {
                boolean hover = !pageHandoff.active() && visiblePage != null && visiblePage.contains(mouseX, mouseY);
                renderPage(graphics, x, hover ? mouseX : -1, hover ? mouseY : -1, partialTick);
                if (maxScroll() > 0) {
                    int trackX = scrollbarX();
                    int thumbY = thumbTop();
                    graphics.fill(trackX + 1, bodyTop, trackX + SCROLLBAR_WIDTH - 1, bodyBottom, 0x544A5565);
                    graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight(),
                            LiquidGlassTheme.TEXT_MUTED);
                }
                if (opacity < 1f) {
                    graphics.fill(clip.x(), clip.y(), clip.right(), clip.bottom(),
                            GuiMotion.lerpColor(surface, surface & 0xFFFFFF, opacity));
                }
            }
            if (opacity < 1f) renderSkeleton(graphics, clip, x, now, 1f - opacity);
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderSkeleton(GuiGraphics graphics, Bounds viewport, int x, long now, float opacity) {
        var blocks = new ArrayList<UiSkeleton.Block>();
        blocks.add(new UiSkeleton.Block(new Bounds(x, bodyTop - scroll + 5, 48, 5)));
        for (AbstractWidget widget : rows.keySet()) {
            blocks.add(new UiSkeleton.Block(new Bounds(widget.getX() + 2, widget.getY() + 3,
                    widget.getWidth() - 4, widget.getHeight() - 6), 8f));
        }
        UiSkeleton.draw(graphics, viewport, blocks, now, preferences, opacity);
    }

    private void renderPage(GuiGraphics graphics, int x, int mouseX, int mouseY, float partialTick) {
        int origin = bodyTop - scroll;
        if (selectedArea != Area.OVERVIEW) {
            graphics.drawString(font, selectedArea == Area.CONFIGURATION ? selectedSection.tab.label() : selectedArea.tab.label(),
                    x, origin + 4, LiquidGlassTheme.ACCENT, false);
        }
        if (selectedArea == Area.CONFIGURATION && selectedSection == Section.GLASS) renderPreview(graphics, x, origin);
        if (selectedArea == Area.DEVELOPER) {
            graphics.drawString(font, label("developer_config.tools"), x, origin + 78, LiquidGlassTheme.TEXT_MUTED, false);
        }
        for (AbstractWidget widget : rows.keySet()) {
            int y = widget.getY();
            if (y + widget.getHeight() > bodyTop && y < bodyBottom) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private void renderPreview(GuiGraphics graphics, int x, int origin) {
        int originalX = sidePreview ? x + glassWidth + 2 * GAP : x;
        int sampleWidth = sidePreview ? x + contentWidth - originalX : (glassWidth - GAP) / 2;
        int previewX = sidePreview ? originalX : x + sampleWidth + GAP;
        int originalY = origin + (sidePreview ? 17 : 158);
        int previewY = sidePreview ? origin + 75 : originalY;
        int sampleHeight = sidePreview ? 36 : 28;
        drawSample(graphics, originalX, originalY, sampleWidth, sampleHeight);
        drawSample(graphics, previewX, previewY, sampleWidth, sampleHeight);
        var backdrop = draft.settings().backdrop();
        boolean sampled = GlassBlur.tryBackdrop(graphics, new GuiRect(previewX, previewY, sampleWidth, sampleHeight),
                1f, GlassStyle.LIQUID_REGULAR, 9f, 6f, backdrop);
        Component previewLabel = sampled ? label("preview")
                : label(backdrop.enabled() && backdrop.strength() > 0.01f ? "preview.unavailable" : "preview.off");
        graphics.drawString(font, label("original"), originalX, originalY - 12, LiquidGlassTheme.TEXT_MUTED, false);
        graphics.drawString(font, previewLabel, previewX, previewY - 12, LiquidGlassTheme.TEXT_MUTED, false);
    }

    private static void drawSample(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fillGradient(x, y, x + width, y + height, 0xFF2B5287, 0xFF15243C);
        for (int stripe = x + 2; stripe < x + width - 1; stripe += 6) {
            graphics.fill(stripe, y, stripe + 1, y + height, 0x805B93B1);
        }
        int segment = width / 4;
        RoundedCorners.draw(graphics, x + segment / 2, y + 5, segment, height - 10,
                4, 0, 0, 0xFF79C5EA);
        RoundedCorners.draw(graphics, x + segment * 2, y + 7, segment, height - 14,
                4, 0, 0, 0xFFDB9A72);
    }

    private static Component label(String key, Object... arguments) {
        return Component.translatable("sh_better_ui.panel." + key, arguments);
    }

    private enum Section {
        GLASS("glass", GraphicIcon.SETTINGS),
        DISPLAY("display", GraphicIcon.MONITOR),
        FEEDBACK("feedback", GraphicIcon.ENABLE),
        TABS("tabs", GraphicIcon.MONITOR),
        SHORTCUT("shortcut", GraphicIcon.KEYBOARD);

        private final UiSectionBar.Section tab;

        Section(String key, GraphicIcon icon) {
            tab = new UiSectionBar.Section(label("section." + key), icon);
        }
    }

    private enum Area {
        CONFIGURATION("configuration", GraphicIcon.SETTINGS),
        DEVELOPER("developer_config", GraphicIcon.CODE),
        OVERVIEW("overview", GraphicIcon.INFO);

        private final UiSectionBar.Section tab;

        Area(String key, GraphicIcon icon) {
            tab = new UiSectionBar.Section(label("section." + key), icon);
        }
    }

    private record Draft(ClientUiSettings settings, Shortcut shortcut) {}

    private record ToggleOption(UiToggleButton control, BooleanSupplier read) {}
    private record SliderOption(UiSlider control, IntSupplier read) {}

    private record Shortcut(InputConstants.Key key, String modifier) {
        boolean matches(InputConstants.Key input, String activeModifier) {
            return !key.equals(InputConstants.UNKNOWN) && key.equals(input)
                    && ("NONE".equals(modifier) || modifier.equals(activeModifier));
        }

        Component displayName(KeyMapping mapping) {
            var nativeModifier = Enum.valueOf(mapping.getDefaultKeyModifier().getDeclaringClass(), modifier);
            return nativeModifier.getCombinedName(key, key::getDisplayName);
        }

        void assign(KeyMapping mapping) {
            var nativeModifier = Enum.valueOf(mapping.getDefaultKeyModifier().getDeclaringClass(), modifier);
            mapping.setKeyModifierAndCode(nativeModifier, key);
            KeyMapping.resetMapping();
        }
    }

    private enum Scale {
        RADIUS("radius", 200), STRENGTH("strength", 100), TINT("tint", 200), GRAIN("grain", 200),
        SATURATION("saturation", 200), LIFT("lift", 15), RIM("rim", 200), REFRACTION("refraction", 200);

        private final String key;
        private final int maxPercent;

        Scale(String key, int maxPercent) {
            this.key = key;
            this.maxPercent = maxPercent;
        }

        private int percent(GlassBackdropSettings settings) {
            return Math.round(read(settings) * 100);
        }

        private float read(GlassBackdropSettings settings) {
            return switch (this) {
                case RADIUS -> settings.blurRadiusScale();
                case STRENGTH -> settings.strength();
                case TINT -> settings.tintScale();
                case GRAIN -> settings.grainScale();
                case SATURATION -> settings.saturationScale();
                case LIFT -> settings.lift();
                case RIM -> settings.rimScale();
                case REFRACTION -> settings.refractionScale();
            };
        }

        private GlassBackdropSettings write(GlassBackdropSettings settings, float value) {
            return new GlassBackdropSettings(settings.enabled(),
                    this == RADIUS ? value : settings.blurRadiusScale(),
                    this == STRENGTH ? value : settings.strength(),
                    this == TINT ? value : settings.tintScale(),
                    this == GRAIN ? value : settings.grainScale(),
                    this == SATURATION ? value : settings.saturationScale(),
                    this == LIFT ? value : settings.lift(),
                    this == RIM ? value : settings.rimScale(),
                    this == REFRACTION ? value : settings.refractionScale(),
                    settings.denseSampling());
        }
    }

}
