package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.api.PlatformUiApi;
import com.dishanhai.sh_better_ui.client.blur.GaussBlur;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.mojang.blaze3d.platform.GlUtil;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/** 山海：主控概览的只读采样；渲染线程调用，宿主按需限频，硬件信息首次采样后缓存。 */
public final class ModOverviewData {
    private static final long MIB = 1024 * 1024;
    private static Hardware hardware;
    private static Metadata metadata;

    private ModOverviewData() {}

    public record Group(Component title, List<Component> lines) {
        public Group {
            lines = List.copyOf(lines);
        }
    }

    /** 未持有客户端时不查询加载器、GL 或硬件；真实客户端应在渲染线程每秒采样一次。 */
    public static List<Group> capture(Minecraft client) {
        if (client == null) return List.of();
        if (hardware == null) hardware = Hardware.capture();
        if (metadata == null) metadata = Metadata.capture();
        var runtime = Runtime.getRuntime();
        var window = client.getWindow();
        var settings = ClientUiSettings.current();
        boolean shaderReady = GaussBlur.isReady();
        var blurState = label(!settings.backdrop().enabled() ? "state.disabled"
                : shaderReady ? "state.ready" : "state.not_ready");
        return List.of(
                group("details",
                        label("details.name", metadata.own().map(info -> value(info.name())).orElseGet(ModOverviewData::unknown)),
                        label("details.id", ShBetterUi.MOD_ID),
                        label("details.version", metadata.own().map(info -> value(info.version())).orElseGet(ModOverviewData::unknown)),
                        label("details.loader", metadata.loader().map(info -> value(info.name())).orElseGet(ModOverviewData::unknown),
                                metadata.loader().map(info -> value(info.version())).orElseGet(ModOverviewData::unknown)),
                        label("details.minecraft", metadata.minecraftVersion())),
                group("runtime",
                        label("runtime.side", label("state.client")),
                        label("runtime.session", session(client.level != null, client.getSingleplayerServer() != null)),
                        label("runtime.fps", client.getFps()),
                        uptime(ManagementFactory.getRuntimeMXBean().getUptime()),
                        memory(runtime.totalMemory() - runtime.freeMemory(), runtime.maxMemory())),
                group("status",
                        label("status.mod", metadata.own().isPresent() ? label("state.loaded") : unknown()),
                        label("status.loaded_mods", metadata.loadedMods()),
                        label("status.observed")),
                group("modules",
                        label("modules.ui_api", metadata.own().isPresent() ? label("state.loaded") : unknown()),
                        label("modules.blur", blurState),
                        label("modules.glass", blurState),
                        label("modules.ldlib", installed(metadata.ldlib())),
                        label("modules.kotlin", installed(metadata.kotlin())),
                        label("modules.jei", installed(metadata.jei()))),
                group("system",
                        label("system.os", value(hardware.osName()), value(hardware.osVersion())),
                        label("system.arch", value(hardware.architecture())),
                        label("system.java", value(hardware.javaVersion()), value(hardware.javaVm()))),
                group("device",
                        label("device.cpu", value(hardware.cpu())),
                        label("device.gpu", value(hardware.gpu())),
                        label("device.vendor", value(hardware.vendor())),
                        label("device.opengl", value(hardware.openGl())),
                        label("device.display", window.getWidth(), window.getHeight()),
                        label("device.gui", window.getGuiScaledWidth(), window.getGuiScaledHeight(),
                                String.format(Locale.ROOT, "%.2f", window.getGuiScale()))));
    }

    static Component session(boolean inWorld, boolean integratedServer) {
        return label(!inWorld ? "state.menu" : integratedServer ? "state.integrated" : "state.remote");
    }

    static Component uptime(long milliseconds) {
        long seconds = Math.max(0, milliseconds) / 1000;
        return label("runtime.uptime", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    static Component memory(long usedBytes, long maxBytes) {
        return label("runtime.memory", Math.max(0, usedBytes) / MIB, Math.max(0, maxBytes) / MIB);
    }

    private static Group group(String key, Component... lines) {
        return new Group(label("group." + key), List.of(lines));
    }

    private static Component installed(boolean loaded) {
        return label(loaded ? "state.loaded" : "state.not_installed");
    }

    private static Component unknown() {
        return label("state.unknown");
    }

    private static Component value(String text) {
        return text == null || text.isBlank() ? unknown() : Component.literal(text);
    }

    private static Component label(String key, Object... arguments) {
        return Component.translatable("sh_better_ui.panel.overview." + key, arguments);
    }

    private record Metadata(Optional<PlatformUiApi.ModInfo> own, Optional<PlatformUiApi.ModInfo> loader,
                            String minecraftVersion, int loadedMods, boolean ldlib, boolean kotlin, boolean jei) {
        static Metadata capture() {
            return new Metadata(PlatformUiApi.modInfo(ShBetterUi.MOD_ID), PlatformUiApi.modInfo(PlatformUiApi.loaderId()),
                    SharedConstants.getCurrentVersion().getName(), PlatformUiApi.loadedModCount(),
                    PlatformUiApi.modInfo("ldlib").isPresent() || PlatformUiApi.modInfo("ldlib2").isPresent(),
                    PlatformUiApi.modInfo("kotlinforforge").isPresent(), PlatformUiApi.modInfo("jei").isPresent());
        }
    }

    private record Hardware(String osName, String osVersion, String architecture, String javaVersion, String javaVm,
                            String cpu, String gpu, String vendor, String openGl) {
        static Hardware capture() {
            return new Hardware(System.getProperty("os.name"), System.getProperty("os.version"),
                    System.getProperty("os.arch"), System.getProperty("java.version"), System.getProperty("java.vm.name"),
                    read(GlUtil::getCpuInfo), read(GlUtil::getRenderer), read(GlUtil::getVendor), read(GlUtil::getOpenGLVersion));
        }

        private static String read(Supplier<String> query) {
            try {
                return query.get();
            } catch (RuntimeException | LinkageError unavailable) {
                return null;
            }
        }
    }
}
