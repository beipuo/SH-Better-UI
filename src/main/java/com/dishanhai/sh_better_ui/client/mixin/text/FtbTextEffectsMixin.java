package com.dishanhai.sh_better_ui.client.mixin.text;

import com.dishanhai.sh_better_ui.client.gui.text.VanillaTextEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Function;

/** 山海：将 FCS 段交回 FTB 处理普通格式、换行和替换项。 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftblibrary.util.TextComponentParser", remap = false)
abstract class FtbTextEffectsMixin {
    @Shadow(remap = false)
    private static Component parse0(String text, Function<String, Component> substitutes) {
        throw new AssertionError();
    }

    @Inject(method = "parse(Ljava/lang/String;Ljava/util/function/Function;)Lnet/minecraft/network/chat/Component;",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void shbui$parseEffects(String raw, Function<String, Component> substitutes,
                                         CallbackInfoReturnable<Component> result) {
        Component formatted = VanillaTextEffects.parse(raw);
        if (formatted == null) return;
        var output = Component.empty();
        formatted.visit((style, text) -> {
            var part = parse0(text, substitutes).copy();
            part.setStyle(part.getStyle().applyTo(style));
            output.append(part);
            return Optional.empty();
        }, Style.EMPTY);
        result.setReturnValue(output);
    }
}
