// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.kernel.api.*;

import java.util.ArrayList;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnimationServiceTest {
    @Test
    void springUsesFrameTimeAndOnePropertyLeaseUntilTheTreeCloses() {
        var property = new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.ANIMATION_PROPERTY,
                new UiId("example", "width"));
        try (var fixture = new ClientServiceFixture(Set.of(property), Set.of())) {
            var service = fixture.publication.current().require(UIApis.ANIMATION);
            assertSame(ShBetterUiApi.motion(), service.motion());
            var node = fixture.tree();
            var samples = new ArrayList<Double>();
            var track = service.bind(fixture.module, node, property.id(), Spring.PANEL, 0, () -> false, samples::add);
            assertThrows(IllegalStateException.class, () -> service.bind(fixture.module, node, property.id(),
                    Spring.PANEL, 0, () -> false, samples::add));
            track.play(1, InterruptibleAnimation.Policy.RETARGET, 5);
            fixture.client.runFrame();
            fixture.time.set(80_000_000);
            fixture.client.runFrame();
            assertTrue(track.value() > 0 && track.value() < 1);
            track.pause();
            var paused = track.value();
            var count = samples.size();
            fixture.time.set(800_000_000);
            fixture.client.runFrame();
            assertEquals(paused, track.value());
            assertEquals(count, samples.size());
            track.resume();
            fixture.time.set(1_000_000_000);
            fixture.client.runFrame();
            assertTrue(track.value() > paused);
            node.close();
            fixture.client.runFrame();
            assertFalse(track.isActive());
            assertThrows(IllegalStateException.class, () -> track.play(0, InterruptibleAnimation.Policy.IMMEDIATE, 5));
        }
    }
}
