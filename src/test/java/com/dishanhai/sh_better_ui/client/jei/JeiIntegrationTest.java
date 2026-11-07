package com.dishanhai.sh_better_ui.client.jei;

import com.dishanhai.sh_better_ui.client.gui.search.JeiSearchPanel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：无 JEI 类路径与客户端生命周期的共用契约。 */
class JeiIntegrationTest {
    @Test
    void facadeAndSearchPanelLoadWithoutTheOptionalPlugin() throws ClassNotFoundException {
        var loader = getClass().getClassLoader();
        assertThrows(ClassNotFoundException.class, () -> Class.forName("mezz.jei.api.IModPlugin", false, loader));
        assertNotNull(Class.forName(JeiSearchPanel.class.getName(), true, loader));
        assertFalse(JeiIntegration.isAvailable());
        assertEquals(List.of(), JeiIntegration.ingredients());
        assertEquals(List.of(), JeiIntegration.recipes(null, JeiIntegration.Direction.USES, 1));
        assertFalse(JeiIntegration.open(null, JeiIntegration.Direction.USES));
    }

    @Test
    void dataAndRuntimeChangesAdvanceRevisionAndOldDisconnectCannotClearNewRuntime() {
        var first = new TestBackend();
        var replacement = new TestBackend();
        long offline = JeiIntegration.revision();
        try {
            JeiIntegration.install(first);
            long online = JeiIntegration.revision();
            assertTrue(online > offline);
            assertEquals(online, JeiIntegration.revision());
            first.revision++;
            long changed = JeiIntegration.revision();
            assertTrue(changed > online);

            JeiIntegration.install(replacement);
            long rejoined = JeiIntegration.revision();
            assertTrue(rejoined > changed);
            JeiIntegration.uninstall(first);
            assertEquals(rejoined, JeiIntegration.revision());
            assertTrue(JeiIntegration.isAvailable());
            JeiIntegration.uninstall(replacement);
            assertFalse(JeiIntegration.isAvailable());
            assertTrue(JeiIntegration.revision() > rejoined);
        } finally {
            JeiIntegration.uninstall(first);
            JeiIntegration.uninstall(replacement);
        }
    }

    @Test
    void overlayExclusionUsesOnlyTheRequestedScreenAndItsCurrentHeight() {
        Screen screen = new Screen(Component.empty()) {};
        Screen other = new Screen(Component.empty()) {};
        screen.height = 180;
        JeiIntegration.setExtraAreas(screen, height -> List.of(new Rect2i(5, height - 20, 50, 20)));
        try {
            assertEquals(160, JeiIntegration.extraAreas(screen).iterator().next().getY());
            assertTrue(JeiIntegration.extraAreas(other).isEmpty());
            screen.height = 220;
            assertEquals(200, JeiIntegration.extraAreas(screen).iterator().next().getY());
        } finally {
            JeiIntegration.setExtraAreas(screen, null);
        }
        assertTrue(JeiIntegration.extraAreas(screen).isEmpty());
    }

    private static final class TestBackend implements JeiIntegration.Backend {
        private long revision;

        @Override
        public long revision() { return revision; }

        @Override
        public List<JeiIntegration.Ingredient> ingredients() { return List.of(); }

        @Override
        public List<JeiIntegration.Recipe> recipes(JeiIntegration.Ingredient ingredient,
                                                   JeiIntegration.Direction direction, int limit) { return List.of(); }

        @Override
        public boolean open(JeiIntegration.Ingredient ingredient, JeiIntegration.Direction direction) { return false; }
    }
}
