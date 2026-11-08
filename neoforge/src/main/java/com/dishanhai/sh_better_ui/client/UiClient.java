package com.dishanhai.sh_better_ui.client;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.blur.GaussBlur;
import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSwipeToast;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToast;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** 山海：整個進程只註冊一條共用後處理管線；消費端僅提供畫面 predicates。 */
@EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT)
public final class UiClient {
    private static KeyMapping controlPanelKey;

    private UiClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (controlPanelKey == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        while (controlPanelKey.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null && minecraft.getOverlay() == null) {
                minecraft.setScreen(createControlPanel(null));
            }
        }
    }

    private static Screen createControlPanel(Screen parent) {
        return new ControlPanelScreen(parent, controlPanelKey, ShBetterUi::saveClientUiSettings);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        GaussBlur.onScreenRenderPost(event);
        if (event.getScreen() != Minecraft.getInstance().screen) return;
        UiPointerCapture.validate(event.getScreen());
        UiOverlayHost overlay = UiOverlayHost.current(event.getScreen());
        if (overlay != null) {
            overlay.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
        }
        UiToast.renderOverlay(event.getGuiGraphics());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() instanceof ControlPanelScreen panel && panel.reopenFromMouse(event.getButton())) {
            event.setCanceled(true);
            return;
        }
        UiPointerCapture.beforePress(event.getScreen(), event.getButton());
        UiOverlayHost overlay = UiOverlayHost.current(event.getScreen());
        if (overlay != null && overlay.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        } else if (UiSwipeToast.handleOverlayPress(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton())
                || UiPointerCapture.press(event.getScreen(), event.getButton(), event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseButtonReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (UiPointerCapture.release(event.getScreen(), event.getButton(), event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
            return;
        }
        UiOverlayHost overlay = UiOverlayHost.current(event.getScreen());
        if (overlay != null && overlay.mouseReleased(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (UiPointerCapture.drag(event.getScreen(), event.getMouseButton(), event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
            return;
        }
        UiOverlayHost overlay = UiOverlayHost.current(event.getScreen());
        if (overlay != null && overlay.mouseDragged(event.getMouseX(), event.getMouseY(), event.getMouseButton(), event.getDragX(), event.getDragY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (event.getScrollDeltaY() != 0 || event.getScrollDeltaX() != 0) UiPointerCapture.cancel(event.getScreen());
        UiOverlayHost overlay = UiOverlayHost.current(event.getScreen());
        if (overlay != null && overlay.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (event.getScreen() instanceof ControlPanelScreen panel
                && panel.reopenFromKey(event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
            return;
        }
        UiOverlayHost overlay = UiOverlayHost.current(event.getScreen());
        if (overlay != null && overlay.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
        } else if (event.getKeyCode() == GLFW.GLFW_KEY_ESCAPE
                && (UiPointerCapture.cancel(event.getScreen()) || UiSwipeToast.handleEscape(event.getScreen()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        UiOverlayHost overlay = UiOverlayHost.current(event.getScreen());
        if (overlay != null && overlay.keyReleased(event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCharacterTyped(ScreenEvent.CharacterTyped.Pre event) {
        UiOverlayHost overlay = UiOverlayHost.current(event.getScreen());
        if (overlay != null && overlay.charTyped(event.getCodePoint(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTooltip(RenderTooltipEvent.Pre event) {
        UiOverlayHost overlay = UiOverlayHost.current(Minecraft.getInstance().screen);
        if (overlay != null && overlay.isModalOpen()) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHudRenderPost(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && minecraft.getOverlay() == null && !minecraft.options.hideGui) {
            UiToast.renderOverlay(event.getGuiGraphics());
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        UiPointerCapture.cancel(event.getScreen());
        if (GaussBlur.isAllowed(event.getScreen())) GaussBlur.warmUp();
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        UiPointerCapture.ownerClosed(event.getScreen());
        UiOverlayHost overlay = UiOverlayHost.current(event.getScreen());
        if (overlay != null) overlay.ownerClosed();
        GaussBlur.clear();
        GaussBlur.clearTopLayer();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        UiToast.clear();
        Minecraft.getInstance().execute(() -> {
            UiPointerCapture.validate(null);
            GaussBlur.release();
        });
    }

    /** 資源重載仍停留在玻璃畫面時，立即以新資源恢復管線，不必重開畫面。 */
    private static void reloadCurrentScreen() {
        GaussBlur.release();
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null && GaussBlur.isAllowed(screen)) GaussBlur.warmUp();
    }

    @EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ReloadRegistration {
        private ReloadRegistration() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            ModList.get().getModContainerById(ShBetterUi.MOD_ID).ifPresent(container ->
                    container.registerExtensionPoint(IConfigScreenFactory.class,
                            (ignored, parent) -> createControlPanel(parent)));
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            controlPanelKey = new KeyMapping("key.sh_better_ui.control_panel", KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.sh_better_ui");
            event.register(controlPanelKey);
        }

        @SubscribeEvent
        public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
            // 等待資源準備完成，再回主執行緒重建目前畫面需要的 GL 資源。
            event.registerReloadListener((barrier, resources, preparationProfiler, reloadProfiler,
                                          preparationExecutor, reloadExecutor) ->
                    barrier.wait(null).thenRunAsync(UiClient::reloadCurrentScreen, Minecraft.getInstance()));
        }
    }
}
