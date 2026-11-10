package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.theme.ThemeManager;

/** 山海：Java、脚本及独立上下文共用的类型化分组访问契约。 */
public interface UiApi {
    /** Client-owned dynamic theme manager. */
    default ThemeManager themes() { return ShBetterUiApi.themes(); }
    default SettingsApi settings() { return ShBetterUiApi.settings(); }

    default ControlsApi controls() { return ShBetterUiApi.controls(); }

    default InteractionApi interactions() { return ShBetterUiApi.interactions(); }

    default SearchApi search() { return ShBetterUiApi.search(); }

    default IngredientApi ingredients() { return ShBetterUiApi.ingredients(); }

    default MaterialsApi materials() { return ShBetterUiApi.materials(); }

    default BackgroundRegistry backgrounds() { return ShBetterUiApi.backgrounds(); }

    default BlurApi blur() { return ShBetterUiApi.blur(); }

    default GraphicsApi graphics() { return ShBetterUiApi.graphics(); }

    default LayoutApi layout() { return ShBetterUiApi.layout(); }

    default MotionApi motion() { return ShBetterUiApi.motion(); }

    default AudioApi audio() { return ShBetterUiApi.audio(); }

    default PlatformUiApi platform() { return ShBetterUiApi.platform(); }

    default UiExtensionRegistry extensions() { return ShBetterUiApi.extensions(); }
}
