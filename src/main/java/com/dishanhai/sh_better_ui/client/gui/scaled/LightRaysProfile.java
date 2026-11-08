package com.dishanhai.sh_better_ui.client.gui.scaled;

import java.util.Objects;

/** 山海：光束的不可变配置。颜色使用 ARGB，倾斜使用角度。 */
public record LightRaysProfile(Origin raysOrigin, int raysColor, float raysSpeed, float lightSpread,
                               float rayLength, float rayDensity, float tilt, float raySharpness,
                               float glow, float haze, float atmosphere, float intensity, boolean pulsating,
                               float fadeDistance, float saturation, boolean followMouse, float mouseInfluence,
                               boolean cursorShadow, boolean clickPulse, float noiseAmount, float distortion,
                               boolean intro, boolean lightMode, float opacity) {
    public enum Origin {
        TOP_LEFT(0, -.2f, 0, 1),
        TOP_CENTER(.5f, -.2f, 0, 1),
        TOP_RIGHT(1, -.2f, 0, 1),
        LEFT(-.2f, .5f, 1, 0),
        RIGHT(1.2f, .5f, -1, 0),
        BOTTOM_LEFT(0, 1.2f, 0, -1),
        BOTTOM_CENTER(.5f, 1.2f, 0, -1),
        BOTTOM_RIGHT(1, 1.2f, 0, -1);

        final float x;
        final float y;
        final float directionX;
        final float directionY;

        Origin(float x, float y, float directionX, float directionY) {
            this.x = x;
            this.y = y;
            this.directionX = directionX;
            this.directionY = directionY;
        }
    }

    public static final LightRaysProfile DEFAULT = builder().build();

    public LightRaysProfile {
        Objects.requireNonNull(raysOrigin);
        raysSpeed = range(raysSpeed, 0, 10);
        lightSpread = range(lightSpread, .001f, 10);
        rayLength = range(rayLength, .01f, 20);
        rayDensity = range(rayDensity, .1f, 6);
        tilt = range(tilt, -90, 90);
        raySharpness = range(raySharpness, 0, 1);
        glow = range(glow, 0, 10);
        haze = range(haze, 0, 1);
        atmosphere = range(atmosphere, 0, 1);
        intensity = range(intensity, 0, 10);
        fadeDistance = range(fadeDistance, .01f, 20);
        saturation = range(saturation, 0, 4);
        mouseInfluence = range(mouseInfluence, 0, 1);
        noiseAmount = range(noiseAmount, 0, 1);
        distortion = range(distortion, -4, 4);
        opacity = range(opacity, 0, 1);
    }

    public static Builder builder() { return new Builder(); }

    private static float range(float value, float min, float max) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite light rays parameter");
        return Math.clamp(value, min, max);
    }

    public static final class Builder {
        private Origin raysOrigin = Origin.TOP_CENTER;
        private int raysColor = 0xFFFFFFFF;
        private float raysSpeed = 1f;
        private float lightSpread = .5f;
        private float rayLength = 3f;
        private float rayDensity = 1f;
        private float tilt;
        private float raySharpness = .35f;
        private float glow = .6f;
        private float haze = .25f;
        private float atmosphere = 1f;
        private float intensity = 1f;
        private boolean pulsating;
        private float fadeDistance = 1f;
        private float saturation = 1f;
        private boolean followMouse = true;
        private float mouseInfluence = .1f;
        private boolean cursorShadow = true;
        private boolean clickPulse = true;
        private float noiseAmount;
        private float distortion;
        private boolean intro = true;
        private boolean lightMode;
        private float opacity = 1f;

        public Builder raysOrigin(Origin value) { raysOrigin = value; return this; }
        public Builder raysColor(int value) { raysColor = value; return this; }
        public Builder raysSpeed(float value) { raysSpeed = value; return this; }
        public Builder lightSpread(float value) { lightSpread = value; return this; }
        public Builder rayLength(float value) { rayLength = value; return this; }
        public Builder rayDensity(float value) { rayDensity = value; return this; }
        public Builder tilt(float value) { tilt = value; return this; }
        public Builder raySharpness(float value) { raySharpness = value; return this; }
        public Builder glow(float value) { glow = value; return this; }
        public Builder haze(float value) { haze = value; return this; }
        public Builder atmosphere(float value) { atmosphere = value; return this; }
        public Builder intensity(float value) { intensity = value; return this; }
        public Builder pulsating(boolean value) { pulsating = value; return this; }
        public Builder fadeDistance(float value) { fadeDistance = value; return this; }
        public Builder saturation(float value) { saturation = value; return this; }
        public Builder followMouse(boolean value) { followMouse = value; return this; }
        public Builder mouseInfluence(float value) { mouseInfluence = value; return this; }
        public Builder cursorShadow(boolean value) { cursorShadow = value; return this; }
        public Builder clickPulse(boolean value) { clickPulse = value; return this; }
        public Builder noiseAmount(float value) { noiseAmount = value; return this; }
        public Builder distortion(float value) { distortion = value; return this; }
        public Builder intro(boolean value) { intro = value; return this; }
        public Builder lightMode(boolean value) { lightMode = value; return this; }
        public Builder opacity(float value) { opacity = value; return this; }

        public LightRaysProfile build() {
            return new LightRaysProfile(raysOrigin, raysColor, raysSpeed, lightSpread, rayLength, rayDensity, tilt,
                    raySharpness, glow, haze, atmosphere, intensity, pulsating, fadeDistance, saturation,
                    followMouse, mouseInfluence, cursorShadow, clickPulse, noiseAmount, distortion, intro, lightMode,
                    opacity);
        }
    }
}
