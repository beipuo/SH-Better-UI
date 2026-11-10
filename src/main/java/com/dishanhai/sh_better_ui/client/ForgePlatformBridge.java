// Author: dishanhai
package com.dishanhai.sh_better_ui.client;

import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.NativeInput;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.fml.ModList;

import java.util.stream.Collectors;

final class ForgePlatformBridge {
    private ForgePlatformBridge() {}

    static void start() {
        var files = ModList.get().getModFiles().stream().map(file -> new ClientUiKernel.ModFile(
                file.getFile().findResource("META-INF", NativeClientBridge.MODULE_DESCRIPTOR), file.moduleName(),
                file.getMods().stream().map(mod -> mod.getModId()).collect(Collectors.toUnmodifiableSet()))).toList();
        ClientUiKernel.start(ModuleDescriptor.Platform.FORGE_1_20_1, files);
    }

    static boolean scroll(ScreenEvent.MouseScrolled.Pre event) {
        return NativeClientBridge.input(event.getScreen(), new NativeInput.Pointer(NativeInput.PointerAction.SCROLL,
                event.getMouseX(), event.getMouseY(), -1, 0, event.getScrollDelta()));
    }

    static void hud(RenderGuiEvent.Post event) {
        NativeClientBridge.render(null, event.getGuiGraphics(), event.getPartialTick());
    }
}
