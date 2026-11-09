package com.dishanhai.sh_better_ui.client.gui.collision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：动画帧速、暂停和反向输入回归。 */
class SpringMotionTest {
    @Test
    void reverseKeepsCurrentPositionAndVelocity() {
        var motion = new SpringMotion(0);
        motion.retarget(100, 10);
        motion.update(40);
        float position = motion.value();
        float speed = motion.velocity();
        assertTrue(speed > 0);
        motion.retarget(0, 40);
        assertEquals(position, motion.value());
        assertEquals(speed, motion.velocity());
        for (long time = 50; time <= 800; time += 10) motion.update(time);
        assertEquals(0, motion.value());
        assertEquals(0, motion.velocity());
    }

    @Test
    void refreshRatesConvergeAndLongPauseIsCapped() {
        var high = new SpringMotion(0);
        var low = new SpringMotion(0);
        var paused = new SpringMotion(0);
        high.retarget(100, 0);
        low.retarget(100, 0);
        paused.retarget(100, 0);
        for (int i = 1; i <= 144; i++) high.update(Math.round(i * 1000.0 / 144));
        low.update(50);
        paused.update(120_000);
        assertEquals(low.value(), paused.value());
        assertEquals(low.velocity(), paused.velocity());
        paused.update(120_000);
        assertEquals(low.value(), paused.value());
        for (int i = 2; i <= 20; i++) {
            low.update(i * 50L);
            paused.update(119_950 + i * 50L);
        }
        assertEquals(high.value(), low.value(), 0.01);
        assertEquals(low.value(), paused.value(), 0.01);
    }

    @Test
    void repeatedExpansionAndDirectDragRespectFrameAndViewport() {
        var world = new CollisionWorld(new CollisionShape(0, 0, 200, 80));
        var panel = world.add(new CollisionShape(10, 5, 20, 20));
        world.update(0, false);
        for (int frame = 1; frame <= 8; frame++) {
            panel.resize(frame % 2 == 0 ? 20 : 80, 20);
            world.update(frame * 16L, false);
            assertTrue(panel.visual().width() >= 20 && panel.visual().width() <= 80);
        }
        panel.direct(true).moveTo(100, 10);
        world.update(145, false);
        assertEquals(panel.target(), panel.visual());
        panel.direct(false).moveTo(20, 10);
        world.update(160, false);
        assertNotEquals(panel.target(), panel.visual());
        world.viewport(new CollisionShape(0, 0, 25, 22));
        world.update(176, false);
        assertTrue(panel.visual().right() <= 25);
        assertTrue(panel.visual().bottom() <= 22);
        world.update(192, true);
        assertEquals(panel.target(), panel.visual());
    }
}
