package com.dishanhai.sh_better_ui.client.gui.scaled;

import java.util.List;
import java.util.Objects;

/** 山海：点阵背景的配色、网格、动画与指针反馈设置。尺寸均使用 GUI 单位。 */
public record DotMatrixProfile(Palette palette, Grid grid, Motion motion, Interaction interaction,
                               Fade fade, Gradient gradient) {
    public static final DotMatrixProfile VIOLET = new DotMatrixProfile(
            new Palette(0xFF050208, 0xB06D249A, 0xF0D267FA, 0.7f),
            new Grid(8, 3, 0.36f, 27L),
            new Motion(Effect.TWINKLE, 2800, 0.09f, 36, 9f),
            new Interaction(38, 96));

    public DotMatrixProfile {
        Objects.requireNonNull(palette);
        Objects.requireNonNull(grid);
        Objects.requireNonNull(motion);
        Objects.requireNonNull(interaction);
        Objects.requireNonNull(fade);
        Objects.requireNonNull(gradient);
    }

    public DotMatrixProfile(Palette palette, Grid grid, Motion motion, Interaction interaction) {
        this(palette, grid, motion, interaction, Fade.NONE, Gradient.NONE);
    }

    public DotMatrixProfile withPalette(Palette value) {
        return new DotMatrixProfile(value, grid, motion, interaction, fade, gradient);
    }

    public DotMatrixProfile withGrid(Grid value) {
        return new DotMatrixProfile(palette, value, motion, interaction, fade, gradient);
    }

    public DotMatrixProfile withMotion(Motion value) {
        return new DotMatrixProfile(palette, grid, value, interaction, fade, gradient);
    }

    public DotMatrixProfile withInteraction(Interaction value) {
        return new DotMatrixProfile(palette, grid, motion, value, fade, gradient);
    }

    public DotMatrixProfile withFade(Fade value) {
        return new DotMatrixProfile(palette, grid, motion, interaction, value, gradient);
    }

    public DotMatrixProfile withGradient(Gradient value) {
        return new DotMatrixProfile(palette, grid, motion, interaction, fade, value);
    }

    /** 颜色为 ARGB；底色可透明。glow 为光晕强度，取值 0..1。 */
    public record Palette(int backgroundArgb, int dotArgb, int highlightArgb, float glow) {
        public Palette {
            if (!(glow >= 0f && glow <= 1f)) throw new IllegalArgumentException();
        }
    }

    /** spacing 和 dotSize 为正整数，且 dotSize 不超过 spacing。density 取值 0..1。 */
    public record Grid(int spacing, int dotSize, float density, long seed) {
        public Grid {
            if (dotSize <= 0 || dotSize > spacing || !(density >= 0f && density <= 1f)) {
                throw new IllegalArgumentException();
            }
        }
    }

    /** 每轮为每个点抽签一次，再从中选出至多 maxFloatingDots 个点。浮动参数为零时关闭位移。 */
    public record Motion(Effect effect, int periodMs, float floatProbability, int maxFloatingDots,
                         float floatDistance) {
        public Motion {
            Objects.requireNonNull(effect);
            if (periodMs <= 0 || !(floatProbability >= 0f && floatProbability <= 1f) || maxFloatingDots < 0
                    || !Float.isFinite(floatDistance) || floatDistance < 0f) {
                throw new IllegalArgumentException();
            }
        }
    }

    /** 各半径为非负整数。设为零可分别关闭悬停高亮和点击波纹。 */
    public record Interaction(int hoverRadius, int rippleRadius) {
        public Interaction {
            if (hoverRadius < 0 || rippleRadius < 0) throw new IllegalArgumentException();
        }
    }

    /** 每个点独立抽签，入选后淡出、短暂消失并在同一格重新浮现。概率取值 0..1，周期为正毫秒数。 */
    public record Fade(float probability, int periodMs) {
        public static final Fade NONE = new Fade(0f, 1);

        public Fade {
            if (!(probability >= 0f && probability <= 1f) || periodMs <= 0) {
                throw new IllegalArgumentException();
            }
        }
    }

    /** ARGB 色标在循环中的位置，取值 [0, 1)。 */
    public record ColorStop(float position, int argb) {
        public ColorStop {
            if (!(position >= 0f && position < 1f)) throw new IllegalArgumentException();
        }
    }

    /** 首个色标必须在 0，其余严格递增。末色渐变回首色。色标透明度乘以原点色透明度。空色板关闭渐变。 */
    public record Gradient(List<ColorStop> stops, int periodMs, Flow flow) {
        public static final Gradient NONE = new Gradient(List.of(), 1, Flow.RIGHT);
        public static final Gradient RAINBOW = new Gradient(List.of(
                new ColorStop(0f, 0xFFFF476B), new ColorStop(1f / 7f, 0xFFFF9C42),
                new ColorStop(2f / 7f, 0xFFFFE773), new ColorStop(3f / 7f, 0xFF57E58D),
                new ColorStop(4f / 7f, 0xFF41D9F5), new ColorStop(5f / 7f, 0xFF627BFF),
                new ColorStop(6f / 7f, 0xFFC567FF)), 5000, Flow.RIGHT);

        public Gradient {
            stops = List.copyOf(stops);
            Objects.requireNonNull(flow);
            if (periodMs <= 0 || (!stops.isEmpty() && stops.getFirst().position() != 0f)) {
                throw new IllegalArgumentException();
            }
            for (int i = 1; i < stops.size(); i++) {
                if (stops.get(i - 1).position() >= stops.get(i).position()) throw new IllegalArgumentException();
            }
        }

        int sample(float x, float y, float phase) {
            float value = flow.position(x, y, phase);
            double position = Float.isFinite(value) ? value - Math.floor(value) : 0d;
            int low = 0, high = stops.size();
            while (high - low > 1) {
                int middle = (low + high) >>> 1;
                if (position < stops.get(middle).position()) high = middle;
                else low = middle;
            }
            ColorStop from = stops.get(low);
            boolean wrap = high == stops.size();
            ColorStop to = wrap ? stops.getFirst() : stops.get(high);
            float end = wrap ? 1f : to.position();
            return GuiMotion.lerpColor(from.argb(), to.argb(), (float) ((position - from.position())
                    / (end - from.position())));
        }
    }

    @FunctionalInterface
    public interface Flow {
        Flow RIGHT = (x, y, phase) -> x - phase;
        Flow DOWN = (x, y, phase) -> y - phase;
        Flow DIAGONAL = (x, y, phase) -> (x + y) * 0.5f - phase;
        Flow OUTWARD = (x, y, phase) -> {
            float dx = x - 0.5f, dy = y - 0.5f;
            return (float) Math.sqrt(2f * (dx * dx + dy * dy)) - phase;
        };

        /** 以网格锚点和周期进度采样，三者归一化。返回色标位置，按 1 循环，非有限结果取首色。 */
        float position(float x, float y, float phase);
    }

    @FunctionalInterface
    public interface Effect {
        Effect STEADY = (x, y, phase, variation) -> 0f;
        Effect TWINKLE = (x, y, phase, variation) -> {
            float light = crest(phase + variation);
            return 0.44f * light * light;
        };
        Effect WAVE = (x, y, phase, variation) -> 0.63f * crest(phase - x - y * 0.65f);

        /** 在渲染线程采样。坐标和周期进度均为 0..1，variation 是该点固定的随机值。返回额外的高亮强度 0..1。 */
        float brightness(float x, float y, float phase, float variation);

        private static float crest(float phase) {
            return 0.5f + 0.5f * (float) Math.cos(2d * Math.PI * phase);
        }
    }
}
