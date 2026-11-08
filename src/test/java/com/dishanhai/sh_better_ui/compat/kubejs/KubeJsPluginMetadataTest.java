package com.dishanhai.sh_better_ui.compat.kubejs;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 山海：插件发现资源必须在类加载前限定物理客户端，保持专用服务器隔离。 */
class KubeJsPluginMetadataTest {
    @Test
    void pluginDiscoveryIsRestrictedToThePhysicalClient() throws Exception {
        try (var resource = getClass().getResourceAsStream("/kubejs.plugins.txt")) {
            assertNotNull(resource);
            var entries = new String(resource.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(line -> line.split("#", 2)[0].trim())
                    .filter(line -> !line.isEmpty())
                    .toList();

            assertEquals(List.of("com.dishanhai.sh_better_ui.compat.kubejs.ShBetterUiKubeJsPlugin client"),
                    entries);
        }
    }
}
