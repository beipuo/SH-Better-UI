package com.dishanhai.sh_better_ui.config;

/** 山海：两个加载器共用的毛玻璃设置快照，可在渲染线程读取。 */
public record GlassBackdropSettings(boolean enabled, float blurRadiusScale, float strength,
                                    float tintScale, float grainScale) {
    public static final GlassBackdropSettings DEFAULT = new GlassBackdropSettings(true, 1f, 1f, 1f, 1f);
    private static volatile GlassBackdropSettings current = DEFAULT;

    public static GlassBackdropSettings current() {
        return current;
    }

    public static void update(GlassBackdropSettings settings) {
        current = settings;
    }
}
