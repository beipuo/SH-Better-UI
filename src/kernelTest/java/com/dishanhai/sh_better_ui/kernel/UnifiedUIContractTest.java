// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel;

import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.*;

class UnifiedUIContractTest {
    @Test
    @ResourceLock("client-ui-kernel")
    void accessingTheFacadeBeforeClientStartupReportsUnavailability() {
        assertFalse(UnifiedUI.isAvailable());
        var error = assertThrows(IllegalStateException.class, UnifiedUI::get);
        assertTrue(error.getMessage().contains("client"));
        assertTrue(error.getMessage().contains("initialized"));
    }

    @Test
    void contractTestsRunOnJava17WithoutGameOrKotlinClasses() {
        assertEquals(17, Runtime.version().feature());
        assertThrows(ClassNotFoundException.class, () -> Class.forName("net.minecraft.client.Minecraft"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("kotlin.Unit"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.dishanhai.sh_better_ui.client.api.ShBetterUiApi"));
    }
}
