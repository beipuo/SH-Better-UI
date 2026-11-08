package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：胶囊与弹窗的轮廓、过阻尼轨迹和中途反向。 */
class FluidMorphTest {
    private static final GuiRect CAPSULE = new GuiRect(8.25f, 16.5f, 91f, 21f);
    private static final GuiRect PANEL = new GuiRect(65f, 43f, 260f, 112f);

    @Test
    void geometryUsesOneSubpixelProgressAndExactEndpoints() {
        assertEquals(new FluidMorph(CAPSULE, 10.5f), frame(-0.1f));
        assertEquals(new FluidMorph(new GuiRect(22.4375f, 23.125f, 133.25f, 43.75f), 9.125f), frame(0.25f));
        assertEquals(new FluidMorph(PANEL, 5f), frame(1.1f));
    }

    @Test
    void roundedCornersFitEvenWhenTheShortAxisChanges() {
        var panel = new GuiRect(30, 30, 6, 45);
        assertEquals(new FluidMorph(panel, 3f), FluidMorph.between(CAPSULE, panel, 5f, 1f));
        for (int i = 0; i <= 100; i++) {
            var frame = FluidMorph.between(CAPSULE, panel, 5f, i / 100f);
            assertTrue(frame.radius() > 0f && frame.radius() <= Math.min(frame.rect().getW(), frame.rect().getH()) / 2f);
        }
    }

    @Test
    void unbrokenOpeningAndClosingSettleWithoutOvershootingAtDifferentFrameRates() {
        for (int step : new int[]{8, 16, 33, 50}) {
            var animation = new InterruptibleAnimation(Spring.FLUID_MORPH);
            animation.setOpen(true, 0);
            float previous = 0f;
            for (long now = step; now <= 640; now += step) {
                float progress = animation.sample(now);
                assertTrue(progress >= previous && progress <= 1f);
                previous = progress;
            }
            assertFalse(animation.running());
            assertEquals(1f, previous);
            animation.setOpen(false, 640);
            for (long now = 640 + step; now <= 1280; now += step) {
                float progress = animation.sample(now);
                assertTrue(progress <= previous && progress >= 0f);
                previous = progress;
            }
            assertFalse(animation.running());
            assertEquals(0f, previous);
        }
    }

    @Test
    void reversingMidFlightPreservesGeometryAndVelocityAndReducedMotionSnaps() {
        var animation = new InterruptibleAnimation(Spring.FLUID_MORPH);
        animation.setOpen(true, 100);
        FluidMorph before = frame(animation.sample(132));
        float velocity = animation.velocity();
        assertTrue(velocity > 0f);
        animation.setOpen(false, 132);
        assertEquals(before, frame(animation.sample(132)));
        assertEquals(velocity, animation.velocity());
        assertEquals(frame(0f), frame(animation.sample(148, true)));
        animation.setOpen(true, 149);
        assertEquals(frame(1f), frame(animation.sample(149, true)));
    }

    private static FluidMorph frame(float progress) {
        return FluidMorph.between(CAPSULE, PANEL, LiquidGlassTheme.POPOVER_RADIUS, progress);
    }
}
