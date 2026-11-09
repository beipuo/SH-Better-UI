package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.collision.CollisionBody;
import com.dishanhai.sh_better_ui.client.gui.collision.CollisionShape;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：适配层量化、重绑、命中和视口回归。 */
class UiCollisionLayoutTest {
    @Test
    void layoutAndHitTestUseTheSameQuantizedViewportCoordinates() {
        var viewport = new Bounds(10, 24, 100, 60);
        try (var layout = new UiCollisionLayout(viewport)) {
            var writes = new ArrayList<Bounds>();
            var body = layout.world().add(new CollisionShape(10.6f, 24.4f, 20.8f, 10.2f));
            layout.bind(body, writes::add);
            layout.update(viewport, 0, true);
            assertEquals(new Bounds(11, 24, 20, 11), writes.get(0));
            assertSame(body, layout.hitTest(11, 24));
            assertNull(layout.hitTest(10.9, 24));
            assertNull(layout.hitTest(31, 25));

            layout.update(viewport, 16, false);
            assertEquals(1, writes.size());
            // 窗口及 GUI Scale 变化在宿主处表现为新的逻辑视口。
            var scaled = new Bounds(12, 26, 9, 7);
            layout.update(scaled, 32, false);
            assertEquals(scaled, writes.get(1));
            assertNull(layout.hitTest(11, 27));
            assertSame(body, layout.hitTest(20.9, 32.9));
            assertNull(layout.hitTest(21, 27));
        }
    }

    @Test
    void overlapSelectsLastBindingAndRebindingReplacesTheOldSink() {
        var area = new Bounds(0, 0, 80, 80);
        try (var layout = new UiCollisionLayout(area)) {
            var base = layout.world().add(new CollisionShape(0, 0, 40, 40));
            var overlay = layout.world().add(base.desired()).strategy(CollisionBody.Strategy.OVERLAY);
            var original = new ArrayList<Bounds>();
            var replacement = new ArrayList<Bounds>();
            layout.bind(base, original::add).bind(overlay, bounds -> {});
            layout.update(area, 0, true);
            assertSame(overlay, layout.hitTest(10, 10));
            layout.bind(base, replacement::add);
            layout.update(area, 1, true);
            assertEquals(1, original.size());
            assertEquals(1, replacement.size());
            assertSame(overlay, layout.hitTest(10, 10));
            layout.world().remove(overlay);
            layout.update(area, 2, true);
            assertSame(base, layout.hitTest(10, 10));
            layout.unbind(base);
            assertNull(layout.hitTest(10, 10));
        }
    }

    @Test
    void contextReleasesBodiesAndBindingsOnClose() {
        var area = new Bounds(0, 0, 80, 80);
        var context = new UiApiContext(new UiExtensionRegistry());
        var layout = context.collisionLayout(area);
        var body = layout.world().add(new CollisionShape(0, 0, 40, 40));
        context.close();
        assertFalse(layout.world().contains(body));
        assertThrows(IllegalStateException.class, () -> layout.update(area, 0, false));
        assertThrows(IllegalStateException.class, () -> context.collisionLayout(area));
    }

    @Test
    void nativeWidgetAndScreenUseTheAppliedBoundsForPointerDispatch() {
        var screen = new UiScreenBuilder(Component.empty()).backButton(false).build();
        screen.width = 80;
        screen.height = 60;
        screen.init();
        var layout = screen.context().collisionLayout(screen.viewport());
        var body = layout.world().add(new CollisionShape(5, 10, 20, 15));
        var widget = screen.add(new AbstractWidget(0, 0, 0, 0, Component.empty()) {
            @Override
            protected void renderWidget(GuiGraphics graphics, int x, int y, float partialTick) {}

            @Override
            protected void updateWidgetNarration(NarrationElementOutput narration) {}
        });
        layout.bind(body, widget);
        layout.update(screen.viewport(), 0, true);
        assertSame(widget, screen.getChildAt(10, 12).orElseThrow());
        assertSame(body, layout.hitTest(10, 12));

        body.moveTo(42, 30).resize(22, 18);
        layout.update(screen.viewport(), 16, true);
        assertTrue(screen.getChildAt(10, 12).isEmpty());
        assertSame(widget, screen.getChildAt(63, 47).orElseThrow());
        assertTrue(screen.getChildAt(64, 47).isEmpty());
        widget.active = false;
        assertNull(layout.hitTest(63, 47));
        screen.removed();
    }
}
