package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.util.Mth;

import java.util.Objects;

/** 山海：图案波面的可复用配置，距离使用 GUI 逻辑单位，颜色使用 ARGB。 */
public record PatternWavesProfile(Pattern pattern, Wave wave, float spacing, float markSize,
                                  float depth, float light, float shine, float contrast,
                                  float speed, float scale, float direction, int color, int backgroundColor,
                                  float opacity, Fade fade, float fadeSize, String characters,
                                  boolean interactive, float cursorSize, float cursorStrength, boolean intro) {
    // 顺序与两段 pattern_waves GLSL 的编号一致。
    public enum Pattern { DOT, SQUARE, PLUS, LINE, GLYPH }
    public enum Wave { SILK, SWELL, RIPPLE }
    public enum Fade { NONE, EDGES, CENTER, BOTTOM, TOP }

    public enum Preset {
        SILK(Pattern.DOT, Wave.SILK, 9, .95f, .95f, .8f, 1.2f, .35f, 1f, 20),
        OCEAN(Pattern.DOT, Wave.SWELL, 10, .9f, .42f, 1f, 1.2f, .5f, 1f, 100),
        POND(Pattern.DOT, Wave.RIPPLE, 10, .9f, .55f, 1.2f, 1.2f, .5f, 1f, 35),
        LINES(Pattern.LINE, Wave.SILK, 12, .42f, .9f, .6f, 1.2f, .3f, 1f, 20),
        TERMINAL(Pattern.GLYPH, Wave.RIPPLE, 13, .95f, .75f, .9f, 1.4f, .3f, 1.1f, 200),
        MESH(Pattern.PLUS, Wave.SILK, 14, .8f, .95f, 1f, 1.45f, .4f, 1.1f, 325);

        private final PatternWavesProfile profile;

        Preset(Pattern pattern, Wave wave, float spacing, float markSize, float depth,
               float shine, float contrast, float speed, float scale, float direction) {
            profile = new PatternWavesProfile(pattern, wave, spacing, markSize, depth, 0, shine, contrast,
                    speed, scale, direction, 0xFFFFFFFF, 0xFF000000, 1, Fade.EDGES, .5f,
                    DEFAULT_CHARACTERS, true, 50, .6f, true);
        }

        public PatternWavesProfile profile() { return profile; }
    }

    private static final String DEFAULT_CHARACTERS = ".:-=+*#%@";
    static final int MAX_GLYPHS = 64;

    public PatternWavesProfile {
        Objects.requireNonNull(pattern);
        Objects.requireNonNull(wave);
        Objects.requireNonNull(fade);
        Objects.requireNonNull(characters);
        if (characters.isEmpty()) characters = DEFAULT_CHARACTERS;
        if (characters.codePointCount(0, characters.length()) > MAX_GLYPHS) {
            throw new IllegalArgumentException("Too many pattern glyphs: " + MAX_GLYPHS);
        }
        spacing = range(spacing, 4, 120);
        markSize = range(markSize, .05f, 1);
        depth = range(depth, 0, 1.5f);
        light = range(light, -90, 90);
        shine = range(shine, 0, 2);
        contrast = range(contrast, .3f, 3);
        speed = range(speed, 0, 10);
        scale = range(scale, .2f, 5);
        if (!Float.isFinite(direction)) throw new IllegalArgumentException("Non-finite direction");
        direction %= 360;
        opacity = range(opacity, 0, 1);
        fadeSize = range(fadeSize, .05f, 1);
        cursorSize = range(cursorSize, 8, 400);
        cursorStrength = range(cursorStrength, 0, 1);
    }

    public static Builder builder(Preset preset) { return new Builder(preset.profile()); }

    private static float range(float value, float min, float max) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite pattern parameter");
        return Mth.clamp(value, min, max);
    }

    public static final class Builder {
        private Pattern pattern;
        private Wave wave;
        private float spacing, markSize, depth, light, shine, contrast, speed, scale, direction;
        private int color, backgroundColor;
        private float opacity, fadeSize;
        private Fade fade;
        private String characters;
        private boolean interactive, intro;
        private float cursorSize, cursorStrength;

        private Builder(PatternWavesProfile base) {
            pattern = base.pattern;
            wave = base.wave;
            spacing = base.spacing;
            markSize = base.markSize;
            depth = base.depth;
            light = base.light;
            shine = base.shine;
            contrast = base.contrast;
            speed = base.speed;
            scale = base.scale;
            direction = base.direction;
            color = base.color;
            backgroundColor = base.backgroundColor;
            opacity = base.opacity;
            fade = base.fade;
            fadeSize = base.fadeSize;
            characters = base.characters;
            interactive = base.interactive;
            intro = base.intro;
            cursorSize = base.cursorSize;
            cursorStrength = base.cursorStrength;
        }

        public Builder pattern(Pattern value) { pattern = value; return this; }
        public Builder wave(Wave value) { wave = value; return this; }
        public Builder spacing(float value) { spacing = value; return this; }
        public Builder markSize(float value) { markSize = value; return this; }
        public Builder depth(float value) { depth = value; return this; }
        public Builder light(float value) { light = value; return this; }
        public Builder shine(float value) { shine = value; return this; }
        public Builder contrast(float value) { contrast = value; return this; }
        public Builder speed(float value) { speed = value; return this; }
        public Builder scale(float value) { scale = value; return this; }
        public Builder direction(float value) { direction = value; return this; }
        public Builder color(int value) { color = value; return this; }
        public Builder backgroundColor(int value) { backgroundColor = value; return this; }
        public Builder opacity(float value) { opacity = value; return this; }
        public Builder fade(Fade value) { fade = value; return this; }
        public Builder fadeSize(float value) { fadeSize = value; return this; }
        public Builder characters(String value) { characters = value; return this; }
        public Builder interactive(boolean value) { interactive = value; return this; }
        public Builder cursorSize(float value) { cursorSize = value; return this; }
        public Builder cursorStrength(float value) { cursorStrength = value; return this; }
        public Builder intro(boolean value) { intro = value; return this; }

        public PatternWavesProfile build() {
            return new PatternWavesProfile(pattern, wave, spacing, markSize, depth, light, shine, contrast,
                    speed, scale, direction, color, backgroundColor, opacity, fade, fadeSize, characters,
                    interactive, cursorSize, cursorStrength, intro);
        }
    }
}
