package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.scaled.CodeRainEffect.Cell;
import com.dishanhai.sh_better_ui.client.gui.scaled.CodeRainEffect.Frame;
import net.minecraft.util.Mth;

import java.util.Arrays;
import java.util.Objects;

/** 山海：可独立使用或叠加的代码雨扩展。 */
public final class CodeRainEffects {
    private CodeRainEffects() {}

    public enum Shape { HORIZON, PINCH, RISE, ECLIPSE }

    public enum Click { NONE, WAVE, BURST }

    /** 用光形裁切字符场，ASCII 模式同时按光强选择字形。softness 范围为 0–1。 */
    public static CodeRainEffect shape(Shape shape, float softness) {
        return shape(shape, softness, .5f);
    }

    /** flow 范围为 0–2，传 0 固定光形。 */
    public static CodeRainEffect shape(Shape shape, float softness, float flow) {
        Objects.requireNonNull(shape);
        float edge = CodeRainProfile.range(softness, 0, 1);
        float drift = .035f * CodeRainProfile.range(flow, 0, 2);
        return (frame, cell) -> {
            float x = cell.x() / frame.bounds().width();
            float y = cell.y() / frame.bounds().height();
            float aspect = frame.bounds().width() / (float) frame.bounds().height();
            if (drift > 0 && frame.profile().speed() > 0) {
                float phase = (float) (frame.seconds() * frame.profile().speed() * .1 % (2 * Math.PI));
                x += drift * Mth.sin(phase + (y - .5f) * 3);
                y += drift * Mth.sin(phase + (x - .5f) * 3);
            }
            float light = shapeLight(shape, edge, x, y, aspect);
            cell.setBrightness(cell.brightness() * light);
            if (frame.profile().mode() == CodeRainProfile.Mode.ASCII) {
                cell.setGlyphIndex(Math.round(light * (frame.glyphCount() - 1)));
            }
        };
    }

    static float shapeLight(Shape shape, float softness, float x, float y, float aspect) {
        float centeredX = 2 * x - 1;
        float centeredY = 2 * y - 1;
        return switch (shape) {
            case HORIZON -> smooth((.62f + .18f * centeredX * centeredX - y) / (.13f + .4f * softness));
            case PINCH -> {
                float band = (.16f + .23f * centeredX * centeredX) * (.75f + .75f * softness);
                yield smooth(1 - Math.abs(centeredY) / band) * (1 - .4f * centeredX * centeredX);
            }
            case RISE -> smooth(1 - (float) Math.hypot(centeredX * .56f, (1 - y) / (.58f + .36f * softness)));
            case ECLIPSE -> {
                float fit = Math.min(aspect, 1);
                float distance = (float) Math.hypot(centeredX * aspect, centeredY) / fit;
                yield smooth((distance - .42f) / .06f)
                        * smooth(1 - (distance - .48f) / (.16f + .32f * softness));
            }
        };
    }

    /** 以当前单元亮度取样，接受 2–8 个从暗到亮的 ARGB 颜色。 */
    public static CodeRainEffect gradient(int... argb) {
        if (argb.length < 2 || argb.length > 8) throw new IllegalArgumentException("Expected 2 to 8 colors");
        int[] colors = argb.clone();
        return (frame, cell) -> {
            float position = cell.brightness() * (colors.length - 1);
            int lower = Math.min((int) position, colors.length - 2);
            int color = GuiMotion.lerpColor(colors[lower], colors[lower + 1], position - lower);
            int alpha = ((color >>> 24) * (cell.argb() >>> 24) + 127) / 255;
            cell.setArgb(alpha << 24 | (color & 0xFFFFFF));
        };
    }

    /** 用柔和的偏移字形补光，强度范围为 0–1。 */
    public static CodeRainEffect glow(float strength) {
        float amount = CodeRainProfile.range(strength, 0, 1);
        return (frame, cell) -> cell.setGlow(amount);
    }

    /** 用方点代替全部或部分字形。size 为点宽占单元的比例，coverage 为替换比例，均为 0–1。 */
    public static CodeRainEffect dotMatrix(float size, float coverage) {
        float width = CodeRainProfile.range(size, 0, 1);
        float ratio = CodeRainProfile.range(coverage, 0, 1);
        return (frame, cell) -> {
            if (CodeRain.noise(frame.profile().seed(), cell.column(), cell.row(), 0xD075) < ratio) {
                cell.setDotSize(width);
            }
        };
    }

    /** 由上向下淡入并解码。时长使用秒，允许 0–10。 */
    public static CodeRainEffect intro(float duration) {
        float seconds = CodeRainProfile.range(duration, 0, 10);
        return (frame, cell) -> {
            if (seconds == 0 || frame.reducedMotion() || frame.seconds() >= seconds) return;
            float delay = .6f * cell.y() / frame.bounds().height();
            float amount = smooth(((float) (frame.seconds() / seconds) - delay) / .4f);
            cell.setBrightness(cell.brightness() * amount);
            if (amount < .9f) scramble(frame, cell, (int) (frame.seconds() * 18));
        };
    }

    /** 以低幅度、错相的亮度变化增加字符闪动，强度范围为 0–1。 */
    public static CodeRainEffect flicker(float strength) {
        float amount = CodeRainProfile.range(strength, 0, 1);
        return (frame, cell) -> {
            if (frame.reducedMotion()) return;
            double phase = CodeRain.noise(frame.profile().seed(), cell.column(), cell.row(), 3);
            float shimmer = .5f + .5f * Mth.sin((float) (2 * Math.PI * ((frame.seconds() * 2 + phase) % 1)));
            cell.setBrightness(cell.brightness() * (1 - .32f * amount * shimmer));
        };
    }

    /** 创建独立互动状态。radius 为区域短边的比例，允许 .01–.5。 */
    public static Interaction interaction(boolean cursorScramble, Click click, float radius) {
        return new Interaction(cursorScramble, Objects.requireNonNull(click), CodeRainProfile.range(radius, .01f, .5f));
    }

    private static void scramble(Frame frame, Cell cell, int tick) {
        float noise = CodeRain.noise(frame.profile().seed(), cell.column(), cell.row(), tick ^ 0x5273);
        cell.setGlyphIndex((int) (noise * frame.glyphCount()));
    }

    private static float smooth(float value) {
        float t = GuiMotion.clamp01(value);
        return t * t * (3 - 2 * t);
    }

    /** 一次互动最多保留八个鼠标轨迹点与四个点击波，适合由一个区域独占。 */
    public static final class Interaction implements CodeRainEffect {
        private static final double TRAIL_SECONDS = .65;
        private static final double CLICK_SECONDS = 1.1;
        private final boolean cursorScramble;
        private final Click click;
        private final float radius;
        private final Point[] trail = new Point[8];
        private final Point[] clicks = new Point[4];
        private int trailIndex;
        private int clickIndex;
        private Frame frame;
        private float localRadius;
        private float waveDistance;
        private boolean pointerAvailable;

        private Interaction(boolean cursorScramble, Click click, float radius) {
            this.cursorScramble = cursorScramble;
            this.click = click;
            this.radius = radius;
        }

        @Override
        public boolean usesPointer() { return cursorScramble || click != Click.NONE; }

        @Override
        public void beginFrame(Frame frame) {
            this.frame = frame;
            boolean available = frame.pointer() != null;
            if (frame.reducedMotion() || pointerAvailable && !available) clearPoints();
            pointerAvailable = available;
            localRadius = radius * Math.min(frame.bounds().width(), frame.bounds().height());
            waveDistance = (float) Math.hypot(frame.bounds().width(), frame.bounds().height());
            var input = frame.pointer();
            if (frame.reducedMotion() || input == null) return;
            if (input.pressed()) pulse(input.x(), input.y());
            if (!cursorScramble || !input.inside() || input.idleMillis() != 0) return;
            Point previous = trail[(trailIndex + trail.length - 1) % trail.length];
            if (previous != null && (frame.seconds() - previous.seconds < .025
                    || previous.x == input.x() && previous.y == input.y())) return;
            trail[trailIndex] = new Point(input.x(), input.y(), frame.seconds());
            trailIndex = (trailIndex + 1) % trail.length;
        }

        /** 供宿主动作或键盘触发。x/y 为最近绘制区域内的局部 GUI 坐标。 */
        public void pulse(float x, float y) {
            if (frame == null || frame.reducedMotion() || click == Click.NONE
                    || !Float.isFinite(x) || !Float.isFinite(y)
                    || x < 0 || x >= frame.bounds().width() || y < 0 || y >= frame.bounds().height()) return;
            clicks[clickIndex] = new Point(x, y, frame.seconds());
            clickIndex = (clickIndex + 1) % clicks.length;
        }

        @Override
        public void apply(Frame frame, Cell cell) {
            if (frame.reducedMotion()) return;
            float strength = strength(cell, trail, TRAIL_SECONDS, false);
            strength = Math.max(strength, strength(cell, clicks, CLICK_SECONDS, click == Click.WAVE));
            if (strength <= .04f) return;
            int tick = (int) (frame.seconds() * 18);
            if (CodeRain.noise(frame.profile().seed(), cell.column(), cell.row(), tick) < strength) {
                scramble(frame, cell, tick);
                cell.setBrightness(cell.brightness() * (1 + .4f * strength));
            }
        }

        @Override
        public void suspend() {
            frame = null;
            pointerAvailable = false;
            clearPoints();
        }

        private void clearPoints() {
            Arrays.fill(trail, null);
            Arrays.fill(clicks, null);
            trailIndex = clickIndex = 0;
        }

        private float strength(Cell cell, Point[] points, double duration, boolean wave) {
            float result = 0;
            for (Point point : points) {
                if (point == null) continue;
                float age = (float) ((frame.seconds() - point.seconds) / duration);
                if (age >= 1) continue;
                float dx = cell.x() - point.x;
                float dy = cell.y() - point.y;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float radius = localRadius * (wave ? .55f : 1);
                if (wave) distance = Math.abs(distance - age * waveDistance);
                result = Math.max(result, smooth(1 - distance / radius) * (1 - age));
            }
            return result;
        }

        private record Point(float x, float y, double seconds) {}
    }
}
