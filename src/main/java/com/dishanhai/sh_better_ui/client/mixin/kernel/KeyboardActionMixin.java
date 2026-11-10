// Author: dishanhai
package com.dishanhai.sh_better_ui.client.mixin.kernel;

import com.dishanhai.sh_better_ui.client.NativeClientBridge;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyboardActionMixin {
    // Forge production names this method m_90893_. Both dev runs and NeoForge use Mojmap.
    @Inject(method = {"keyPress(JIIII)V", "m_90893_(JIIII)V"}, at = @At("HEAD"), remap = false)
    private void shbui$beginKey(long window, int key, int scan, int action, int mods, CallbackInfo callback) {
        NativeClientBridge.beginKey(window, key, scan, action);
    }

    @Inject(method = {"keyPress(JIIII)V", "m_90893_(JIIII)V"}, at = @At("RETURN"), remap = false)
    private void shbui$endKey(long window, int key, int scan, int action, int mods, CallbackInfo callback) {
        NativeClientBridge.endKey();
    }
}
