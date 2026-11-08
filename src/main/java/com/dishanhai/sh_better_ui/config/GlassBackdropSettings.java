package com.dishanhai.sh_better_ui.config;

/** 山海：两个加载器共用的毛玻璃设置快照，可在渲染线程读取。 */
public record GlassBackdropSettings(boolean enabled, float blurRadiusScale, float strength,
                                    float tintScale, float grainScale, float saturationScale, float lift,
                                    float rimScale, float refractionScale, boolean denseSampling) {
    public static final GlassBackdropSettings DEFAULT = new GlassBackdropSettings(true, 1f, 1f, 1f, 1f, 1f, 0f,
            1f, 1f, true);

    public GlassBackdropSettings(boolean enabled, float blurRadiusScale, float strength, float tintScale,
                                 float grainScale) {
        this(enabled, blurRadiusScale, strength, tintScale, grainScale, DEFAULT.saturationScale(), DEFAULT.lift());
    }

    public GlassBackdropSettings(boolean enabled, float blurRadiusScale, float strength, float tintScale,
                                 float grainScale, float saturationScale, float lift) {
        this(enabled, blurRadiusScale, strength, tintScale, grainScale, saturationScale, lift,
                DEFAULT.rimScale(), DEFAULT.refractionScale(), DEFAULT.denseSampling());
    }

    public static GlassBackdropSettings current() {
        return ClientUiSettings.current().backdrop();
    }

    public static void update(GlassBackdropSettings settings) {
        ClientUiSettings.updateBackdrop(settings);
    }

    public GlassBackdropSettings withEnabled(boolean enabled) {
        return new GlassBackdropSettings(enabled, blurRadiusScale, strength, tintScale, grainScale,
                saturationScale, lift, rimScale, refractionScale, denseSampling);
    }

    public GlassBackdropSettings withDenseSampling(boolean denseSampling) {
        return new GlassBackdropSettings(enabled, blurRadiusScale, strength, tintScale, grainScale,
                saturationScale, lift, rimScale, refractionScale, denseSampling);
    }
}
