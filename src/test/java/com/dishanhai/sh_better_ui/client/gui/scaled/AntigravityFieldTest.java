package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：验证粒子环的吸附、爆散、确定性、缩放与减少动效。 */
class AntigravityFieldTest {
    private static final AntigravityProfile STILL = AntigravityProfile.builder()
            .count(128).intro(false).autoAnimate(false).waveAmplitude(0).rotationSpeed(0)
            .depthFactor(0).magnetRadius(100).ringRadius(7).fieldStrength(100).build();
    private static final UiPointerTracker.Frame CENTER = pointer(false, false, false, false);

    @Test
    void capturedParticlesConvergeToTheConfiguredRing() {
        var field = field(STILL);
        for (int i = 0; i < 360; i++) field.advance(STILL, CENTER, 1f / 60, false);
        double error = 0;
        for (int i = 0; i < field.x.length; i++) {
            error += Math.abs(Math.hypot(field.x[i] - 157.5, field.y[i] - 157.5) - 70);
        }
        assertTrue(error / field.x.length < .6, "Captured particles should settle inside the narrow ring band");
    }

    @Test
    void burstAddsOutwardVelocityWithoutMovingPositionsImmediately() {
        var field = field(STILL);
        field.advance(STILL, CENTER, 0, true);
        float[] beforeX = field.x.clone(), beforeY = field.y.clone();
        field.burst(STILL, 1);
        assertTrue(energy(field) > 1_000);
        assertArrayEquals(beforeX, field.x);
        assertArrayEquals(beforeY, field.y);
        for (int i = 0; i < field.x.length; i++) {
            assertTrue((field.x[i] - field.cursorX) * field.vx[i]
                    + (field.y[i] - field.cursorY) * field.vy[i] > 0);
        }
    }

    @Test
    void cancellationClearsChargeWithoutTheReleaseBurst() {
        var cancelled = chargedField();
        float[] beforeVx = cancelled.vx.clone(), beforeVy = cancelled.vy.clone();
        cancelled.advance(STILL, pointer(false, false, false, true), 0, false);
        assertEquals(0, cancelled.charge);
        assertArrayEquals(beforeVx, cancelled.vx);
        assertArrayEquals(beforeVy, cancelled.vy);

        var released = chargedField();
        double beforeEnergy = energy(released);
        released.advance(STILL, pointer(false, false, true, false), 0, false);
        assertTrue(energy(released) > beforeEnergy + 1_000);
    }

    @Test
    void disablingClickBurstDiscardsExistingCharge() {
        var field = chargedField();
        var passive = STILL.toBuilder().clickBurst(false).build();
        float[] beforeVx = field.vx.clone(), beforeVy = field.vy.clone();
        field.advance(passive, pointer(false, false, true, false), 0, false);
        assertEquals(0, field.charge);
        assertArrayEquals(beforeVx, field.vx);
        assertArrayEquals(beforeVy, field.vy);
    }

    @Test
    void resizePreservesNormalizedPositionsAndVelocitiesAndMergesCounts() {
        var field = field(STILL);
        field.advance(STILL, CENTER, 0, true);
        field.burst(STILL, .5f);
        float[] x = field.x.clone(), y = field.y.clone(), vx = field.vx.clone(), vy = field.vy.clone();
        var larger = STILL.toBuilder().count(200).build();
        field.resize(630, 630, larger);
        assertEquals(200, field.x.length);
        assertEquals(315, field.cursorX);
        assertEquals(315, field.cursorY);
        for (int i = 0; i < x.length; i++) {
            assertEquals(x[i] * 2, field.x[i], .001);
            assertEquals(y[i] * 2, field.y[i], .001);
            assertEquals(vx[i] * 2, field.vx[i], .001);
            assertEquals(vy[i] * 2, field.vy[i], .001);
        }
        field.resize(630, 630, larger.toBuilder().count(16).build());
        assertEquals(16, field.x.length);
        assertEquals(x[0] * 2, field.x[0], .001);
    }

    @Test
    void fixedSeedsAndRestartReproduceTheInitialField() {
        var profile = STILL.toBuilder().intro(true).build();
        var first = field(profile);
        var second = field(profile);
        assertArrayEquals(first.x, second.x);
        assertArrayEquals(first.y, second.y);
        assertArrayEquals(first.depth, second.depth);
        assertArrayEquals(first.colorSeed, second.colorSeed);
        for (int i = 1; i < first.depth.length; i++) assertTrue(first.depth[i - 1] <= first.depth[i]);
        float[] initialX = first.x.clone(), initialY = first.y.clone();
        first.advance(profile, CENTER, 1f / 30, false);
        first.burst(profile, 1);
        first.restart();
        first.resize(315, 315, profile);
        assertArrayEquals(initialX, first.x);
        assertArrayEquals(initialY, first.y);
        assertEquals(0, first.time);
        assertEquals(0, first.charge);
        assertEquals(0, first.reveal);
    }

    @Test
    void reducedMotionStopsTimeCursorChargeAndAllParticleMovement() {
        var profile = STILL.toBuilder().waveAmplitude(2).rotationSpeed(3).autoAnimate(true).intro(true).build();
        var field = field(profile);
        for (int i = 0; i < 30; i++) field.advance(profile, pointer(true, false, false, false), 1f / 30, false);
        field.advance(profile, CENTER, 1f / 30, true);
        float time = field.time, hue = field.hue;
        float[] x = field.x.clone(), y = field.y.clone();
        var moved = new UiPointerTracker.Frame(20, 20, true, true, false, false, false, 0);
        for (int i = 0; i < 30; i++) field.advance(profile, moved, 1f / 30, true);
        assertEquals(time, field.time);
        assertEquals(hue, field.hue);
        assertEquals(157.5f, field.cursorX);
        assertEquals(157.5f, field.cursorY);
        assertEquals(0, field.charge);
        assertEquals(1, field.reveal);
        assertArrayEquals(x, field.x);
        assertArrayEquals(y, field.y);
        for (int i = 0; i < field.x.length; i++) {
            assertEquals(0, field.vx[i]);
            assertEquals(0, field.vy[i]);
        }
    }

    @Test
    void longAndNonFiniteFrameIntervalsStayFiniteAtMaximumFollowSpeed() {
        var profile = STILL.toBuilder().lerpSpeed(1).waveAmplitude(20).rotationSpeed(20).fieldStrength(0).build();
        var field = field(profile);
        for (int i = 0; i < 1000; i++) {
            if (i % 50 == 0) field.burst(profile, 2);
            field.advance(profile, CENTER, Float.MAX_VALUE, false);
        }
        field.advance(profile, CENTER, Float.NaN, false);
        field.advance(profile, CENTER, Float.POSITIVE_INFINITY, false);
        for (int i = 0; i < field.x.length; i++) {
            assertTrue(Float.isFinite(field.x[i]));
            assertTrue(Float.isFinite(field.y[i]));
            assertTrue(Float.isFinite(field.vx[i]));
            assertTrue(Float.isFinite(field.vy[i]));
            assertTrue(Math.abs(field.x[i]) < 100_000);
            assertTrue(Math.abs(field.y[i]) < 100_000);
        }
    }

    @Test
    void zeroRadiiBurstAndInactiveInteractionRemainFinite() {
        var profile = STILL.toBuilder().magnetRadius(0).ringRadius(0).interactive(false).build();
        var field = field(profile);
        field.advance(profile, pointer(true, true, true, false), 1f / 30, false);
        assertEquals(0, field.charge);
        field.burst(profile, Float.NaN);
        field.burst(profile, 1);
        for (int i = 0; i < field.x.length; i++) {
            assertTrue(Float.isFinite(field.vx[i]));
            assertTrue(Float.isFinite(field.vy[i]));
        }
    }

    private static AntigravityField field(AntigravityProfile profile) {
        var field = new AntigravityField();
        field.resize(315, 315, profile);
        return field;
    }

    private static AntigravityField chargedField() {
        var field = field(STILL);
        for (int i = 0; i < 60; i++) field.advance(STILL, pointer(true, false, false, false), 1f / 30, false);
        assertTrue(field.charge > .99f);
        return field;
    }

    private static UiPointerTracker.Frame pointer(boolean down, boolean pressed, boolean released, boolean cancelled) {
        return new UiPointerTracker.Frame(157.5f, 157.5f, true, down, pressed, released, cancelled, 0);
    }

    private static double energy(AntigravityField field) {
        double result = 0;
        for (int i = 0; i < field.x.length; i++) result += field.vx[i] * field.vx[i] + field.vy[i] * field.vy[i];
        return result;
    }
}
