package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.blur.GaussBlur;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
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

/** 山海：在同一画面试用公共控件、检查渲染状态并复制接入示例。 */
public final class DeveloperPanelScreen extends Screen {
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
    private UiOverlayHost overlays;
    private DeveloperGalleryScreen.Section returnFocus;
    private UiButton controls;
    private UiButton materials;
    private int panelWidth;
    private int left;
    private int top;
    private boolean compact;
    private boolean copied;

    public DeveloperPanelScreen(Screen parent) {
        this(parent, UiPreferences.configured());
    }

    public DeveloperPanelScreen(Screen parent, UiPreferences preferences) {
        super(label("title"));
        this.parent = parent;
        this.preferences = preferences;
        this.overlays = new UiOverlayHost(this, preferences);
    }

    @Override
    protected void init() {
        controls = null;
        materials = null;
        compact = width < 248 || height < PANEL_HEIGHT + 8;
        if (compact) {
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

        int contentX = left + PADDING;
        int contentWidth = panelWidth - 2 * PADDING;
        int demoWidth = (contentWidth - GAP) / 2;
        controls = addButton(contentX, top + 25, demoWidth, label("gallery.controls"), GraphicIcon.INTERACTION,
                () -> openGallery(DeveloperGalleryScreen.Section.CONTROLS));
        materials = addButton(contentX + demoWidth + GAP, top + 25, contentWidth - demoWidth - GAP,
                label("gallery.materials"), GraphicIcon.TEMPLATE,
                () -> openGallery(DeveloperGalleryScreen.Section.MATERIALS));
        addRenderableWidget(button(contentX, top + DEMOS_Y + 13, demoWidth, BUTTON_HEIGHT,
                label("demo.dialog"), GraphicIcon.WARNING, preferences,
                pressed -> showDialog(overlays, (UiButton) pressed, this::showToast)));
        addButton(contentX + demoWidth + GAP, top + DEMOS_Y + 13, contentWidth - demoWidth - GAP,
                label("demo.toast"), GraphicIcon.INFO, this::showToast);

        int copyX = contentX + contentWidth - COPY_WIDTH;
        addButton(contentX, top + EXAMPLES_Y + 13, demoWidth, label("example.copy_dialog"),
                null, () -> copy(DIALOG_CODE));
        addButton(contentX + demoWidth + GAP, top + EXAMPLES_Y + 13, contentWidth - demoWidth - GAP,
                label("example.copy_toast"),
                null, () -> copy(TOAST_CODE));
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

    private void openGallery(DeveloperGalleryScreen.Section section) {
        returnFocus = section;
        minecraft.setScreen(new DeveloperGalleryScreen(this, preferences, section));
    }

    void restoreGalleryFocus() {
        overlays = new UiOverlayHost(this, preferences);
        if (returnFocus == null) return;
        UiButton target = returnFocus == DeveloperGalleryScreen.Section.CONTROLS ? controls : materials;
        returnFocus = null;
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

    private void copy(String code) {
        minecraft.keyboardHandler.setClipboard(code);
        copied = true;
        minecraft.getNarrator().say(label("copied"));
    }

    // NeoForge 由 Screen.render 调用，Forge 1.20.1 使用不同签名。
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) {
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
            renderPanel(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPanel(GuiGraphics graphics) {
        LiquidGlassTheme.popover(graphics, left, top, panelWidth, PANEL_HEIGHT, preferences);
        int x = left + PADDING;
        graphics.drawString(font, getTitle(), x, top + 11, LiquidGlassTheme.TEXT, false);
        graphics.drawString(font, label("demos"), x, top + DEMOS_Y, LiquidGlassTheme.ACCENT, false);
        graphics.drawString(font, label("diagnostics"), x, top + DIAGNOSTICS_Y, LiquidGlassTheme.ACCENT, false);
        renderDiagnostics(graphics, x, top + DIAGNOSTICS_Y + 13);
        graphics.drawString(font, label("examples"), x, top + EXAMPLES_Y, LiquidGlassTheme.ACCENT, false);
        if (copied) {
            graphics.drawString(font, label("copied"), x, top + FOOTER_Y + 7, LiquidGlassTheme.ACCENT, false);
        }
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
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
