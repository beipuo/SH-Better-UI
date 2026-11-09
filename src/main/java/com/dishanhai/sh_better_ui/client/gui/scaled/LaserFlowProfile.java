package com.dishanhai.sh_better_ui.client.gui.scaled;

/** 山海：激光流的不可变配置。颜色使用 ARGB，落点使用 0–1 的区域比例。 */
public record LaserFlowProfile(float beamPosition, float surfaceLevel, int color, float intensity,
                               float beamWidth, float flare, float spread, float spill, float fog,
                               float dust, float streaks, float dots, float pulse, float speed, int seed,
                               boolean mouseInteraction, boolean intro, boolean lightMode, boolean paused) {
    public static final LaserFlowProfile DEFAULT = new LaserFlowProfile(.6f, 1f, 0xFF3847FF, 1.8f,
            1.5f, 1.5f, 1.5f, 1f, .2f, 1.6f, 1.5f, 1f, 1.5f, 1.5f, 3,
            true, true, false, false);

    public LaserFlowProfile {
        beamPosition = range(beamPosition, 0, 1);
        surfaceLevel = range(surfaceLevel, 0, 1);
        intensity = range(intensity, 0, 8);
        beamWidth = range(beamWidth, .1f, 8);
        flare = range(flare, 0, 8);
        spread = range(spread, 0, 8);
        spill = range(spill, 0, 8);
        fog = range(fog, 0, 4);
        dust = range(dust, 0, 8);
        streaks = range(streaks, 0, 8);
        dots = range(dots, 0, 8);
        pulse = range(pulse, 0, 8);
        speed = range(speed, 0, 8);
    }

    public static Builder builder() { return new Builder(DEFAULT); }

    public Builder toBuilder() { return new Builder(this); }

    private static float range(float value, float min, float max) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite laser flow parameter");
        return UiMath.clamp(value, min, max);
    }

    public static final class Builder {
        private float beamPosition;
        private float surfaceLevel;
        private int color;
        private float intensity;
        private float beamWidth;
        private float flare;
        private float spread;
        private float spill;
        private float fog;
        private float dust;
        private float streaks;
        private float dots;
        private float pulse;
        private float speed;
        private int seed;
        private boolean mouseInteraction;
        private boolean intro;
        private boolean lightMode;
        private boolean paused;

        private Builder(LaserFlowProfile profile) {
            beamPosition = profile.beamPosition;
            surfaceLevel = profile.surfaceLevel;
            color = profile.color;
            intensity = profile.intensity;
            beamWidth = profile.beamWidth;
            flare = profile.flare;
            spread = profile.spread;
            spill = profile.spill;
            fog = profile.fog;
            dust = profile.dust;
            streaks = profile.streaks;
            dots = profile.dots;
            pulse = profile.pulse;
            speed = profile.speed;
            seed = profile.seed;
            mouseInteraction = profile.mouseInteraction;
            intro = profile.intro;
            lightMode = profile.lightMode;
            paused = profile.paused;
        }

        public Builder beamPosition(float value) { beamPosition = value; return this; }
        public Builder surfaceLevel(float value) { surfaceLevel = value; return this; }
        public Builder color(int value) { color = value; return this; }
        public Builder intensity(float value) { intensity = value; return this; }
        public Builder beamWidth(float value) { beamWidth = value; return this; }
        public Builder flare(float value) { flare = value; return this; }
        public Builder spread(float value) { spread = value; return this; }
        public Builder spill(float value) { spill = value; return this; }
        public Builder fog(float value) { fog = value; return this; }
        public Builder dust(float value) { dust = value; return this; }
        public Builder streaks(float value) { streaks = value; return this; }
        public Builder dots(float value) { dots = value; return this; }
        public Builder pulse(float value) { pulse = value; return this; }
        public Builder speed(float value) { speed = value; return this; }
        public Builder seed(int value) { seed = value; return this; }
        public Builder mouseInteraction(boolean value) { mouseInteraction = value; return this; }
        public Builder intro(boolean value) { intro = value; return this; }
        public Builder lightMode(boolean value) { lightMode = value; return this; }
        public Builder paused(boolean value) { paused = value; return this; }

        public LaserFlowProfile build() {
            return new LaserFlowProfile(beamPosition, surfaceLevel, color, intensity, beamWidth, flare,
                    spread, spill, fog, dust, streaks, dots, pulse, speed, seed, mouseInteraction, intro,
                    lightMode, paused);
        }
    }
}
