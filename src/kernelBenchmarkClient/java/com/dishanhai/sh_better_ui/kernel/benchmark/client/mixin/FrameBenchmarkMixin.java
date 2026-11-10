// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.benchmark.client.mixin;

import com.dishanhai.sh_better_ui.client.NativeClientBridge;
import com.dishanhai.sh_better_ui.kernel.benchmark.client.NativeKernelBenchmark;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NativeClientBridge.class, remap = false)
abstract class FrameBenchmarkMixin {
    @Inject(method = "frameStart", at = @At("HEAD"))
    private static void beginMeasurement(CallbackInfo callback) {
        NativeKernelBenchmark.beforeFrame();
    }

    @Inject(method = "frameEnd", at = @At("TAIL"))
    private static void finishMeasurement(CallbackInfo callback) {
        NativeKernelBenchmark.afterFrame();
    }
}
