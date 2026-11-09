package com.dishanhai.sh_better_ui.client;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.blur.GaussBlur;
import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiLocalModal;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSwipeToast;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiToast;
import com.dishanhai.sh_better_ui.client.lyrics.LyricClient;
import com.dishanhai.sh_better_ui.client.music.ClientMusic;
import com.dishanhai.sh_better_ui.client.lyrics.render.LyricView;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** 山海：整個進程只註冊一條共用後處理管線；消費端僅提供畫面 predicates。 */
@Mod.EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UiClient {
    private static KeyMapping controlPanelKey;

    private UiClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        LyricClient.tick(minecraft);
        UiLocalModal.current(minecraft.screen);
        if (controlPanelKey == null) return;
        while (controlPanelKey.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null && minecraft.getOverlay() == null) {
                minecraft.setScreen(createControlPanel(null));
            }
        }
    }

    @SubscribeEvent
    public static void onLyricCommands(RegisterClientCommandsEvent event) {
        LyricClient.registerCommands(event.getDispatcher());
    }

    private static Screen createControlPanel(Screen parent) {
        return new ControlPanelScreen(parent, controlPanelKey, ShBetterUi::saveClientUiSettings);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFrameStart(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) UiFrameScheduler.beginFrame();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFrameEnd(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) UiClientRendering.endFrame();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        UiClientRendering.prepareScreenOverlays(event.getScreen());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        GaussBlur.onScreenRenderPost(event);
        UiClientRendering.renderScreenOverlays(event.getScreen(), event.getGuiGraphics(), event.getMouseX(),
                event.getMouseY(), event.getPartialTick());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() instanceof ControlPanelScreen panel && panel.reopenFromMouse(event.getButton())) {
            event.setCanceled(true);
            return;
        }
        UiPointerCapture.beforePress(event.getScreen(), event.getButton());
        UiOverlayHost overlay = UiOverlayHost.current(event.getScreen());
        UiLocalModal local = UiLocalModal.current(event.getScreen());
        if (overlay != null && overlay.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        } else if (local != null && local.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        } else if (UiSwipeToast.handleOverlayPress(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton())
                || UiPointerCapture.press(event.getScreen(), event.getButton(), event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseButtonReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        UiLocalModal local = UiLocalModal.current(event.getScreen());
        if (UiPointerCapture.release(event.getScreen(), event.getButton(), event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
            return;
        }
        UiOverlayHost overlay = UiOverlayHost.current(event.getScreen());
        if ((overlay != null && overlay.mouseReleased(event.getMouseX(), event.getMouseY(), event.getButton()))
                || (local != null && local.mouseReleased(event.getMouseX(), event.getMouseY(), event.getButton()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        UiLocalModal local = UiLocalModal.current(event.getScreen());
        if (UiPointerCapture.drag(event.getScreen(), event.getMouseButton(), event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
            return;
        }
        UiOverlayHost overlay = UiOverlayHost.current(event.getScreen());
        if ((overlay != null && overlay.mouseDragged(event.getMouseX(), event.getMouseY(), event.getMouseButton(),
                event.getDragX(), event.getDragY()))
                || (local != null && local.mouseDragged(event.getMouseX(), event.getMouseY(), event.getMouseButton()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        UiLocalModal local = UiLocalModal.current(event.getScreen());
        if (local != null && local.mouseScrolled(event.getMouseX(), event.getMouseY(), 0, event.getScrollDelta())) {
            event.setCanceled(true);
            return;
        }
        if (event.getScrollDelta() != 0) UiPointerCapture.cancel(event.getScreen());
        UiOverlayHost overlay = UiOverlayHost.current(event.getScreen());
        if (overlay != null && overlay.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDelta())) {
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
        UiLocalModal local = UiLocalModal.current(event.getScreen());
        if (overlay != null && overlay.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
        } else if (local != null && local.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
        } else if (event.getKeyCode() == GLFW.GLFW_KEY_ESCAPE
                && (UiPointerCapture.cancel(event.getScreen()) || UiSwipeToast.handleEscape(event.getScreen()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        UiOverlayHost overlay = UiOverlayHost.current(event.getScreen());
        UiLocalModal local = UiLocalModal.current(event.getScreen());
        if ((overlay != null && overlay.keyReleased(event.getKeyCode(), event.getScanCode(), event.getModifiers()))
                || (local != null && local.keyReleased(event.getKeyCode(), event.getScanCode(), event.getModifiers()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCharacterTyped(ScreenEvent.CharacterTyped.Pre event) {
        UiOverlayHost overlay = UiOverlayHost.current(event.getScreen());
        UiLocalModal local = UiLocalModal.current(event.getScreen());
        if ((overlay != null && overlay.charTyped(event.getCodePoint(), event.getModifiers()))
                || (local != null && local.focusedCovered())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTooltip(RenderTooltipEvent.Pre event) {
        var client = Minecraft.getInstance();
        UiOverlayHost overlay = UiOverlayHost.current(client.screen);
        UiLocalModal local = UiLocalModal.current(client.screen);
        if ((overlay != null && overlay.isModalOpen())
                || (local != null && client.getLastInputType().isKeyboard() && local.hidesFocusedTooltip())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHudRenderPost(RenderGuiEvent.Post event) {
        LyricClient.renderHud(Minecraft.getInstance(), event.getGuiGraphics());
        UiClientRendering.renderHudOverlay(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        UiLocalModal.interrupt(event.getScreen());
        UiPointerCapture.cancel(event.getScreen());
        if (GaussBlur.isAllowed(event.getScreen())) GaussBlur.warmUp();
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        UiClientRendering.screenClosing(event.getScreen());
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
        ClientMusic.artwork().invalidate();
        GaussBlur.release();
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null && GaussBlur.isAllowed(screen)) GaussBlur.warmUp();
    }

    @Mod.EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ReloadRegistration {
        private ReloadRegistration() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> ClientMusic.start(Minecraft.getInstance().gameDirectory.toPath().resolve("config")));
            ModList.get().getModContainerById(ShBetterUi.MOD_ID).ifPresent(container ->
                    container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                            () -> new ConfigScreenHandler.ConfigScreenFactory(UiClient::createControlPanel)));
        }

        @SubscribeEvent
        public static void onRegisterLyricShader(RegisterShadersEvent event) {
            LyricView.registerShader(event.getResourceProvider(), event::registerShader);
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
