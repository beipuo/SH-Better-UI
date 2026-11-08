package com.dishanhai.sh_better_ui.client.gui.panel;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：概览在无客户端时不探测硬件，运行状态和单位转换依据可观测值。 */
class ModOverviewDataTest {
    @Test
    void noClientDoesNotQueryTheLoaderOrHardware() {
        assertTrue(ModOverviewData.capture(null).isEmpty());
    }

    @Test
    void menuTakesPrecedenceOverAnIntegratedServerDuringDisconnect() {
        assertEquals("sh_better_ui.panel.overview.state.menu", text(ModOverviewData.session(false, true)).getKey());
        assertEquals("sh_better_ui.panel.overview.state.menu", text(ModOverviewData.session(false, false)).getKey());
        assertEquals("sh_better_ui.panel.overview.state.integrated", text(ModOverviewData.session(true, true)).getKey());
        assertEquals("sh_better_ui.panel.overview.state.remote", text(ModOverviewData.session(true, false)).getKey());
    }

    @Test
    void uptimeKeepsAllHoursAndSplitsMinutesAndSecondsWithoutRoundingUp() {
        long milliseconds = (25L * 3600 + 14 * 60 + 37) * 1000 + 999;
        var text = text(ModOverviewData.uptime(milliseconds));
        assertEquals("sh_better_ui.panel.overview.runtime.uptime", text.getKey());
        assertArrayEquals(new Object[]{25L, 14L, 37L}, text.getArgs());
        assertArrayEquals(new Object[]{0L, 0L, 0L}, text(ModOverviewData.uptime(-1)).getArgs());
    }

    @Test
    void heapUsesBinaryMebibytesAndPreservesValuesBeyondTwoGigabytes() {
        long mebibyte = 1024L * 1024;
        var text = text(ModOverviewData.memory(3072 * mebibyte + mebibyte - 1, 8192 * mebibyte));
        assertEquals("sh_better_ui.panel.overview.runtime.memory", text.getKey());
        assertArrayEquals(new Object[]{3072L, 8192L}, text.getArgs());
        assertArrayEquals(new Object[]{0L, 0L}, text(ModOverviewData.memory(-1, -1)).getArgs());
    }

    private static TranslatableContents text(Component component) {
        return assertInstanceOf(TranslatableContents.class, component.getContents());
    }
}
