package com.dishanhai.sh_better_ui.client.mixin.text;

import com.dishanhai.sh_better_ui.client.gui.text.GuideMeTextEffects;
import com.dishanhai.sh_better_ui.client.gui.text.VanillaTextEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 山海：GuideME 先排净文本，按它实际产生的文本片段携带效果。 */
@Pseudo
@Mixin(targets = "guideme.layout.flow.LineBuilder", remap = false)
abstract class GuideMeLineBuilderMixin {
    private static final String APPEND = "appendText(Ljava/lang/String;Lguideme/document/flow/LytFlowContent;)V";
    @Unique private GuideMeTextEffects.Layout shbui$layout;

    @ModifyVariable(method = APPEND, at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private String shbui$layoutEffects(String raw) {
        var styled = VanillaTextEffects.parse(raw);
        shbui$layout = styled == null ? null : new GuideMeTextEffects.Layout(styled);
        return shbui$layout == null ? raw : shbui$layout.text();
    }

    @ModifyArg(method = "lambda$appendText$1", at = @At(value = "INVOKE",
            target = "Lguideme/layout/flow/LineTextRun;<init>(Ljava/lang/String;Lguideme/style/ResolvedTextStyle;"
                    + "Lguideme/style/ResolvedTextStyle;)V"), index = 0, require = 0)
    private String shbui$prepareRun(String text) {
        if (shbui$layout != null) shbui$layout.prepareRun(text);
        return text;
    }

    @Inject(method = APPEND, at = @At("RETURN"), require = 0)
    private void shbui$endLayout(CallbackInfo callback) {
        shbui$layout = null;
    }
}
