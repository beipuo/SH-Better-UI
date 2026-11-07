package com.dishanhai.sh_better_ui.client.jei;

import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** 山海：Forge 1.20.1 的 JEI 提示文本适配。 */
final class JeiPlatformTooltip {
    private JeiPlatformTooltip() {}

    static <T> List<Component> get(IIngredientRenderer<T> renderer, T ingredient) {
        var flag = Minecraft.getInstance().options.advancedItemTooltips
                ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;
        return renderer.getTooltip(ingredient, flag);
    }
}
