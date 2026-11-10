// Author: dishanhai
package com.dishanhai.sh_better_ui.client.mixin.kernel;

import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class WindowFocusMixin {
    @Inject(method = {"setWindowActive(Z)V", "m_7440_(Z)V"}, at = @At("RETURN"), remap = false)
    private void shbui$windowFocusChanged(boolean focused, CallbackInfo callback) {
        ClientUiKernel.windowFocus((Minecraft) (Object) this);
    }
}
