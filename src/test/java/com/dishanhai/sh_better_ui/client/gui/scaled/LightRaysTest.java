package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：验证区域实例隔离、输入过渡和有界点击波。 */
class LightRaysTest {
    @Test
    void factoryCreatesIndependentEffectsWithValidatedProfiles() {
        var profile = LightRaysProfile.builder().raysOrigin(LightRaysProfile.Origin.BOTTOM_RIGHT)
                .rayDensity(20).tilt(-100).opacity(-1).build();
        assertEquals(6f, profile.rayDensity());
        assertEquals(-90f, profile.tilt());
        assertEquals(0f, profile.opacity());
        assertThrows(IllegalArgumentException.class, () -> LightRaysProfile.builder().raysSpeed(Float.NaN).build());

        var graphics = ShBetterUiApi.graphics();
        var configured = graphics.lightRays(profile);
        var defaults = graphics.lightRays();
        assertNotSame(defaults, configured);
        assertSame(LightRaysProfile.DEFAULT, defaults.profile());
        assertSame(profile, configured.profile());
    }

    @Test
    void frozenPhaseStillHandlesPointerAndClickWaves() {
        var profile = LightRaysProfile.builder().raysSpeed(0).build();
        var motion = new LightRaysMotion();
        var press = new UiPointerTracker.Frame(30, 40, true, true, true, false, false, 0);
        var outside = new UiPointerTracker.Frame(0, 0, false, false, false, false, true, Long.MAX_VALUE);
        motion.advance(profile, press, 60, 160, .05f);

        assertEquals(0f, motion.phase);
        assertEquals(.25f, motion.pointerY);
        assertTrue(motion.opening > 0);
        assertTrue(motion.aim > 0);
        assertTrue(motion.shadow > 0);
        assertEquals(0f, motion.pulseAges[0]);

        float shadow = motion.shadow;
        motion.advance(profile, outside, 60, 160, .05f);
        assertTrue(motion.shadow < shadow);
        assertEquals(.05f, motion.pulseAges[0]);
        assertEquals(-1f, motion.pulseAges[1]);

        var noPulses = LightRaysProfile.builder().clickPulse(false).build();
        motion.advance(noPulses, press, 60, 160, .05f);
        assertEquals(-1f, motion.pulseAges[0]);

        motion.clearInteraction();
        assertEquals(0f, motion.aim);
        assertEquals(0f, motion.shadow);
        assertEquals(-1f, motion.pulseAges[0]);
    }

    @Test
    void overlappingWavesDropTheOldestAndExpire() {
        var motion = new LightRaysMotion();
        var outside = new UiPointerTracker.Frame(0, 0, false, false, false, false, false, Long.MAX_VALUE);
        for (int i = 0; i < 5; i++) {
            motion.pulse();
            motion.advance(LightRaysProfile.DEFAULT, outside, 60, 160, .05f);
        }
        assertEquals(.2f, motion.pulseAges[3], .00001f);
        for (int i = 0; i < 40; i++) motion.advance(LightRaysProfile.DEFAULT, outside, 60, 160, .05f);
        for (float age : motion.pulseAges) assertEquals(-1f, age);
    }
}
