// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "sh_better_ui", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeThemeSmokeEvents {
    private static final DynamicThemeWorldSmoke.Hooks WORLDS = new DynamicThemeWorldSmoke.Hooks() {
        public void create(Minecraft client, Screen parent, String name) {
            client.createWorldOpenFlows().createFreshLevel(name, DynamicThemeWorldSmoke.settings(),
                    DynamicThemeWorldSmoke.options(), DynamicThemeWorldSmoke::flat);
        }

        public void leave(Minecraft client, Screen title) {
            client.level.disconnect();
            client.clearLevel(title);
        }
    };

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) DynamicThemeSmoke.frame(WORLDS);
    }
}
