package com.dishanhai.sh_better_ui.client.gui.filter;

import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Ingredient;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Kind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：无渲染验证素材筛选、不可变快照和自定义条件组合。 */
class IngredientFilterTest {
    @Test
    void factoriesSelectAllItemsOrFluids() {
        var item = ingredient("iron", "minecraft:iron_ingot", Kind.ITEM);
        var fluid = ingredient("water", "minecraft:water", Kind.FLUID);

        assertTrue(IngredientFilter.all().test(item));
        assertTrue(IngredientFilter.all().test(fluid));
        assertTrue(IngredientFilter.items().test(item));
        assertFalse(IngredientFilter.items().test(fluid));
        assertTrue(IngredientFilter.fluids().test(fluid));
        assertFalse(IngredientFilter.fluids().test(item));
        assertTrue(IngredientFilter.all().withKinds(Set.of(Kind.ITEM, Kind.FLUID)).test(fluid));
    }

    @Test
    void intersectsCategoriesAndAcceptsAnyValueWithinEachCategory() {
        var filter = IngredientFilter.items()
                .withIds(Set.of(id("minecraft:iron_ingot"), id("gtceu:copper_ingot")))
                .withMods(Set.of("minecraft", "gtceu"))
                .withTags(Set.of(id("forge:ingots/iron"), id("forge:ingots/copper")));

        assertTrue(filter.test(ingredient("iron", "minecraft:iron_ingot", Kind.ITEM, "forge:ingots/iron")));
        assertTrue(filter.test(ingredient("copper", "gtceu:copper_ingot", Kind.ITEM, "forge:ingots/copper")));
        assertFalse(filter.test(ingredient("fluid", "minecraft:iron_ingot", Kind.FLUID, "forge:ingots/iron")));
        assertFalse(filter.test(ingredient("gold", "minecraft:gold_ingot", Kind.ITEM, "forge:ingots/iron")));
        assertFalse(filter.test(ingredient("untagged", "minecraft:iron_ingot", Kind.ITEM)));
        assertFalse(filter.withMods(Set.of("other")).test(
                ingredient("iron", "minecraft:iron_ingot", Kind.ITEM, "forge:ingots/iron")));
    }

    @Test
    void idsModsAndTagsUseExactMatches() {
        var iron = ingredient("iron", "gtceu:iron_ingot", Kind.ITEM, "forge:ingots/iron");

        assertFalse(IngredientFilter.all().withIds(Set.of(id("gtceu:iron"))).test(iron));
        assertFalse(IngredientFilter.all().withIds(Set.of(id("minecraft:iron_ingot"))).test(iron));
        assertFalse(IngredientFilter.all().withMods(Set.of("gt")).test(iron));
        assertFalse(IngredientFilter.all().withTags(Set.of(id("forge:ingots"))).test(iron));
        assertFalse(IngredientFilter.all().withTags(Set.of(id("minecraft:ingots/iron"))).test(iron));
        assertTrue(IngredientFilter.all().withTags(Set.of(id("forge:ingots/iron"))).test(iron));
    }

    @Test
    void emptySetsRemoveOnlyTheirOwnRestriction() {
        var fluid = ingredient("water", "minecraft:water", Kind.FLUID);
        var restricted = IngredientFilter.items()
                .withIds(Set.of(id("gtceu:iron_ingot")))
                .withMods(Set.of("gtceu"))
                .withTags(Set.of(id("forge:ingots/iron")));
        var cleared = restricted.withKinds(Set.of()).withIds(Set.of()).withMods(Set.of()).withTags(Set.of());

        assertFalse(restricted.test(fluid));
        assertTrue(cleared.test(fluid));
        assertEquals(restricted.ids(), restricted.withKinds(Set.of()).ids());
        assertEquals(restricted.mods(), restricted.withIds(Set.of()).mods());
        assertEquals(restricted.tags(), restricted.withMods(Set.of()).tags());
        assertEquals(restricted.kinds(), restricted.withTags(Set.of()).kinds());
    }

    @Test
    void snapshotsInputSetsAndExposesOnlyImmutableSets() {
        var kinds = new HashSet<>(Set.of(Kind.ITEM));
        var ids = new HashSet<>(Set.of(id("gtceu:iron_ingot")));
        var mods = new HashSet<>(Set.of("gtceu"));
        var tags = new HashSet<>(Set.of(id("forge:ingots/iron")));
        var original = IngredientFilter.all();
        var filter = original.withKinds(kinds).withIds(ids).withMods(mods).withTags(tags);
        kinds.clear();
        ids.clear();
        mods.clear();
        tags.clear();

        assertEquals(Set.of(Kind.ITEM), filter.kinds());
        assertEquals(Set.of(id("gtceu:iron_ingot")), filter.ids());
        assertEquals(Set.of("gtceu"), filter.mods());
        assertEquals(Set.of(id("forge:ingots/iron")), filter.tags());
        assertTrue(original.kinds().isEmpty());
        assertTrue(original.ids().isEmpty());
        assertTrue(original.mods().isEmpty());
        assertTrue(original.tags().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> filter.kinds().clear());
        assertThrows(UnsupportedOperationException.class, () -> filter.ids().clear());
        assertThrows(UnsupportedOperationException.class, () -> filter.mods().clear());
        assertThrows(UnsupportedOperationException.class, () -> filter.tags().clear());
    }

    @Test
    void normalizesNamespacesAndRejectsBlankNamespaces() {
        var filter = IngredientFilter.all().withMods(Set.of("  GTCEU\t", "gtceu"));

        assertEquals(Set.of("gtceu"), filter.mods());
        assertTrue(filter.test(ingredient("iron", "gtceu:iron_ingot", Kind.ITEM)));
        assertThrows(IllegalArgumentException.class, () -> IngredientFilter.all().withMods(Set.of(" \t")));
    }

    @Test
    void idFiltersKeepVariantsAndStandardPredicatesCanNarrowThem() {
        var first = ingredient("iron:first", "minecraft:iron_ingot", Kind.ITEM);
        var second = ingredient("iron:second", "minecraft:iron_ingot", Kind.ITEM);
        var water = ingredient("water", "minecraft:water", Kind.FLUID);
        var filter = IngredientFilter.items().withIds(Set.of(first.id()));
        var custom = filter.and(value -> value.key().equals(second.key()));

        assertTrue(filter.test(first));
        assertTrue(filter.test(second));
        assertFalse(custom.test(first));
        assertTrue(custom.test(second));
        assertTrue(filter.or(IngredientFilter.fluids()).test(water));
        assertTrue(filter.negate().test(water));
        assertFalse(filter.negate().test(first));
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.tryParse(value);
    }

    private static TestIngredient ingredient(String key, String id, Kind kind, String... tags) {
        return new TestIngredient(key, id(id), kind, List.of(tags).stream().map(IngredientFilterTest::id).toList());
    }

    private record TestIngredient(String key, ResourceLocation id, Kind kind,
                                  List<ResourceLocation> tags) implements Ingredient {
        @Override
        public String name() {
            return key;
        }

        @Override
        public String modName() {
            return id.getNamespace();
        }

        @Override
        public List<String> aliases() {
            return List.of();
        }

        @Override
        public boolean visible() {
            return true;
        }

        @Override
        public int width() {
            return 16;
        }

        @Override
        public int height() {
            return 16;
        }

        @Override
        public void render(GuiGraphics graphics, int x, int y) {}

        @Override
        public List<Component> tooltip() {
            return List.of();
        }
    }
}
