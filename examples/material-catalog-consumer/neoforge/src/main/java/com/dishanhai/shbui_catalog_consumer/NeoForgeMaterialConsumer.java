// Author: dishanhai
package com.dishanhai.shbui_catalog_consumer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ScreenEvent;

@Mod(CatalogConsumerModule.MOD_ID)
public final class NeoForgeMaterialConsumer {
    @EventBusSubscriber(modid = CatalogConsumerModule.MOD_ID, value = Dist.CLIENT)
    public static final class ClientEvents {
        @SubscribeEvent
        public static void screen(ScreenEvent.Init.Post event) {
            CatalogConsumerModule.addTitleEntry(event.getScreen(), event::addListener);
        }
    }
}
