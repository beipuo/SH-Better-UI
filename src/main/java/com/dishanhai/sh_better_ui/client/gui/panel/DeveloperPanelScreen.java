package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.blur.GaussBlur;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCapsuleReveal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScreenReturn;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiConfirmation;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToast;
import com.dishanhai.sh_better_ui.config.GlassBackdropSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;

/** 山海：在同一画面试用公共控件、检查渲染状态并复制接入示例。 */
public final class DeveloperPanelScreen extends Screen implements UiScreenReturn.Target {
    private static final int PANEL_HEIGHT = 218;
    private static final int PADDING = 12;
    private static final int GAP = 6;
    private static final int BUTTON_HEIGHT = 22;
    private static final int COPY_WIDTH = 86;
    private static final int DEMOS_Y = 55;
    private static final int DIAGNOSTICS_Y = 97;
    private static final int EXAMPLES_Y = 146;
    private static final int FOOTER_Y = 186;
    private static final Component BACK = Component.translatable("gui.back");
    private static final Component TOO_SMALL = Component.translatable("sh_better_ui.picker.too_small");
    static final String DIALOG_CODE = """
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiConfirmation;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
            import net.minecraft.network.chat.CommonComponents;
            import net.minecraft.network.chat.Component;

            private final UiOverlayHost host = new UiOverlayHost(this, UiPreferences.configured());

            private void confirm(UiButton source, Component title, Component details, Runnable onConfirm) {
                var prompt = new UiConfirmation.Prompt(title, details,
                        CommonComponents.GUI_OK, CommonComponents.GUI_CANCEL);
                host.showDialogFrom(source, prompt, onConfirm);
            }
            """;
    static final String TOAST_CODE = """
            import com.dishanhai.sh_better_ui.client.gui.scaled.UiToast;
            import net.minecraft.network.chat.Component;

            private void showNotice(Component title, Component details) {
                UiToast.show(UiToast.Kind.INFO, title, details);
            }
            """;

    static {
        GaussBlur.allowScreen(screen -> screen instanceof DeveloperPanelScreen);
    }

    private final Screen parent;
    private final UiPreferences preferences;
    private final UiCapsuleReveal entrance = new UiCapsuleReveal();
    private final UiScreenReturn returning = new UiScreenReturn(this);
    private boolean entered;
    private UiOverlayHost overlays;
    private Supplier<UiButton> galleryEntry;
    private UiButton controls;
    private UiButton materials;
    private UiButton bottomSheet;
    private UiButton dialogExample;
    private UiButton toastExample;
    private int panelWidth;
    private int left;
    private int top;
    private boolean compact;

    public DeveloperPanelScreen(Screen parent) {
        this(parent, UiPreferences.configured());
    }

    public DeveloperPanelScreen(Screen parent, UiPreferences preferences) {
        this(parent, preferences, null);
    }

    DeveloperPanelScreen(Screen parent, UiPreferences preferences, UiButton source) {
        super(label("title"));
        this.parent = parent;
        this.preferences = preferences;
        this.overlays = new UiOverlayHost(this, preferences);
        if (source != null) {
            entrance.startFrom(source);
            entered = true;
        }
    }

    @Override
    protected void init() {
        controls = null;
        materials = null;
        bottomSheet = null;
        dialogExample = null;
        toastExample = null;
        compact = width < 248 || height < PANEL_HEIGHT + 8;
        if (compact) {
            entrance.finish();
            if (width >= 36 && height >= BUTTON_HEIGHT + 8) {
                int backWidth = Math.min(100, width - 8);
                addButton((width - backWidth) / 2, height - BUTTON_HEIGHT - 4,
                        backWidth, BACK, GraphicIcon.BACK, this::onClose);
            }
            return;
        }
        panelWidth = Math.min(width - 16, 440);
        left = (width - panelWidth) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        if (!entered) {
            entered = true;
            entrance.startAt(width * 0.5f, top + 5f);
        }

        int contentX = left + PADDING;
        int contentWidth = panelWidth - 2 * PADDING;
        int columnWidth = (contentWidth - GAP) / 2;
        controls = addButton(contentX, top + 25, columnWidth, label("gallery.controls"), GraphicIcon.INTERACTION,
                () -> openGallery(DeveloperGalleryScreen.Section.CONTROLS, null, () -> controls));
        materials = addButton(contentX + columnWidth + GAP, top + 25, contentWidth - columnWidth - GAP,
                label("gallery.materials"), GraphicIcon.TEMPLATE,
                () -> openGallery(DeveloperGalleryScreen.Section.MATERIALS, null, () -> materials));
        int demoY = top + DEMOS_Y + 13;
        int demoWidth = (contentWidth - 2 * GAP) / 3;
        int sheetX = contentX + 2 * (demoWidth + GAP);
        addRenderableWidget(button(contentX, demoY, demoWidth, BUTTON_HEIGHT,
                label("demo.dialog"), GraphicIcon.WARNING, preferences,
                pressed -> showDialog(overlays, (UiButton) pressed, this::showToast)));
        addButton(contentX + demoWidth + GAP, demoY, demoWidth, label("demo.toast"), GraphicIcon.INFO, this::showToast);
        bottomSheet = addButton(sheetX, demoY, contentX + contentWidth - sheetX, label("demo.bottom_sheet"),
                null, () -> minecraft.setScreen(new DeveloperBottomSheetScreen(this, preferences, bottomSheet)));

        int copyX = contentX + contentWidth - COPY_WIDTH;
        dialogExample = addButton(contentX, top + EXAMPLES_Y + 13, columnWidth, label("example.open_dialog"),
                null, () -> openGallery(DeveloperGalleryScreen.Section.CONTROLS, DeveloperSamples.DIALOG, () -> dialogExample));
        toastExample = addButton(contentX + columnWidth + GAP, top + EXAMPLES_Y + 13, contentWidth - columnWidth - GAP,
                label("example.open_toast"),
                null, () -> openGallery(DeveloperGalleryScreen.Section.CONTROLS, DeveloperSamples.TOAST, () -> toastExample));
        addButton(copyX, top + FOOTER_Y, COPY_WIDTH, BACK, GraphicIcon.BACK, this::onClose);
    }

    private UiButton addButton(int x, int y, int buttonWidth, Component text, GraphicIcon icon, Runnable action) {
        return addRenderableWidget(button(x, y, buttonWidth, BUTTON_HEIGHT, text, icon, preferences, action));
    }

    static UiButton button(int x, int y, int width, int height, Component text, GraphicIcon icon,
                           UiPreferences preferences, Runnable action) {
        return button(x, y, width, height, text, icon, preferences, ignored -> action.run());
    }

    static UiButton button(int x, int y, int width, int height, Component text, GraphicIcon icon,
                           UiPreferences preferences, Button.OnPress action) {
        var button = new UiButton(x, y, width, height, text, action, preferences, false, UiStyle.LIQUID_GLASS);
        button.setSampleBackground(false);
        if (icon != null) button.setLeadingIcon(icon);
        return button;
    }

    private void openGallery(DeveloperGalleryScreen.Section section, DeveloperSamples example, Supplier<UiButton> entry) {
        galleryEntry = entry;
        minecraft.setScreen(new DeveloperGalleryScreen(this, preferences, section, example, entry.get()));
    }

    void restoreGalleryFocus() {
        UiButton target = galleryEntry == null ? null : galleryEntry.get();
        galleryEntry = null;
        restoreFocus(target);
    }

    void restoreBottomSheetFocus() {
        restoreFocus(bottomSheet);
    }

    private void restoreFocus(UiButton target) {
        overlays = new UiOverlayHost(this, preferences);
        if (target != null) setInitialFocus(target);
    }

    static void showDialog(UiOverlayHost overlays, UiButton source, Runnable confirmed) {
        var prompt = new UiConfirmation.Prompt(label("demo.dialog"), label("demo.dialog_details"),
                CommonComponents.GUI_OK, CommonComponents.GUI_CANCEL);
        overlays.showDialogFrom(source, prompt, confirmed);
    }

    private void showToast() {
        showToast(preferences);
    }

    static void showToast(UiPreferences preferences) {
        UiToast.show(UiToast.Kind.INFO, label("demo.toast"), label("demo.toast_details"),
                UiToast.Effect.SLIDE_FROM_RIGHT, 2_500L, preferences);
    }

    // NeoForge 由 Screen.render 调用，Forge 1.20.1 使用不同签名。
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
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
            var panel = new Bounds(left, top, panelWidth, PANEL_HEIGHT);
            if (entrance.render(graphics, panel, preferences, () -> renderContent(graphics, -1, -1, partialTick))) return;
            LiquidGlassTheme.popover(graphics, left, top, panelWidth, PANEL_HEIGHT, preferences);
            renderContent(graphics, mouseX, mouseY, partialTick);
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = left + PADDING;
        graphics.drawString(font, getTitle(), x, top + 11, LiquidGlassTheme.TEXT, false);
        graphics.drawString(font, label("demos"), x, top + DEMOS_Y, LiquidGlassTheme.ACCENT, false);
        graphics.drawString(font, label("diagnostics"), x, top + DIAGNOSTICS_Y, LiquidGlassTheme.ACCENT, false);
        renderDiagnostics(graphics, x, top + DIAGNOSTICS_Y + 13);
        graphics.drawString(font, label("examples"), x, top + EXAMPLES_Y, LiquidGlassTheme.ACCENT, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return !entrance.active() && super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (entrance.closing()) return true;
        if (key != GLFW.GLFW_KEY_ESCAPE) entrance.finish();
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void renderDiagnostics(GuiGraphics graphics, int x, int y) {
        var window = minecraft.getWindow();
        var target = minecraft.getMainRenderTarget();
        Component enabled = Component.translatable(GlassBackdropSettings.current().enabled() ? "options.on" : "options.off");
        Component shader = label(GaussBlur.isReady() ? "shader_ready" : "shader_unavailable");
        graphics.drawString(font, label("gui", width, height, (int) window.getGuiScale()), x, y, LiquidGlassTheme.TEXT, false);
        graphics.drawString(font, label("framebuffer", target.width, target.height),
                x, y + 11, LiquidGlassTheme.TEXT, false);
        graphics.drawString(font, label("blur", enabled, shader), x, y + 22, LiquidGlassTheme.TEXT, false);
    }

    static Component label(String key, Object... args) {
        return Component.translatable("sh_better_ui.panel.developer." + key, args);
    }

    @Override
    public void onClose() {
        if (!compact && !preferences.reducedMotion()) returning.prepare(parent, width, height);
        entrance.close(preferences, () -> minecraft.setScreen(parent));
        if (compact) entrance.finish();
    }

    @Override
    public void prepareReturnLayer() {
        if (!compact) entrance.startAt(width * 0.5f, top + 5f);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
