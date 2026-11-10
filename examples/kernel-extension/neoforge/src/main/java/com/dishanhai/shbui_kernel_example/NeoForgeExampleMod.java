// Author: dishanhai
package com.dishanhai.shbui_kernel_example;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

@Mod(ExampleUiModule.MOD_ID)
public final class NeoForgeExampleMod {
    @EventBusSubscriber(modid = ExampleUiModule.MOD_ID, value = Dist.CLIENT)
    public static final class ClientEvents {
        @SubscribeEvent
        public static void screen(ScreenEvent.Init.Post event) {
            ExampleClient.addTitleEntry(event.getScreen(), event::addListener);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void frame(RenderFrameEvent.Post event) {
            ExampleSmoke.frame();
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void shutdown(GameShuttingDownEvent event) {
            ExampleSmoke.shutdown();
        }
    }
}
