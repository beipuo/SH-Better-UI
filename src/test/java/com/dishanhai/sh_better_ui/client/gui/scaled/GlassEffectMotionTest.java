package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：流光与呼吸光晕无需 Minecraft 的行为测试。 */
class GlassEffectMotionTest {
    @Test
    void phaseWrapsAtPeriodAndRemainsFiniteForAllClockValues() {
        assertEquals(0f, GlassEffectMotion.phase(0L, 2_000L));
        assertEquals(0.5f, GlassEffectMotion.phase(1_000L, 2_000L));
        assertEquals(0f, GlassEffectMotion.phase(2_000L, 2_000L));
        assertEquals(0.9995f, GlassEffectMotion.phase(-1L, 2_000L), 0.00001f);
        assertEquals(0f, GlassEffectMotion.phase(10L, 0L));
        assertTrue(Float.isFinite(GlassEffectMotion.phase(Long.MAX_VALUE, 2_000L)));
        assertTrue(GlassEffectMotion.phase(Long.MAX_VALUE - 1L, Long.MAX_VALUE) < 1f,
                "舍入后的相位仍须留在半开区间内");
    }

    @Test
    void flowHighlightMovesAndWrapsContinuouslyAcrossTheCorner() {
        assertTrue(GlassEffectMotion.flowIntensity(0.5f, 0.5f, false)
                > GlassEffectMotion.flowIntensity(0.5f, 0f, false));
        assertEquals(GlassEffectMotion.flowIntensity(0f, 0f, false),
                GlassEffectMotion.flowIntensity(1f, 0f, false));
        for (int i = 0; i <= 100; i++) {
            float intensity = GlassEffectMotion.flowIntensity(i / 100f, 0.3f, false);
            assertTrue(intensity >= 0f && intensity <= 1f);
        }
    }

    @Test
    void reducedMotionStopsBothContinuousEffects() {
        assertEquals(GlassEffectMotion.flowIntensity(0f, 0f, true),
                GlassEffectMotion.flowIntensity(0.5f, 0.8f, true));
        assertEquals(GlassEffectMotion.breath(0L, true), GlassEffectMotion.breath(1_200L, true));
    }

    @Test
    void breathingIsBoundedPeriodicAndNonConstant() {
        assertNotEquals(GlassEffectMotion.breath(0L, false), GlassEffectMotion.breath(1_200L, false));
        assertEquals(GlassEffectMotion.breath(0L, false), GlassEffectMotion.breath(2_400L, false));
        for (long now = 0L; now <= 2_400L; now += 20L) {
            float breath = GlassEffectMotion.breath(now, false);
            assertTrue(breath >= 0f && breath <= 1f);
        }
    }

    @Test
    void opacityPreservesRgbAndNeverIncreasesSourceAlpha() {
        assertEquals(0x00123456, GlassEffectMotion.withOpacity(0x80123456, 0f));
        assertEquals(0x40123456, GlassEffectMotion.withOpacity(0x80123456, 0.5f));
        assertEquals(0x80123456, GlassEffectMotion.withOpacity(0x80123456, 2f));
        assertEquals(0x00123456, GlassEffectMotion.withOpacity(0x80123456, Float.NaN));
    }
}
