package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

/** 山海：每个区域持有一个反重力粒子场。客户端线程调用，绘制不消费宿主输入。 */
public final class Antigravity {
    private final UiPointerTracker pointer = new UiPointerTracker();
    private final AntigravityField field = new AntigravityField();
    private AntigravityProfile profile;
    private AntigravityProfile paletteProfile;
    private int[] palette;
    private long lastFrameMs = Long.MIN_VALUE;
    private boolean paused;
    private boolean motionAllowed;

    public Antigravity() { this(AntigravityProfile.DEFAULT); }
    public Antigravity(AntigravityProfile profile) { setProfile(profile); }
    public AntigravityProfile profile() { return profile; }
    public void setProfile(AntigravityProfile profile) { this.profile = Objects.requireNonNull(profile); }
    public boolean isPaused() { return paused; }
    public void setPaused(boolean paused) {
        this.paused = paused;
        pointer.reset();
        field.charge = 0;
        motionAllowed = false;
        lastFrameMs = Long.MIN_VALUE;
    }
    /** 离页、重建布局或暂时停止绘制时清除按压；保留粒子位置和配置。 */
    public void suspend() {
        pointer.reset();
        field.charge = 0;
        lastFrameMs = Long.MIN_VALUE;
        motionAllowed = false;
    }

    public void restart() {
        field.restart();
        paused = false;
        pointer.reset();
        lastFrameMs = Long.MIN_VALUE;
        motionAllowed = false;
    }

    /** 键盘或宿主动作触发爆散；仅在最近一次可动效、未暂停的绘制后生效。 */
    public void burst(float strength) {
        if (motionAllowed && profile.clickBurst()) field.burst(profile, strength);
    }

    public boolean draw(GuiGraphics graphics, Bounds bounds) {
        return draw(graphics, bounds, profile.interactive() ? UiMouseState.read() : null, UiPreferences.configured());
    }

    /** 被模态层或自定义裁切挡住时传 null。返回 false 表示 shader 不可用或高对比降级。 */
    public boolean draw(GuiGraphics graphics, Bounds bounds, UiMouseState mouse, UiPreferences preferences) {
        Objects.requireNonNull(preferences);
        long now = PageTransition.nowMs();
        float dt = lastFrameMs == Long.MIN_VALUE ? 0 : Math.clamp((now - lastFrameMs) / 1000f, 0, 1f / 30);
        lastFrameMs = now;
        motionAllowed = !paused && !preferences.reducedMotion() && !preferences.highContrast()
                && profile.opacity() > 0 && bounds.width() > 0 && bounds.height() > 0;
        var input = pointer.update(bounds, graphics.pose().last().pose(), mouse,
                motionAllowed && profile.interactive(), now);
        if (preferences.highContrast() || bounds.width() == 0 || bounds.height() == 0 || profile.opacity() == 0) {
            field.charge = 0;
            return !preferences.highContrast();
        }
        boolean firstFrame = field.x.length == 0;
        field.resize(bounds.width(), bounds.height(), profile);
        if (paused && firstFrame) field.advance(profile, null, 0, true);
        else if (!paused || preferences.reducedMotion()) field.advance(profile, input, dt, preferences.reducedMotion());
        if (paletteProfile == null || !paletteProfile.colors().equals(profile.colors())
                || paletteProfile.lightMode() != profile.lightMode() || paletteProfile.colorMode() != profile.colorMode()) {
            palette = AntigravityPalette.build(profile.colors(), profile.lightMode(),
                    profile.colorMode() == AntigravityProfile.ColorMode.CONIC);
            paletteProfile = profile;
        }
        return AntigravityRenderer.draw(graphics, bounds, profile, field, palette);
    }
}
