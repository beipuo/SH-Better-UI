package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.blur.GaussBlur;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.glass.GlassBlur;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.ButtonFeedback.Status;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.NetworkToggle;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiBottomSheet;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCapsuleReveal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScreenReturn;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiDisclosure;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiDynamicStatus;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLoading;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPageSlide;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiRecipePageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSkeleton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlideConfirm;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTextDetails;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
import com.dishanhai.sh_better_ui.client.gui.search.SearchField;
import com.dishanhai.sh_better_ui.client.gui.search.SearchIndex;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen.label;

/** 山海：可交互预览公共控件与材质，从底部抽屉查看并复制对应的 Java 代码。 */
final class DeveloperGalleryScreen extends Screen {
    private static final int MIN_PANEL_HEIGHT = 218;
    private static final int MAX_PANEL_HEIGHT = 272;
    private static final int MAX_PANEL_WIDTH = 600;
    private static final int PADDING = 12;
    private static final int BUTTON_HEIGHT = 22;
    private static final int NAV_WIDTH = 24;
    private static final int SAMPLE_Y = 28;
    private static final int PREVIEW_Y = 59;
    private static final int PREVIEW_HEIGHT = 46;
    private static final int DETAILS_PREVIEW_EXTRA = 26;
    private static final int SLIDE_PREVIEW_EXTRA = 20;
    private static final int FOOTER_GAP = 5;
    private static final int CODE_PADDING = 4;
    private static final int MIN_DRAWER_HEIGHT = 58;
    private static final int DRAWER_HEADER_ALLOWANCE = 18;
    private static final int STATUS_BUTTON_WIDTH = 86;
    private static final Status[] DEMO_STATUSES = Status.values();
    private static final Component BACK = Component.translatable("gui.back");
    private static final Component TOO_SMALL = Component.translatable("sh_better_ui.picker.too_small");
    private static final UiPageSlide.Effects DEPTH_EFFECTS = new UiPageSlide.Effects(true, true, GlassStyle.LIQUID_REGULAR);

    enum Section {
        CONTROLS("gallery.controls", List.of(DeveloperSamples.BUTTON, DeveloperSamples.TOGGLE, DeveloperSamples.SECTIONS,
                DeveloperSamples.DISCLOSURE, DeveloperSamples.TEXT_DETAILS, DeveloperSamples.SLIDER, DeveloperSamples.FRAME_TIMELINE,
                DeveloperSamples.ELECTRIC_LOGO, DeveloperSamples.PATTERN_WAVES,
                DeveloperSamples.SLIDE_CONFIRM, DeveloperSamples.CAPSULE_TOGGLE, DeveloperSamples.MODE_INDICATORS,
                DeveloperSamples.PAGE_SLIDE,
                DeveloperSamples.RECIPE_TRANSITION, DeveloperSamples.DYNAMIC_STATUS, DeveloperSamples.LOADING_SPINNER,
                DeveloperSamples.LOADING_PROGRESS, DeveloperSamples.LOADING_SKELETON, DeveloperSamples.SEARCH,
                DeveloperSamples.DIALOG, DeveloperSamples.TOAST)),
        MATERIALS("gallery.materials", List.of(DeveloperSamples.DOT_MATRIX,
                DeveloperSamples.LIQUID_BUTTON, DeveloperSamples.DARK_BUTTON,
                DeveloperSamples.GRADIENT_CARD, DeveloperSamples.NETWORK_TAB, DeveloperSamples.CLEAR_GLASS,
                DeveloperSamples.FROSTED_GLASS, DeveloperSamples.RESET_ICON, DeveloperSamples.SETTINGS_ICON,
                DeveloperSamples.COPY_ICON, DeveloperSamples.STATUS_ICON));

        final String titleKey;
        final List<DeveloperSamples> samples;

        Section(String titleKey, List<DeveloperSamples> samples) {
            this.titleKey = titleKey;
            this.samples = samples;
        }
    }

    static {
        GaussBlur.allowScreen(screen -> screen instanceof DeveloperGalleryScreen);
    }

    private final DeveloperPanelScreen parent;
    private final UiPreferences preferences;
    private final List<DeveloperSamples> samples;
    private final UiOverlayHost overlays;
    private final UiBottomSheet codeDrawer;
    private final UiCapsuleReveal entrance = new UiCapsuleReveal();
    private final UiScreenReturn returning = new UiScreenReturn(this);
    private final UiCapsuleReveal sampleReveal = new UiCapsuleReveal();
    private final List<AbstractWidget> previewWidgets = new ArrayList<>();
    private boolean openCodeOnEntry;
    private final DarkModeTheme.Interaction cardMotion = DarkModeTheme.Interaction.card();
    private final UiDisclosure disclosure = new UiDisclosure(false);
    private final UiTextDetails textDetails = new UiTextDetails(label("gallery.sample.text_details"),
            label("gallery.long_text"), 1);
    private final UiPageSlide pageSlide = new UiPageSlide();
    private final UiDynamicStatus liveStatus = new UiDynamicStatus(this::demoStatus);
    private final DotMatrixGalleryPreview dotMatrix = new DotMatrixGalleryPreview();
    private final PatternWavesGalleryPreview patternWaves = new PatternWavesGalleryPreview();
    private final FrameTimelineGalleryPreview frameTimeline = new FrameTimelineGalleryPreview();
    private final ElectricLogoGalleryPreview electricLogo = new ElectricLogoGalleryPreview();
    private UiRecipePageTransition recipeTransition;
    private Bounds preview;
    private Bounds codeViewport;
    private UiBottomSheet.Frame codeFrame;
    private UiTextDetails codeDetails;
    private UiTextDetails.Layout codeLayout;
    private int panelWidth;
    private int left;
    private int top;
    private int sampleIndex;
    private int pendingStep;
    private UiButton previousSample;
    private UiButton nextSample;
    private UiButton codeToggle;
    private UiButton codeDetailsButton;
    private UiButton copyButton;
    private SearchField<String> search;
    private NetworkToggle toggle;
    private UiTextDetails.Layout detailsLayout;
    private UiButton detailsPage;
    private UiToggleButton capsule;
    private UiToggleButton slideMode;
    private UiPageSlide.Effects slideEffects = UiPageSlide.Effects.NONE;
    private boolean slideDepth;
    private boolean alternateSlidePage;
    private int sliderValue = 50;
    private int demoMode;
    private Status status = Status.READY;
    private long statusStartedAt;
    private int loadingPercent = 45;
    private boolean cardLoading = true;
    private boolean capsuleValue;
    private boolean compact;
    private boolean copied;

    DeveloperGalleryScreen(DeveloperPanelScreen parent, UiPreferences preferences, Section section,
                           DeveloperSamples example, UiButton source) {
        super(label(section.titleKey));
        this.parent = parent;
        this.preferences = preferences;
        this.samples = section.samples;
        this.overlays = new UiOverlayHost(this, preferences);
        sampleIndex = example == null ? 0 : samples.indexOf(example);
        codeDrawer = UiBottomSheet.capsule(0, 0f, 1f);
        openCodeOnEntry = example != null;
        entrance.startFrom(source);
    }

    @Override
    protected void init() {
        previewWidgets.clear();
        search = null;
        toggle = null;
        detailsLayout = null;
        detailsPage = null;
        codeFrame = null;
        capsule = null;
        slideMode = null;
        recipeTransition = new UiRecipePageTransition();
        compact = width < 240 || height < MIN_PANEL_HEIGHT + 8;
        if (compact || sample() != DeveloperSamples.ELECTRIC_LOGO) electricLogo.release();
        if (compact) {
            frameTimeline.pause();
            entrance.finish();
            sampleReveal.finish();
            if (width >= 36 && height >= BUTTON_HEIGHT + 8) {
                int buttonWidth = Math.min(100, width - 8);
                addButton((width - buttonWidth) / 2, height - BUTTON_HEIGHT - 4,
                        buttonWidth, BACK, GraphicIcon.BACK, this::onClose);
            }
            return;
        }

        panelWidth = Math.min(width - 16, MAX_PANEL_WIDTH);
        int previewExtra = switch (sample()) {
            case DOT_MATRIX -> DotMatrixGalleryPreview.PREVIEW_EXTRA;
            case PATTERN_WAVES -> PatternWavesGalleryPreview.PREVIEW_EXTRA;
            case FRAME_TIMELINE -> FrameTimelineGalleryPreview.PREVIEW_EXTRA;
            case ELECTRIC_LOGO -> ElectricLogoGalleryPreview.PREVIEW_EXTRA;
            case TEXT_DETAILS -> DETAILS_PREVIEW_EXTRA;
            case PAGE_SLIDE, RECIPE_TRANSITION, LOADING_SPINNER, LOADING_PROGRESS, LOADING_SKELETON -> SLIDE_PREVIEW_EXTRA;
            default -> 0;
        };
        int frameHeight = Math.min(height - 8, MAX_PANEL_HEIGHT);
        int fixedHeight = PREVIEW_Y + PREVIEW_HEIGHT + 2 * FOOTER_GAP + PADDING + BUTTON_HEIGHT;
        previewExtra = Math.min(previewExtra, frameHeight - fixedHeight - MIN_DRAWER_HEIGHT);
        left = (width - panelWidth) / 2;
        top = (height - frameHeight) / 2;
        int contentX = left + PADDING;
        int contentRight = left + panelWidth - PADDING;
        int contentWidth = contentRight - contentX;
        preview = new Bounds(contentX, top + PREVIEW_Y, contentWidth, PREVIEW_HEIGHT + previewExtra);
        int footerY = preview.bottom() + FOOTER_GAP;
        int drawerY = footerY + BUTTON_HEIGHT + PADDING + FOOTER_GAP;
        codeViewport = new Bounds(contentX, drawerY, contentWidth, top + frameHeight - drawerY);

        previousSample = addArrow(contentX, top + SAMPLE_Y, false, "gallery.previous_sample", () -> pendingStep = -1);
        nextSample = addArrow(contentRight - NAV_WIDTH, top + SAMPLE_Y, true, "gallery.next_sample", () -> pendingStep = 1);
        previousSample.active = sampleIndex > 0;
        nextSample.active = sampleIndex + 1 < samples.size();
        addPreviewWidgets();
        addCodeDrawer(contentX, contentRight, footerY);
    }

    private void addCodeDrawer(int x, int right, int footerY) {
        int actionY = codeViewport.bottom() - BUTTON_HEIGHT - CODE_PADDING;
        int actionWidth = (codeViewport.width() - 3 * CODE_PADDING) / 2;
        int excerptHeight = actionY - codeViewport.y() - DRAWER_HEADER_ALLOWANCE - 2 * CODE_PADDING;
        codeDetails = new UiTextDetails(label("gallery.code_title"), Component.literal(sample().code),
                Math.max(1, excerptHeight / font.lineHeight));
        codeLayout = codeDetails.layout(font, codeViewport.width() - 2 * CODE_PADDING);
        codeToggle = addButton(x, footerY, right - x - 74, label("gallery.code_open"),
                GraphicIcon.EXPAND, () -> setCodeDrawerOpen(codeDrawer.targetAnchor() == 0));
        codeDetailsButton = addButton(x + CODE_PADDING, actionY, actionWidth, label("gallery.code_details"),
                GraphicIcon.NEXT, () -> minecraft.setScreen(codeDetails.pageFrom(
                        this, preferences, codeDetailsButton, () -> codeDetailsButton)));
        Component copyLabel = label("gallery.copy");
        copyButton = addButton(right - CODE_PADDING - actionWidth, actionY, actionWidth,
                copied ? label("gallery.copied") : copyLabel, GraphicIcon.COPY, this::copyCode);
        copyButton.setPreview(copyLabel);
        addButton(right - 68, footerY, 68, BACK, GraphicIcon.BACK, this::onClose);
        updateCodeDrawerControls(codeDrawer.sample(codeViewport, PageTransition.nowMs(), preferences));
    }

    private void setCodeDrawerOpen(boolean open) {
        openCodeOnEntry = false;
        codeDrawer.animateTo(open ? 1 : 0, PageTransition.nowMs());
        updateCodeDrawerControls(codeDrawer.sample(codeViewport, PageTransition.nowMs(), preferences));
        codeFrame = null;
    }

    private void updateCodeDrawerControls(UiBottomSheet.Frame frame) {
        boolean open = codeDrawer.targetAnchor() != 0;
        if (open != codeToggle.isSelected()) {
            codeToggle.setSelected(open);
            codeToggle.setMessage(label(open ? "gallery.code_close" : "gallery.code_open"));
            codeToggle.setLeadingIcon(open ? GraphicIcon.COLLAPSE : GraphicIcon.EXPAND);
        }
        boolean showActions = open && frame.panel().width() == frame.viewport().width()
                && frame.content().y() <= codeDetailsButton.getY();
        codeDetailsButton.visible = showActions;
        copyButton.visible = showActions;
        if (!showActions && (getFocused() == codeDetailsButton || getFocused() == copyButton)) setFocused(codeToggle);
    }

    private UiButton addButton(int x, int y, int buttonWidth, Component text, GraphicIcon icon, Runnable action) {
        return addRenderableWidget(DeveloperPanelScreen.button(x, y, buttonWidth, BUTTON_HEIGHT,
                text, icon, preferences, action));
    }

    private UiButton addArrow(int x, int y, boolean forward, String key, Runnable action) {
        var button = addButton(x, y, NAV_WIDTH, label(key), null, action);
        button.setIcon(forward ? GraphicIcon.NEXT : GraphicIcon.PREVIOUS);
        return button;
    }

    private <T extends AbstractWidget> T addPreviewWidget(T widget) {
        previewWidgets.add(widget);
        return addWidget(widget);
    }

    private UiButton addPreviewButton(int x, int y, int width, Component text, GraphicIcon icon, Runnable action) {
        return addPreviewWidget(DeveloperPanelScreen.button(x, y, width, BUTTON_HEIGHT, text, icon, preferences, action));
    }

    private void addPreviewWidgets() {
        DeveloperSamples sample = sample();
        int w = Math.min(164, preview.width() - 16);
        int x = preview.x() + (preview.width() - w) / 2;
        int y = preview.y() + (preview.height() - BUTTON_HEIGHT) / 2;
        if (sample.buttonStyle != null) {
            var button = new UiButton(x, y, w, BUTTON_HEIGHT, Component.translatable("gui.done"),
                    pressed -> {
                        var control = (UiButton) pressed;
                        control.setSelected(!control.isSelected());
                    }, preferences, false, sample.buttonStyle);
            button.setLeadingIcon(GraphicIcon.RESET);
            addPreviewWidget(button);
            return;
        }
        switch (sample) {
            case FRAME_TIMELINE -> frameTimeline.widgets(preview, preferences).forEach(this::addPreviewWidget);
            case ELECTRIC_LOGO -> electricLogo.widgets(preview, preferences).forEach(this::addPreviewWidget);
            case PATTERN_WAVES -> patternWaves.widgets(preview, preferences,
                            () -> !entrance.active() && !sampleReveal.active() && !overlays.isModalOpen())
                    .forEach(this::addPreviewWidget);
            case DOT_MATRIX -> {
                int settingsWidth = DotMatrixGalleryPreview.SETTINGS_WIDTH;
                var settings = new Bounds(preview.right() - settingsWidth, top + 5, settingsWidth, BUTTON_HEIGHT);
                dotMatrix.widgets(preview, settings, preferences).forEach(this::addPreviewWidget);
            }
            case TOGGLE -> {
                toggle = new NetworkToggle(new Bounds(x, y, w, BUTTON_HEIGHT), label("gallery.try_toggle"),
                        false, preferences, value -> toggle.setValue(value));
                addPreviewWidget(toggle);
            }
            case SECTIONS -> {
                var sections = new UiSectionBar(x, y, w, BUTTON_HEIGHT, preferences, List.of(
                        new UiSectionBar.Section(Component.translatable("options.title"), GraphicIcon.SETTINGS),
                        new UiSectionBar.Section(Component.translatable("gui.stats"), GraphicIcon.MONITOR)),
                        0, ignored -> {});
                sections.buttons().forEach(this::addPreviewWidget);
            }
            case DISCLOSURE -> addPreviewWidget(disclosure.button(x, preview.y() + 4, w, BUTTON_HEIGHT,
                    label("gallery.expand"), label("gallery.collapse"), preferences, expanded -> {}));
            case TEXT_DETAILS -> addTextDetails();
            case SLIDER -> addPreviewWidget(new UiSlider(new Bounds(x, y, w, BUTTON_HEIGHT), 0, 100, 5,
                    sliderValue, preferences, value -> label("gallery.slider_value", value), value -> sliderValue = value));
            case SLIDE_CONFIRM -> addSlideConfirm();
            case CAPSULE_TOGGLE -> addCapsuleToggle(new Bounds(x, y, w, BUTTON_HEIGHT));
            case MODE_INDICATORS -> addModeDemo(x, y, w);
            case PAGE_SLIDE, RECIPE_TRANSITION -> addSlideControls();
            case DYNAMIC_STATUS -> addPreviewButton(preview.right() - STATUS_BUTTON_WIDTH - 6, y,
                    STATUS_BUTTON_WIDTH, label("gallery.status_next"), null, this::advanceStatus);
            case LOADING_SPINNER, LOADING_PROGRESS -> addLoadingSlider(sample);
            case LOADING_SKELETON -> addPreviewButton(x, preview.bottom() - BUTTON_HEIGHT - 5, w,
                    label("gallery.loading_toggle_card"), null, () -> cardLoading = !cardLoading);
            case SEARCH -> addSearch(x, y, w);
            case DIALOG -> addPreviewWidget(DeveloperPanelScreen.button(x, y, w, BUTTON_HEIGHT,
                    label("demo.dialog"), GraphicIcon.WARNING, preferences,
                    pressed -> DeveloperPanelScreen.showDialog(overlays, (UiButton) pressed,
                            () -> DeveloperPanelScreen.showToast(preferences))));
            case TOAST -> addPreviewButton(x, y, w, label("demo.toast"), GraphicIcon.INFO,
                    () -> DeveloperPanelScreen.showToast(preferences));
            default -> { }
        }
    }

    private void addTextDetails() {
        int contentWidth = Math.min(264, preview.width() - 16);
        int x = preview.x() + (preview.width() - contentWidth) / 2;
        int y = preview.bottom() - BUTTON_HEIGHT - 5;
        int buttonWidth = (contentWidth - 6) / 2;
        detailsLayout = textDetails.layout(font, contentWidth);
        textDetails.inlineButton(detailsLayout, x, y, buttonWidth, BUTTON_HEIGHT,
                preferences, expanded -> detailsLayout = textDetails.layout(font, contentWidth))
                .ifPresent(this::addPreviewWidget);
        textDetails.pageButton(detailsLayout, this, x + buttonWidth + 6, y,
                contentWidth - buttonWidth - 6, BUTTON_HEIGHT, preferences, () -> detailsPage)
                .ifPresent(button -> detailsPage = addPreviewWidget(button));
    }

    private void addSlideConfirm() {
        int resetWidth = 60;
        int gap = 6;
        int slideWidth = Math.min(164, preview.width() - 16 - resetWidth - gap);
        int x = preview.x() + (preview.width() - slideWidth - gap - resetWidth) / 2;
        int y = preview.bottom() - BUTTON_HEIGHT - 5;
        var slide = addPreviewWidget(new UiSlideConfirm(this, new Bounds(x, y, slideWidth, BUTTON_HEIGHT),
                preferences, () -> {}));
        addPreviewButton(x + slideWidth + gap, y, resetWidth, Component.translatable("controls.reset"),
                GraphicIcon.RESET, slide::reset);
    }

    private void addCapsuleToggle(Bounds bounds) {
        capsule = new UiToggleButton(bounds, capsuleValue, preferences, UiStyle.LIQUID_GLASS,
                value -> Component.translatable(value ? "options.on" : "options.off"),
                value -> {
                    capsuleValue = value;
                    capsule.setValue(value);
                });
        capsule.setIcons(GraphicIcon.ENABLE, GraphicIcon.DISABLE);
        capsule.setModeIndicatorsVisible(true);
        addPreviewWidget(capsule.button());
    }

    private void addModeDemo(int x, int y, int width) {
        int modeCount = 3;
        var button = new UiButton(x, y, width, BUTTON_HEIGHT, label("gallery.mode", demoMode + 1), pressed -> {
            demoMode = (demoMode + 1) % modeCount;
            var control = (UiButton) pressed;
            control.setMessage(label("gallery.mode", demoMode + 1));
            control.setModeIndicators(modeCount, demoMode);
        }, preferences, false, UiStyle.LIQUID_GLASS);
        button.setModeIndicators(modeCount, demoMode);
        button.setPreview(label("gallery.mode_tip"));
        addPreviewWidget(button);
    }

    private void addSlideControls() {
        int x = preview.x() + 6;
        int y = preview.bottom() - BUTTON_HEIGHT - 5;
        int step = NAV_WIDTH + 3;
        addSlideArrow(x, y, UiPageSlide.Direction.LEFT, GraphicIcon.PREVIOUS, "gallery.slide_left");
        addSlideArrow(x + step, y, UiPageSlide.Direction.UP, GraphicIcon.COLLAPSE, "gallery.slide_up");
        addSlideArrow(x + step * 2, y, UiPageSlide.Direction.DOWN, GraphicIcon.EXPAND, "gallery.slide_down");
        addSlideArrow(x + step * 3, y, UiPageSlide.Direction.RIGHT, GraphicIcon.NEXT, "gallery.slide_right");
        if (sample() == DeveloperSamples.RECIPE_TRANSITION) return;
        int modeX = x + step * 4 + 3;
        slideMode = new UiToggleButton(new Bounds(modeX, y, preview.right() - modeX - 6, BUTTON_HEIGHT),
                slideDepth, preferences, UiStyle.LIQUID_GLASS,
                depth -> label(depth ? "gallery.slide_depth" : "gallery.slide_flat"), depth -> {
                    slideDepth = depth;
                    slideMode.setValue(depth);
                });
        slideMode.button().setPreview(label("gallery.slide_mode_tip"));
        addPreviewWidget(slideMode.button());
    }

    private void addSlideArrow(int x, int y, UiPageSlide.Direction direction, GraphicIcon icon, String key) {
        var button = addPreviewButton(x, y, NAV_WIDTH, label(key), null, () -> {
            alternateSlidePage = !alternateSlidePage;
            if (sample() == DeveloperSamples.RECIPE_TRANSITION) {
                recipeTransition.start(direction, recipeSlideViewport(), PageTransition.nowMs(), preferences);
                return;
            }
            Bounds viewport = slideViewport();
            int distance = switch (direction) {
                case LEFT, RIGHT -> viewport.width();
                case UP, DOWN -> viewport.height();
            };
            slideEffects = slideDepth ? DEPTH_EFFECTS : UiPageSlide.Effects.NONE;
            pageSlide.start(direction, slideDepth ? UiPageSlide.Motion.DEPTH : UiPageSlide.Motion.FLAT,
                    distance, PageTransition.nowMs(), preferences);
        });
        button.setIcon(icon);
    }

    private Bounds slideViewport() {
        return new Bounds(preview.x() + 6, preview.y() + 5, preview.width() - 12,
                preview.height() - BUTTON_HEIGHT - 15);
    }

    private Bounds recipeSlideViewport() {
        var viewport = slideViewport();
        return new Bounds(viewport.x(), viewport.y(), viewport.width() * 3 / 4 - 6, viewport.height());
    }

    private UiDynamicStatus.Snapshot demoStatus() {
        if (status != Status.PENDING) return new UiDynamicStatus.Snapshot(status);
        int progress = (int) Math.clamp((PageTransition.nowMs() - statusStartedAt) / 40L, 0L, 100L);
        return progress < 100 ? new UiDynamicStatus.Snapshot(status, progress)
                : new UiDynamicStatus.Snapshot(Status.SUCCEEDED);
    }

    private void advanceStatus() {
        int current = liveStatus.sample().status().ordinal();
        status = DEMO_STATUSES[(current + 1) % DEMO_STATUSES.length];
        statusStartedAt = PageTransition.nowMs();
    }

    private void addLoadingSlider(DeveloperSamples sample) {
        var area = new Bounds(preview.x() + 6, preview.bottom() - BUTTON_HEIGHT - 5, preview.width() - 12, BUTTON_HEIGHT);
        addPreviewWidget(new UiSlider(area, 0, 100, 5, loadingPercent, preferences,
                value -> sample == DeveloperSamples.LOADING_SPINNER ? label("gallery.loading_organizing")
                        : label("gallery.slider_value", value), value -> loadingPercent = value));
    }

    private void addSearch(int x, int y, int w) {
        var index = new SearchIndex<>(List.of(
                new SearchIndex.Entry<>("minecraft:apple", Component.translatable("item.minecraft.apple").getString(),
                        List.of("minecraft:apple"), List.of()),
                new SearchIndex.Entry<>("minecraft:carrot", Component.translatable("item.minecraft.carrot").getString(),
                        List.of("minecraft:carrot"), List.of())));
        search = addPreviewWidget(new SearchField<>(font, x, y, w, BUTTON_HEIGHT,
                index, UiStyle.LIQUID_GLASS, preferences));
        search.setHint(label("gallery.search_hint"));
    }

    private DeveloperSamples sample() {
        return samples.get(sampleIndex);
    }

    private void copyCode() {
        minecraft.keyboardHandler.setClipboard(sample().code);
        copied = true;
        copyButton.setMessage(label("gallery.copied"));
        minecraft.getNarrator().say(label("copied"));
    }

    private void applyPendingStep() {
        if (pendingStep == 0) return;
        int step = pendingStep;
        pendingStep = 0;
        frameTimeline.pause();
        sampleIndex = Math.clamp(sampleIndex + step, 0, samples.size() - 1);
        copied = false;
        codeDrawer.cancelGesture(PageTransition.nowMs());
        rebuildWidgets();
        if (!compact) {
            UiButton direction = step < 0 ? previousSample : nextSample;
            sampleReveal.startAt(direction.getX() + direction.getWidth() * 0.5f, preview.y() + 5f);
            setInitialFocus(direction.active ? direction : step < 0 ? nextSample : previousSample);
            minecraft.getNarrator().say(sampleTitle());
        }
    }

    private Component sampleTitle() {
        return label("gallery.sample_page", label("gallery.sample." + sample().key), sampleIndex + 1, samples.size());
    }

    // NeoForge 由 Screen.render 调用，Forge 1.20.1 使用不同签名。
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        applyPendingStep();
        if (entrance.closing()) {
            returning.render(graphics, width, height, partialTick, preferences);
        } else if (minecraft.level == null) {
            DarkModeTheme.background(graphics, 0, 0, width, height);
        } else {
            graphics.fill(0, 0, width, height, 0x6020252E);
        }
        if (compact) {
            int y = PADDING;
            for (var line : font.split(TOO_SMALL, Math.max(1, width - 2 * PADDING))) {
                if (y + font.lineHeight > height - BUTTON_HEIGHT - 6) break;
                graphics.drawString(font, line, PADDING, y, LiquidGlassTheme.TEXT, false);
                y += font.lineHeight + 2;
            }
        } else {
            var panel = new Bounds(left, top, panelWidth, codeViewport.y() - top - FOOTER_GAP);
            if (entrance.render(graphics, panel, preferences, () -> {
                renderPanelContent(graphics, -1, -1, partialTick);
                super.render(graphics, -1, -1, partialTick);
            })) return;
            LiquidGlassTheme.popover(graphics, panel.x(), panel.y(), panel.width(), panel.height(), preferences);
            renderPanelContent(graphics, mouseX, mouseY, partialTick);
            if (openCodeOnEntry) setCodeDrawerOpen(true);
            codeFrame = codeDrawer.render(graphics, codeViewport, mouseX, mouseY, PageTransition.nowMs(),
                    preferences, this::renderCodeExcerpt);
            updateCodeDrawerControls(codeFrame);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (search != null && !sampleReveal.active()) search.renderSuggestions(graphics, mouseX, mouseY, height);
    }

    private void renderPanelContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawString(font, getTitle(), preview.x(), top + 11, LiquidGlassTheme.TEXT, false);
        drawCentered(graphics, sampleTitle(), preview.x() + NAV_WIDTH + 4, top + SAMPLE_Y,
                preview.width() - (NAV_WIDTH + 4) * 2);
        if (!sampleReveal.render(graphics, preview, preferences,
                () -> renderPreviewContent(graphics, -1, -1, partialTick))) {
            RoundedCorners.draw(graphics, preview.x(), preview.y(), preview.width(), preview.height(), 5, 1,
                    preferences.highContrast() ? DarkModeTheme.TEXT : DarkModeTheme.BORDER, DarkModeTheme.BACKGROUND);
            renderPreviewContent(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderPreviewContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderPreview(graphics);
        for (AbstractWidget widget : previewWidgets) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderCodeExcerpt(GuiGraphics graphics, Bounds content) {
        int textY = content.y() + CODE_PADDING;
        int bottom = codeDetailsButton.getY() - CODE_PADDING;
        if (bottom <= textY) return;
        graphics.enableScissor(content.x(), textY, content.right(), bottom);
        try {
            codeLayout.render(graphics, font, content.x() + CODE_PADDING, textY,
                    LiquidGlassTheme.TEXT, false, preferences);
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderPreview(GuiGraphics graphics) {
        DeveloperSamples sample = sample();
        if (sample == DeveloperSamples.FRAME_TIMELINE) {
            frameTimeline.render(graphics, preferences);
        } else if (sample.glassStyle != null) {
            renderGlass(graphics, sample);
        } else if (sample == DeveloperSamples.GRADIENT_CARD) {
            int w = Math.min(164, preview.width() - 32);
            int x = preview.x() + (preview.width() - w) / 2;
            int y = preview.y() + 6;
            var state = cardMotion.sample(false, false, false, true, preferences, PageTransition.nowMs());
            DarkModeTheme.card(graphics, x, y, w, BUTTON_HEIGHT, state,
                    () -> drawCentered(graphics, Component.translatable("gui.stats"), x + 6, y, w - 12));
        } else if (sample.icon != null) {
            int x = preview.x() + 10;
            int size = GraphicIcon.size(2);
            sample.icon.draw(graphics, x, preview.y() + (preview.height() - size) / 2, 2, 0xFFFFFFFF, preferences);
            int pathX = x + size + 8;
            var lines = font.split(Component.literal(sample.icon.texture().toString()), preview.right() - pathX - 8);
            int y = preview.y() + (preview.height() - lines.size() * font.lineHeight) / 2;
            for (var line : lines) {
                graphics.drawString(font, line, pathX, y, LiquidGlassTheme.TEXT, false);
                y += font.lineHeight;
            }
        } else if (detailsLayout != null) {
            int contentWidth = Math.min(264, preview.width() - 16);
            int x = preview.x() + (preview.width() - contentWidth) / 2;
            detailsLayout.render(graphics, font, x, preview.y() + 5, LiquidGlassTheme.TEXT, false, preferences);
        } else if (sample == DeveloperSamples.SLIDE_CONFIRM) {
            drawCentered(graphics, label("gallery.slide_confirm_hint"), preview.x() + 8, preview.y(), preview.width() - 16);
        } else if (sample == DeveloperSamples.PAGE_SLIDE) {
            renderSlide(graphics);
        } else if (sample == DeveloperSamples.RECIPE_TRANSITION) {
            renderRecipeSlide(graphics);
        } else if (sample == DeveloperSamples.DYNAMIC_STATUS) {
            var area = new Bounds(preview.x() + 6, preview.y() + 5,
                    preview.width() - STATUS_BUTTON_WIDTH - 18, preview.height() - 10);
            liveStatus.render(graphics, font, area, preferences);
        } else if (sample == DeveloperSamples.LOADING_SPINNER || sample == DeveloperSamples.LOADING_PROGRESS
                || sample == DeveloperSamples.LOADING_SKELETON) {
            renderLoading(graphics, sample);
        } else if (sample == DeveloperSamples.DISCLOSURE && disclosure.expanded()) {
            drawCentered(graphics, label("gallery.details"), preview.x() + 6, preview.y() + 23, preview.width() - 12);
        }
    }

    private void renderSlide(GuiGraphics graphics) {
        Bounds viewport = slideViewport();
        drawSampleBackdrop(graphics, viewport.x(), viewport.y(), viewport.width(), viewport.height());
        var frame = pageSlide.sample(PageTransition.nowMs(), preferences);
        UiPageSlide.draw(graphics, viewport, frame, preferences, slideEffects, () -> {
            var pose = graphics.pose();
            pose.pushPose();
            try {
                // 旋转后的卡片仍位于预览底板前方，避免背向的一侧被深度缓冲挡住。
                double depth = Math.abs(Math.sin(Math.toRadians(frame.yaw()))) * viewport.width()
                        + Math.abs(Math.sin(Math.toRadians(frame.pitch()))) * viewport.height();
                if (depth > 0) pose.translate(0f, 0f, (float) (depth / 2 + 1));
                frame.applyDepth(pose, viewport.x() + viewport.width() / 2f, viewport.y() + viewport.height() / 2f);
                int color = alternateSlidePage ? 0xAC2F4F67 : 0xAC493C63;
                RoundedCorners.draw(graphics, viewport.x() + 2, viewport.y() + 2,
                        viewport.width() - 4, viewport.height() - 4, 4, 1, LiquidGlassTheme.ACCENT, color);
                drawCentered(graphics, label("gallery.slide_page", alternateSlidePage ? 2 : 1),
                        viewport.x() + 4, viewport.y() + (viewport.height() - BUTTON_HEIGHT) / 2, viewport.width() - 8);
            } finally {
                pose.popPose();
            }
        });
    }

    private void renderRecipeSlide(GuiGraphics graphics) {
        var viewport = recipeSlideViewport();
        var frame = recipeTransition.sample(PageTransition.nowMs(), preferences);
        UiRecipePageTransition.draw(graphics, viewport, frame, preferences,
                () -> renderRecipeCard(graphics, viewport, !alternateSlidePage),
                () -> renderRecipeCard(graphics, viewport, alternateSlidePage));

        var side = frame.companion();
        int sideX = viewport.right() + 6;
        int right = slideViewport().right();
        graphics.enableScissor(sideX, viewport.y(), right, viewport.bottom());
        try {
            var pose = graphics.pose();
            pose.pushPose();
            try {
                pose.translate(0f, side.offsetY(), 0f);
                boolean alternate = side.incoming() ? alternateSlidePage : !alternateSlidePage;
                drawCentered(graphics, label("gallery.slide_page", alternate ? 2 : 1),
                        sideX, viewport.y() + (viewport.height() - BUTTON_HEIGHT) / 2, right - sideX);
            } finally {
                pose.popPose();
            }
            if (side.veilOpacity() > 0f) {
                int color = Math.round(side.veilOpacity() * 255f) << 24 | (DarkModeTheme.BACKGROUND & 0xFFFFFF);
                graphics.fill(sideX, viewport.y(), right, viewport.bottom(), 2, color);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderRecipeCard(GuiGraphics graphics, Bounds viewport, boolean alternate) {
        int color = alternate ? 0xFF503D73 : 0xFF315879;
        graphics.fillGradient(viewport.x(), viewport.y(), viewport.right(), viewport.bottom(), color, 0xFF212A3A);
        drawCentered(graphics, label("gallery.slide_page", alternate ? 2 : 1),
                viewport.x() + 4, viewport.y() + (viewport.height() - BUTTON_HEIGHT) / 2, viewport.width() - 8);
    }

    private void renderLoading(GuiGraphics graphics, DeveloperSamples sample) {
        var area = slideViewport();
        long now = PageTransition.nowMs();
        float progress = loadingPercent / 100f;
        switch (sample) {
            case LOADING_SPINNER -> {
                int half = area.width() / 2;
                UiLoading.spinner(graphics, new Bounds(area.x() + (half - 20) / 2,
                        area.y() + (area.height() - 20) / 2, 20, 20), progress, preferences);
                var list = new Bounds(area.x() + half + 3, area.y(), area.width() - half - 3, area.height());
                graphics.fill(list.x(), list.y(), list.right(), list.bottom(), 0xFF252F3D);
                UiLoading.edgeSpinner(graphics, list, UiLoading.Edge.TOP, now, preferences);
                UiLoading.edgeSpinner(graphics, list, UiLoading.Edge.BOTTOM, now, preferences);
            }
            case LOADING_PROGRESS -> {
                UiLoading.topBar(graphics, area, progress, preferences);
                var track = new Bounds(area.x() + 6, area.y() + (area.height() - 5) / 2, area.width() - 12, 5);
                UiLoading.progressBar(graphics, track, progress, preferences);
            }
            case LOADING_SKELETON -> {
                if (cardLoading) {
                    UiSkeleton.card(graphics, area, now, preferences);
                } else {
                    LiquidGlassTheme.popover(graphics, area.x(), area.y(), area.width(), area.height(), preferences);
                    drawCentered(graphics, label("gallery.loading_card_ready"), area.x() + 6,
                            area.y() + (area.height() - BUTTON_HEIGHT) / 2, area.width() - 12);
                }
            }
            default -> { }
        }
    }

    private void renderGlass(GuiGraphics graphics, DeveloperSamples sample) {
        int x = preview.x() + 8;
        int y = preview.y() + 5;
        int w = preview.width() - 16;
        int h = preview.height() - 10;
        drawSampleBackdrop(graphics, x, y, w, h);
        boolean sampled = preferences.useBackdropBlur() && GlassBlur.tryBackdrop(graphics,
                new GuiRect(x, y, w, h), 1f, sample.glassStyle, 12f, 4f);
        if (!sampled) {
            graphics.fill(x, y, x + w, y + h, 0x9020252E);
            drawCentered(graphics, label("gallery.blur_unavailable"), x + 3, y + 7, w - 6);
        }
    }

    private static void drawSampleBackdrop(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fillGradient(x, y, x + w, y + h, 0xFF153F79, 0xFF40264F);
        graphics.fill(x + w / 5, y + 3, x + w * 2 / 5, y + h - 3, 0xFF85C9D7);
        graphics.fill(x + w * 3 / 5, y + 3, x + w * 4 / 5, y + h - 3, 0xFFD79D66);
    }

    private void drawCentered(GuiGraphics graphics, Component text, int x, int y, int w) {
        var lines = font.split(text, w);
        int count = Math.min(2, lines.size());
        int textY = y + (BUTTON_HEIGHT - count * font.lineHeight) / 2;
        for (int i = 0; i < count; i++) {
            var line = lines.get(i);
            graphics.drawString(font, line, x + (w - font.width(line)) / 2, textY, LiquidGlassTheme.TEXT, false);
            textY += font.lineHeight;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (entrance.active() || (sampleReveal.active() && previewWidgets.stream()
                .anyMatch(widget -> widget.isMouseOver(mouseX, mouseY)))) return false;
        return (search != null && search.mouseClickedSuggestion(mouseX, mouseY, button, height))
                || codeDrawer.beginCaptured(this, codeFrame, button, mouseX, mouseY)
                || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (entrance.closing()) return true;
        if (key != GLFW.GLFW_KEY_ESCAPE) entrance.finish();
        if (key == GLFW.GLFW_KEY_TAB || (getFocused() != previousSample && getFocused() != nextSample)) {
            sampleReveal.finish();
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (search != null && search.dismissSuggestions()) return true;
            if (!compact && (openCodeOnEntry || codeDrawer.targetAnchor() != 0)) {
                setCodeDrawerOpen(false);
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return !entrance.active() && !sampleReveal.active() && search != null
                && search.mouseScrolledSuggestions(mouseX, mouseY, delta, height);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        return mouseScrolled(mouseX, mouseY, deltaY);
    }

    @Override
    public void removed() {
        frameTimeline.pause();
        electricLogo.release();
        super.removed();
    }

    @Override
    public void onClose() {
        frameTimeline.pause();
        pendingStep = 0;
        if (!compact && !preferences.reducedMotion()) returning.prepare(parent, width, height);
        entrance.close(preferences, () -> {
            minecraft.setScreen(parent);
            if (minecraft.screen == parent) parent.restoreGalleryFocus();
        });
        if (compact) entrance.finish();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
