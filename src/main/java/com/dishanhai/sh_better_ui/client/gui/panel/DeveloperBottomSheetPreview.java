package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiBottomSheet;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSectionBar;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen.label;

/** 山海：用通用预览宿主试用抽屉拖动、档位和减少动效。 */
final class DeveloperBottomSheetPreview implements PreviewScene {
    static final String ID = "sh_better_ui:bottom_sheet";
    private static final String TITLE_KEY = "sh_better_ui.panel.developer.demo.bottom_sheet";
    private static final int GAP = 6;
    private static final int BUTTON_HEIGHT = 22;
    private static final int MAX_STAGE_HEIGHT = 192;
    private static final float[] HEIGHTS = {0.25f, 0.47f, 0.70f};
    private static final List<UiSectionBar.Section> DETENTS = List.of(
            new UiSectionBar.Section(label("demo.bottom_sheet.low"), GraphicIcon.EXPAND),
            new UiSectionBar.Section(label("demo.bottom_sheet.middle"), GraphicIcon.LIST),
            new UiSectionBar.Section(label("demo.bottom_sheet.high"), GraphicIcon.COLLAPSE));
    private static final Component GUIDE = label("demo.bottom_sheet.guide");
    private static final Component HANDLE_USAGE = label("demo.bottom_sheet.handle_usage");
    private static final Component TOO_SMALL = Component.translatable("sh_better_ui.picker.too_small");

    private final UiBottomSheet sheet = UiBottomSheet.capsule(0, HEIGHTS);
    private PreviewContext host;
    private UiPreferences preferences;
    private UiBottomSheet.Frame sheetFrame;
    private Bounds stage;
    private Bounds sheetViewport;
    private List<FormattedCharSequence> guide;
    private UiSectionBar detents;
    private UiToggleButton motion;
    private int displayedAnchor;

    static void registerInto(PreviewRegistry registry) {
        registry.register(new PreviewSpec(ID, PreviewSpec.Category.CONTROLS, 1000, TITLE_KEY,
                110, 0, "", DeveloperBottomSheetPreview::new));
    }

    @Override
    public void layout(PreviewContext host) {
        this.host = host;
        if (preferences == null) preferences = host.preferences();
        suspend();
        stage = null;
        var area = host.viewport();
        var font = host.font();
        int width = Math.min(416, area.width() - 2 * GAP);
        if (width < 176) return;

        guide = font.split(GUIDE, width);
        int guideHeight = guide.size() * font.lineHeight + GAP;
        int statusHeight = font.lineHeight + GAP;
        int available = area.height() - 2 * GAP;
        boolean stacked = available >= guideHeight + statusHeight + 2 * BUTTON_HEIGHT + 2 * GAP + 64;
        if (!stacked) {
            guide = List.of();
            guideHeight = 0;
        }
        int controlsHeight = stacked ? 2 * BUTTON_HEIGHT + 2 * GAP : BUTTON_HEIGHT + GAP;
        int stageHeight = Math.min(MAX_STAGE_HEIGHT, available - guideHeight - statusHeight - controlsHeight);
        if (stageHeight < 36) return;

        int height = guideHeight + controlsHeight + stageHeight + statusHeight;
        int x = area.x() + (area.width() - width) / 2;
        int controlsY = area.y() + (area.height() - height) / 2 + guideHeight;
        int stageY = controlsY + BUTTON_HEIGHT + GAP;
        stage = new Bounds(x, stageY, width, stageHeight);
        sheetViewport = new Bounds(x + 12, stageY + 4, width - 17, stageHeight - 8);
        int motionWidth = stacked ? width : Math.min(110, width / 3);
        int detentWidth = stacked ? width : width - motionWidth - GAP;
        displayedAnchor = sheet.targetAnchor();
        detents = new UiSectionBar(x, controlsY, detentWidth, BUTTON_HEIGHT, preferences, DETENTS, displayedAnchor,
                anchor -> sheet.animateTo(anchor, PageTransition.nowMs()));
        detents.buttons().forEach(host::add);

        var motionBounds = stacked ? new Bounds(x, stage.bottom() + statusHeight + GAP, width, BUTTON_HEIGHT)
                : new Bounds(x + detentWidth + GAP, controlsY, motionWidth, BUTTON_HEIGHT);
        motion = new UiToggleButton(motionBounds, preferences.reducedMotion(), preferences, UiStyle.LIQUID_GLASS,
                enabled -> Component.translatable("sh_better_ui.panel.motion",
                        Component.translatable(enabled ? "options.on" : "options.off")), this::setReducedMotion);
        motion.button().setSampleBackground(false);
        motion.button().setPreview(label("demo.bottom_sheet.motion_tip"));
        host.add(motion.button());
        host.add(new SheetHandle(stage));
    }

    private void setReducedMotion(boolean enabled) {
        preferences = new UiPreferences(preferences.highContrast(), enabled, preferences.backgroundBlur());
        motion.setValue(enabled);
        motion.button().setPreferences(preferences);
        detents.setPreferences(preferences);
    }

    @Override
    public void suspend() {
        sheet.cancelGesture(PageTransition.nowMs());
        sheetFrame = null;
    }

    @Override
    public void render(UiFrame frame) {
        var graphics = frame.graphics();
        var font = host.font();
        if (stage == null) {
            var area = frame.viewport();
            graphics.drawWordWrap(font, TOO_SMALL, area.x() + GAP, area.y() + GAP,
                    Math.max(1, area.width() - 2 * GAP), DarkModeTheme.textColor());
            return;
        }

        int y = stage.y() - BUTTON_HEIGHT - 2 * GAP - guide.size() * font.lineHeight;
        for (var line : guide) {
            graphics.drawString(font, line, stage.x(), y, LiquidGlassTheme.textColor(), false);
            y += font.lineHeight;
        }

        RoundedCorners.draw(graphics, stage.x(), stage.y(), stage.width(), stage.height(), 5, 1,
                preferences.highContrast() ? DarkModeTheme.textColor() : DarkModeTheme.borderColor(),
                DarkModeTheme.backgroundColor());
        int selected = sheet.targetAnchor();
        for (int index = 0; index < HEIGHTS.length; index++) {
            int stopY = sheetViewport.bottom() - Math.round(sheetViewport.height() * HEIGHTS[index]);
            int color = index == selected ? LiquidGlassTheme.accentColor() : DarkModeTheme.hoverBorderColor();
            graphics.fill(stage.x() + 4, stopY, sheetViewport.x() - 2, stopY + 1, color);
        }
        boolean interactive = host.interactive() && frame.mouse().available();
        sheetFrame = sheet.render(graphics, sheetViewport, interactive ? (int) frame.mouse().x() : -1,
                interactive ? (int) frame.mouse().y() : -1, frame.nowMs(), preferences, this::renderSheetContent);
        String state = sheetFrame.dragging() ? "preview" : sheetFrame.settling() ? "settling" : "current";
        Component status = label("demo.bottom_sheet." + state, DETENTS.get(sheetFrame.targetAnchor()).label());
        graphics.drawString(font, status, stage.x(), stage.bottom() + 2, LiquidGlassTheme.textColor(), false);
        if (selected != displayedAnchor) {
            displayedAnchor = selected;
            detents.select(selected);
            if (host.interactive()) Minecraft.getInstance().getNarrator().say(status);
        }
    }

    private void renderSheetContent(GuiGraphics graphics, Bounds content) {
        var font = host.font();
        if (content.height() >= font.lineHeight + GAP) {
            graphics.drawString(font, label("demo.bottom_sheet.content"), content.x() + GAP, content.y() + 2,
                    DarkModeTheme.secondaryTextColor(), false);
        }
    }

    private final class SheetHandle extends AbstractWidget {
        private SheetHandle(Bounds area) {
            super(area.x(), area.y(), area.width(), area.height(), Component.translatable(TITLE_KEY));
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            return active && visible && host.interactive() && sheet.beginCaptured(host.screen(), sheetFrame, button, x, y);
        }

        @Override
        public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (!isFocused() || !active || !visible || !host.interactive()) return false;
            int step = switch (key) {
                case GLFW.GLFW_KEY_UP -> 1;
                case GLFW.GLFW_KEY_DOWN -> -1;
                default -> 0;
            };
            if (step == 0) return false;
            sheet.animateTo(Math.clamp(sheet.targetAnchor() + step, 0, HEIGHTS.length - 1), PageTransition.nowMs());
            return true;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (isFocused() && sheetFrame != null) {
                var handle = sheetFrame.handle();
                graphics.renderOutline(handle.x() - 1, handle.y() - 1, handle.width() + 2, handle.height() + 2,
                        LiquidGlassTheme.textColor());
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, createNarrationMessage());
            output.add(NarratedElementType.USAGE, HANDLE_USAGE);
        }
    }
}
