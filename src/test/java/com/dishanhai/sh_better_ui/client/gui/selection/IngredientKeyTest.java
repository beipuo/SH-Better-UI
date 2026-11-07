package com.dishanhai.sh_better_ui.client.gui.selection;

import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Kind;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** 山海：比较活跃候选的结构化形态键，无需初始化游戏注册表。 */
class IngredientKeyTest {
    @Test
    void liveCandidatesShareAKeyOnlyWhenKindIdAndDataMatch() {
        var id = ResourceLocation.tryParse("test:ingredient");
        var first = IngredientKey.of(Kind.ITEM, id, Map.of("variant", 1));
        var duplicate = IngredientKey.of(Kind.ITEM, id, Map.of("variant", 1));
        var variant = IngredientKey.of(Kind.ITEM, id, Map.of("variant", 2));
        var fluid = IngredientKey.of(Kind.FLUID, id, Map.of("variant", 1));
        var other = IngredientKey.of(Kind.ITEM, ResourceLocation.tryParse("test:other"), Map.of("variant", 1));

        assertSame(first, duplicate);
        assertNotEquals(first.value(), variant.value());
        assertNotEquals(first.value(), fluid.value());
        assertNotEquals(first.value(), other.value());
    }
}
