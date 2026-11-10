// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.benchmark.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

@EventBusSubscriber(modid = "sh_better_ui", value = Dist.CLIENT)
public final class NeoBenchmarkEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void shutdown(GameShuttingDownEvent event) { NativeKernelBenchmark.shutdown(); }
}
