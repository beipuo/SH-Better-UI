package com.dishanhai.sh_better_ui.client.gui.selection;

import com.dishanhai.sh_better_ui.client.jei.JeiIntegration;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：固定来源与扩展默认方法保持无 JEI 依赖的共用契约。 */
class IngredientCatalogTest {
    @Test
    void catalogAndFactoriesLoadWithoutOptionalJeiClasses() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        assertThrows(ClassNotFoundException.class, () -> Class.forName("mezz.jei.api.IModPlugin", false, loader));
        assertNotNull(Class.forName(IngredientCatalog.class.getName(), true, loader));
        assertNotNull(Class.forName(IngredientValues.class.getName(), true, loader));
        assertFalse(IngredientCatalog.jei().isAvailable());
        assertEquals(List.of(), IngredientCatalog.jei().ingredients());
        assertTrue(IngredientCatalog.registry().isAvailable());
    }

    @Test
    void fixedSourceOwnsAnImmutableCandidateSnapshot() {
        var ingredient = new TestIngredient("custom:first");
        var candidates = new ArrayList<JeiIntegration.Ingredient>(List.of(ingredient));
        IngredientCatalog catalog = IngredientCatalog.of(candidates);
        candidates.clear();

        assertTrue(catalog.isAvailable());
        long revision = catalog.revision();
        assertEquals(List.of(ingredient), catalog.ingredients());
        assertSame(catalog.ingredients(), catalog.ingredients());
        assertThrows(UnsupportedOperationException.class, () -> catalog.ingredients().clear());
        assertThrows(NullPointerException.class, () -> IngredientCatalog.of(null));
        assertEquals(revision, catalog.revision());
    }

    @Test
    void tagUpdateRefreshesFixedJeiAndRegistryCatalogs() {
        var fixed = IngredientCatalog.of(List.of(new TestIngredient("custom:tags")));
        var jei = IngredientCatalog.jei();
        var registry = IngredientCatalog.registry();
        long fixedBefore = fixed.revision();
        long jeiBefore = jei.revision();
        long registryBefore = registry.revision();

        IngredientValues.RegistryEvents.onTagsUpdated(null);

        assertTrue(fixed.revision() > fixedBefore);
        assertTrue(jei.revision() > jeiBefore);
        assertTrue(registry.revision() > registryBefore);
    }

    @Test
    void legacyIngredientsReceiveEmptyTagsAndCopyDefaults() {
        var ingredient = new TestIngredient("legacy");
        assertEquals(List.of(), ingredient.tags());
        assertTrue(ingredient.copy(Object.class).isEmpty());
        assertThrows(NullPointerException.class, () -> ingredient.copy(null));
    }

    private record TestIngredient(String key) implements JeiIntegration.Ingredient {
        @Override public String name() { return key; }
        @Override public ResourceLocation id() { return ResourceLocation.tryParse("test:custom"); }
        @Override public String modName() { return "test"; }
        @Override public List<String> aliases() { return List.of(); }
        @Override public JeiIntegration.Kind kind() { return JeiIntegration.Kind.ITEM; }
        @Override public boolean visible() { return true; }
        @Override public int width() { return 16; }
        @Override public int height() { return 16; }
        @Override public void render(GuiGraphics graphics, int x, int y) {}
        @Override public List<Component> tooltip() { return List.of(); }
    }
}
