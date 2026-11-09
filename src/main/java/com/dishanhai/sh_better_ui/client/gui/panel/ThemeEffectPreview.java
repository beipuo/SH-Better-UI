package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.ElectricLogo;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 山海：在主题草稿作用域内绘制设置页的效果样例。 */
final class ThemeEffectPreview implements AutoCloseable {
    private final Antigravity particles = new Antigravity();
    private final LightRays rays = new LightRays();
    private final LaserFlow laser = new LaserFlow();
    private final CodeRain rain = new CodeRain();
    private final CodeRain ascii = CodeRain.ascii();
    private final PatternWaves patterns = new PatternWaves();
    private final ShapeWaves shapes = new ShapeWaves();
    private final UiDotMatrix dots = new UiDotMatrix();
    private final ClickRipple ripple = new ClickRipple();
    private final UiPageSlide slide = new UiPageSlide();
    private final UiRecipePageTransition recipe = new UiRecipePageTransition();
    private final UiCapsuleReveal capsule = new UiCapsuleReveal();
    private String previousGroup;
    private long replayAt;
    private ElectricLogo logo;

    void draw(String group, GuiGraphics graphics, Font font, Bounds area, Component caption,
              int mouseX, int mouseY, UiPreferences preferences) {
        int x = area.x(), y = area.y(), w = area.width(), h = area.height();
        long now = PageTransition.nowMs();
        boolean restart = !group.equals(previousGroup) || now - replayAt >= 1800;
        if (restart) {
            previousGroup = group;
            replayAt = now;
        }
        DarkModeTheme.background(graphics, x, y, w, h);
        graphics.enableScissor(x, y, area.right(), area.bottom());
        try {
            var materials = ShBetterUiApi.materials();
            switch (group) {
                case "dotMatrix" -> dots.render(graphics, area, mouseX, mouseY, PageTransition.nowMs(), preferences);
                case "antigravity" -> particles.draw(graphics, area);
                case "lightRays" -> rays.draw(graphics, area);
                case "laserFlow" -> laser.draw(graphics, area);
                case "codeRain" -> rain.draw(graphics, font, area);
                case "ascii" -> ascii.draw(graphics, font, area);
                case "patternWaves" -> patterns.draw(graphics, area);
                case "shapeWaves" -> shapes.draw(graphics, area);
                case "electricLogo" -> {
                    if (logo == null) logo = ShBetterUiApi.graphics().electricLogo();
                    logo.draw(graphics, x, y, w, h, mouseX, mouseY);
                }
                case "classic" -> {
                    var classic = materials.classicTheme();
                    classic.box(graphics, x, y, w, h, classic.frameDark, classic.panelBg);
                    caption(graphics, font, area, caption, classic.ink("", classic.tabHover()));
                }
                case "clearGlass" -> {
                    materials.clearPanel(graphics, area);
                    caption(graphics, font, area, caption, ThemeEffects.material(group).text(0xFF1C2B35));
                }
                case "smokeGlass" -> {
                    materials.smokePanel(graphics, area);
                    caption(graphics, font, area, caption, ThemeEffects.material(group).text(0xFFD3D8DB));
                }
                case "network" -> {
                    NetworkTheme.drawPanel(graphics, area, preferences);
                    caption(graphics, font, area, caption, NetworkTheme.labelColor(preferences));
                }
                case "slate" -> {
                    materials.slateInput(graphics, area, new DarkModeTheme.State(0, 0, 0, false, true,
                            preferences.reducedMotion(), preferences.highContrast()));
                    caption(graphics, font, area, caption, DarkModeTheme.textColor());
                }
                case "clickRipple" -> {
                    if (restart) ripple.trigger(x + w / 2.0, y + h / 2.0, x, y, w, h, now);
                    GlassEffectRenderer.drawRipple(graphics, x, y, w, h, ripple, now,
                            preferences.reducedMotion(), DarkModeTheme.accentColor());
                }
                case "flowBorder" -> GlassEffectRenderer.drawFlowBorder(graphics, x + 3, y + 3, w - 6, h - 6,
                        now, preferences.reducedMotion(), DarkModeTheme.accentColor());
                case "breathingGlow" -> GlassEffectRenderer.drawBreathingGlow(graphics, x + 6, y + 6, w - 12, h - 12,
                        1f, now, preferences.reducedMotion(), DarkModeTheme.accentColor());
                case "reveal" -> {
                    VisualReveal.drawRadialGradient(graphics, x, y, w, h);
                    VisualReveal.blurRegion(graphics, x, y, w, h, RevealProfile.DEFAULT);
                }
                case "pageSlide" -> {
                    if (restart) slide.start(UiPageSlide.Direction.RIGHT, UiPageSlide.Motion.FLAT, w, now, preferences);
                    UiPageSlide.draw(graphics, area, slide.sample(now, preferences), preferences, UiPageSlide.Effects.NONE,
                            () -> caption(graphics, font, area, caption, DarkModeTheme.textColor()));
                }
                case "recipeTransition" -> {
                    if (restart) recipe.start(UiPageSlide.Direction.RIGHT, area, now, preferences);
                    UiRecipePageTransition.draw(graphics, area, recipe.sample(now, preferences), preferences,
                            () -> caption(graphics, font, area, caption, DarkModeTheme.textColor()));
                }
                case "capsule" -> {
                    if (restart) capsule.startAt(x + w / 2f, y + h / 2f);
                    Runnable content = () -> caption(graphics, font, area, caption, DarkModeTheme.textColor());
                    if (!capsule.renderInset(graphics, area, preferences, content)) content.run();
                }
                case "screenReturn" -> {
                    var effect = ThemeEffects.local(group);
                    float progress = (now - replayAt) * effect.speed() / 240f;
                    if (effect.enabled() && !preferences.reducedMotion() && progress < 1f) {
                        int color = effect.tint(DarkModeTheme.accentColor());
                        graphics.fill(x, y, x + w, y + h, GuiMotion.lerpColor(color, color & 0xFFFFFF, progress));
                    }
                    caption(graphics, font, area, caption, DarkModeTheme.textColor());
                }
                case "glass", "liquidGlass" -> {
                    materials.liquidPopover(graphics, area, preferences);
                    caption(graphics, font, area, caption, LiquidGlassTheme.textColor());
                }
            }
        } finally {
            graphics.disableScissor();
        }
    }

    static boolean supports(String group) {
        return switch (group) {
            case "dotMatrix", "antigravity", "lightRays", "laserFlow", "codeRain", "ascii", "patternWaves",
                    "shapeWaves", "electricLogo", "classic", "clearGlass", "smokeGlass", "network", "slate",
                    "glass", "liquidGlass", "clickRipple", "flowBorder", "breathingGlow", "reveal",
                    "pageSlide", "recipeTransition", "capsule", "screenReturn" -> true;
            default -> false;
        };
    }

    private static void caption(GuiGraphics graphics, Font font, Bounds area, Component caption, int color) {
        graphics.drawString(font, caption, area.x() + 7, area.y() + 7, color, false);
    }

    @Override
    public void close() {
        if (logo != null) {
            logo.close();
            logo = null;
        }
    }
}
