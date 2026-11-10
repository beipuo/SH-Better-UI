package com.dishanhai.sh_better_ui.client.gui.scaled;

import java.util.Objects;

/** 山海：代码雨的不可变配置。单元尺寸以 GUI 单位计，颜色为 ARGB。 */
public record CodeRainProfile(Mode mode, String characters, int cellSize, float density,
                              float speed, int trailLength, int color, int seed) {
    public static final CodeRainProfile DEFAULT = builder().build();
    public static final CodeRainProfile ASCII = builder().mode(Mode.ASCII).build();

    /** RAIN 为下落列，ASCII 为原地变化的字符场。 */
    public enum Mode { RAIN, ASCII }

    public CodeRainProfile {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(characters);
        int count = characters.codePointCount(0, characters.length());
        if (count < 1 || count > 128 || characters.codePoints().anyMatch(codePoint ->
                Character.isISOControl(codePoint) || codePoint == 0xA7
                        || codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
            throw new IllegalArgumentException("Expected 1 to 128 renderable code points");
        }
        cellSize = UiMath.clamp(cellSize, 6, 128);
        density = range(density, 0, 1);
        speed = range(speed, 0, 64);
        trailLength = UiMath.clamp(trailLength, 1, 128);
    }

    static float range(float value, float min, float max) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite code rain parameter");
        return UiMath.clamp(value, min, max);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Mode mode = Mode.RAIN;
        // 字形素材，按从疏到密排列，供渐变遮罩按亮度选字。
        private String characters = ".:+=xX0S8#@";
        private int cellSize = 12;
        private float density = .85f;
        private float speed = 8;
        private int trailLength = 12;
        private int color = 0xFF56F6A0;
        private int seed;

        public Builder mode(Mode value) { mode = value; return this; }
        public Builder characters(String value) { characters = value; return this; }
        public Builder cellSize(int value) { cellSize = value; return this; }
        public Builder density(float value) { density = value; return this; }
        /** RAIN 以每秒行数计，ASCII 以每秒换字次数计。 */
        public Builder speed(float value) { speed = value; return this; }
        public Builder trailLength(int value) { trailLength = value; return this; }
        public Builder color(int value) { color = value; return this; }
        public Builder seed(int value) { seed = value; return this; }

        public CodeRainProfile build() {
            return new CodeRainProfile(mode, characters, cellSize, density, speed, trailLength, color, seed);
        }
    }
}
