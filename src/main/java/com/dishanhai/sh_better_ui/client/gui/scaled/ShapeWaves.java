package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

/** 山海：用三角形、圆形与方形绘制带文字镂空和鼠标涟漪的波面。每个区域持有一个实例。 */
public final class ShapeWaves {
    private static final float TURN = (float) (2 * Math.PI);
    private static final int DRIFT_PERIOD = 4096;

    private final WavePointer pointer = new WavePointer();
    private ShapeWavesProfile profile;
    private boolean paused;
    private float phase;
    private double driftX;
    private double driftY;
    private float intro;
    private long lastFrameMs = Long.MIN_VALUE;
    private long introStartedAt = Long.MIN_VALUE;

    public ShapeWaves() { this(ShapeWavesProfile.DEFAULT); }

    public ShapeWaves(ShapeWavesProfile profile) { setProfile(profile); }

    public ShapeWavesProfile profile() { return profile; }

    /** 切换样式后接续当前波面。 */
    public void setProfile(ShapeWavesProfile profile) { this.profile = Objects.requireNonNull(profile); }

    /** 暂停波面的变化和漂移，入场与鼠标涟漪继续播放。 */
    public void setPaused(boolean paused) { this.paused = paused; }

    public boolean isPaused() { return paused; }

    /** 重播入场并保留当前波面和涟漪。 */
    public void replayIntro() {
        intro = 0;
        introStartedAt = Long.MIN_VALUE;
    }

    /** 从初始波面重播入场，并清除涟漪。 */
    public void restart() {
        phase = 0;
        driftX = 0;
        driftY = 0;
        lastFrameMs = Long.MIN_VALUE;
        replayIntro();
        pointer.reset();
    }

    /** 在客户端渲染回调中绘制。使用配置中的偏好和当前鼠标状态。 */
    public boolean draw(GuiGraphics graphics, Bounds bounds) {
        return draw(graphics, bounds, profile.interactive() ? UiMouseState.read() : null, UiPreferences.configured());
    }

    /**
     * 使用共享鼠标快照绘制。传 null 可关闭当前区域的鼠标交互。
     * 返回 true 表示波面已绘制，false 表示使用底色回退或区域为空。
     */
    public boolean draw(GuiGraphics graphics, Bounds bounds, UiMouseState mouse, UiPreferences preferences) {
        long now = PageTransition.nowMs();
        float elapsed = lastFrameMs == Long.MIN_VALUE ? 0 : Math.min(100, Math.max(0, now - lastFrameMs)) * .001f;
        lastFrameMs = now;
        if (!paused && !preferences.reducedMotion() && profile.speed() > 0) advance(elapsed);
        if (!profile.intro() || preferences.reducedMotion()) {
            intro = 1;
        } else if (intro < 1) {
            if (introStartedAt == Long.MIN_VALUE) introStartedAt = now;
            intro = Math.min(1f, (now - introStartedAt) / (profile.introDuration() * 1000f));
        }

        boolean interactive = profile.interactive() && profile.splashStrength() > 0
                && !preferences.reducedMotion() && !preferences.highContrast();
        pointer.update(bounds, graphics.pose().last().pose(), mouse, interactive, profile.splashRadius(), now);

        if (bounds.width() == 0 || bounds.height() == 0) return false;
        if (!preferences.highContrast() && ShapeWavesRenderer.draw(graphics, bounds, profile, phase,
                (float) driftX, (float) driftY, intro, now, pointer.ripples())) return true;
        if ((profile.backgroundColor() >>> 24) != 0) {
            graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), profile.backgroundColor());
        }
        return false;
    }

    /** 选用。由宿主转发 Screen.mouseClicked 可接收两帧之间的短按。 */
    public void mousePressed(double guiX, double guiY, int button) {
        if (profile.interactive() && profile.splashStrength() > 0) {
            pointer.mousePressed(guiX, guiY, button, PageTransition.nowMs());
        }
    }

    private void advance(float elapsed) {
        phase = (phase + elapsed * profile.speed()) % TURN;
        double angle = Math.toRadians(profile.direction());
        double distance = elapsed * profile.flow();
        driftX = (driftX + Math.cos(angle) * distance) % DRIFT_PERIOD;
        driftY = (driftY + Math.sin(angle) * distance) % DRIFT_PERIOD;
    }
}
