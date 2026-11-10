// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.collision.CollisionShape;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CollisionServiceTest {
    @Test
    void picksTheForemostBodyUsingItsOwnHitSlopOnEveryDispatch() {
        var writer = UiId.parse("example:geometry");
        var claim = new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.LAYOUT_MUTATION, writer);
        try (var fixture = new ClientServiceFixture(Set.of(claim), Set.of())) {
            var tree = fixture.tree();
            var backNode = tree.openChild(ScopeKind.COMPONENT, UiId.parse("example:back"));
            var frontNode = tree.openChild(ScopeKind.COMPONENT, UiId.parse("example:front"));
            var service = fixture.publication.current().require(UIApis.COLLISION);
            var geometry = service.mount(fixture.module, tree, writer, new Bounds(0, 0, 200, 200), () -> true);
            var layout = geometry.layout();
            var back = layout.world().add(new CollisionShape(30, 40, 20, 10));
            var front = layout.world().add(new CollisionShape(52, 40, 20, 10));
            layout.bind(back, bounds -> {});
            layout.bind(front, bounds -> {});
            service.target(fixture.module, backNode, geometry, back, 10, 10);
            var frontTarget = service.target(fixture.module, frontNode, geometry, front, 0, 0);
            var screen = ((ScopeLifecycle.Scope) tree).ancestor(ScopeKind.SCREEN);
            var inGap = new NativeInput.Pointer(NativeInput.PointerAction.PRESS, 51, 45, 0, 0, 0);
            var onFront = new NativeInput.Pointer(NativeInput.PointerAction.PRESS, 53, 45, 0, 0, 0);
            var calls = new ArrayList<String>();
            var events = fixture.module.engine().events();
            events.subscribe(InputService.EVENT, backNode, UIEventPhase.TARGET, 0, event -> {
                calls.add("back");
                fixture.client.dispatch(screen, onFront);
            });
            events.subscribe(InputService.EVENT, frontNode, UIEventPhase.TARGET, 0, event -> calls.add("front"));
            fixture.client.runFrame();

            fixture.client.dispatch(screen, inGap);
            assertEquals(List.of("back", "front"), calls, "The expanded back body owns the gap, even during nested dispatch");

            frontTarget.close();
            service.target(fixture.module, frontNode, geometry, front, 0, 10);
            fixture.client.dispatch(screen, inGap);
            assertEquals(List.of("back", "front", "front"), calls, "Reusing a pointer event still picks current geometry");
        }
    }

    @Test
    void publishesTheSameGeometryToLayoutAndExpandedInputAndReindexesMoves() {
        var writer = UiId.parse("example:geometry");
        var claim = new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.LAYOUT_MUTATION, writer);
        try (var fixture = new ClientServiceFixture(Set.of(claim), Set.of())) {
            var tree = fixture.tree();
            var node = tree.openChild(ScopeKind.COMPONENT, UiId.parse("example:control"));
            var viewport = new Bounds(0, 0, 200, 200);
            var service = fixture.publication.current().require(UIApis.COLLISION);
            var geometry = service.mount(fixture.module, tree, writer, viewport, () -> true);
            var body = geometry.layout().world().add(new CollisionShape(40, 40, 20, 10));
            var writes = new AtomicInteger();
            geometry.layout().bind(body, bounds -> writes.incrementAndGet());
            var target = service.target(fixture.module, node, geometry, body, 0, 5);
            fixture.module.engine().events().subscribe(InputService.EVENT, node, UIEventPhase.TARGET, 0,
                    event -> event.markHandled());
            fixture.client.runFrame();
            fixture.client.runFrame();
            assertEquals(1, writes.get());
            assertNull(geometry.layout().hitTest(39, 45));
            var screen = ((ScopeLifecycle.Scope) tree).ancestor(ScopeKind.SCREEN);
            var press = new NativeInput.Pointer(NativeInput.PointerAction.PRESS, 39, 45, 0, 0, 0);
            assertTrue(fixture.client.dispatch(screen, press).handled());
            body.moveTo(70, 110);
            geometry.invalidate(viewport);
            fixture.client.runFrame();
            assertEquals(2, writes.get());
            assertFalse(fixture.client.dispatch(screen, press).handled());
            assertSame(body, geometry.layout().hitTest(70, 111));
            geometry.close();
            assertFalse(target.isActive());
            assertTrue(node.isOpen());
            assertFalse(geometry.isActive());
            assertFalse(geometry.layout().world().contains(body));
        }
    }
}
