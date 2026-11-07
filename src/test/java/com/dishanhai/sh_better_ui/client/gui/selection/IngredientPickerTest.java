package com.dishanhai.sh_better_ui.client.gui.selection;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：公开选取请求与标签筛选的无渲染契约。 */
class IngredientPickerTest {
    @Test
    void requestFactoriesKeepExplicitLimitsAndDefaultPreferences() {
        var catalog = IngredientCatalog.of(List.of());
        var single = IngredientPicker.Request.single(Component.translatable("gui.yes"), catalog);
        var multiple = IngredientPicker.Request.multiple(Component.translatable("gui.yes"), catalog, 8);
        assertEquals(1, single.maxSelections());
        assertEquals(8, multiple.maxSelections());
        assertSame(catalog, single.catalog());
        assertEquals(UiPreferences.DEFAULT, single.preferences());
        assertTrue(single.initial().isEmpty());
        assertTrue(single.baseFilter().test(IngredientSelectionTest.ingredient("test", true)));
    }

    @Test
    void requestCopiesTheInitialListAndRejectsInvalidLimits() {
        var ingredient = IngredientSelectionTest.ingredient("test", true);
        var initial = new ArrayList<JeiIntegration.Ingredient>(List.of(ingredient));
        var request = new IngredientPicker.Request(Component.translatable("gui.yes"), IngredientCatalog.of(initial),
                value -> true, 2, initial, new UiPreferences(true, true, false));
        initial.clear();
        assertEquals(List.of(ingredient), request.initial());
        assertThrows(UnsupportedOperationException.class, () -> request.initial().clear());
        assertThrows(IllegalArgumentException.class, () -> new IngredientPicker.Request(
                Component.translatable("gui.yes"), request.catalog(), 0));
    }

    @Test
    void tagFragmentsMatchIdsButAnExistingFullIdMatchesExactly() {
        var root = ResourceLocation.tryParse("forge:ingots");
        var child = ResourceLocation.tryParse("forge:ingots/iron");
        var ingredient = tagged(child);
        var tags = Set.of(root, child);
        assertTrue(IngredientPicker.tagFilter("", tags).test(ingredient));
        assertTrue(IngredientPicker.tagFilter("INGOTS", tags).test(ingredient));
        assertTrue(IngredientPicker.tagFilter("#forge:ingots/iron", tags).test(ingredient));
        assertFalse(IngredientPicker.tagFilter("forge:ingots", tags).test(ingredient));
        assertFalse(IngredientPicker.tagFilter("dusts", tags).test(ingredient));
    }

    private static JeiIntegration.Ingredient tagged(ResourceLocation tag) {
        return new JeiIntegration.Ingredient() {
            @Override public String key() { return "tagged"; }
            @Override public String name() { return "Tagged"; }
            @Override public ResourceLocation id() { return ResourceLocation.tryParse("minecraft:iron_ingot"); }
            @Override public String modName() { return "Minecraft"; }
            @Override public List<String> aliases() { return List.of(); }
            @Override public List<ResourceLocation> tags() { return List.of(tag); }
            @Override public JeiIntegration.Kind kind() { return JeiIntegration.Kind.ITEM; }
            @Override public boolean visible() { return true; }
            @Override public int width() { return 16; }
            @Override public int height() { return 16; }
            @Override public void render(GuiGraphics graphics, int x, int y) {}
            @Override public List<Component> tooltip() { return List.of(); }
        };
    }
}
