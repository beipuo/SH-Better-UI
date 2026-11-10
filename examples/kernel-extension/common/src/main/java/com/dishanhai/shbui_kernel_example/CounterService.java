// Author: dishanhai
package com.dishanhai.shbui_kernel_example;

import com.dishanhai.sh_better_ui.kernel.api.ServiceKey;
import com.dishanhai.sh_better_ui.kernel.api.UIService;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Supplier;

/** The example's own public UI service. Call it on the client thread. */
public interface CounterService extends UIService {
    ServiceKey<CounterService> KEY = new ServiceKey<>(
            ExampleUiModule.id("counter"), CounterService.class, 1);

    void open(Screen parent, Supplier<? extends GuiEventListener> returnFocus);
}
