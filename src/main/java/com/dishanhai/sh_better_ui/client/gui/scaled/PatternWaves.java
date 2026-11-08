package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

/** 山海：每个 GUI 区域各持有一个图案波面，客户端线程更新，渲染回调中绘制。 */
public final class PatternWaves {
    private static final float TURN = (float) (2 * Math.PI);
    private static final float INTRO_MS = 2000;

    private final WavePointer pointer = new WavePointer();
    private PatternWavesProfile profile;
    private long lastFrameMs = Long.MIN_VALUE;
    private boolean paused;
    private float phase;
    private float intro;

    public PatternWaves() { this(PatternWavesProfile.Preset.SILK.profile()); }

    public PatternWaves(PatternWavesProfile profile) { setProfile(profile); }

    public PatternWavesProfile profile() { return profile; }

    /** 切换配置时沿用波面时间。 */
    public void setProfile(PatternWavesProfile profile) { this.profile = Objects.requireNonNull(profile); }

    /** 冻结背景波面，鼠标涟漪继续播放。 */
    public void setPaused(boolean paused) { this.paused = paused; }

    public boolean isPaused() { return paused; }

    /** 重新播放入场并清除当前尾迹。 */
    public void restart() {
        phase = 0;
        intro = 0;
        lastFrameMs = Long.MIN_VALUE;
        pointer.reset();
    }

    public void draw(GuiGraphics graphics, Bounds bounds) {
        draw(graphics, bounds, profile.interactive() ? UiMouseState.read() : null, UiPreferences.configured());
    }

    /** 传入共享鼠标快照。模态层或自定义裁切挡住此区域时传 null。 */
    public void draw(GuiGraphics graphics, Bounds bounds, UiMouseState mouse, UiPreferences preferences) {
        long now = PageTransition.nowMs();
        long elapsed = lastFrameMs == Long.MIN_VALUE ? 0 : Math.min(50, Math.max(0, now - lastFrameMs));
        lastFrameMs = now;
        if (!paused && !preferences.reducedMotion()) phase = (phase + elapsed * .001f * profile.speed()) % TURN;
        intro = profile.intro() && !preferences.reducedMotion() ? Math.min(1, intro + elapsed / INTRO_MS) : 1;

        boolean interactive = !preferences.reducedMotion() && !preferences.highContrast()
                && profile.interactive() && profile.cursorStrength() > 0;
        pointer.update(bounds, graphics.pose().last().pose(), mouse, interactive, profile.cursorSize(), now);

        if (bounds.width() == 0 || bounds.height() == 0) return;
        if (preferences.highContrast() || profile.opacity() == 0 || (profile.color() >>> 24) == 0
                || !PatternWavesRenderer.draw(graphics, bounds, profile, phase, intro, now, pointer.ripples())) {
            if ((profile.backgroundColor() >>> 24) != 0) {
                graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), profile.backgroundColor());
            }
        }
    }

    /** 选用。转发 Screen.mouseClicked 可捕捉两帧之间的短按；按键继续由宿主分派。 */
    public void mousePressed(double guiX, double guiY, int button) {
        if (profile.interactive() && profile.cursorStrength() > 0) {
            pointer.mousePressed(guiX, guiY, button, PageTransition.nowMs());
        }
    }
}
