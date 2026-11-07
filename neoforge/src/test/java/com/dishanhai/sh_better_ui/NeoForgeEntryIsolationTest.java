package com.dishanhai.sh_better_ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：平台入口可在專用伺服器使用，且不依賴 Forge 1.20.1。 */
class NeoForgeEntryIsolationTest {
    @Test
    void usesNeoForgeEntrypointAnnotation() {
        var mod = ShBetterUi.class.getAnnotation(net.neoforged.fml.common.Mod.class);
        assertEquals("sh_better_ui", mod.value());
        assertTrue(ShBetterUi.class.getDeclaredConstructors().length > 0);
    }
}
