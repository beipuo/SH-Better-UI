// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.kernel.api.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LayoutServiceTest {
    @Test
    void coalescesLayoutAndUsesTheLegacyAlignmentFactory() {
        var id = UiId.parse("example:arrange");
        var claim = new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.LAYOUT_MUTATION, id);
        try (var fixture = new ClientServiceFixture(Set.of(claim), Set.of())) {
            var service = fixture.publication.current().require(UIApis.LAYOUT);
            assertSame(ShBetterUiApi.layout(), service.layout());
            var tree = fixture.tree();
            var child = tree.openChild(ScopeKind.COMPONENT, UiId.parse("example:child"));
            var trace = new ArrayList<String>();
            try (var parent = service.bind(fixture.module, tree, id, () -> trace.add("parent.measure"),
                    () -> trace.add("parent.arrange"));
                 var leaf = service.bind(fixture.module, child, id, () -> trace.add("leaf.measure"),
                         () -> trace.add("leaf.arrange"))) {
                parent.invalidate();
                leaf.invalidate();
                leaf.invalidate();
                fixture.client.runFrame();
                fixture.client.runFrame();
                assertEquals(List.of("leaf.measure", "parent.measure", "parent.arrange", "leaf.arrange"), trace);
                assertThrows(IllegalStateException.class, () -> service.bind(fixture.module, child, id, () -> {}, () -> {}));
            }
        }
    }

    @Test
    void aPropertyChangeInvalidatesLayoutForTheNextFrame() {
        var layoutId = UiId.parse("example:layout");
        var width = UiId.parse("example:width");
        var claims = Set.of(new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.LAYOUT_MUTATION, layoutId),
                new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.ANIMATION_PROPERTY, width));
        try (var fixture = new ClientServiceFixture(claims, Set.of())) {
            var tree = fixture.tree();
            var engine = fixture.publication.current();
            var bounds = new ArrayList<Bounds>();
            var size = new int[1];
            var binding = engine.require(UIApis.LAYOUT).bind(fixture.module, tree, layoutId, () -> {},
                    () -> bounds.add(new Bounds(0, 0, size[0], 10)));
            engine.require(UIApis.ANIMATION).bind(fixture.module, tree, width, Spring.PANEL, 0, () -> false, value -> {
                size[0] = (int) value;
                binding.invalidate();
            }).play(40, InterruptibleAnimation.Policy.IMMEDIATE, 0);
            fixture.client.runFrame();
            fixture.client.runFrame();
            assertEquals(List.of(new Bounds(0, 0, 0, 10), new Bounds(0, 0, 40, 10)), bounds);
            tree.close();
            fixture.client.runFrame();
            assertFalse(binding.isActive());
        }
    }
}
