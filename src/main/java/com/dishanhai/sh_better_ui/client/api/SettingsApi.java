package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.GlassBackdropSettings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;

/** 山海：客户端设置快照、保存和内置面板入口。 */
public final class SettingsApi {
    SettingsApi() {}

    public UiPreferences preferences() {
        return UiPreferences.configured();
    }

    public UiPreferences preferences(ClientUiSettings settings) {
        return UiPreferences.fromSettings(settings);
    }

    public ClientUiSettings current() {
        return ClientUiSettings.current();
    }

    public GlassBackdropSettings backdrop() {
        return GlassBackdropSettings.current();
    }

    public boolean save(ClientUiSettings settings) {
        return ShBetterUi.saveClientUiSettings(settings);
    }

    public boolean saveBackdrop(GlassBackdropSettings settings) {
        return ShBetterUi.saveGlassBackdrop(settings);
    }

    /** 传入已注册的快捷键，面板使用库的客户端配置保存入口。 */
    public ControlPanelScreen controlPanel(Screen parent, KeyMapping keyMapping) {
        return new ControlPanelScreen(parent, keyMapping, ShBetterUi::saveClientUiSettings);
    }

    public DeveloperPanelScreen developerPanel(Screen parent) {
        return new DeveloperPanelScreen(parent);
    }

    public DeveloperPanelScreen developerPanel(Screen parent, UiPreferences preferences) {
        return new DeveloperPanelScreen(parent, preferences);
    }
}
