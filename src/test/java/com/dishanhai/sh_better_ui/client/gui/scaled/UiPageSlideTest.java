package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPageSlide.Direction;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPageSlide.Motion;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：覆盖方向、空间变换、连续重定向和减少动效。 */
class UiPageSlideTest {
    @Test
    void eachDirectionEntersFromTheOppositeEdgeAndSettlesAtIdentity() {
        assertTravel(Direction.LEFT, 24, 0);
        assertTravel(Direction.RIGHT, -24, 0);
        assertTravel(Direction.UP, 0, 24);
        assertTravel(Direction.DOWN, 0, -24);
    }

    @Test
    void depthRotatesTheAppropriateAxisAroundTheRequestedPivot() {
        var slide = new UiPageSlide();
        slide.start(Direction.LEFT, Motion.DEPTH, 24, 0, UiPreferences.DEFAULT);
        var horizontal = slide.sample(0, UiPreferences.DEFAULT);
        assertEquals(0f, horizontal.pitch());
        assertTrue(horizontal.yaw() > 0f);
        assertTrue(horizontal.scale() < 1f);

        var pose = new PoseStack();
        horizontal.applyDepth(pose, 20f, 30f);
        var matrix = pose.last().pose();
        var pivot = matrix.transformPosition(new Vector3f(20f, 30f, 0f));
        assertEquals(20f, pivot.x(), 0.0001f);
        assertEquals(30f, pivot.y(), 0.0001f);
        assertEquals(0f, pivot.z(), 0.0001f);
        assertNotEquals(0f, matrix.m02());

        slide.sample(UiPageSlide.DURATION_MS, UiPreferences.DEFAULT);
        slide.start(Direction.UP, Motion.DEPTH, 24, UiPageSlide.DURATION_MS, UiPreferences.DEFAULT);
        var vertical = slide.sample(UiPageSlide.DURATION_MS, UiPreferences.DEFAULT);
        assertTrue(vertical.pitch() < 0f);
        assertEquals(0f, vertical.yaw(), 0.0001f);
        assertTrue(vertical.scale() < 1f);
    }

    @Test
    void interruptingUsesTheLastVisibleGeometryAndTurnsTowardTheNewDirection() {
        var slide = new UiPageSlide();
        long midpoint = UiPageSlide.DURATION_MS / 2;
        slide.start(Direction.LEFT, Motion.DEPTH, 32, 0, UiPreferences.DEFAULT);
        var previous = slide.sample(midpoint, UiPreferences.DEFAULT);
        slide.start(Direction.DOWN, Motion.DEPTH, 40, midpoint, UiPreferences.DEFAULT);
        var first = slide.sample(midpoint, UiPreferences.DEFAULT);
        assertEquals(previous.offsetX(), first.offsetX());
        assertEquals(previous.offsetY(), first.offsetY());
        assertEquals(previous.pitch(), first.pitch());
        assertEquals(previous.yaw(), first.yaw());
        assertEquals(previous.scale(), first.scale());
        assertEquals(previous.effectAmount(), first.effectAmount());
        assertEquals(Direction.DOWN, first.direction());
        var turning = slide.sample(midpoint * 2, UiPreferences.DEFAULT);
        assertTrue(turning.offsetY() < 0);
        assertTrue(turning.pitch() > 0f);
        assertIdentity(slide.sample(midpoint + UiPageSlide.DURATION_MS, UiPreferences.DEFAULT));
    }

    @Test
    void reducedMotionSkipsNewSlidesAndFinishesAnExistingOne() {
        var slide = new UiPageSlide();
        var reducedMotion = new UiPreferences(false, true, true);
        assertIdentity(slide.sample(0, UiPreferences.DEFAULT));
        slide.start(Direction.LEFT, Motion.DEPTH, 24, 0, reducedMotion);
        assertIdentity(slide.sample(1, UiPreferences.DEFAULT));
        slide.start(Direction.DOWN, Motion.DEPTH, 24, 2, UiPreferences.DEFAULT);
        assertTrue(slide.sample(2, UiPreferences.DEFAULT).active());
        assertIdentity(slide.sample(3, reducedMotion));
        assertIdentity(slide.sample(4, UiPreferences.DEFAULT));
    }

    private static void assertTravel(Direction direction, int x, int y) {
        var slide = new UiPageSlide();
        slide.start(direction, Motion.FLAT, 24, 0, UiPreferences.DEFAULT);
        var first = slide.sample(0, UiPreferences.DEFAULT);
        assertEquals(x, first.offsetX());
        assertEquals(y, first.offsetY());
        assertEquals(0f, first.pitch(), 0.0001f);
        assertEquals(0f, first.yaw(), 0.0001f);
        assertEquals(1f, first.scale());
        var half = slide.sample(UiPageSlide.DURATION_MS / 2, UiPreferences.DEFAULT);
        assertEquals(x / 2, half.offsetX());
        assertEquals(y / 2, half.offsetY());
        assertEquals(0.5f, half.effectAmount());
        assertIdentity(slide.sample(UiPageSlide.DURATION_MS, UiPreferences.DEFAULT));
    }

    private static void assertIdentity(UiPageSlide.Frame frame) {
        assertFalse(frame.active());
        assertEquals(0, frame.offsetX());
        assertEquals(0, frame.offsetY());
        assertEquals(1f, frame.progress());
        assertEquals(0f, frame.pitch());
        assertEquals(0f, frame.yaw());
        assertEquals(1f, frame.scale());
        assertEquals(0f, frame.effectAmount());
    }
}
