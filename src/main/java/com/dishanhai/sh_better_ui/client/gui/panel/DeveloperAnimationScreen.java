package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.api.UiScreenBuilder;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.GlassEffectMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation.Policy;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.Supplier;

import static com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen.label;

/** 山海：在同一预览中反复打断位移和透明度轨道，开关始终可以操作。 */
final class DeveloperAnimationScreen {
    private static final int GAP = 6;
    private static final int BUTTON_HEIGHT = 22;
    private static final int STAGE_HEIGHT = 68;
    private static final int PANEL_PRIORITY = 30;
    private static final Component TITLE = label("demo.animation");
    private static final Component GUIDE = label("demo.animation.guide");
    private static final Component TOO_SMALL = Component.translatable("sh_better_ui.picker.too_small");
    private static final Component OPEN = label("demo.animation.open");
    private static final Component CLOSE = label("demo.animation.close");
    private static final Component EXPANDED = label("demo.animation.expanded");
    private static final Component COLLAPSED = label("demo.animation.collapsed");

    private UiPreferences preferences;
    private InterruptibleAnimation position;
    private InterruptibleAnimation opacity;
    private Bounds stage;
    private List<FormattedCharSequence> guide;
    private UiToggleButton reveal;
    private UiToggleButton motion;
    private int displayedProgress = -1;
    private boolean displayedOpen;
    private Component status;

    private DeveloperAnimationScreen(UiPreferences preferences) {
        this.preferences = preferences;
    }

    static void open(DeveloperPanelScreen parent, UiPreferences preferences, Supplier<UiButton> source) {
        var demo = new DeveloperAnimationScreen(preferences);
        new UiScreenBuilder(TITLE).parent(parent).returnFocus(source)
                .onInit(demo::init).onRender((screen, frame) -> demo.render(frame))
                .onRemoved(screen -> {
                    demo.position = null;
                    demo.opacity = null;
                }).open();
    }

    private void init(UiScreen screen) {
        if (position == null) {
            position = screen.context().animation(Spring.FLUID_PANEL, 0f);
            opacity = screen.context().animation(Spring.PANEL, 0f);
        }
        stage = null;
        reveal = null;
        motion = null;
        Bounds viewport = screen.viewport();
        int width = Math.min(360, viewport.width() - 24);
        if (width < 140) return;
        Font font = Minecraft.getInstance().font;
        guide = font.split(GUIDE, width);
        int height = (guide.size() + 1) * font.lineHeight + STAGE_HEIGHT + 2 * BUTTON_HEIGHT + 4 * GAP;
        if (height > viewport.height() - 16) return;
        int x = viewport.x() + (viewport.width() - width) / 2;
        int y = viewport.y() + (viewport.height() - height) / 2 + guide.size() * font.lineHeight + GAP;
        stage = new Bounds(x, y, width, STAGE_HEIGHT);
        int controlsY = stage.bottom() + font.lineHeight + 2 * GAP;
        reveal = new UiToggleButton(new Bounds(x, controlsY, width, BUTTON_HEIGHT),
                position.target() == 1f, preferences, UiStyle.LIQUID_GLASS, open -> open ? CLOSE : OPEN,
                this::setOpen);
        motion = new UiToggleButton(new Bounds(x, controlsY + BUTTON_HEIGHT + GAP, width, BUTTON_HEIGHT),
                preferences.reducedMotion(), preferences, UiStyle.LIQUID_GLASS,
                reduced -> Component.translatable("sh_better_ui.panel.motion",
                        Component.translatable(reduced ? "options.on" : "options.off")), this::setReducedMotion);
        reveal.button().setSampleBackground(false);
        motion.button().setSampleBackground(false);
        motion.button().setPreview(label("demo.animation.motion_tip"));
        screen.add(reveal.button());
        screen.add(motion.button());
        screen.setFocused(reveal.button());
    }

    private void setOpen(boolean open) {
        long now = PageTransition.nowMs();
        float target = open ? 1f : 0f;
        position.transitionTo(target, now, Policy.REVERSE, PANEL_PRIORITY);
        opacity.blendTo(target, now, PANEL_PRIORITY, 300L, InterruptibleAnimation.BlendCurve.SMOOTHSTEP);
        reveal.setValue(open);
    }

    private void setReducedMotion(boolean reduced) {
        preferences = new UiPreferences(preferences.highContrast(), reduced, preferences.backgroundBlur());
        motion.setValue(reduced);
        motion.button().setPreferences(preferences);
        reveal.button().setPreferences(preferences);
        if (reduced) {
            position.finish();
            opacity.finish();
        }
    }

    private void render(UiFrame frame) {
        var graphics = frame.graphics();
        Bounds viewport = frame.viewport();
        var font = Minecraft.getInstance().font;
        DarkModeTheme.background(graphics, viewport.x(), viewport.y(), viewport.width(), viewport.height());
        if (stage == null) {
            graphics.drawWordWrap(font, TOO_SMALL, viewport.x() + GAP, viewport.y() + GAP,
                    Math.max(1, viewport.width() - 2 * GAP), DarkModeTheme.TEXT);
            return;
        }

        int guideY = stage.y() - guide.size() * font.lineHeight - GAP;
        for (var line : guide) {
            graphics.drawString(font, line, stage.x(), guideY, DarkModeTheme.TEXT, false);
            guideY += font.lineHeight;
        }
        float progress = GuiMotion.clamp01(position.sample(frame.nowMs(), preferences));
        float fade = GuiMotion.clamp01(opacity.sample(frame.nowMs(), preferences));
        int border = preferences.highContrast() ? DarkModeTheme.TEXT : DarkModeTheme.BORDER_HOVER;
        RoundedCorners.draw(graphics, stage.x(), stage.y(), stage.width(), stage.height(), 6, 1,
                border, DarkModeTheme.SURFACE);
        int middle = stage.y() + stage.height() / 2;
        graphics.fill(stage.x() + 8, middle, stage.right() - 8, middle + 1, border);
        float expandedWidth = Math.min(156f, stage.width() * 0.56f);
        float capsuleWidth = GuiMotion.lerp(32f, expandedWidth, progress);
        float capsuleHeight = GuiMotion.lerp(20f, 40f, progress);
        float x = GuiMotion.lerp(stage.x() + 8f, stage.right() - 8f - expandedWidth, progress);
        float alpha = GuiMotion.lerp(0.42f, 1f, fade);
        RoundedCorners.draw(graphics, x, middle - capsuleHeight / 2f, capsuleWidth, capsuleHeight,
                capsuleHeight / 2f, 1, GlassEffectMotion.withOpacity(LiquidGlassTheme.TEXT, alpha),
                GlassEffectMotion.withOpacity(LiquidGlassTheme.ACCENT, alpha));
        int percent = Math.round(progress * 100f);
        boolean open = position.target() == 1f;
        if (percent != displayedProgress || open != displayedOpen) {
            displayedProgress = percent;
            displayedOpen = open;
            status = label("demo.animation.status", open ? EXPANDED : COLLAPSED, percent);
        }
        graphics.drawString(font, status, stage.x(), stage.bottom() + GAP, DarkModeTheme.TEXT, false);
    }
}
