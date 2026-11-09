package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

/** 山海：每个 GUI 区域持有一个激光流实例，在客户端渲染线程调用。 */
public final class LaserFlow {
    private final UiPointerTracker pointer = new UiPointerTracker();
    private LaserFlowProfile profile;
    private long lastFrameMs = Long.MIN_VALUE;
    private float phase;
    private float opening;

    private boolean themed;

    public LaserFlow() {
        this(LaserFlowProfile.DEFAULT);
        themed = true;
    }
    public LaserFlow(LaserFlowProfile profile) {
        setProfile(profile);
        opening = profile.paused() ? 1 : 0;
    }
    public LaserFlowProfile profile() { return profile; }

    /** 替换参数并保留当前动画相位。 */
    public void setProfile(LaserFlowProfile profile) {
        themed = false;
        this.profile = Objects.requireNonNull(profile);
    }

    /** 隐藏效果时清除指针和帧时间，保留当前动画相位。 */
    public void suspend() {
        pointer.reset();
        lastFrameMs = Long.MIN_VALUE;
    }

    public void restart() {
        suspend();
        phase = 0;
        opening = 0;
    }

    public boolean draw(GuiGraphics graphics, Bounds bounds) {
        return draw(graphics, bounds, null, null);
    }

    public boolean draw(GuiGraphics graphics, Bounds bounds, LaserFlowSurface surface, LaserFlowReveal reveal) {
        UiMouseState mouse = profile.mouseInteraction() || reveal != null ? UiMouseState.read() : null;
        return draw(graphics, bounds, surface, reveal, mouse, UiPreferences.configured());
    }

    /**
     * 在底板之后、前景控件之前绘制。surface 或 reveal 可为 null，mouse 为 null 时屏蔽游标效果。
     * 高对比或着色器不可用时返回 false，宿主可以保留普通底板。
     */
    public boolean draw(GuiGraphics graphics, Bounds bounds, LaserFlowSurface surface, LaserFlowReveal reveal,
                        UiMouseState mouse, UiPreferences preferences) {
        if (themed) {
            var resolved = ThemeEffects.profile("laserFlow", LaserFlowProfile.DEFAULT);
            if (!resolved.equals(profile)) setProfile(resolved);
            themed = true;
        }
        Objects.requireNonNull(preferences);
        if (preferences.highContrast() || !LaserFlowRenderer.available()) {
            suspend();
            return false;
        }
        if (bounds.width() == 0 || bounds.height() == 0 || profile.intensity() == 0
                || (profile.color() >>> 24) == 0) {
            suspend();
            return true;
        }

        long now = PageTransition.nowMs();
        float dt = lastFrameMs == Long.MIN_VALUE ? 0 : Math.clamp((now - lastFrameMs) / 1000f, 0, .05f);
        lastFrameMs = now;
        if (!profile.intro() || profile.speed() == 0 || preferences.reducedMotion()) {
            opening = 1;
        }
        if (!profile.paused() && !preferences.reducedMotion()) {
            phase += dt * profile.speed();
            opening = Math.min(1, opening + dt / 1.5f);
        }
        boolean interactive = profile.mouseInteraction() || reveal != null;
        var input = pointer.update(bounds, graphics.pose().last().pose(), mouse,
                interactive && !preferences.reducedMotion(), now);
        LaserFlowRenderer.draw(graphics, bounds, profile, surface, reveal, input, phase, opening);
        return true;
    }
}
