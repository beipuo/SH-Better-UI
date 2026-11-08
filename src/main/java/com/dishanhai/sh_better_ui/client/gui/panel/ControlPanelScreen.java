package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.blur.GaussBlur;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.glass.GlassBlur;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.FluidMorph;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiRenderUtil;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCapsuleReveal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScreenReturn;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiConfirmation;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSkeleton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.GlassBackdropSettings;
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

/** 山海：在同一画面编辑 SHBUI 客户端配置和快捷键。 */
public final class ControlPanelScreen extends Screen implements UiScreenReturn.Target {
    private static final int MAX_WIDTH = 624;
    private static final int PADDING = 14;
    private static final int GAP = 8;
    private static final int BUTTON_HEIGHT = 22;
    private static final int HEADER_HEIGHT = 64;
    private static final int FOOTER_HEIGHT = 46;
    private static final int GLASS_STACK_HEIGHT = 190;
    private static final int GLASS_SIDE_HEIGHT = 146;
    private static final int SCROLLBAR_WIDTH = 5;
    private static final float BODY_RADIUS = 5f;
    private static final List<Section> SECTIONS = List.of(Section.values());
    private static final List<UiSectionBar.Section> TABS = SECTIONS.stream().map(section -> section.tab).toList();

    static {
        GaussBlur.allowScreen(screen -> screen instanceof ControlPanelScreen);
    }

    private final Screen parent;
    private final KeyMapping keyMapping;
    private final Predicate<ClientUiSettings> save;
    private final Map<AbstractWidget, Integer> rows = new LinkedHashMap<>();
    private final EnumMap<Scale, UiSlider> sliders = new EnumMap<>(Scale.class);
    private final int[] sectionScroll = new int[SECTIONS.size()];
    private final UiCapsuleReveal entrance = new UiCapsuleReveal();
    private final UiScreenReturn returning = new UiScreenReturn(this);
    private final InterruptibleAnimation pageMorph = new InterruptibleAnimation(Spring.FLUID_PANEL, 1f);
    private final InterruptibleAnimation pageOrigin = new InterruptibleAnimation(Spring.INDICATOR);
    private final Draft defaults;
    private Draft saved;
    private Draft draft;
    private Draft beforeReset;
    private UiPreferences preferences;
    private UiOverlayHost overlays;
    private UiSectionBar sections;
    private UiToggleButton enabled;
    private UiToggleButton sampling;
    private UiToggleButton contrast;
    private UiToggleButton motion;
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
        pageMorph.snapTo(1f);
        back = null;
        clearPage();
        fits = width >= 240 && height >= 156;
        if (!fits) {
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
        panelHeight = Math.min(height - 12, HEADER_HEIGHT + GLASS_STACK_HEIGHT + FOOTER_HEIGHT);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        if (!entered) {
            entered = true;
            entrance.startAt(width * 0.5f, top + 5f);
        }
        bodyTop = top + HEADER_HEIGHT;
        bodyBottom = top + panelHeight - FOOTER_HEIGHT;
        int x = left + PADDING;
        int innerWidth = panelWidth - 2 * PADDING;
        int developerWidth = Math.min(120, innerWidth / 2);
        developer = addRenderableWidget(button(x + innerWidth - developerWidth, top + 8, developerWidth,
                Component.translatable("sh_better_ui.panel.developer.title"), GraphicIcon.CODE, false, () -> {
                    restoreDeveloperFocus = true;
                    minecraft.setScreen(new DeveloperPanelScreen(this, preferences, developer));
                }));
        sections = new UiSectionBar(x, top + 35, innerWidth, BUTTON_HEIGHT, preferences, TABS,
                selectedSection.ordinal(), index -> requestedSection = SECTIONS.get(index));
        sections.buttons().forEach(this::addRenderableWidget);
        addFooter(x, innerWidth);
        buildPage();
        if (restoreDeveloperFocus && minecraft != null && minecraft.screen == this) {
            setFocused(developer);
            restoreDeveloperFocus = false;
        }
    }

    private void clearPage() {
        rows.keySet().forEach(this::removeWidget);
        rows.clear();
        sliders.clear();
        enabled = null;
        sampling = null;
        contrast = null;
        motion = null;
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
        contentHeight = switch (selectedSection) {
            case GLASS -> sidePreview ? GLASS_SIDE_HEIGHT : GLASS_STACK_HEIGHT;
            case DISPLAY -> 68;
            case SHORTCUT -> 42;
        };
        contentWidth = panelWidth - 2 * PADDING - (maxScroll() == 0 ? 0 : SCROLLBAR_WIDTH + GAP);
        glassWidth = sidePreview ? contentWidth * 5 / 8 : contentWidth;
        switch (selectedSection) {
            case GLASS -> addGlassControls(x);
            case DISPLAY -> addDisplayControls(x);
            case SHORTCUT -> addShortcutControls(x);
        }
        scrollTo(sectionScroll[selectedSection.ordinal()]);
        refreshActions();
    }

    private void addGlassControls(int x) {
        int columnWidth = (glassWidth - GAP) / 2;
        int right = x + columnWidth + GAP;
        int rightWidth = glassWidth - columnWidth - GAP;
        enabled = toggle(x, 16, columnWidth, "enabled", value -> label("enabled", onOff(value)),
                value -> editBackdrop(draft.settings().backdrop().withEnabled(value)));
        enabled.setIcons(GraphicIcon.ENABLE, GraphicIcon.DISABLE);
        sampling = toggle(right, 16, rightWidth, "sampling",
                value -> label("sampling", label(value ? "sampling.dense" : "sampling.standard")),
                value -> editBackdrop(draft.settings().backdrop().withDenseSampling(value)));
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
        widgetBlur = toggle(x, 42, contentWidth, "widget_blur",
                value -> label("widget_blur", onOff(value)), value -> {
                    var settings = draft.settings();
                    editDisplay(settings.highContrast(), settings.reducedMotion(), value);
                });
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
        edit(new Draft(new ClientUiSettings(draft.settings().backdrop(), highContrast, reducedMotion, widgetBackdropBlur),
                draft.shortcut()));
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
        refreshActions();
    }

    private void refreshActions() {
        var settings = draft.settings();
        var nextPreferences = UiPreferences.fromSettings(settings);
        if (!preferences.equals(nextPreferences)) {
            preferences = nextPreferences;
            for (GuiEventListener listener : children()) {
                if (listener instanceof UiButton button) button.setPreferences(preferences);
                else if (listener instanceof UiSlider slider) slider.setPreferences(preferences);
            }
        }
        switch (selectedSection) {
            case GLASS -> {
                var backdrop = settings.backdrop();
                enabled.setValue(backdrop.enabled());
                sampling.setValue(backdrop.denseSampling());
            }
            case DISPLAY -> {
                contrast.setValue(settings.highContrast());
                motion.setValue(settings.reducedMotion());
                widgetBlur.setValue(settings.widgetBackdropBlur() && !settings.highContrast());
                widgetBlur.button().active = !settings.highContrast();
                if (settings.highContrast()) {
                    widgetBlur.button().setMessage(label("widget_blur.suspended"));
                }
            }
            case SHORTCUT -> {
                shortcut.setMessage(capturingShortcut ? label("shortcut.capture") : label("shortcut.binding",
                        draft.shortcut().displayName(keyMapping)));
                shortcut.setSelected(capturingShortcut);
                clearShortcut.active = !capturingShortcut && !draft.shortcut().key().equals(InputConstants.UNKNOWN);
            }
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
        if (entrance.closing()) return true;
        if (key != GLFW.GLFW_KEY_ESCAPE) entrance.finish();
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
        if (fits && maxScroll() > 0 && (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN)) {
            scrollTo(scroll + (key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1) * (bodyBottom - bodyTop - GAP));
            return true;
        }
        if (key != GLFW.GLFW_KEY_ESCAPE) revealFocus(getFocused());
        boolean handled = super.keyPressed(key, scanCode, modifiers);
        applyRequestedSection();
        return handled;
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
        sectionScroll[selectedSection.ordinal()] = scroll;
        rows.forEach((widget, y) -> widget.setY(bodyTop + y - scroll));
    }

    private void applyRequestedSection() {
        Section next = requestedSection;
        requestedSection = null;
        if (next == null || next == selectedSection) return;
        selectedSection = next;
        setDragging(false);
        clearPage();
        buildPage();
        var tab = sections.buttons().get(next.ordinal());
        startPageMorph(tab, PageTransition.nowMs());
        setFocused(tab);
    }

    private void startPageMorph(UiButton tab, long now) {
        float center = tab.getX() + tab.getWidth() * 0.5f;
        pageMorph.sample(now, preferences);
        if (preferences.reducedMotion()) {
            pageOrigin.snapTo(center);
        } else if (!pageMorph.running()) {
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
        if (rows.containsKey(listener)) pageMorph.finish();
    }

    private void revealFocus(GuiEventListener listener) {
        if (!fits || !(listener instanceof AbstractWidget widget)) return;
        Integer offset = rows.get(widget);
        if (offset == null) return;
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
        if (fits && entrance.active()) return false;
        if (capturingShortcut) {
            finishShortcut(new Shortcut(InputConstants.Type.MOUSE.getOrCreate(button),
                    modifierName(hasControlDown(), hasShiftDown(), hasAltDown())));
            return true;
        }
        boolean inBody = inBody(x, y);
        if (button == 0 && inBody && visiblePage != null && maxScroll() > 0 && x >= scrollbarX() - 3) {
            int thumbTop = thumbTop();
            scrollGrab = y >= thumbTop && y < thumbTop + thumbHeight() ? (int) y - thumbTop : thumbHeight() / 2;
            dragScrollbar(y);
            return true;
        }
        GuiEventListener clicked = null;
        for (GuiEventListener child : children()) {
            if (rows.containsKey(child) && (visiblePage == null || !visiblePage.contains(x, y))) continue;
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
        if (button == 0 && isDragging() && getFocused() instanceof UiSlider slider) {
            setDragging(false);
            return slider.mouseReleased(x, y, button);
        }
        return super.mouseReleased(x, y, button);
    }

    // Forge 与 NeoForge 分别使用单轴和双轴滚轮签名。
    public boolean mouseScrolled(double x, double y, double delta) {
        if (entrance.active() || !inBody(x, y) || maxScroll() <= 0 || delta == 0 || isDragging() || scrollGrab >= 0) {
            return false;
        }
        scrollTo(scroll - (int) Math.round(delta * 26));
        return true;
    }

    public boolean mouseScrolled(double x, double y, double deltaX, double deltaY) {
        return mouseScrolled(x, y, deltaY);
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
        if (fits && !preferences.reducedMotion()) returning.prepare(parent, width, height);
        entrance.close(preferences, () -> minecraft.setScreen(parent));
        if (!fits) entrance.finish();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void prepareReturnLayer() {
        restoreDeveloperFocus = true;
        if (fits) entrance.startAt(width * 0.5f, top + 5f);
    }

    // NeoForge 由 Screen.render 调用，Forge 1.20.1 使用不同签名。
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (entrance.closing()) returning.render(graphics, width, height, partialTick, preferences);
        else graphics.fillGradient(0, 0, width, height, 0xBA101827, 0xE20B101B);
        if (!fits) {
            var lines = font.split(Component.translatable("sh_better_ui.picker.too_small"), Math.max(1, width - 8));
            int y = 6;
            for (var line : lines) {
                if (y + font.lineHeight > height - 27) break;
                graphics.drawCenteredString(font, line, width / 2, y, LiquidGlassTheme.TEXT);
                y += font.lineHeight;
            }
        } else {
            var panel = new Bounds(left, top, panelWidth, panelHeight);
            if (entrance.render(graphics, panel, preferences, () -> renderContent(graphics, -1, -1, partialTick))) return;
            LiquidGlassTheme.popover(graphics, left, top, panelWidth, panelHeight, preferences);
            renderContent(graphics, mouseX, mouseY, partialTick);
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = left + PADDING;
        graphics.drawString(font, title, x, top + 15, LiquidGlassTheme.TEXT, false);
        renderBody(graphics, x, mouseX, mouseY, partialTick);
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
                        BODY_RADIUS, 0f, 0, GuiMotion.lerpColor(surface, DarkModeTheme.BACKGROUND, 0.24f));
                float opacity = GuiRenderUtil.easeOut((progress - 0.54f) / 0.44f);
                renderSkeleton(graphics, viewport, x, now, (1f - opacity) * 0.42f);
            }
            RoundedCorners.draw(graphics, rect.getX(), rect.getY(), rect.getW(), rect.getH(),
                    frame.radius(), 1f, edge, surface);
            renderMorphContents(graphics, viewport, frame, x, mouseX, mouseY, partialTick, now, progress, surface);
        } finally {
            graphics.disableScissor();
        }
        if (visiblePage != null && maxScroll() > 0) {
            int trackX = scrollbarX();
            int thumbY = thumbTop();
            graphics.fill(trackX + 1, bodyTop, trackX + SCROLLBAR_WIDTH - 1, bodyBottom, 0x544A5565);
            graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight(), LiquidGlassTheme.TEXT_MUTED);
        }
    }

    private void renderMorphContents(GuiGraphics graphics, Bounds viewport, FluidMorph frame, int x, int mouseX,
                                     int mouseY, float partialTick, long now, float progress, int surface) {
        var rect = frame.rect();
        float inset = frame.radius() * 0.4f * (1f - progress);
        int left = Math.max(viewport.x(), (int) Math.ceil(rect.getX() + inset));
        int top = Math.max(viewport.y(), (int) Math.ceil(rect.getY() + inset));
        int right = Math.min(viewport.right(), (int) Math.floor(rect.getX() + rect.getW() - inset));
        int bottom = Math.min(viewport.bottom(), (int) Math.floor(rect.getY() + rect.getH() - inset));
        var clip = new Bounds(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
        float opacity = GuiRenderUtil.easeOut((progress - 0.54f) / 0.44f);
        visiblePage = opacity >= 0.9f ? clip : null;
        if (clip.width() == 0 || clip.height() == 0) return;
        graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        try {
            if (opacity > 0f) {
                boolean hover = visiblePage != null && visiblePage.contains(mouseX, mouseY);
                renderPage(graphics, x, hover ? mouseX : -1, hover ? mouseY : -1, partialTick);
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
        graphics.drawString(font, selectedSection.tab.label(), x, origin + 4, LiquidGlassTheme.ACCENT, false);
        if (selectedSection == Section.GLASS) renderPreview(graphics, x, origin);
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
        SHORTCUT("shortcut", GraphicIcon.KEYBOARD);

        private final UiSectionBar.Section tab;

        Section(String key, GraphicIcon icon) {
            tab = new UiSectionBar.Section(label("section." + key), icon);
        }
    }

    private record Draft(ClientUiSettings settings, Shortcut shortcut) {}

    private record Shortcut(InputConstants.Key key, String modifier) {
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
