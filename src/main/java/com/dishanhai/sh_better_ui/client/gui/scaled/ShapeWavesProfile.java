package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.util.Mth;

import java.util.Objects;

/** 山海：形状波面的配置。距离使用 GUI 逻辑单位，颜色使用 ARGB。 */
public record ShapeWavesProfile(String text, String fontFamily, int fontWeight, float textSize, Shapes shapes,
                                float cellSize, float dotSize, int color, int hoverColor, int backgroundColor,
                                float speed, float scale, float contrast, float brightness, float flow,
                                float direction, float fade, boolean interactive, float splashRadius,
                                float splashStrength, float glow, boolean intro, float introDuration) {
    // 顺序与 shape_waves GLSL 的模式编号一致。
    public enum Shapes { MIXED, SQUARES, CIRCLES, TRIANGLES }

    public static final ShapeWavesProfile DEFAULT = builder().build();

    public ShapeWavesProfile {
        text = Objects.requireNonNull(text).strip();
        fontFamily = Objects.requireNonNull(fontFamily).strip();
        if (fontFamily.isEmpty()) fontFamily = "SansSerif";
        fontWeight = Mth.clamp(fontWeight, 100, 900);
        textSize = range(textSize, .05f, 1f);
        Objects.requireNonNull(shapes);
        cellSize = range(cellSize, 4f, 120f);
        dotSize = range(dotSize, .05f, 1f);
        speed = range(speed, 0f, 10f);
        scale = range(scale, .2f, 5f);
        contrast = range(contrast, 0f, 5f);
        brightness = range(brightness, 0f, 1f);
        flow = range(flow, 0f, 120f);
        if (!Float.isFinite(direction)) throw new IllegalArgumentException("Non-finite shape waves direction");
        direction %= 360f;
        fade = range(fade, 0f, 1f);
        splashRadius = range(splashRadius, 8f, 400f);
        splashStrength = range(splashStrength, 0f, 1f);
        glow = range(glow, 0f, 1f);
        introDuration = range(introDuration, .05f, 30f);
    }

    public static Builder builder() { return new Builder(); }

    private static float range(float value, float min, float max) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite shape waves parameter");
        return Mth.clamp(value, min, max);
    }

    public static final class Builder {
        private String text = "";
        private String fontFamily = "SansSerif";
        private int fontWeight = 500;
        private float textSize = .6f;
        private Shapes shapes = Shapes.MIXED;
        private float cellSize = 10f;
        private float dotSize = .75f;
        private int color = 0xFF929292;
        private int hoverColor = 0xFFFFFFFF;
        private int backgroundColor = 0xFF000000;
        private float speed = 1f;
        private float scale = 1f;
        private float contrast = 1f;
        private float brightness = .4f;
        private float flow;
        private float direction;
        private float fade = .25f;
        private boolean interactive = true;
        private float splashRadius = 40f;
        private float splashStrength = .4f;
        private float glow = .35f;
        private boolean intro = true;
        private float introDuration = 1.6f;

        /** 传入调用方已完成本地化的镂空文字。 */
        public Builder text(String value) { text = value; return this; }
        /** AWT 字体族名，例如 SansSerif。 */
        public Builder fontFamily(String value) { fontFamily = value; return this; }
        /** 字重范围为 100 到 900。 */
        public Builder fontWeight(int value) { fontWeight = value; return this; }
        /** 字形占区域高度的比例。 */
        public Builder textSize(float value) { textSize = value; return this; }
        public Builder shapes(Shapes value) { shapes = value; return this; }
        public Builder cellSize(float value) { cellSize = value; return this; }
        public Builder dotSize(float value) { dotSize = value; return this; }
        public Builder color(int value) { color = value; return this; }
        public Builder hoverColor(int value) { hoverColor = value; return this; }
        public Builder backgroundColor(int value) { backgroundColor = value; return this; }
        public Builder speed(float value) { speed = value; return this; }
        public Builder scale(float value) { scale = value; return this; }
        public Builder contrast(float value) { contrast = value; return this; }
        public Builder brightness(float value) { brightness = value; return this; }
        /** 波面每秒漂移的格数。 */
        public Builder flow(float value) { flow = value; return this; }
        /** 0 度朝右，90 度朝下。 */
        public Builder direction(float value) { direction = value; return this; }
        public Builder fade(float value) { fade = value; return this; }
        public Builder interactive(boolean value) { interactive = value; return this; }
        public Builder splashRadius(float value) { splashRadius = value; return this; }
        public Builder splashStrength(float value) { splashStrength = value; return this; }
        public Builder glow(float value) { glow = value; return this; }
        public Builder intro(boolean value) { intro = value; return this; }
        /** 入场时长，单位为秒。 */
        public Builder introDuration(float value) { introDuration = value; return this; }

        public ShapeWavesProfile build() {
            return new ShapeWavesProfile(text, fontFamily, fontWeight, textSize, shapes, cellSize, dotSize,
                    color, hoverColor, backgroundColor, speed, scale, contrast, brightness, flow, direction,
                    fade, interactive, splashRadius, splashStrength, glow, intro, introDuration);
        }
    }
}
