package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Objects;

/** 山海：在客户端绘制代码雨。每个背景区域持有一个实例。 */
public final class CodeRain {
    private final List<CodeRainEffect> effects;
    private final boolean usesPointer;
    private final UiPointerTracker pointer = new UiPointerTracker();
    private final CodeRainEffect.Cell cell = new CodeRainEffect.Cell();
    private CodeRainProfile profile;
    private FormattedCharSequence[] glyphs;
    private int[] glyphWidths;
    private DotBatch dots;
    private long lastFrameMs = Long.MIN_VALUE;
    private boolean wasReduced;
    private double seconds;
    private int lastWidth;
    private int lastHeight;

    private CodeRainProfile themeBase;

    public static CodeRain ascii() {
        var fallback = CodeRainProfile.builder().mode(CodeRainProfile.Mode.ASCII).build();
        var rain = new CodeRain(fallback);
        rain.themeBase = fallback;
        return rain;
    }

    public CodeRain() {
        this(CodeRainProfile.DEFAULT);
        themeBase = CodeRainProfile.DEFAULT;
    }

    public CodeRain(CodeRainProfile profile, CodeRainEffect... effects) {
        this.effects = List.of(effects);
        usesPointer = this.effects.stream().anyMatch(CodeRainEffect::usesPointer);
        setProfile(profile);
    }

    public CodeRainProfile profile() { return profile; }

    /** 替换配置，重新生成字形并从初始相位播放。 */
    public void setProfile(CodeRainProfile profile) {
        themeBase = null;
        Objects.requireNonNull(profile);
        if (profile.equals(this.profile)) return;
        this.profile = profile;
        glyphs = profile.characters().codePoints()
                .mapToObj(point -> FormattedCharSequence.codepoint(point, Style.EMPTY))
                .toArray(FormattedCharSequence[]::new);
        glyphWidths = new int[glyphs.length];
        restart();
    }

    /** 区域隐藏时清理输入状态，保留动画相位。 */
    public void suspend() {
        lastFrameMs = Long.MIN_VALUE;
        pointer.reset();
        effects.forEach(CodeRainEffect::suspend);
    }

    /** 清理输入状态并重播动画和入场效果。 */
    public void restart() {
        suspend();
        seconds = 0;
    }

    public boolean draw(GuiGraphics graphics, Font font, Bounds bounds) {
        UiPreferences preferences = UiPreferences.configured();
        UiMouseState mouse = usesPointer && !preferences.highContrast() && !preferences.reducedMotion()
                ? UiMouseState.read() : null;
        return draw(graphics, font, bounds, mouse, preferences);
    }

    /**
     * 在底板之后、前景之前绘制。mouse 为 null 时暂停指针互动，减少动效时显示稳定字形。
     * 高对比模式返回 false，宿主可保留原底板。返回 true 表示已处理该区域。
     */
    public boolean draw(GuiGraphics graphics, Font font, Bounds bounds, UiMouseState mouse,
                        UiPreferences preferences) {
        if (themeBase != null) {
            var fallback = themeBase;
            String group = fallback.mode() == CodeRainProfile.Mode.ASCII ? "ascii" : "codeRain";
            var resolved = ThemeEffects.profile(group, fallback);
            if (!resolved.equals(profile)) setProfile(resolved);
            themeBase = fallback;
        }
        if (preferences.highContrast() || bounds.width() == 0 || bounds.height() == 0 || profile.density() == 0) {
            if (lastFrameMs != Long.MIN_VALUE) suspend();
            return !preferences.highContrast();
        }
        if (bounds.width() != lastWidth || bounds.height() != lastHeight) {
            if (lastFrameMs != Long.MIN_VALUE) suspend();
            lastWidth = bounds.width();
            lastHeight = bounds.height();
        }
        var pose = graphics.pose().last().pose();
        var min = new Vector3f();
        var max = new Vector3f();
        pose.transformAab(bounds.x(), bounds.y(), 0, bounds.right(), bounds.bottom(), 0, min, max);
        if (!min.isFinite() || !max.isFinite() || min.x >= max.x || min.y >= max.y) {
            if (lastFrameMs != Long.MIN_VALUE) suspend();
            return false;
        }

        long now = PageTransition.nowMs();
        boolean reduced = preferences.reducedMotion();
        if (!reduced && !wasReduced && lastFrameMs != Long.MIN_VALUE) {
            seconds += UiMath.clamp((now - lastFrameMs) / 1000.0, 0, .05);
        }
        wasReduced = reduced;
        lastFrameMs = now;
        var input = pointer.update(bounds, pose, mouse, usesPointer && !reduced, now);
        var frame = new CodeRainEffect.Frame(bounds, profile, glyphs.length, seconds, reduced,
                usesPointer && !reduced && mouse != null && mouse.available() ? input : null);
        for (CodeRainEffect effect : effects) effect.beginFrame(frame);

        Grid grid = Grid.fit(bounds.width(), bounds.height(), profile.cellSize());
        int maxGlyphWidth = font.lineHeight;
        for (int i = 0; i < glyphs.length; i++) {
            glyphWidths[i] = font.width(glyphs[i]);
            maxGlyphWidth = Math.max(maxGlyphWidth, glyphWidths[i]);
        }
        float scale = .8f * grid.cellSize / Math.max(1, maxGlyphWidth);
        var glyphPose = new Matrix4f(pose).translate(bounds.x(), bounds.y(), 0).scale(scale, scale, 1);
        graphics.flush();
        graphics.enableScissor((int) Math.floor(min.x), (int) Math.floor(min.y),
                (int) Math.ceil(max.x), (int) Math.ceil(max.y));
        try {
            try {
                drawCells(graphics, font, frame, grid, scale, glyphPose);
            } finally {
                graphics.flush();
            }
        } finally {
            graphics.disableScissor();
        }
        return true;
    }

    private void drawCells(GuiGraphics graphics, Font font, CodeRainEffect.Frame frame, Grid grid, float scale,
                           Matrix4f glyphPose) {
        var buffers = graphics.bufferSource();
        double phase = seconds * profile.speed();
        float stepX = frame.bounds().width() / (float) grid.columns;
        float stepY = frame.bounds().height() / (float) grid.rows;
        int dotCount = 0;
        for (int column = 0; column < grid.columns; column++) {
            double head = head(profile, column, grid.rows, phase);
            for (int row = 0; row < grid.rows; row++) {
                float brightness = brightness(profile, column, row, head);
                if (brightness <= 0) continue;
                cell.column = column;
                cell.row = row;
                cell.x = (column + .5f) * stepX;
                cell.y = (row + .5f) * stepY;
                cell.brightness = brightness;
                cell.glyphIndex = glyphIndex(profile, glyphs.length, column, row, phase);
                cell.argb = profile.color();
                cell.glow = 0;
                cell.dotSize = 0;
                for (CodeRainEffect effect : effects) effect.apply(frame, cell);
                int color = glyphColor(cell.argb, cell.brightness);
                if ((color >>> 24) == 0) continue;
                int glowColor = glyphColor(color, cell.glow * .22f);
                if (cell.dotSize > 0) {
                    int capacity = grid.columns * grid.rows;
                    if (dots == null || dots.indices.length < capacity) dots = new DotBatch(capacity);
                    dots.indices[dotCount] = column * grid.rows + row;
                    dots.halves[dotCount] = cell.dotSize * grid.cellSize * .5f;
                    dots.colors[dotCount] = color;
                    dots.glowColors[dotCount] = glowColor;
                    dotCount++;
                    continue;
                }
                int index = UiMath.clamp(cell.glyphIndex, 0, glyphs.length - 1);
                var glyph = glyphs[index];
                float x = cell.x / scale - glyphWidths[index] * .5f;
                float y = cell.y / scale - font.lineHeight * .5f;
                if ((glowColor >>> 24) != 0) {
                    font.drawInBatch(glyph, x - 1, y, glowColor, false, glyphPose, buffers,
                            Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
                    font.drawInBatch(glyph, x + 1, y, glowColor, false, glyphPose, buffers,
                            Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
                    font.drawInBatch(glyph, x, y - 1, glowColor, false, glyphPose, buffers,
                            Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
                    font.drawInBatch(glyph, x, y + 1, glowColor, false, glyphPose, buffers,
                            Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
                }
                font.drawInBatch(glyph, x, y, color, false, glyphPose, buffers,
                        Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            }
        }
        if (dotCount > 0) drawDots(graphics, frame.bounds(), grid, stepX, stepY, dotCount);
    }

    private void drawDots(GuiGraphics graphics, Bounds bounds, Grid grid, float stepX, float stepY, int count) {
        var vertices = CapsuleRenderer.colorVertices(graphics, bounds.x(), bounds.y());
        for (int i = 0; i < count; i++) {
            int index = dots.indices[i];
            float x = (index / grid.rows + .5f) * stepX;
            float y = (index % grid.rows + .5f) * stepY;
            float half = dots.halves[i];
            if (dots.glowColors[i] != 0) {
                float glowHalf = Math.min(grid.cellSize * .5f, half * 1.6f);
                square(vertices, x, y, glowHalf, dots.glowColors[i]);
            }
            square(vertices, x, y, half, dots.colors[i]);
        }
    }

    private static void square(CapsuleMesh.VertexSink vertices, float x, float y, float half, int argb) {
        vertices.vertex(x - half, y - half, argb);
        vertices.vertex(x - half, y + half, argb);
        vertices.vertex(x + half, y + half, argb);
        vertices.vertex(x + half, y - half, argb);
    }

    static int glyphColor(int argb, float opacity) {
        int alpha = Math.round((argb >>> 24) * opacity);
        // 原版 Font 会把 alpha 小于 4 的颜色视为不透明。
        return alpha < 4 ? 0 : alpha << 24 | (argb & 0xFFFFFF);
    }

    static double head(CodeRainProfile profile, int column, int rows, double phase) {
        int cycle = rows + profile.trailLength();
        double travel = cycle * noise(profile.seed(), column, 0, 0)
                + phase * (.65 + .7 * noise(profile.seed(), column, 1, 0));
        return travel % cycle;
    }

    static float brightness(CodeRainProfile profile, int column, int row, double head) {
        float sample = noise(profile.seed(), column, row, 1);
        if (sample >= profile.density()) return 0;
        if (profile.mode() == CodeRainProfile.Mode.ASCII) return .65f + .35f * sample;
        double distance = head - row;
        if (distance <= -1 || distance >= profile.trailLength()) return 0;
        if (distance < 0) return (float) (1 + distance);
        float tail = (float) (1 - distance / profile.trailLength());
        return tail * tail;
    }

    static int glyphIndex(CodeRainProfile profile, int count, int column, int row, double phase) {
        int tick = (int) ((long) (phase + noise(profile.seed(), column, row, 2)));
        return (int) (count * noise(profile.seed(), column, row, tick ^ 0x97531));
    }

    static float noise(int seed, int column, int row, int tick) {
        int hash = seed ^ (column * 0x9E3779B9) ^ (row * 0x85EBCA6B) ^ (tick * 0xC2B2AE35);
        hash = (hash ^ (hash >>> 16)) * 0x7FEB352D;
        hash = (hash ^ (hash >>> 15)) * 0x846CA68B;
        return (hash >>> 8) * 0x1.0p-24f;
    }

    record Grid(int columns, int rows, float cellSize) {
        static Grid fit(int width, int height, int minimumSize) {
            // 大视口自动增大单元，避免粒度设置导致每帧绘制无界增长。
            double size = Math.max(minimumSize, Math.max(Math.max(width, height) / 128.0,
                    Math.sqrt((double) width * height / 3600)));
            return new Grid(Math.max(1, (int) (width / size)), Math.max(1, (int) (height / size)),
                    (float) Math.min(size, Math.min(width, height)));
        }
    }

    private record DotBatch(int[] indices, float[] halves, int[] colors, int[] glowColors) {
        DotBatch(int capacity) {
            this(new int[capacity], new float[capacity], new int[capacity], new int[capacity]);
        }
    }
}
