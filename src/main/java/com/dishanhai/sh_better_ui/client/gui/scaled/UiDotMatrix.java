package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.SplittableRandom;

/** 山海：每个画面或面板持有一个实例，在客户端渲染线程绘制和更新。 */
public final class UiDotMatrix {
    static final int MAX_GRID_CELLS = 16_384;
    private static final int RGB_MASK = 0x00FF_FFFF;

    private final ClickRipple click = new ClickRipple();
    private DotMatrixProfile profile;
    private int layoutWidth = -1;
    private int layoutHeight;
    private Dot[] dots = new Dot[0];
    private boolean[] floating = new boolean[0];
    private int[] selected = new int[0];
    private boolean selectionDirty = true;
    private long selectedCycle;

    public UiDotMatrix() {
        this(DotMatrixProfile.VIOLET);
    }

    public UiDotMatrix(DotMatrixProfile profile) {
        this.profile = Objects.requireNonNull(profile);
    }

    /** 换色、换效果或调整互动范围时保留网格位置。 */
    public void setProfile(DotMatrixProfile profile) {
        Objects.requireNonNull(profile);
        if (!this.profile.grid().equals(profile.grid())) layoutWidth = -1;
        if (!this.profile.motion().equals(profile.motion())) selectionDirty = true;
        if (profile.interaction().rippleRadius() == 0) click.clear();
        this.profile = profile;
    }

    /** 使用客户端显示偏好和单调时钟，在前景控件之前绘制。 */
    public void render(GuiGraphics graphics, Bounds area, double pointerX, double pointerY) {
        render(graphics, area, pointerX, pointerY, PageTransition.nowMs(), UiPreferences.configured());
    }

    /** 指针与区域使用同一坐标系。传入 NaN 可在模态覆层打开时暂停悬停反馈。 */
    public void render(GuiGraphics graphics, Bounds area, double pointerX, double pointerY,
                       long nowMs, UiPreferences preferences) {
        if (area.width() == 0 || area.height() == 0) return;
        graphics.flush();
        try {
            CapsuleMesh.VertexSink vertices = CapsuleRenderer.colorVertices(graphics, area.x(), area.y());
            forEachQuad(area, pointerX, pointerY, nowMs, preferences, (left, top, right, bottom, argb) -> {
                vertices.vertex(left, top, argb);
                vertices.vertex(left, bottom, argb);
                vertices.vertex(right, bottom, argb);
                vertices.vertex(right, top, argb);
            });
        } finally {
            graphics.flush();
        }
    }

    /** 由宿主在点击或键盘激活后通知背景。新波纹替换当前波纹。 */
    public void pulse(Bounds area, double x, double y) {
        pulse(area, x, y, PageTransition.nowMs());
    }

    /** nowMs 使用与 render 相同的时钟。区域外的坐标保持当前波纹。 */
    public void pulse(Bounds area, double x, double y, long nowMs) {
        if (profile.interaction().rippleRadius() > 0) {
            click.trigger(x, y, area.x(), area.y(), area.width(), area.height(), nowMs);
        }
    }

    /** 宿主失焦或打开模态覆层时可立即收掉反馈。 */
    public void clearPulse() {
        click.clear();
    }

    @FunctionalInterface
    interface QuadSink {
        void quad(float left, float top, float right, float bottom, int argb);
    }

    void forEachQuad(Bounds area, double pointerX, double pointerY, long nowMs, UiPreferences preferences,
                     QuadSink sink) {
        int width = area.width(), height = area.height();
        if (width == 0 || height == 0) return;
        DotMatrixProfile style = profile;
        DotMatrixProfile.Palette palette = style.palette();
        if (preferences.highContrast()) {
            click.clear();
            sink.quad(0, 0, width, height, DarkModeTheme.BACKGROUND);
            return;
        }
        if ((palette.backgroundArgb() >>> 24) != 0) sink.quad(0, 0, width, height, palette.backgroundArgb());
        if (style.grid().density() == 0f) return;
        if (layoutWidth != width || layoutHeight != height) rebuildGrid(width, height, style.grid());

        DotMatrixProfile.Motion motion = style.motion();
        DotMatrixProfile.Interaction interaction = style.interaction();
        DotMatrixProfile.Fade fade = style.fade();
        DotMatrixProfile.Gradient gradient = style.gradient();
        boolean reducedMotion = preferences.reducedMotion();
        boolean canFloat = !reducedMotion && motion.floatProbability() > 0f && motion.maxFloatingDots() > 0
                && motion.floatDistance() > 0f;
        boolean canFade = !reducedMotion && fade.probability() > 0f;
        boolean hasGradient = !gradient.stops().isEmpty();
        float phase = Math.floorMod(nowMs, motion.periodMs()) / (float) motion.periodMs();
        if (canFloat) selectFloating(Math.floorDiv(nowMs, motion.periodMs()), style.grid().seed(), motion);
        long fadeCycle = canFade ? Math.floorDiv(nowMs, fade.periodMs()) : 0L;
        double fadePhase = canFade ? Math.floorMod(nowMs, fade.periodMs()) / (double) fade.periodMs() : 0d;
        float gradientPhase = hasGradient && !reducedMotion
                ? Math.floorMod(nowMs, gradient.periodMs()) / (float) gradient.periodMs() : 0f;

        double localX = pointerX - area.x(), localY = pointerY - area.y();
        boolean hovering = interaction.hoverRadius() > 0 && localX >= 0d && localX < width
                && localY >= 0d && localY < height;
        ClickRipple.Frame pulse = click.sample(nowMs, reducedMotion);
        float pulseX = pulse.originX() * width, pulseY = pulse.originY() * height;
        float half = style.grid().dotSize() * 0.5f;
        float glowAlpha = palette.glow() * 0.22f;
        float glowHalf = half + Math.max(0.65f, half * 0.55f);
        for (int index = 0; index < dots.length; index++) {
            Dot dot = dots[index];
            float opacity = canFade ? visibility(dot, fadeCycle, fadePhase, fade.probability()) : 1f;
            if (opacity == 0f) continue;
            float lift = canFloat && floating[index] ? lift(phase, dot.variation()) : 0f;
            float drift = lift * motion.floatDistance();
            float x = dot.x() + drift * (dot.variation() - 0.5f) * 0.8f;
            float y = dot.y() - drift;
            float anchorX = Math.min(1f, dot.x() / width), anchorY = Math.min(1f, dot.y() / height);
            float animated = reducedMotion ? 0f : motion.effect().brightness(
                    anchorX, anchorY, phase, dot.variation());
            float light = 0.27f * dot.variation() + lift * 0.32f;
            if (Float.isFinite(animated)) light += GuiMotion.clamp01(animated);
            if (hovering) light += 0.64f * localLight(x - localX, y - localY, interaction.hoverRadius());
            if (pulse.opacity() > 0f) {
                light += pulse.opacity() * rippleLight(x - pulseX, y - pulseY, interaction.rippleRadius(), pulse);
            }

            int color = hasGradient ? gradientColor(palette, gradient.sample(anchorX, anchorY, gradientPhase), light)
                    : GuiMotion.lerpColor(palette.dotArgb(), palette.highlightArgb(), light);
            if (opacity < 1f) color = scaleAlpha(color, opacity);
            if (glowAlpha > 0f) square(sink, width, height, x, y, glowHalf, scaleAlpha(color, glowAlpha));
            square(sink, width, height, x, y, half, color);
        }
    }

    private void rebuildGrid(int width, int height, DotMatrixProfile.Grid grid) {
        int step = gridStep(width, height, grid.spacing());
        int columns = Math.ceilDiv(width, step), rows = Math.ceilDiv(height, step);
        ArrayList<Dot> cells = new ArrayList<>();
        SplittableRandom layoutRandom = new SplittableRandom(grid.seed());
        for (int row = 0; row < rows; row++) {
            // 每行独立抽样，窗口改变宽度时，保留重叠区域内的图案。
            SplittableRandom random = layoutRandom.split();
            float y = (row + 0.5f) * step;
            for (int column = 0; column < columns; column++) {
                double occupancy = random.nextDouble();
                float variation = (float) random.nextDouble();
                if (occupancy < grid.density()) {
                    float x = (column + 0.5f) * step;
                    long fadeKey = grid.seed() ^ ((long) row << 32) ^ column;
                    cells.add(new Dot(x, y, variation, fadeKey));
                }
            }
        }
        dots = cells.toArray(Dot[]::new);
        floating = new boolean[dots.length];
        selectionDirty = true;
        layoutWidth = width;
        layoutHeight = height;
    }

    private static int gridStep(int width, int height, int spacing) {
        // 大分辨率保持均匀分布，放宽间距，避免只绘制矩形的一角。
        int min = spacing, max = Math.max(spacing, Math.max(width, height));
        while (min < max) {
            int step = min + (max - min) / 2;
            long cells = (long) Math.ceilDiv(width, step) * Math.ceilDiv(height, step);
            if (cells > MAX_GRID_CELLS) min = step + 1;
            else max = step;
        }
        return min;
    }

    private void selectFloating(long cycle, long seed, DotMatrixProfile.Motion motion) {
        if (!selectionDirty && selectedCycle == cycle) return;
        int limit = Math.min(motion.maxFloatingDots(), dots.length);
        if (selected.length != limit) selected = new int[limit];
        Arrays.fill(floating, false);
        SplittableRandom random = new SplittableRandom(seed ^ cycle);
        SplittableRandom reservoir = random.split();
        int eligible = 0;
        for (int index = 0; index < dots.length; index++) {
            if (random.nextDouble() >= motion.floatProbability()) continue;
            // 蓄水池抽样：候选超过上限时，屏幕各处的候选有相同机会入选。
            int slot = eligible < limit ? eligible : reservoir.nextInt(eligible + 1);
            if (slot < limit) selected[slot] = index;
            eligible++;
        }
        for (int i = 0; i < Math.min(eligible, limit); i++) floating[selected[i]] = true;
        selectedCycle = cycle;
        selectionDirty = false;
    }

    private static float lift(float phase, float variation) {
        float t = GuiMotion.clamp01((phase - variation * 0.42f) / 0.58f);
        float arch = 4f * t * (1f - t);
        return arch * arch;
    }

    private static float visibility(Dot dot, long cycle, double phase, float probability) {
        double localPhase = phase + dot.variation();
        if (localPhase >= 1d) {
            localPhase -= 1d;
            cycle++;
        }
        // 各点错开相位，且在自己的周期边界恢复不透明，重新抽样时没有跳变。
        float visible = GuiMotion.clamp01((float) ((Math.abs(localPhase - 0.5d) - 0.125d) / 0.25d));
        if (visible == 1f) return 1f;
        long hash = dot.fadeKey() ^ (cycle * 0x9E3779B97F4A7C15L);
        hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
        double roll = ((hash ^ (hash >>> 31)) >>> 11) * 0x1.0p-53;
        if (roll >= probability) return 1f;
        return visible * visible * (3f - 2f * visible);
    }

    private static int gradientColor(DotMatrixProfile.Palette palette, int sample, float light) {
        float strength = GuiMotion.clamp01(light);
        int dotAlpha = Math.round(GuiMotion.lerp(palette.dotArgb() >>> 24, palette.highlightArgb() >>> 24, strength));
        int alpha = Math.round(dotAlpha * (sample >>> 24) / 255f);
        int rgb = GuiMotion.lerpColor(sample & RGB_MASK, RGB_MASK, strength * 0.45f);
        return (alpha << 24) | rgb;
    }

    private static int scaleAlpha(int color, float opacity) {
        return (Math.round((color >>> 24) * opacity) << 24) | (color & RGB_MASK);
    }

    private static float localLight(double dx, double dy, double radius) {
        float strength = (float) Math.max(0d, 1d - (dx * dx + dy * dy) / (radius * radius));
        return strength * strength;
    }

    private static float rippleLight(double dx, double dy, int radius, ClickRipple.Frame pulse) {
        if (pulse.uniform()) return localLight(dx, dy, radius * 0.38d);
        double band = Math.clamp(radius * 0.15d, 1d, 10d);
        double crest = pulse.radiusProgress() * radius;
        double distanceSquared = dx * dx + dy * dy;
        double outer = crest + band;
        if (distanceSquared >= outer * outer) return 0f;
        float strength = (float) Math.max(0d, 1d - Math.abs(Math.sqrt(distanceSquared) - crest) / band);
        return strength * strength;
    }

    private static void square(QuadSink sink, int width, int height, float x, float y, float half, int argb) {
        if ((argb >>> 24) == 0) return;
        float left = Math.max(0f, x - half), top = Math.max(0f, y - half);
        float right = Math.min(width, x + half), bottom = Math.min(height, y + half);
        if (left < right && top < bottom) sink.quad(left, top, right, bottom, argb);
    }

    private record Dot(float x, float y, float variation, long fadeKey) {}
}
