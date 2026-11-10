// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResourcesServiceTest {
    @Test
    void commitsOnlyTheNewestPreparedGenerationAndInvalidatesScopedCaches() {
        try (var fixture = new ClientServiceFixture(Set.of(), Set.of())) {
            var service = fixture.publication.current().require(UIApis.RESOURCES);
            var node = fixture.tree();
            var cache = service.alignmentAssets(fixture.module, node, ResourceManager.Empty.INSTANCE);
            var missing = ResourceLocation.tryParse("example:textures/absent.png");
            cache.texture(missing, 1);
            cache.texture(missing, 1);
            assertEquals(1, cache.stats().hits());

            var notified = new ArrayList<Long>();
            var observer = service.observe(fixture.module, node, notified::add);
            var reload = fixture.client.resources();
            long stale = reload.begin();
            long live = reload.begin();
            assertFalse(reload.commit(stale, () -> fail("stale reload applied")));
            assertTrue(reload.commit(live, () -> {}));
            assertEquals(live, service.version());
            assertEquals(List.of(live), notified);
            cache.texture(missing, 1);
            assertEquals(2, cache.stats().misses());

            long bad = reload.begin();
            assertThrows(IllegalArgumentException.class, () -> reload.commit(bad, () -> {
                throw new IllegalArgumentException("invalid prepared resource");
            }));
            assertEquals(live, service.version());
            assertEquals(List.of(live), notified);
            node.close();
            assertFalse(observer.isActive());
            assertTrue(reload.commit(reload.begin(), () -> {}));
            assertEquals(List.of(live), notified);
        }
    }
}
