package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
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
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToggleButton;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

import static com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen.label;

/** 山海：在同一预览中反复打断位移和透明度轨道，开关始终可以操作。 */
final class DeveloperAnimationPreview implements PreviewScene {
    static final String ID = "sh_better_ui:interruptible_animation";
    private static final int GAP = 6;
    private static final int BUTTON_HEIGHT = 22;
    private static final int STAGE_HEIGHT = 68;
    private static final int PANEL_PRIORITY = 30;
    private static final String TITLE_KEY = "sh_better_ui.panel.developer.demo.animation";
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
    private Font font;
    private List<FormattedCharSequence> guide;
    private UiToggleButton reveal;
    private UiToggleButton motion;
    private int displayedProgress = -1;
    private boolean displayedOpen;
    private Component status;

    static void registerInto(PreviewRegistry registry) {
        registry.register(new PreviewSpec(ID, PreviewSpec.Category.CONTROLS, 1001, TITLE_KEY,
                108, 0, "", DeveloperAnimationPreview::new));
    }

    @Override
    public void layout(PreviewContext host) {
        if (position == null) {
            preferences = host.preferences();
            position = host.context().animation(Spring.FLUID_PANEL, 0f);
            opacity = host.context().animation(Spring.PANEL, 0f);
        }
        stage = null;
        font = host.font();
        Bounds viewport = host.viewport();
        int width = Math.min(360, viewport.width() - 24);
        if (width < 140) return;
        guide = font.split(GUIDE, width);
        int guideHeight = guide.size() * font.lineHeight + GAP;
        int statusHeight = font.lineHeight + 2 * GAP;
        int available = viewport.height() - 2 * GAP;
        boolean stacked = available >= guideHeight + STAGE_HEIGHT + statusHeight + 2 * BUTTON_HEIGHT + GAP;
        if (!stacked) {
            guide = List.of();
            guideHeight = 0;
        }
        int controlsHeight = stacked ? 2 * BUTTON_HEIGHT + GAP : BUTTON_HEIGHT;
        int stageHeight = Math.min(STAGE_HEIGHT, available - guideHeight - statusHeight - controlsHeight);
        if (stageHeight < 26) return;
        int height = guideHeight + stageHeight + statusHeight + controlsHeight;
        int x = viewport.x() + (viewport.width() - width) / 2;
        int y = viewport.y() + (viewport.height() - height) / 2 + guideHeight;
        stage = new Bounds(x, y, width, stageHeight);
        int controlsY = stage.bottom() + statusHeight;
        int revealWidth = stacked ? width : (width - GAP) / 2;
        reveal = new UiToggleButton(new Bounds(x, controlsY, revealWidth, BUTTON_HEIGHT),
                position.target() == 1f, preferences, UiStyle.LIQUID_GLASS, open -> open ? CLOSE : OPEN,
                this::setOpen);
        var motionBounds = stacked ? new Bounds(x, controlsY + BUTTON_HEIGHT + GAP, width, BUTTON_HEIGHT)
                : new Bounds(x + revealWidth + GAP, controlsY, width - revealWidth - GAP, BUTTON_HEIGHT);
        motion = new UiToggleButton(motionBounds,
                preferences.reducedMotion(), preferences, UiStyle.LIQUID_GLASS,
                reduced -> Component.translatable("sh_better_ui.panel.motion",
                        Component.translatable(reduced ? "options.on" : "options.off")), this::setReducedMotion);
        reveal.button().setSampleBackground(false);
        motion.button().setSampleBackground(false);
        motion.button().setPreview(label("demo.animation.motion_tip"));
        host.add(reveal.button());
        host.add(motion.button());
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

    @Override
    public void render(UiFrame frame) {
        var graphics = frame.graphics();
        Bounds viewport = frame.viewport();
        if (stage == null) {
            graphics.drawWordWrap(font, TOO_SMALL, viewport.x() + GAP, viewport.y() + GAP,
                    Math.max(1, viewport.width() - 2 * GAP), DarkModeTheme.textColor());
            return;
        }

        int guideY = stage.y() - guide.size() * font.lineHeight - GAP;
        for (var line : guide) {
            graphics.drawString(font, line, stage.x(), guideY, DarkModeTheme.textColor(), false);
            guideY += font.lineHeight;
        }
        float progress = GuiMotion.clamp01(position.sample(frame.nowMs(), preferences));
        float fade = GuiMotion.clamp01(opacity.sample(frame.nowMs(), preferences));
        int border = preferences.highContrast() ? DarkModeTheme.textColor() : DarkModeTheme.hoverBorderColor();
        RoundedCorners.draw(graphics, stage.x(), stage.y(), stage.width(), stage.height(), 6, 1,
                border, DarkModeTheme.surfaceColor());
        int middle = stage.y() + stage.height() / 2;
        graphics.fill(stage.x() + 8, middle, stage.right() - 8, middle + 1, border);
        float expandedWidth = Math.min(156f, stage.width() * 0.56f);
        float capsuleWidth = GuiMotion.lerp(32f, expandedWidth, progress);
        float capsuleHeight = GuiMotion.lerp(20f, Math.min(40f, stage.height() - 4f), progress);
        float x = GuiMotion.lerp(stage.x() + 8f, stage.right() - 8f - expandedWidth, progress);
        float alpha = GuiMotion.lerp(0.42f, 1f, fade);
        RoundedCorners.draw(graphics, x, middle - capsuleHeight / 2f, capsuleWidth, capsuleHeight,
                capsuleHeight / 2f, 1, GlassEffectMotion.withOpacity(LiquidGlassTheme.textColor(), alpha),
                GlassEffectMotion.withOpacity(LiquidGlassTheme.accentColor(), alpha));
        int percent = Math.round(progress * 100f);
        boolean open = position.target() == 1f;
        if (percent != displayedProgress || open != displayedOpen) {
            displayedProgress = percent;
            displayedOpen = open;
            status = label("demo.animation.status", open ? EXPANDED : COLLAPSED, percent);
        }
        graphics.drawString(font, status, stage.x(), stage.bottom() + GAP, DarkModeTheme.textColor(), false);
    }
}
