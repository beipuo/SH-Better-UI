package com.dishanhai.sh_better_ui.client.gui.scaled;

import java.util.List;
import java.util.Objects;

/** 山海：反重力粒子的不可变配置。距离和大小按区域高度 / 31.5 换算为 GUI 单位。 */
public record AntigravityProfile(
        int count,
        List<Integer> colors,
        ColorMode colorMode,
        Shape particleShape,
        float colorSpeed,
        float particleSize,
        float particleVariance,
        float magnetRadius,
        float ringRadius,
        float fieldStrength,
        float waveAmplitude,
        int waveCount,
        float waveSpeed,
        float rotationSpeed,
        float pulseSpeed,
        float depthFactor,
        float ambient,
        float lerpSpeed,
        boolean autoAnimate,
        boolean interactive,
        boolean clickBurst,
        boolean intro,
        boolean lightMode,
        float opacity) {
    public static final int MAX_PARTICLES = 20_000;
    public static final AntigravityProfile DEFAULT = builder().build();
    public enum ColorMode { CONIC, RADIAL, RANDOM }
    public enum Shape { CAPSULE, SPHERE, BOX, TETRAHEDRON }

    public AntigravityProfile {
        count = Math.clamp(count, 1, MAX_PARTICLES);
        colors = List.copyOf(colors);
        if (colors.isEmpty() || colors.size() > 256) throw new IllegalArgumentException("Palette needs 1..256 colors");
        Objects.requireNonNull(colorMode);
        Objects.requireNonNull(particleShape);
        waveCount = Math.clamp(waveCount, 0, 64);
        colorSpeed = range(colorSpeed, -10f, 10f);
        particleSize = range(particleSize, 0f, 10f);
        particleVariance = range(particleVariance, 0f, 1f);
        magnetRadius = range(magnetRadius, 0f, 100f);
        ringRadius = range(ringRadius, 0f, 100f);
        fieldStrength = range(fieldStrength, 0f, 100f);
        waveAmplitude = range(waveAmplitude, 0f, 20f);
        waveSpeed = range(waveSpeed, -10f, 10f);
        rotationSpeed = range(rotationSpeed, -20f, 20f);
        pulseSpeed = range(pulseSpeed, -20f, 20f);
        depthFactor = range(depthFactor, 0f, 2f);
        ambient = range(ambient, 0f, 1f);
        lerpSpeed = range(lerpSpeed, 0.001f, 1f);
        opacity = range(opacity, 0f, 1f);
    }

    private static float range(float value, float min, float max) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite antigravity parameter");
        return Math.clamp(value, min, max);
    }

    public static Builder builder() { return new Builder(); }
    public Builder toBuilder() { return new Builder(this); }

    public static final class Builder {
        private int count = 1500;
        private List<Integer> colors = List.of(0xFF5227FF, 0xFFFF9FFC, 0xFFB497CF);
        private ColorMode colorMode = ColorMode.CONIC;
        private Shape particleShape = Shape.CAPSULE;
        private float colorSpeed = .05f;
        private float particleSize = .9f;
        private float particleVariance = 1;
        private float magnetRadius = 9;
        private float ringRadius = 7;
        private float fieldStrength = 10;
        private float waveAmplitude = 1;
        private int waveCount = 3;
        private float waveSpeed = .4f;
        private float rotationSpeed = 0;
        private float pulseSpeed = 3;
        private float depthFactor = 1;
        private float ambient = .25f;
        private float lerpSpeed = .05f;
        private boolean autoAnimate = true;
        private boolean interactive = true;
        private boolean clickBurst = true;
        private boolean intro = true;
        private boolean lightMode = false;
        private float opacity = 1;
        private Builder() {}
        private Builder(AntigravityProfile base) {
            count = base.count;
            colors = base.colors;
            colorMode = base.colorMode;
            particleShape = base.particleShape;
            colorSpeed = base.colorSpeed;
            particleSize = base.particleSize;
            particleVariance = base.particleVariance;
            magnetRadius = base.magnetRadius;
            ringRadius = base.ringRadius;
            fieldStrength = base.fieldStrength;
            waveAmplitude = base.waveAmplitude;
            waveCount = base.waveCount;
            waveSpeed = base.waveSpeed;
            rotationSpeed = base.rotationSpeed;
            pulseSpeed = base.pulseSpeed;
            depthFactor = base.depthFactor;
            ambient = base.ambient;
            lerpSpeed = base.lerpSpeed;
            autoAnimate = base.autoAnimate;
            interactive = base.interactive;
            clickBurst = base.clickBurst;
            intro = base.intro;
            lightMode = base.lightMode;
            opacity = base.opacity;
        }
        public Builder count(int value) { count = value; return this; }
        public Builder colors(List<Integer> value) { colors = value; return this; }
        public Builder colorMode(ColorMode value) { colorMode = value; return this; }
        public Builder particleShape(Shape value) { particleShape = value; return this; }
        public Builder colorSpeed(float value) { colorSpeed = value; return this; }
        public Builder particleSize(float value) { particleSize = value; return this; }
        public Builder particleVariance(float value) { particleVariance = value; return this; }
        public Builder magnetRadius(float value) { magnetRadius = value; return this; }
        public Builder ringRadius(float value) { ringRadius = value; return this; }
        public Builder fieldStrength(float value) { fieldStrength = value; return this; }
        public Builder waveAmplitude(float value) { waveAmplitude = value; return this; }
        public Builder waveCount(int value) { waveCount = value; return this; }
        public Builder waveSpeed(float value) { waveSpeed = value; return this; }
        public Builder rotationSpeed(float value) { rotationSpeed = value; return this; }
        public Builder pulseSpeed(float value) { pulseSpeed = value; return this; }
        public Builder depthFactor(float value) { depthFactor = value; return this; }
        public Builder ambient(float value) { ambient = value; return this; }
        public Builder lerpSpeed(float value) { lerpSpeed = value; return this; }
        public Builder autoAnimate(boolean value) { autoAnimate = value; return this; }
        public Builder interactive(boolean value) { interactive = value; return this; }
        public Builder clickBurst(boolean value) { clickBurst = value; return this; }
        public Builder intro(boolean value) { intro = value; return this; }
        public Builder lightMode(boolean value) { lightMode = value; return this; }
        public Builder opacity(float value) { opacity = value; return this; }
        /** 单色覆盖色板；再次调用 colors 可切回多色。 */
        public Builder color(int argb) { return colors(List.of(argb)); }
        public AntigravityProfile build() {
            return new AntigravityProfile(count, colors, colorMode, particleShape, colorSpeed, particleSize, particleVariance, magnetRadius, ringRadius, fieldStrength, waveAmplitude, waveCount, waveSpeed, rotationSpeed, pulseSpeed, depthFactor, ambient, lerpSpeed, autoAnimate, interactive, clickBurst, intro, lightMode, opacity);
        }
    }
}
