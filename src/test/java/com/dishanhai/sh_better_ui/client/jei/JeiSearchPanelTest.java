package com.dishanhai.sh_better_ui.client.jei;

import com.dishanhai.sh_better_ui.client.gui.search.JeiSearchPanel;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.filter.IngredientFilter;
import com.dishanhai.sh_better_ui.client.gui.selection.IngredientCatalog;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
            assertTrue(((UiButton) button(panel, "uses")).isSelected());
            assertFalse(((UiButton) button(panel, "obtaining")).isSelected());
            assertNotNull(button(panel, "uses").getTooltip());
            assertEquals(new Request(variant, JeiIntegration.Direction.USES), backend.lookups.getLast());
            var choose = new ArrayList<JeiIntegration.Ingredient>();
            panel.setOnChoose(choose::add);
            assertTrue(button(panel, "choose").isActive());
            button(panel, "choose").onPress();
            assertEquals(List.of(variant), choose);

            assertTrue(button(panel, "open").isActive());
            button(panel, "open").onPress();
            button(panel, "obtaining").onPress();
            assertTrue(((UiButton) button(panel, "obtaining")).isSelected());
            assertFalse(((UiButton) button(panel, "uses")).isSelected());
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
            assertTrue(panel.hasOpenSuggestions(screenHeight));
            var dropdown = areas.getLast();
            assertTrue(panel.mouseClicked(dropdown.getX() + 1, dropdown.getY() + 1,
                    GLFW.GLFW_MOUSE_BUTTON_LEFT, screenHeight));
            assertEquals("Iron Ingot", search.getValue());
            assertNull(panel.snapshot().selectedKey());
            assertTrue(backend.lookups.isEmpty());
            assertEquals(1, panel.extraAreas(screenHeight).size());
            assertFalse(panel.hasOpenSuggestions(screenHeight));

            assertTrue(search.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
            assertEquals(visible.key(), panel.snapshot().selectedKey());
            assertEquals(List.of(new Request(visible, JeiIntegration.Direction.OBTAINING)), backend.lookups);
        } finally {
            JeiIntegration.uninstall(backend);
        }
    }

    @Test
    void pinyinModFilterAndItemQueryShareTheResultAndSuggestionScope() {
        var vanilla = new TestIngredient("vanilla", "铁锭", ResourceLocation.tryParse("minecraft:iron_ingot"),
                "Minecraft", List.of(), JeiIntegration.Kind.ITEM, true);
        var modded = new TestIngredient("modded", "铁板", ResourceLocation.tryParse("demo:iron_plate"),
                "测试模组", List.of(), JeiIntegration.Kind.ITEM, true);
        var backend = new TestBackend(vanilla, modded);
        JeiIntegration.install(backend);
        try {
            var panel = newPanel();
            var search = (EditBox) panel.widgets().get(0);
            var modSearch = (EditBox) panel.widgets().get(1);
            modSearch.setValue("csmz");
            modSearch.setFocused(true);
            assertTrue(panel.hasOpenSuggestions(400));
            modSearch.setFocused(false);

            search.setValue("tie");
            search.setFocused(true);
            assertTrue(panel.hasOpenSuggestions(400));
            assertTrue(search.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
            assertEquals("铁板", search.getValue());
            assertNull(panel.snapshot().selectedKey());

            search.setValue("tie");
            assertTrue(search.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
            assertEquals(modded.key(), panel.snapshot().selectedKey());
        } finally {
            JeiIntegration.uninstall(backend);
        }
    }

    @Test
    void mainSearchCompletesModAndTagFiltersFromTheCurrentCatalog() {
        var iron = new TestIngredient("iron", "Iron Ingot", ResourceLocation.tryParse("minecraft:iron_ingot"),
                "Minecraft", List.of(), JeiIntegration.Kind.ITEM, true,
                List.of(ResourceLocation.tryParse("forge:ingots/iron")));
        var other = new TestIngredient("other", "Iron Ingot", ResourceLocation.tryParse("other:iron_ingot"),
                "Other Mods", List.of(), JeiIntegration.Kind.ITEM, true,
                List.of(ResourceLocation.tryParse("forge:ingots/iron")));
        var panel = newPanel();
        panel.setCatalog(IngredientCatalog.of(List.of(other, iron)));
        var search = (EditBox) panel.widgets().getFirst();
        search.setFocused(true);
        search.setValue("iron @mine");

        assertTrue(search.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
        assertEquals("iron @minecraft", search.getValue());
        search.setValue("iron @minecraft #forge:ing");
        assertTrue(search.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
        assertEquals("iron @minecraft #forge:ingots/iron", search.getValue());
        assertTrue(search.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertEquals(iron.key(), panel.snapshot().selectedKey());

        panel.setFilter(IngredientFilter.all().withMods(Set.of("other")));
        search.setValue("@mine");
        assertFalse(panel.hasOpenSuggestions(400));
        search.setValue("@other #forge:ingots/iron");
        assertTrue(search.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertEquals(other.key(), panel.snapshot().selectedKey());
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

    @Test
    void customCatalogFiltersResultsAndSuggestionsTogetherWithoutJei() {
        var metal = ResourceLocation.tryParse("forge:ingots/iron");
        var iron = new TestIngredient("iron", "Iron Ingot", ResourceLocation.tryParse("minecraft:iron_ingot"),
                "Minecraft", List.of("tieding"), JeiIntegration.Kind.ITEM, true, List.of(metal));
        var other = item("other", "other:iron_ingot", "Other", "tieding", true);
        var water = new TestIngredient("water", "Water", ResourceLocation.tryParse("minecraft:water"),
                "Minecraft", List.of("tieding"), JeiIntegration.Kind.FLUID, true, List.of());
        var panel = newPanel();
        panel.setCatalog(IngredientCatalog.of(List.of(water, other, iron)));
        panel.setFilter(IngredientFilter.items().withMods(Set.of("minecraft")).withTags(Set.of(metal)));
        var search = (EditBox) panel.widgets().getFirst();
        search.setValue("tieding");
        search.setFocused(true);

        var dropdown = panel.extraAreas(400).getLast();
        assertTrue(panel.mouseClicked(dropdown.getX() + 1, dropdown.getY() + 1, 0, 400));
        assertEquals("Iron Ingot", search.getValue());
        assertTrue(search.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertEquals(iron.key(), panel.snapshot().selectedKey());
        assertFalse(button(panel, "open").isActive());

        panel.setFilter(IngredientFilter.fluids());
        assertEquals("Iron Ingot", search.getValue());
        search.setValue("tieding");
        search.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        assertEquals(water.key(), panel.snapshot().selectedKey());
    }

    @Test
    void chosenDraftStateIsIndependentFromPreviewAndOnlyChangesOnChoose() {
        var iron = item("iron", "minecraft:iron_ingot", "Minecraft", "iron", true);
        var copper = item("copper", "minecraft:copper_ingot", "Minecraft", "copper", true);
        var panel = newPanel();
        panel.setCatalog(IngredientCatalog.of(List.of(iron, copper)));
        var staged = new HashSet<String>();
        var commits = new ArrayList<JeiIntegration.Ingredient>();
        panel.setChosen(ingredient -> staged.contains(ingredient.key()));
        panel.setOnChoose(ingredient -> {
            if (!staged.remove(ingredient.key())) staged.add(ingredient.key());
            commits.add(ingredient);
            panel.refreshSelection();
        });
        ((Button) panel.widgets().get(2)).onPress();
        assertTrue(commits.isEmpty());
        button(panel, "choose").onPress();
        assertTrue(((UiButton) button(panel, "unchoose")).isSelected());
        ((Button) panel.widgets().get(3)).onPress();
        assertFalse(((UiButton) button(panel, "choose")).isSelected());
        button(panel, "choose").onPress();
        assertEquals(Set.of(iron.key(), copper.key()), staged);
        ((Button) panel.widgets().get(2)).onPress();
        button(panel, "unchoose").onPress();
        assertFalse(((UiButton) button(panel, "choose")).isSelected());
        assertEquals(Set.of(copper.key()), staged);
        assertEquals(List.of(iron, copper, iron), commits);
    }

    @Test
    void customCatalogRevisionDiscardsStalePreviewBeforeChoose() {
        var old = item("old", "minecraft:iron_ingot", "Minecraft", "iron", true);
        var replacement = item("new", "minecraft:iron_ingot", "Minecraft", "iron", true);
        class Catalog implements IngredientCatalog {
            long version;
            List<JeiIntegration.Ingredient> values = List.of(old);

            @Override
            public long revision() { return version; }

            @Override
            public List<JeiIntegration.Ingredient> ingredients() { return values; }
        }
        var catalog = new Catalog();
        var panel = newPanel();
        panel.setCatalog(catalog);
        var commits = new ArrayList<JeiIntegration.Ingredient>();
        panel.setOnChoose(commits::add);
        ((Button) panel.widgets().get(2)).onPress();
        assertTrue(button(panel, "choose").isActive());
        catalog.values = List.of(replacement);
        catalog.version++;
        // 模拟鼠标按下后、回调前来源更新，旧素材不得提交。
        button(panel, "choose").onPress();
        assertTrue(commits.isEmpty());
        assertFalse(button(panel, "choose").isActive());
        ((Button) panel.widgets().get(2)).onPress();
        button(panel, "choose").onPress();
        assertEquals(List.of(replacement), commits);
    }

    @Test
    void stableCustomCatalogRefreshesRecipesWhenJeiDisconnectsOrRejoins() {
        var iron = item("custom:iron", "minecraft:iron_ingot", "Minecraft", "iron", true);
        var first = new TestBackend(iron);
        var next = new TestBackend(iron);
        var oldLayout = new TestLayout();
        var newLayout = new TestLayout();
        first.preview = oldLayout;
        next.preview = newLayout;
        JeiIntegration.install(first);
        try {
            var panel = newPanel();
            panel.setCatalog(IngredientCatalog.of(List.of(iron)));
            ((Button) panel.widgets().get(2)).onPress();
            panel.tick();
            assertEquals(1, oldLayout.ticks);
            assertTrue(button(panel, "open").isActive());

            JeiIntegration.uninstall(first);
            panel.tick();
            assertEquals(1, oldLayout.ticks);
            assertFalse(button(panel, "open").isActive());
            assertEquals(iron.key(), panel.snapshot().selectedKey());

            JeiIntegration.install(next);
            panel.tick();
            assertEquals(1, oldLayout.ticks);
            assertEquals(1, newLayout.ticks);
            assertTrue(button(panel, "open").isActive());
        } finally {
            JeiIntegration.uninstall(first);
            JeiIntegration.uninstall(next);
        }
    }

    @Test
    void catalogReplacementAndSameKeyRefreshUseTheCurrentIngredient() {
        var old = item("stable", "minecraft:iron_ingot", "Minecraft", "iron", true);
        var replacement = item("stable", "other:iron_ingot", "Other", "iron", true);
        var panel = newPanel();
        panel.setCatalog(IngredientCatalog.of(List.of(old)));
        var search = (EditBox) panel.widgets().getFirst();
        search.setValue("iron");
        search.setFocused(true);
        assertTrue(search.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertEquals(old.key(), panel.snapshot().selectedKey());
        var commits = new ArrayList<JeiIntegration.Ingredient>();
        panel.setOnChoose(commits::add);
        // 两个固定来源的 revision 相同，替换来源本身仍必须刷新。
        panel.setCatalog(IngredientCatalog.of(List.of(replacement)));
        button(panel, "choose").onPress();
        assertEquals(List.of(replacement), commits);
        assertEquals("iron", panel.snapshot().query());
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
                                  JeiIntegration.Kind kind, boolean visible, List<ResourceLocation> tags)
            implements JeiIntegration.Ingredient {
        private TestIngredient(String key, String name, ResourceLocation id, String modName, List<String> aliases,
                               JeiIntegration.Kind kind, boolean visible) {
            this(key, name, id, modName, aliases, kind, visible, List.of());
        }
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
