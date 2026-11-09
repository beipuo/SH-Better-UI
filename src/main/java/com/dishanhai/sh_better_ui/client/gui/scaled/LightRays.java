package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

/** 山海：每个 GUI 区域持有一个光束实例。在客户端渲染线程调用。 */
public final class LightRays {
    private final UiPointerTracker pointer = new UiPointerTracker();
    private final LightRaysMotion motion = new LightRaysMotion();
    private LightRaysProfile profile;
    private long lastFrameMs = Long.MIN_VALUE;
    private boolean motionAllowed;

    private boolean themed;

    public LightRays() {
        this(LightRaysProfile.DEFAULT);
        themed = true;
    }
    public LightRays(LightRaysProfile profile) { setProfile(profile); }
    public LightRaysProfile profile() { return profile; }

    public void setProfile(LightRaysProfile profile) {
        themed = false;
        this.profile = Objects.requireNonNull(profile);
    }

    /** 区域暂时隐藏或界面离开时清除指针和点击波，保留光束相位与入场进度。 */
    public void suspend() {
        pointer.reset();
        motion.clearInteraction();
        lastFrameMs = Long.MIN_VALUE;
        motionAllowed = false;
    }

    /** 重置相位并按当前配置重播入场。 */
    public void restart() {
        suspend();
        motion.phase = 0;
        motion.opening = 0;
    }

    /** 供键盘和宿主动作触发点击波。需要最近一次绘制可用且允许动效。 */
    public void pulse() {
        if (motionAllowed && profile.clickPulse()) motion.pulse();
    }

    public boolean draw(GuiGraphics graphics, Bounds bounds) {
        return draw(graphics, bounds, interactive() ? UiMouseState.read() : null, UiPreferences.configured());
    }

    /**
     * 在背景与前景控件之间绘制。mouse 为 null 时屏蔽指针互动。
     * 高对比模式或着色器不可用时返回 false，宿主可绘制后备背景。
     */
    public boolean draw(GuiGraphics graphics, Bounds bounds, UiMouseState mouse, UiPreferences preferences) {
        if (themed) {
            var resolved = ThemeEffects.profile("lightRays", LightRaysProfile.DEFAULT);
            if (!resolved.equals(profile)) setProfile(resolved);
            themed = true;
        }
        Objects.requireNonNull(preferences);
        if (preferences.highContrast() || !LightRaysRenderer.available()) {
            suspend();
            return false;
        }
        if (bounds.width() == 0 || bounds.height() == 0 || profile.opacity() == 0
                || profile.intensity() == 0 || (profile.raysColor() >>> 24) == 0) {
            suspend();
            return true;
        }

        long now = PageTransition.nowMs();
        float dt = lastFrameMs == Long.MIN_VALUE ? 0 : Math.clamp((now - lastFrameMs) / 1000f, 0, .05f);
        lastFrameMs = now;
        motionAllowed = !preferences.reducedMotion();
        if (motionAllowed) {
            var input = pointer.update(bounds, graphics.pose().last().pose(), mouse, interactive(), now);
            motion.advance(profile, input, bounds.width(), bounds.height(), dt);
        } else {
            pointer.reset();
            motion.clearInteraction();
        }
        if (!profile.intro() || !motionAllowed) motion.opening = 1;
        LightRaysRenderer.draw(graphics, bounds, profile, motion);
        return true;
    }

    private boolean interactive() {
        return profile.followMouse() || profile.cursorShadow() || profile.clickPulse();
    }
}
