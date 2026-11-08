package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.blur.GaussBlur;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiBottomSheet;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiCapsuleReveal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiScreenReturn;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.Supplier;

import static com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen.label;

/** 山海：在独立视口试用抽屉手势和减少动效。 */
final class DeveloperBottomSheetScreen extends Screen {
    private static final int PADDING = 12;
    private static final int GAP = 6;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BACK_WIDTH = 72;
    private static final float[] HEIGHTS = {0.25f, 0.47f, 0.70f};
    private static final List<UiSectionBar.Section> DETENTS = List.of(
            new UiSectionBar.Section(label("demo.bottom_sheet.low"), GraphicIcon.EXPAND),
            new UiSectionBar.Section(label("demo.bottom_sheet.middle"), GraphicIcon.LIST),
            new UiSectionBar.Section(label("demo.bottom_sheet.high"), GraphicIcon.COLLAPSE));
    private static final Component BACK = Component.translatable("gui.back");
    private static final Component GUIDE = label("demo.bottom_sheet.guide");
    private static final Component TOO_SMALL = Component.translatable("sh_better_ui.picker.too_small");

    static {
        GaussBlur.allowScreen(screen -> screen instanceof DeveloperBottomSheetScreen);
    }

    private final DeveloperPanelScreen parent;
    private final UiBottomSheet sheet = UiBottomSheet.capsule(0, HEIGHTS);
    private final UiCapsuleReveal entrance = new UiCapsuleReveal();
    private final UiScreenReturn returning = new UiScreenReturn(this);
    private UiPreferences preferences;
    private UiBottomSheet.Frame frame;
    private Bounds panel;
    private Bounds stage;
    private Bounds viewport;
    private List<FormattedCharSequence> guide;
    private UiSectionBar detents;
    private UiToggleButton motion;
    private UiButton back;
    private int displayedAnchor;

    DeveloperBottomSheetScreen(DeveloperPanelScreen parent, UiPreferences preferences, Supplier<UiButton> source) {
        super(label("demo.bottom_sheet"));
        this.parent = parent;
        this.preferences = preferences;
        entrance.startFrom(source);
        returning.beginHandoff(parent, preferences);
    }

    @Override
    protected void init() {
        frame = null;
        panel = null;
        if (width >= 224 && height >= 200) {
            int panelWidth = Math.min(440, width - 16);
            int panelHeight = Math.min(360, height - 16);
            int left = (width - panelWidth) / 2;
            int top = (height - panelHeight) / 2;
            int x = left + PADDING;
            int contentWidth = panelWidth - 2 * PADDING;
            guide = font.split(GUIDE, contentWidth);
            int detentY = top + PADDING + (guide.size() + 1) * font.lineHeight + 2 * GAP;
            int footerY = top + panelHeight - PADDING - BUTTON_HEIGHT;
            int stageY = detentY + BUTTON_HEIGHT + GAP;
            int stageHeight = footerY - font.lineHeight - 2 * GAP - stageY;
            if (stageHeight >= 64) {
                panel = new Bounds(left, top, panelWidth, panelHeight);
                stage = new Bounds(x, stageY, contentWidth, stageHeight);
                viewport = new Bounds(x + 12, stageY + 4, contentWidth - 17, stageHeight - 8);
                addDemoControls(x, detentY, footerY, contentWidth);
                return;
            }
        }
        entrance.finish();
        int buttonWidth = Math.min(BACK_WIDTH, width - 8);
        if (buttonWidth >= 20 && height >= BUTTON_HEIGHT + 8) {
            addRenderableWidget(DeveloperPanelScreen.button((width - buttonWidth) / 2, height - BUTTON_HEIGHT - 4,
                    buttonWidth, BUTTON_HEIGHT, BACK, GraphicIcon.BACK, preferences, this::onClose));
        }
    }

    private void addDemoControls(int x, int detentY, int footerY, int contentWidth) {
        displayedAnchor = sheet.targetAnchor();
        detents = new UiSectionBar(x, detentY, contentWidth, BUTTON_HEIGHT, preferences, DETENTS, displayedAnchor,
                anchor -> sheet.animateTo(anchor, PageTransition.nowMs()));
        detents.buttons().forEach(this::addRenderableWidget);
        var motionBounds = new Bounds(x, footerY, contentWidth - BACK_WIDTH - GAP, BUTTON_HEIGHT);
        motion = new UiToggleButton(motionBounds, preferences.reducedMotion(), preferences, UiStyle.LIQUID_GLASS,
                enabled -> Component.translatable("sh_better_ui.panel.motion",
                        Component.translatable(enabled ? "options.on" : "options.off")), this::setReducedMotion);
        motion.button().setSampleBackground(false);
        motion.button().setPreview(label("demo.bottom_sheet.motion_tip"));
        addRenderableWidget(motion.button());
        back = addRenderableWidget(DeveloperPanelScreen.button(x + contentWidth - BACK_WIDTH, footerY,
                BACK_WIDTH, BUTTON_HEIGHT, BACK, GraphicIcon.BACK, preferences, this::onClose));
    }

    private void setReducedMotion(boolean enabled) {
        preferences = new UiPreferences(preferences.highContrast(), enabled, preferences.backgroundBlur());
        motion.setValue(enabled);
        motion.button().setPreferences(preferences);
        detents.setPreferences(preferences);
        back.setPreferences(preferences);
    }

    // NeoForge 由 Screen.render 调用，Forge 1.20.1 使用不同签名。
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderScreen(graphics, mouseX, mouseY, partialTick);
        returning.renderHandoff(graphics, preferences);
    }

    private void renderScreen(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (entrance.closing()) returning.renderBackdrop(graphics, width, height, partialTick);
        else if (minecraft.level == null) DarkModeTheme.background(graphics, 0, 0, width, height);
        else graphics.fill(0, 0, width, height, 0x6020252E);
        if (panel == null) {
            renderTooSmall(graphics);
        } else {
            if (returning.renderTransition(entrance, graphics, panel, preferences, () -> {
                boolean pointer = entrance.allowsPointer(mouseX, mouseY);
                renderContent(graphics, pointer ? mouseX : -1, pointer ? mouseY : -1, partialTick);
            })) return;
            LiquidGlassTheme.popover(graphics, panel.x(), panel.y(), panel.width(), panel.height(), preferences);
            renderContent(graphics, mouseX, mouseY, partialTick);
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTooSmall(GuiGraphics graphics) {
        int y = PADDING;
        for (var line : font.split(TOO_SMALL, Math.max(1, width - 2 * PADDING))) {
            if (y + font.lineHeight > height - BUTTON_HEIGHT - 6) break;
            graphics.drawString(font, line, PADDING, y, LiquidGlassTheme.TEXT, false);
            y += font.lineHeight;
        }
    }

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int y = panel.y() + PADDING;
        graphics.drawString(font, getTitle(), stage.x(), y, LiquidGlassTheme.TEXT, false);
        y += font.lineHeight + GAP;
        for (var line : guide) {
            graphics.drawString(font, line, stage.x(), y, LiquidGlassTheme.TEXT, false);
            y += font.lineHeight;
        }

        RoundedCorners.draw(graphics, stage.x(), stage.y(), stage.width(), stage.height(), 5, 1,
                preferences.highContrast() ? DarkModeTheme.TEXT : DarkModeTheme.BORDER, DarkModeTheme.BACKGROUND);
        int selected = sheet.targetAnchor();
        for (int index = 0; index < HEIGHTS.length; index++) {
            int stopY = viewport.bottom() - Math.round(viewport.height() * HEIGHTS[index]);
            int color = index == selected ? LiquidGlassTheme.ACCENT : DarkModeTheme.BORDER_HOVER;
            graphics.fill(stage.x() + 4, stopY, viewport.x() - 2, stopY + 1, color);
        }
        frame = sheet.render(graphics, viewport, mouseX, mouseY, PageTransition.nowMs(), preferences,
                this::renderSheetContent);
        String state = frame.dragging() ? "preview" : frame.settling() ? "settling" : "current";
        Component status = label("demo.bottom_sheet." + state, DETENTS.get(frame.targetAnchor()).label());
        graphics.drawString(font, status, stage.x(), stage.bottom() + GAP, LiquidGlassTheme.TEXT, false);
        if (selected != displayedAnchor) {
            displayedAnchor = selected;
            detents.select(selected);
            minecraft.getNarrator().say(status);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderSheetContent(GuiGraphics graphics, Bounds content) {
        if (content.height() >= font.lineHeight + GAP) {
            graphics.drawString(font, label("demo.bottom_sheet.content"), content.x() + GAP, content.y() + 2,
                    DarkModeTheme.TEXT_SECONDARY, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (entrance.closing()) {
            if (entrance.reopenFromSource(mouseX, mouseY, button)) returning.beginHandoff(this, preferences);
            return true;
        }
        return entrance.allowsPointer(mouseX, mouseY)
                && (sheet.beginCaptured(this, frame, button, mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button));
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (entrance.closing()) {
            if (entrance.reopenFromKey(key)) returning.beginHandoff(this, preferences);
            return true;
        }
        if (!entrance.allowsActivation(key, getFocused())) return true;
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (entrance.closing()) return;
        UiPointerCapture.cancel(this);
        if (panel != null && !preferences.reducedMotion()) {
            returning.beginHandoff(this, preferences);
            returning.prepare(parent, width, height);
        }
        entrance.close(preferences, () -> {
            minecraft.setScreen(parent);
            if (minecraft.screen == parent) parent.restoreReturnFocus();
        });
        if (panel == null) entrance.finish();
    }

    @Override
    public void removed() {
        returning.clearHandoff();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
