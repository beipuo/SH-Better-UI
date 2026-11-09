package com.dishanhai.sh_better_ui.client.mixin.text;

import com.dishanhai.sh_better_ui.client.gui.text.VanillaTextEffects;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 山海：在聊天等界面断行前移除旧格式码。 */
@Mixin(ComponentRenderUtils.class)
abstract class ComponentWrapTextEffectsMixin {
    private static final String WRAP = "(Lnet/minecraft/network/chat/FormattedText;I"
            + "Lnet/minecraft/client/gui/Font;)Ljava/util/List;";

    @ModifyVariable(method = {"wrapComponents" + WRAP, "m_94005_" + WRAP},
            at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private static FormattedText shbui$normalizeLegacy(FormattedText text) {
        return VanillaTextEffects.normalize(text);
    }
}
