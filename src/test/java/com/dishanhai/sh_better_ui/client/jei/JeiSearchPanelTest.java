package com.dishanhai.sh_better_ui.client.jei;

import com.dishanhai.sh_better_ui.client.gui.search.JeiSearchPanel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：使用无渲染 JEI 后端验证面板的数据与交互。 */
class JeiSearchPanelTest {
    private static final String PREFIX = "sh_better_ui.jei.";

    @Test
    void restoresTheExactVariantAndOpensNativeJeiWhenInlineLayoutIsAbsent() {
        var first = item("iron:first", "minecraft:iron_ingot", "Minecraft", "tieding", true);
        var variant = item("iron:second", "minecraft:iron_ingot", "Minecraft", "tieding", true);
        var backend = new TestBackend(first, variant);
        JeiIntegration.install(backend);
        try {
            var panel = newPanel();
            var saved = new JeiSearchPanel.Snapshot("iron", "minecraft", variant.key(), 0, JeiIntegration.Direction.USES);
            panel.restore(saved);

            assertEquals(saved, panel.snapshot());
            assertEquals(new Request(variant, JeiIntegration.Direction.USES), backend.lookups.getLast());
            var choose = new ArrayList<JeiIntegration.Ingredient>();
            panel.setOnChoose(choose::add);
            assertTrue(button(panel, "choose").isActive());
            button(panel, "choose").onPress();
            assertEquals(List.of(variant), choose);

            assertTrue(button(panel, "open").isActive());
            button(panel, "open").onPress();
            button(panel, "obtaining").onPress();
            button(panel, "open").onPress();
            assertEquals(List.of(new Request(variant, JeiIntegration.Direction.USES),
                    new Request(variant, JeiIntegration.Direction.OBTAINING)), backend.opens);
        } finally {
            JeiIntegration.uninstall(backend);
        }
    }

    @Test
    void modScopeAppliesToAliasesResultsAndDropdownWithoutSelectingOnFill() {
        var vanilla = item("vanilla", "minecraft:iron_ingot", "Minecraft", "tieding", true);
        var hidden = item("hidden", "other:iron_ingot", "Other Mods", "tieding", false);
        var visible = item("visible", "other:iron_ingot", "Other Mods", "tieding", true);
        var backend = new TestBackend(vanilla, hidden, visible);
        JeiIntegration.install(backend);
        try {
            var panel = newPanel();
            var search = (EditBox) panel.widgets().get(0);
            var modSearch = (EditBox) panel.widgets().get(1);
            modSearch.setValue("Other Mods");
            search.setValue("tieding");
            search.setFocused(true);

            int screenHeight = 400;
            var areas = panel.extraAreas(screenHeight);
            assertEquals(2, areas.size());
            var dropdown = areas.getLast();
            assertTrue(panel.mouseClicked(dropdown.getX() + 1, dropdown.getY() + 1,
                    GLFW.GLFW_MOUSE_BUTTON_LEFT, screenHeight));
            assertEquals("Iron Ingot", search.getValue());
            assertNull(panel.snapshot().selectedKey());
            assertTrue(backend.lookups.isEmpty());
            assertEquals(1, panel.extraAreas(screenHeight).size());

            assertTrue(search.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
            assertEquals(visible.key(), panel.snapshot().selectedKey());
            assertEquals(List.of(new Request(visible, JeiIntegration.Direction.OBTAINING)), backend.lookups);
        } finally {
            JeiIntegration.uninstall(backend);
        }
    }

    @Test
    void disconnectStopsOldPreviewAndReconnectDoesNotReuseSelectionAcrossRuntimes() {
        var oldIngredient = item("world1:iron", "minecraft:iron_ingot", "Minecraft", "old", true);
        var newIngredient = item("world2:iron", "minecraft:iron_ingot", "Minecraft", "new", true);
        var oldBackend = new TestBackend(oldIngredient);
        var newBackend = new TestBackend(newIngredient);
        var oldLayout = new TestLayout();
        var newLayout = new TestLayout();
        oldBackend.preview = oldLayout;
        newBackend.preview = newLayout;

        var panel = newPanel();
        var saved = new JeiSearchPanel.Snapshot("minecraft:iron", "", oldIngredient.key(), 0,
                JeiIntegration.Direction.OBTAINING);
        panel.restore(saved);
        assertFalse(button(panel, "open").isActive());
        try {
            JeiIntegration.install(oldBackend);
            panel.tick();
            assertEquals(1, oldLayout.ticks);
            assertTrue(button(panel, "open").isActive());

            JeiIntegration.uninstall(oldBackend);
            panel.tick();
            assertEquals(1, oldLayout.ticks);
            assertFalse(button(panel, "open").isActive());
            assertEquals(saved, panel.snapshot());

            JeiIntegration.install(newBackend);
            panel.tick();
            assertFalse(button(panel, "open").isActive());
            assertTrue(newBackend.lookups.isEmpty());
            assertEquals(saved.query(), panel.snapshot().query());

            ((Button) panel.widgets().get(2)).onPress();
            panel.tick();
            assertEquals(1, newLayout.ticks);
            assertEquals(1, oldLayout.ticks);
            assertEquals(newIngredient.key(), panel.snapshot().selectedKey());
            button(panel, "open").onPress();
            assertSame(newIngredient, newBackend.opens.getFirst().ingredient());
        } finally {
            JeiIntegration.uninstall(oldBackend);
            JeiIntegration.uninstall(newBackend);
        }
    }

    private static JeiSearchPanel newPanel() {
        Font font = new Font(id -> null, false) {
            @Override
            public String plainSubstrByWidth(String text, int width) {
                return plainSubstrByWidth(text, width, false);
            }

            @Override
            public String plainSubstrByWidth(String text, int width, boolean backwards) {
                int length = Math.clamp(width, 0, text.length());
                return backwards ? text.substring(text.length() - length) : text.substring(0, length);
            }

            @Override
            public int width(String text) { return text.length(); }
        };
        return new JeiSearchPanel(font, 12, 15, 320, 192);
    }

    private static Button button(JeiSearchPanel panel, String key) {
        return panel.widgets().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(candidate -> candidate.getMessage().getContents() instanceof TranslatableContents text
                        && text.getKey().equals(PREFIX + key))
                .findFirst().orElseThrow();
    }

    private static TestIngredient item(String key, String id, String modName, String alias, boolean visible) {
        return new TestIngredient(key, "Iron Ingot", ResourceLocation.tryParse(id), modName, List.of(alias),
                JeiIntegration.Kind.ITEM, visible);
    }

    private record TestIngredient(String key, String name, ResourceLocation id, String modName, List<String> aliases,
                                  JeiIntegration.Kind kind, boolean visible) implements JeiIntegration.Ingredient {
        @Override
        public int width() { return 16; }

        @Override
        public int height() { return 16; }

        @Override
        public void render(GuiGraphics graphics, int x, int y) {}

        @Override
        public List<Component> tooltip() { return List.of(Component.literal(name)); }
    }

    private record Request(JeiIntegration.Ingredient ingredient, JeiIntegration.Direction direction) {}

    private static final class TestBackend implements JeiIntegration.Backend {
        private final List<JeiIntegration.Ingredient> ingredients;
        private final List<Request> lookups = new ArrayList<>();
        private final List<Request> opens = new ArrayList<>();
        private TestLayout preview;

        private TestBackend(JeiIntegration.Ingredient... ingredients) {
            this.ingredients = List.of(ingredients);
        }

        @Override
        public long revision() { return 0; }

        @Override
        public List<JeiIntegration.Ingredient> ingredients() { return ingredients; }

        @Override
        public List<JeiIntegration.Recipe> recipes(JeiIntegration.Ingredient ingredient,
                                                  JeiIntegration.Direction direction, int limit) {
            lookups.add(new Request(ingredient, direction));
            return List.of(new JeiIntegration.Recipe() {
                @Override
                public Component title() { return Component.literal("Test recipe"); }

                @Override
                public Optional<JeiIntegration.RecipeLayout> layout() { return Optional.ofNullable(preview); }
            });
        }

        @Override
        public boolean open(JeiIntegration.Ingredient ingredient, JeiIntegration.Direction direction) {
            opens.add(new Request(ingredient, direction));
            return true;
        }
    }

    private static final class TestLayout implements JeiIntegration.RecipeLayout {
        private int ticks;

        @Override
        public int width() { return 16; }

        @Override
        public int height() { return 16; }

        @Override
        public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {}

        @Override
        public void renderOverlays(GuiGraphics graphics, int mouseX, int mouseY) {}

        @Override
        public void tick() { ticks++; }
    }
}
