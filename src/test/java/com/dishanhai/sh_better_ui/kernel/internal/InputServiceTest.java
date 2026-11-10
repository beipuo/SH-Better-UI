// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.kernel.api.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InputServiceTest {
    @Test
    void resolvesOneTargetAndPropagatesWithoutGrantingVanillaCancellation() {
        try (var fixture = new ClientServiceFixture(Set.of(), Set.of())) {
            var service = fixture.publication.current().require(UIApis.INPUT);
            assertSame(ShBetterUiApi.interactions(), service.interactions());
            var screen = fixture.module.scope().openChild(ScopeKind.SCREEN, UiId.parse("example:input"));
            var tree = screen.openChild(ScopeKind.UI_TREE, UiId.parse("example:tree"));
            var child = tree.openChild(ScopeKind.COMPONENT, UiId.parse("example:child"));
            var events = fixture.module.engine().events();
            var observed = new ArrayList<String>();
            events.subscribe(InputService.EVENT, tree, UIEventPhase.CAPTURE, 0, event -> observed.add("capture"));
            events.subscribe(InputService.EVENT, child, UIEventPhase.TARGET, 0, event -> {
                observed.add("target");
                event.markHandled();
                assertThrows(SecurityException.class, event::consume);
            });
            service.target(fixture.module, tree, 0, input -> true);
            var target = service.target(fixture.module, child, 1, input -> input instanceof NativeInput.Pointer pointer
                    && pointer.x() >= 10);
            var result = fixture.client.dispatch(screen, new NativeInput.Pointer(NativeInput.PointerAction.PRESS,
                    15, 20, 0, 0, 0));
            assertEquals(List.of("capture", "target"), observed);
            assertTrue(result.handled());
            assertFalse(result.consumed());
            assertFalse(result.blockVanilla());
            child.close();
            assertFalse(target.isActive());
            assertFalse(fixture.client.dispatch(screen, new NativeInput.Key(NativeInput.KeyAction.REPEAT,
                    65, 1, 0)).consumed());
        }
    }
}
