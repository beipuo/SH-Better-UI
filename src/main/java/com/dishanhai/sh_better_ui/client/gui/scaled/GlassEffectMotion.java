package com.dishanhai.sh_better_ui.client.gui.scaled;

/** 山海：可选玻璃装饰的纯数学参数，不依赖 Minecraft 或全局动画状态。 */
public final class GlassEffectMotion {
    public static final long FLOW_PERIOD_MS = 2_000L;
    public static final long BREATH_PERIOD_MS = 2_400L;
    private static final float FLOW_TRAIL = 0.24f;

    private GlassEffectMotion() {}

    /** 循环相位 [0,1)，包括负时间；无效周期返回静止值。 */
    public static float phase(long nowMs, long periodMs) {
        if (periodMs <= 0L) return 0f;
        return Math.min(Math.nextDown(1f), (float) (Math.floorMod(nowMs, periodMs) / (double) periodMs));
    }

    /** 沿矩形周长顺时针的亮度；减少动效时保留恒定细边。 */
    public static float flowIntensity(float position, float phase, boolean reducedMotion) {
        if (reducedMotion) return 0.35f;
        if (!Float.isFinite(position) || !Float.isFinite(phase)) return 0f;
        double distance = (double) phase - position;
        distance -= Math.floor(distance);
        float trail = GuiMotion.clamp01(1f - (float) distance / FLOW_TRAIL);
        return GuiMotion.lerp(0.12f, 1f, trail * trail);
    }

    /** 柔和的 2.4 秒呼吸；减少动效时不再闪动。 */
    public static float breath(long nowMs, boolean reducedMotion) {
        if (reducedMotion) return 0.72f;
        double cycle = phase(nowMs, BREATH_PERIOD_MS) * Math.PI * 2d;
        return GuiMotion.lerp(0.55f, 1f, (float) ((1d - Math.cos(cycle)) * 0.5d));
    }

    /** 保留 RGB，只缩减调用方提供的透明度。非有限强度视为不可见。 */
    public static int withOpacity(int argb, float amount) {
        float alpha = Float.isFinite(amount) ? GuiMotion.clamp01(amount) : 0f;
        return (argb & 0x00FFFFFF) | (Math.round((argb >>> 24) * alpha) << 24);
    }
}
