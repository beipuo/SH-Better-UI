package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.scaled.AntigravityProfile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 山海：总入口创建的粒子场和指针具有独立状态，工厂不访问 GL 上下文。 */
class AntigravityApiTest {
    @Test void factoriesKeepConfigurationAndPausePerRegion() {
        var profile = AntigravityProfile.builder().count(300).build();
        var first = ShBetterUiApi.graphics().antigravity(profile);
        var second = ShBetterUiApi.graphics().antigravity();
        first.setPaused(true);
        assertTrue(first.isPaused());
        assertFalse(second.isPaused());
        assertSame(profile, first.profile());
        assertSame(AntigravityProfile.DEFAULT, second.profile());
        first.restart();
        assertFalse(first.isPaused());
        assertNotSame(ShBetterUiApi.interactions().pointerTracker(), ShBetterUiApi.interactions().pointerTracker());
        assertDoesNotThrow(() -> first.burst(1));
        assertDoesNotThrow(first::suspend);
    }
}
