// Author: dishanhai
package com.dishanhai.shbui_catalog_consumer;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(CatalogConsumerModule.MOD_ID)
public final class ForgeMaterialConsumer {
    @Mod.EventBusSubscriber(modid = CatalogConsumerModule.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ClientEvents {
        @SubscribeEvent
        public static void screen(ScreenEvent.Init.Post event) {
            CatalogConsumerModule.addTitleEntry(event.getScreen(), event::addListener);
        }
    }
}
