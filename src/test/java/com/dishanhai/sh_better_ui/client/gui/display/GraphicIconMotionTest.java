package com.dishanhai.sh_better_ui.client.gui.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：图标动效保持原槽位，并可与独立的着色器一起使用。 */
class GraphicIconMotionTest {
    @Test
    void shaderAndMotionCanBeSetOrClearedIndependently() {
        var icon = GraphicIcon.REFRESH;
        var shader = GraphicIconShader.GRADIENT_SWEEP;
        var motion = GraphicIconMotion.SPIN;
        var animated = icon.withMotion(motion).withShader(shader);

        assertEquals(animated, icon.withShader(shader).withMotion(motion));
        assertEquals(animated, new GraphicIcon(icon.texture(), shader, motion));
        assertEquals(new GraphicIcon(icon.texture(), shader), animated.withMotion(null));
        assertEquals(icon.withMotion(motion), animated.withShader(null));
        assertNull(icon.motion());
        assertNull(icon.shader());
    }

    @Test
    void periodsRepeatAndReducedMotionPreservesTheOriginalPose() {
        for (var motion : GraphicIconMotion.values()) {
            long period = motion.periodMillis();
            assertEquals(motion.sample(period / 3, 16, false), motion.sample(period + period / 3, 16, false));
            assertEquals(motion.sample(period - 1, 16, false), motion.sample(-1, 16, false));
            assertEquals(motion.sample(0, 16, false), motion.sample(period, 16, false));
            assertSame(GraphicIconMotion.Frame.IDENTITY, motion.sample(period / 3, 16, true));
            assertEquals(0, motion.sample(0, 16, true).offsetY());
            assertEquals(1, motion.sample(0, 16, true).scale());
            assertEquals(0, motion.sample(0, 16, true).rotationDegrees());
        }
    }

    @Test
    void presetsHaveDistinctMovementAndJoinAtTheirLoopBoundary() {
        var spin = GraphicIconMotion.SPIN;
        var floating = GraphicIconMotion.FLOAT;
        var bounce = GraphicIconMotion.BOUNCE;
        assertEquals(90f, spin.sample(spin.periodMillis() / 4, 16, false).rotationDegrees());
        assertEquals(2f, floating.sample(floating.periodMillis() / 4, 16, false).offsetY());
        assertEquals(-2f, floating.sample(floating.periodMillis() * 3 / 4, 16, false).offsetY());
        assertTrue(bounce.sample(bounce.periodMillis() * 41 / 100, 16, false).offsetY() < 0);
        assertTrue(bounce.sample(bounce.periodMillis() * 9 / 10, 16, false).offsetY() > 0);

        for (var motion : GraphicIconMotion.values()) {
            var last = motion.sample(motion.periodMillis() - 1, 16, false);
            var first = motion.sample(0, 16, false);
            assertEquals(first.offsetY(), last.offsetY(), 0.01f);
            assertEquals(first.scale(), last.scale(), 0.01f);
            assertEquals(Math.sin(Math.toRadians(first.rotationDegrees())),
                    Math.sin(Math.toRadians(last.rotationDegrees())), 0.01);
            assertEquals(Math.cos(Math.toRadians(first.rotationDegrees())),
                    Math.cos(Math.toRadians(last.rotationDegrees())), 0.01);
        }
    }

    @Test
    void everyFrameFitsTheOriginalSquareAtEachGuiSize() {
        for (var motion : GraphicIconMotion.values()) {
            for (int size : new int[]{16, 32, 64}) {
                for (long time = 0; time < motion.periodMillis(); time++) {
                    var frame = motion.sample(time, size, false);
                    double radians = Math.toRadians(frame.rotationDegrees());
                    double extent = size * 0.5 * frame.scale()
                            * (Math.abs(Math.sin(radians)) + Math.abs(Math.cos(radians)));
                    assertTrue(extent + Math.abs(frame.offsetY()) <= size * 0.5 + 0.00001);
                }
            }
        }
    }
}
