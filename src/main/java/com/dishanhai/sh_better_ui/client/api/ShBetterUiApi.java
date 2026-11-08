package com.dishanhai.sh_better_ui.client.api;

/** 山海：Forge 与 NeoForge 共用的客户端 API 入口。分组可复用，组件状态归各自的宿主。 */
public final class ShBetterUiApi {
    private static final SettingsApi SETTINGS = new SettingsApi();
    private static final ControlsApi CONTROLS = new ControlsApi();
    private static final InteractionApi INTERACTIONS = new InteractionApi();
    private static final SearchApi SEARCH = new SearchApi();
    private static final IngredientApi INGREDIENTS = new IngredientApi();
    private static final MaterialsApi MATERIALS = new MaterialsApi();
    private static final BlurApi BLUR = new BlurApi();
    private static final GraphicsApi GRAPHICS = new GraphicsApi();
    private static final MotionApi MOTION = new MotionApi();
    private static final PlatformUiApi PLATFORM = new PlatformUiApi();

    private ShBetterUiApi() {}

    public static SettingsApi settings() {
        return SETTINGS;
    }

    public static ControlsApi controls() {
        return CONTROLS;
    }

    public static InteractionApi interactions() {
        return INTERACTIONS;
    }

    public static SearchApi search() {
        return SEARCH;
    }

    public static IngredientApi ingredients() {
        return INGREDIENTS;
    }

    public static MaterialsApi materials() {
        return MATERIALS;
    }

    public static BlurApi blur() {
        return BLUR;
    }

    public static GraphicsApi graphics() {
        return GRAPHICS;
    }

    public static MotionApi motion() {
        return MOTION;
    }

    public static PlatformUiApi platform() {
        return PLATFORM;
    }
}
