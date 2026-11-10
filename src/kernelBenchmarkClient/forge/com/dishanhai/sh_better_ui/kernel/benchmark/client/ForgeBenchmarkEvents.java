// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.benchmark.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "sh_better_ui", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeBenchmarkEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void shutdown(GameShuttingDownEvent event) { NativeKernelBenchmark.shutdown(); }
}
