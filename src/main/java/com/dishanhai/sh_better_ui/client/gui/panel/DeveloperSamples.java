package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;

/** 山海：开发者图库使用的控件、材质和对应 Java 接入示例。 */
enum DeveloperSamples {
    BUTTON("button", UiStyle.DEFAULT),
    TOGGLE("toggle", """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.NetworkToggle;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import net.minecraft.network.chat.Component;

            private NetworkToggle toggle;
            private void addToggle(int x, int y) {
                toggle = new NetworkToggle(new Bounds(x, y, 140, 22),
                        Component.translatable("options.on"), false,
                        UiPreferences.configured(), value -> toggle.setValue(value));
                addRenderableWidget(toggle);
            }
            """),
    SECTIONS("sections", """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
            import net.minecraft.network.chat.Component;
            import java.util.List;
            import java.util.function.IntConsumer;

            private void addSections(int x, int y, IntConsumer onSelect) {
                var bar = new UiSectionBar(x, y, 164, 22, UiPreferences.configured(), List.of(
                        new UiSectionBar.Section(Component.translatable("options.title"), GraphicIcon.SETTINGS),
                        new UiSectionBar.Section(Component.translatable("gui.stats"), GraphicIcon.MONITOR)),
                        0, onSelect);
                bar.buttons().forEach(this::addRenderableWidget);
            }
            """),
    DISCLOSURE("disclosure", """
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiDisclosure;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import net.minecraft.network.chat.Component;

            private final UiDisclosure disclosure = new UiDisclosure(false);

            private void addDisclosure(int x, int y) {
                addRenderableWidget(disclosure.button(x, y, 120, 22,
                        Component.translatable("sh_better_ui.panel.developer.gallery.expand"),
                        Component.translatable("sh_better_ui.panel.developer.gallery.collapse"),
                        UiPreferences.configured(), expanded -> {}));
            }

            private boolean showDetails() {
                return disclosure.expanded();
            }
            """),
    TEXT_DETAILS("text_details", """
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiTextDetails;
            import net.minecraft.client.gui.GuiGraphics;
            import net.minecraft.network.chat.Component;

            private final UiTextDetails text = new UiTextDetails(
                    Component.translatable("sh_better_ui.panel.developer.gallery.sample.text_details"),
                    Component.translatable("sh_better_ui.panel.developer.gallery.long_text"), 1);
            private UiTextDetails.Layout detailsLayout;

            private void addDetails(int x, int buttonY, int width) {
                var prefs = UiPreferences.configured();
                detailsLayout = text.layout(font, width);
                int half = (width - 6) / 2;
                text.inlineButton(detailsLayout, x, buttonY, half, 22, prefs,
                        expanded -> detailsLayout = text.layout(font, width))
                        .ifPresent(this::addRenderableWidget);
                text.pageButton(detailsLayout, this, x + half + 6, buttonY,
                        width - half - 6, 22, prefs).ifPresent(this::addRenderableWidget);
            }

            private void drawDetails(GuiGraphics graphics, int x, int y) {
                detailsLayout.render(graphics, font, x, y, 0xFFFFFFFF, false, UiPreferences.configured());
            }
            """),
    SLIDER("slider", """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
            import net.minecraft.network.chat.Component;

            private int sliderValue = 50;

            private void addSlider(int x, int y) {
                var slider = new UiSlider(new Bounds(x, y, 164, 22), 0, 100, 5,
                        sliderValue, UiPreferences.configured(),
                        value -> Component.translatable("sh_better_ui.panel.developer.gallery.slider_value", value),
                        value -> sliderValue = value);
                addRenderableWidget(slider);
            }
            """),
    FRAME_TIMELINE("frame_timeline", """
            import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiTimeline;

            private final UiTimeline timeline = new UiTimeline(30, 120);
            private final UiTimeline.Sequence caption = new UiTimeline.Sequence(30, 90);

            private void play() { timeline.play(PageTransition.nowMs()); }
            private void pause() { timeline.pause(PageTransition.nowMs()); }
            private void seek(int frame) { timeline.seek(frame); }
            private void reset() { timeline.reset(); }

            private void drawFrame() {
                var frame = timeline.sample(PageTransition.nowMs());
                float x = frame.interpolate(0, 90, 0f, 100f);
                float opacity = frame.interpolate(0, 20, 0f, 1f);
                boolean showCaption = caption.contains(frame);
                int localFrame = caption.localFrame(frame);
                // Use x / opacity to draw; bind UiSlider to seek(frame).
                // Pause when the screen is removed. Reduced motion: seek(119).
            }
            """),
    ELECTRIC_LOGO("electric_logo", """
            import com.dishanhai.sh_better_ui.client.gui.display.ElectricLogo;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
            import net.minecraft.client.gui.GuiGraphics;

            private final ElectricLogo logo = new ElectricLogo();

            private void drawLogo(GuiGraphics graphics, int x, int y, int width, int height,
                                  int mouseX, int mouseY) {
                logo.draw(graphics, x, y, width, height, mouseX, mouseY);
            }

            private void clickLogo(double mouseX, double mouseY) {
                logo.pulse(mouseX, mouseY);
            }

            private void changeLogo() {
                logo.setTexture(GraphicIcon.FAVORITE.texture());
            }

            @Override
            public void removed() {
                logo.close();
                super.removed();
            }
            """),
    SLIDE_CONFIRM("slide_confirm", """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlideConfirm;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
            import net.minecraft.network.chat.Component;

            private void addSlideConfirm(int x, int y, Runnable onConfirm) {
                var prefs = UiPreferences.configured();
                var slide = addRenderableWidget(new UiSlideConfirm(
                        this, new Bounds(x, y, 164, 22), prefs, onConfirm));
                var reset = new UiButton(x + 170, y, 60, 22, Component.translatable("controls.reset"),
                        button -> slide.reset(), prefs, false, UiStyle.LIQUID_GLASS);
                reset.setLeadingIcon(GraphicIcon.RESET);
                addRenderableWidget(reset);
            }
            """),
    CAPSULE_TOGGLE("capsule_toggle", """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
            import net.minecraft.network.chat.Component;

            private UiToggleButton capsule;
            private boolean enabled;

            private void addCapsule(int x, int y) {
                capsule = new UiToggleButton(new Bounds(x, y, 164, 22), enabled,
                        UiPreferences.configured(), UiStyle.LIQUID_GLASS,
                        value -> Component.translatable(value ? "options.on" : "options.off"),
                        value -> {
                            enabled = value;
                            capsule.setValue(enabled);
                        });
                capsule.setIcons(GraphicIcon.ENABLE, GraphicIcon.DISABLE);
                capsule.setModeIndicatorsVisible(true);
                addRenderableWidget(capsule.button());
            }
            """),
    MODE_INDICATORS("mode_indicators", """
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
            import net.minecraft.network.chat.Component;

            private int mode;

            private void addModes(int x, int y) {
                int modeCount = 3;
                var button = new UiButton(x, y, 164, 22, modeLabel(), pressed -> {
                    mode = (mode + 1) % modeCount;
                    var control = (UiButton) pressed;
                    control.setMessage(modeLabel());
                    control.setModeIndicators(modeCount, mode);
                }, UiPreferences.configured(), false, UiStyle.LIQUID_GLASS);
                button.setModeIndicators(modeCount, mode);
                addRenderableWidget(button);
            }

            private Component modeLabel() {
                return Component.translatable("sh_better_ui.panel.developer.gallery.mode", mode + 1);
            }
            """),
    PAGE_SLIDE("page_slide", """
            import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPageSlide;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import net.minecraft.client.gui.GuiGraphics;

            private final UiPageSlide slide = new UiPageSlide();
            private UiPageSlide.Effects effects = UiPageSlide.Effects.NONE;

            private void beginSlide(Bounds viewport, UiPageSlide.Direction direction, boolean depth) {
                var mode = depth ? UiPageSlide.Motion.DEPTH : UiPageSlide.Motion.FLAT;
                effects = depth ? new UiPageSlide.Effects(true, true, GlassStyle.LIQUID_REGULAR)
                        : UiPageSlide.Effects.NONE;
                int distance = switch (direction) {
                    case LEFT, RIGHT -> viewport.width();
                    case UP, DOWN -> viewport.height();
                };
                slide.start(direction, mode, distance, PageTransition.nowMs(), UiPreferences.configured());
            }

            private void drawSlide(GuiGraphics graphics, Bounds viewport, Runnable drawPage) {
                var prefs = UiPreferences.configured();
                var frame = slide.sample(PageTransition.nowMs(), prefs);
                UiPageSlide.draw(graphics, viewport, frame, prefs, effects, () -> {
                    var pose = graphics.pose();
                    pose.pushPose();
                    try {
                        double depth = Math.abs(Math.sin(Math.toRadians(frame.yaw()))) * viewport.width()
                                + Math.abs(Math.sin(Math.toRadians(frame.pitch()))) * viewport.height();
                        if (depth > 0) pose.translate(0f, 0f, (float) (depth / 2 + 1));
                        frame.applyDepth(pose, viewport.x() + viewport.width() / 2f,
                                viewport.y() + viewport.height() / 2f);
                        drawPage.run();
                    } finally {
                        pose.popPose();
                    }
                });
            }
            """),
    RECIPE_TRANSITION("recipe_transition", """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPageSlide.Direction;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiRecipePageTransition;
            import net.minecraft.client.gui.GuiGraphics;

            private final UiRecipePageTransition transition = new UiRecipePageTransition();

            private void beginSwitch(Bounds viewport, Direction direction) {
                transition.start(direction, viewport, PageTransition.nowMs(), UiPreferences.configured());
            }

            private void drawPages(GuiGraphics graphics, Bounds viewport, Runnable oldPage, Runnable newPage) {
                var prefs = UiPreferences.configured();
                var frame = transition.sample(PageTransition.nowMs(), prefs);
                UiRecipePageTransition.draw(graphics, viewport, frame, prefs, oldPage, newPage);
            }
            """),
    DYNAMIC_STATUS("dynamic_status", """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.ButtonFeedback.Status;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiDynamicStatus;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import net.minecraft.client.gui.GuiGraphics;

            private UiDynamicStatus.Snapshot current = new UiDynamicStatus.Snapshot(Status.READY);
            private final UiDynamicStatus indicator = new UiDynamicStatus(() -> current);

            private void onProgress(int percent) {
                current = new UiDynamicStatus.Snapshot(Status.PENDING, percent);
            }

            private void onCompleted() {
                current = new UiDynamicStatus.Snapshot(Status.SUCCEEDED);
            }

            private void drawStatus(GuiGraphics graphics, Bounds area) {
                indicator.render(graphics, font, area, UiPreferences.configured());
            }
            """),
    LOADING_SPINNER("loading_spinner", """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiLoading;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import net.minecraft.client.gui.GuiGraphics;

            private void drawListLoading(GuiGraphics graphics, Bounds viewport,
                                         boolean refreshing, boolean loadingMore) {
                var prefs = UiPreferences.configured();
                long now = PageTransition.nowMs();
                if (refreshing) UiLoading.edgeSpinner(graphics, viewport, UiLoading.Edge.TOP, now, prefs);
                if (loadingMore) UiLoading.edgeSpinner(graphics, viewport, UiLoading.Edge.BOTTOM, now, prefs);
            }

            private void drawOrganizing(GuiGraphics graphics, Bounds iconArea, float progress) {
                UiLoading.spinner(graphics, iconArea, progress, UiPreferences.configured());
            }
            """),
    LOADING_PROGRESS("loading_progress", """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiLoading;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import net.minecraft.client.gui.GuiGraphics;

            private void drawTransfer(GuiGraphics graphics, Bounds track, Bounds viewport,
                                      long transferred, long total) {
                var prefs = UiPreferences.configured();
                if (total > 0) {
                    float progress = (float) ((double) transferred / total);
                    UiLoading.progressBar(graphics, track, progress, prefs);
                    UiLoading.topBar(graphics, viewport, progress, prefs);
                } else {
                    UiLoading.topBar(graphics, viewport, PageTransition.nowMs(), prefs);
                }
            }
            """),
    LOADING_SKELETON("loading_skeleton", """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiSkeleton;
            import net.minecraft.client.gui.GuiGraphics;

            private void drawCard(GuiGraphics graphics, Bounds area, boolean loading, Runnable drawContent) {
                if (loading) {
                    UiSkeleton.card(graphics, area, PageTransition.nowMs(), UiPreferences.configured());
                } else {
                    drawContent.run();
                }
            }
            """),
    SEARCH("search", """
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
            import com.dishanhai.sh_better_ui.client.gui.search.SearchField;
            import com.dishanhai.sh_better_ui.client.gui.search.SearchIndex;
            import net.minecraft.client.gui.GuiGraphics;
            import net.minecraft.network.chat.Component;
            import org.lwjgl.glfw.GLFW;
            import java.util.List;

            private SearchField<String> search;

            private void addSearch(int x, int y) {
                var index = new SearchIndex<>(List.of(new SearchIndex.Entry<>(
                        "minecraft:apple", Component.translatable("item.minecraft.apple").getString(),
                        List.of("minecraft:apple"), List.of())));
                search = addRenderableWidget(new SearchField<>(font, x, y, 160, 22,
                        index, UiStyle.LIQUID_GLASS, UiPreferences.configured()));
            }

            @Override
            public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                super.render(graphics, mouseX, mouseY, partialTick);
                search.renderSuggestions(graphics, mouseX, mouseY, height);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                return search.mouseClickedSuggestion(mouseX, mouseY, button, height)
                        || super.mouseClicked(mouseX, mouseY, button);
            }

            @Override
            public boolean keyPressed(int key, int scan, int modifiers) {
                return (key == GLFW.GLFW_KEY_ESCAPE && search.dismissSuggestions())
                        || super.keyPressed(key, scan, modifiers);
            }
            """),
    DIALOG("dialog", DeveloperPanelScreen.DIALOG_CODE),
    TOAST("toast", DeveloperPanelScreen.TOAST_CODE),
    DOT_MATRIX("dot_matrix", """
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.DotMatrixProfile;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiDotMatrix;
            import net.minecraft.client.gui.GuiGraphics;
            import java.util.List;

            private final UiDotMatrix dots = new UiDotMatrix();

            private void drawDots(GuiGraphics graphics, Bounds area, int mouseX, int mouseY) {
                dots.render(graphics, area, mouseX, mouseY);
            }

            private void pulseDots(Bounds area, double x, double y) {
                dots.pulse(area, x, y);
            }

            private void updateDots(int backgroundArgb, int dotArgb, int highlightArgb,
                                    DotMatrixProfile.Effect effect, int floatPercent, int maxFloatingDots,
                                    int fadePercent, int gradientPeriodMs) {
                var base = DotMatrixProfile.VIOLET;
                var motion = base.motion();
                var gradient = new DotMatrixProfile.Gradient(List.of(
                        new DotMatrixProfile.ColorStop(0f, 0xFFFF71AA),
                        new DotMatrixProfile.ColorStop(0.22f, 0xC079D5FF),
                        new DotMatrixProfile.ColorStop(0.66f, 0xFFB2FFB5)),
                        gradientPeriodMs, (x, y, phase) -> phase - x * x - 0.35f * y);
                dots.setProfile(base
                        .withPalette(new DotMatrixProfile.Palette(backgroundArgb, dotArgb, highlightArgb,
                                base.palette().glow()))
                        .withMotion(new DotMatrixProfile.Motion(effect, motion.periodMs(), floatPercent / 100f,
                                maxFloatingDots, motion.floatDistance()))
                        .withFade(new DotMatrixProfile.Fade(fadePercent / 100f, 2200))
                        .withGradient(gradient));
            }
            """),
    PATTERN_WAVES("pattern_waves", """
            import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
            import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
            import com.dishanhai.sh_better_ui.client.gui.scaled.PatternWaves;
            import com.dishanhai.sh_better_ui.client.gui.scaled.PatternWavesProfile;
            import net.minecraft.client.gui.GuiGraphics;

            private final PatternWaves waves = ShBetterUiApi.motion().patternWaves();

            private void drawWaves(GuiGraphics graphics, Bounds area) {
                var mouse = ShBetterUiApi.interactions().mouseState();
                waves.draw(graphics, area, mouse, ShBetterUiApi.settings().preferences());
            }

            @Override
            public boolean mouseClicked(double x, double y, int button) {
                waves.mousePressed(x, y, button);
                return super.mouseClicked(x, y, button);
            }

            private void selectPreset(PatternWavesProfile.Preset preset) {
                waves.setProfile(preset.profile());
            }

            private void togglePause() { waves.setPaused(!waves.isPaused()); }
            """),
    LIQUID_BUTTON("liquid_button", UiStyle.LIQUID_GLASS),
    DARK_BUTTON("dark_button", UiStyle.DEFAULT),
    GRADIENT_CARD("gradient_card", """
            import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
            import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import net.minecraft.client.gui.GuiGraphics;

            private final DarkModeTheme.Interaction cardMotion = DarkModeTheme.Interaction.card();

            private void drawCard(GuiGraphics graphics, int x, int y, int width, int height, Runnable drawContent) {
                var state = cardMotion.sample(false, false, false, true,
                        UiPreferences.configured(), PageTransition.nowMs());
                DarkModeTheme.card(graphics, x, y, width, height, state, drawContent);
            }
            """),
    NETWORK_TAB("network_tab", UiStyle.NETWORK),
    CLEAR_GLASS("clear_glass", GlassStyle.CLEAR, "CLEAR"),
    FROSTED_GLASS("frosted_glass", GlassStyle.FROSTED, "FROSTED"),
    RESET_ICON("reset_icon", GraphicIcon.RESET, "RESET"),
    SETTINGS_ICON("settings_icon", GraphicIcon.SETTINGS, "SETTINGS"),
    COPY_ICON("copy_icon", GraphicIcon.COPY, "COPY"),
    STATUS_ICON("status_icon", GraphicIcon.STATUS_PENDING, "STATUS_PENDING");

    final String key;
    final String code;
    final UiStyle buttonStyle;
    final GlassStyle glassStyle;
    final GraphicIcon icon;

    DeveloperSamples(String key, String code) {
        this(key, code, null, null, null);
    }

    DeveloperSamples(String key, UiStyle style) {
        this(key, """
                import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
                import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
                import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
                import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
                import net.minecraft.network.chat.Component;

                private void addButton(int x, int y) {
                    var button = new UiButton(x, y, 140, 22,
                            Component.translatable("gui.done"),
                            pressed -> {
                                var control = (UiButton) pressed;
                                control.setSelected(!control.isSelected());
                            },
                            UiPreferences.configured(), false, UiStyle.%s);
                    button.setLeadingIcon(GraphicIcon.RESET);
                    addRenderableWidget(button);
                }
                """.formatted(style.name()), style, null, null);
    }

    DeveloperSamples(String key, GlassStyle style, String constant) {
        this(key, """
                import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
                import com.dishanhai.sh_better_ui.client.glass.GlassBlur;
                import com.dishanhai.sh_better_ui.client.glass.GuiRect;
                import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
                import net.minecraft.client.gui.GuiGraphics;

                private void drawMaterial(GuiGraphics graphics, int x, int y, int w, int h) {
                    var prefs = UiPreferences.configured();
                    if (prefs.useBackdropBlur()) {
                        GlassBlur.tryBackdrop(graphics, new GuiRect(x, y, w, h),
                                1f, GlassStyle.%s, 12f, 4f);
                    }
                }
                """.formatted(constant), null, style, null);
    }

    DeveloperSamples(String key, GraphicIcon icon, String constant) {
        this(key, """
                import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
                import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
                import net.minecraft.client.gui.GuiGraphics;

                private void drawIcon(GuiGraphics graphics, int x, int y) {
                    GraphicIcon.%s.draw(graphics, x, y, 2, 0xFFFFFFFF,
                            UiPreferences.configured());
                }
                """.formatted(constant), null, null, icon);
    }

    DeveloperSamples(String key, String code, UiStyle buttonStyle, GlassStyle glassStyle, GraphicIcon icon) {
        this.key = key;
        this.code = code;
        this.buttonStyle = buttonStyle;
        this.glassStyle = glassStyle;
        this.icon = icon;
    }
}
