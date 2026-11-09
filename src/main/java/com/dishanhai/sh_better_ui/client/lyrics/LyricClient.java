// Adapted by dishanhai from Link-Lyric (beipuo).
// Source: https://github.com/beipuo/Link-Lyric/tree/1a7cd274c3e7afbb33c08c66414ec0def944643b
// See META-INF/third-party/link-lyric for licenses and upstream notices.
package com.dishanhai.sh_better_ui.client.lyrics;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.lyrics.common.bridge.BridgePlaybackSource;
import com.dishanhai.sh_better_ui.client.lyrics.common.config.LayoutConfig;
import com.dishanhai.sh_better_ui.client.lyrics.common.hud.FloatingLyricRenderer;
import com.dishanhai.sh_better_ui.client.lyrics.common.hud.LyricDragController;
import com.dishanhai.sh_better_ui.client.lyrics.common.i18n.LangKeys;
import com.dishanhai.sh_better_ui.client.lyrics.render.LyricView;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** 山海：客户端的可选 Salt Player 歌词 HUD 与设置入口。 */
public final class LyricClient {
    private static final BridgePlaybackSource SOURCE = BridgePlaybackSource.linkMc();
    private static final FloatingLyricRenderer HUD = new FloatingLyricRenderer(SOURCE);

    private static LyricDragController controls;
    private static Supplier<Screen> pendingScreen;
    private static boolean polling;

    static {
        HUD.setTextResolver(key -> Component.translatable(key).getString());
    }

    private LyricClient() {}

    private static LyricDragController controls() {
        if (controls == null) {
            var directory = Minecraft.getInstance().gameDirectory.toPath().resolve("config");
            controls = new LyricDragController(HUD, LayoutConfig.load(directory));
        }
        return controls;
    }

    public static Screen settings(Screen parent) {
        return new LyricConfigScreen(HUD, controls(), parent);
    }

    public static Screen position(Screen parent) {
        return new LyricLocationScreen(HUD, controls(), parent);
    }

    static String linkMcStatus(Minecraft client) {
        if (!controls().isEnabled() || !controls().isSourceEnabled()) return LangKeys.LINK_MC_DISABLED;
        if (client.player == null) return LangKeys.LINK_MC_NO_WORLD;
        if (!SOURCE.isAlive()) return LangKeys.LINK_MC_WAITING;
        return SOURCE.lyric().isEmpty() ? LangKeys.LINK_MC_NO_LYRICS : LangKeys.LINK_MC_SYNCED;
    }

    public static void tick(Minecraft client) {
        if (pendingScreen != null) {
            var next = pendingScreen;
            pendingScreen = null;
            client.setScreen(next.get());
        }
        boolean shouldPoll = client.player != null && controls().isEnabled() && controls().isSourceEnabled();
        if (polling == shouldPoll) return;
        polling = shouldPoll;
        if (polling) SOURCE.start();
        else SOURCE.stop();
    }

    public static void renderHud(Minecraft client, GuiGraphics graphics) {
        if (polling && client.screen == null && client.getOverlay() == null && !client.options.hideGui) {
            LyricView.draw(HUD, graphics, client.font, null, UiPreferences.configured());
        }
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shbetterlyrics")
                .executes(ctx -> {
                    pendingScreen = () -> settings(null);
                    return 1;
                })
                .then(Commands.literal("config").executes(ctx -> {
                    pendingScreen = () -> settings(null);
                    return 1;
                }))
                .then(Commands.literal("on").executes(ctx -> {
                    controls().enable();
                    controls().setSourceEnabled(true);
                    ctx.getSource().sendSuccess(() -> Component.translatable(LangKeys.COMMAND_ON), false);
                    return 1;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    controls().disable();
                    ctx.getSource().sendSuccess(() -> Component.translatable(LangKeys.COMMAND_OFF), false);
                    return 1;
                }))
                .then(Commands.literal("location").executes(ctx -> {
                    pendingScreen = () -> position(null);
                    return 1;
                }).then(Commands.literal("reset").executes(ctx -> {
                    controls().reset();
                    ctx.getSource().sendSuccess(() -> Component.translatable(LangKeys.COMMAND_RESET), false);
                    return 1;
                }))));
    }
}
