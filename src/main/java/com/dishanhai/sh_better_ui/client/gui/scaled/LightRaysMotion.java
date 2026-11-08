package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.util.Mth;

import java.util.Arrays;

/** 山海：区域光束的时间轴和指针插值，不依赖渲染上下文。 */
final class LightRaysMotion {
    static final float PULSE_SECONDS = 2f;
    final float[] pulseAges = {-1, -1, -1, -1};
    float phase;
    float opening;
    float pointerX = .5f;
    float pointerY = .5f;
    float aim;
    float shadow;

    void advance(LightRaysProfile profile, UiPointerTracker.Frame input, int width, int height, float dt) {
        phase += dt * profile.raysSpeed();
        opening = Math.min(1, opening + dt / 1.8f);
        if (input.inside()) {
            float x = input.x() / width;
            float y = input.y() / height;
            float glide = aim + shadow < .001f ? 1f : blend(dt, .1f);
            pointerX = Mth.lerp(glide, pointerX, x);
            pointerY = Mth.lerp(glide, pointerY, y);
        }
        aim = Mth.lerp(blend(dt, .2f), aim, input.inside() && profile.followMouse() ? 1f : 0f);
        boolean castShadow = input.inside() && profile.cursorShadow();
        shadow = Mth.lerp(blend(dt, castShadow ? .25f : .5f), shadow, castShadow ? 1f : 0f);
        for (int i = 0; i < pulseAges.length; i++) {
            if (pulseAges[i] < 0) continue;
            pulseAges[i] += dt;
            if (!profile.clickPulse() || pulseAges[i] >= PULSE_SECONDS) pulseAges[i] = -1;
        }
        if (input.pressed() && profile.clickPulse()) pulse();
    }

    void pulse() {
        System.arraycopy(pulseAges, 0, pulseAges, 1, pulseAges.length - 1);
        pulseAges[0] = 0;
    }

    void clearInteraction() {
        aim = shadow = 0;
        Arrays.fill(pulseAges, -1);
    }

    private static float blend(float dt, float seconds) {
        return (float) -Math.expm1(-dt / seconds);
    }
}
