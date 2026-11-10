// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

@EventBusSubscriber(modid = "sh_better_ui", value = Dist.CLIENT)
public final class NeoThemeSmokeEvents {
    private static final DynamicThemeWorldSmoke.Hooks WORLDS = new DynamicThemeWorldSmoke.Hooks() {
        public void create(Minecraft client, Screen parent, String name) {
            client.createWorldOpenFlows().createFreshLevel(name, DynamicThemeWorldSmoke.settings(),
                    DynamicThemeWorldSmoke.options(), DynamicThemeWorldSmoke::flat, parent);
        }

        public void leave(Minecraft client, Screen title) {
            client.level.disconnect();
            client.disconnect(title);
        }
    };

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void frame(RenderFrameEvent.Post event) { DynamicThemeSmoke.frame(WORLDS); }
}
