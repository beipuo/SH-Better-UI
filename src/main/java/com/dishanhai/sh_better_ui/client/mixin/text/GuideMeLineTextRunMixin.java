package com.dishanhai.sh_better_ui.client.mixin.text;

import com.dishanhai.sh_better_ui.client.gui.text.GuideMeTextEffects;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 山海：在 GuideME 原有位置、字体和缩放下绘制带样式的片段。 */
@Pseudo
@Mixin(targets = "guideme.layout.flow.LineTextRun", remap = false)
abstract class GuideMeLineTextRunMixin {
    @Shadow @Final private String text;
    @Unique private Component shbui$styled;

    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void shbui$attachRun(CallbackInfo callback) {
        shbui$styled = GuideMeTextEffects.takeRun(text);
    }

    @ModifyArg(method = "renderBatch", at = @At(value = "INVOKE",
            target = "Lguideme/render/RenderContext;renderTextInBatch(Ljava/lang/String;"
                    + "Lguideme/style/ResolvedTextStyle;FFLnet/minecraft/client/renderer/MultiBufferSource;)V"),
            index = 0, require = 0)
    private String shbui$drawRun(String raw) {
        return shbui$styled == null ? raw : GuideMeTextEffects.beginDraw(raw, shbui$styled);
    }

    @Inject(method = "renderBatch", at = @At("RETURN"), require = 0)
    private void shbui$endRun(CallbackInfo callback) {
        if (shbui$styled != null) GuideMeTextEffects.endDraw();
    }
}
