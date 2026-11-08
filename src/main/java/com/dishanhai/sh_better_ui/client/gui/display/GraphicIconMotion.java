package com.dishanhai.sh_better_ui.client.gui.display;

/** 山海：在原图标位置内循环播放的几何动效。 */
public enum GraphicIconMotion {
    SPIN(1600),
    FLOAT(1800),
    BOUNCE(1100);

    private static final float SPIN_SCALE = (float) (1.0 / Math.sqrt(2.0));
    private final long periodMillis;

    GraphicIconMotion(long periodMillis) {
        this.periodMillis = periodMillis;
    }

    public long periodMillis() {
        return periodMillis;
    }

    /** 位移按图标大小计算，减少动效时保留原始位置和尺寸。 */
    public Frame sample(long timeMillis, int size, boolean reducedMotion) {
        if (size <= 0) throw new IllegalArgumentException("size must be positive");
        if (reducedMotion) return Frame.IDENTITY;

        double phase = Math.floorMod(timeMillis, periodMillis) / (double) periodMillis;
        return switch (this) {
            case SPIN -> new Frame(0f, SPIN_SCALE, (float) (phase * 360));
            case FLOAT -> new Frame((float) (Math.sin(phase * Math.TAU) * size / 8), 0.75f, 0f);
            case BOUNCE -> {
                double flight = Math.min(1.0, phase / 0.82);
                double height = 4 * flight * (1 - flight);
                yield new Frame((float) ((0.10 - 0.20 * height) * size), 0.78f, 0f);
            }
        };
    }

    /** offsetY 使用 GUI 单位，缩放和旋转以图标中心为原点。 */
    public record Frame(float offsetY, float scale, float rotationDegrees) {
        public static final Frame IDENTITY = new Frame(0f, 1f, 0f);
    }
}
