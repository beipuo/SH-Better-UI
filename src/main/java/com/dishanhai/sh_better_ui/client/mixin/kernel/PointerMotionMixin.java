// Author: dishanhai
package com.dishanhai.sh_better_ui.client.mixin.kernel;

import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class PointerMotionMixin {
    @Inject(method = {"onMove(JDD)V", "m_91561_(JDD)V"}, at = @At("RETURN"), remap = false)
    private void shbui$pointerMoved(long window, double x, double y, CallbackInfo callback) {
        ClientUiKernel.pointerMoved(window, x, y);
    }
}
