package com.dishanhai.sh_better_ui.client.gui.display;

import java.util.Objects;

/** 山海：电光轮廓的配色、线条、动画和指针反馈。所有像素尺寸均为逻辑 GUI 单位。 */
public record ElectricLogoProfile(Palette palette, Lines lines, Motion motion, Interaction interaction) {
    public static final ElectricLogoProfile DEFAULT = new ElectricLogoProfile(
            new Palette(0xFFECC7FF, 0xFFAD6DFF, Theme.DARK, 1f, 1f, 0f),
            new Lines(0.7f, 1.5f, 4),
            new Motion(2.5f, 0.6f, 1.5f, 1f, 0.6f),
            new Interaction(true, 0.75f, 100f));

    public ElectricLogoProfile {
        Objects.requireNonNull(palette);
        Objects.requireNonNull(lines);
        Objects.requireNonNull(motion);
        Objects.requireNonNull(interaction);
    }

    public ElectricLogoProfile withPalette(Palette value) {
        return new ElectricLogoProfile(value, lines, motion, interaction);
    }

    public ElectricLogoProfile withLines(Lines value) {
        return new ElectricLogoProfile(palette, value, motion, interaction);
    }

    public ElectricLogoProfile withMotion(Motion value) {
        return new ElectricLogoProfile(palette, lines, value, interaction);
    }

    public ElectricLogoProfile withInteraction(Interaction value) {
        return new ElectricLogoProfile(palette, lines, motion, value);
    }

    /** Ready-to-render visual styles. Apply them to an existing profile to retain its size and interaction. */
    public enum Preset {
        VIOLET(DEFAULT.palette(), DEFAULT.lines().thickness(), DEFAULT.lines().strands(), DEFAULT.motion()),
        STORM(new Palette(0xFFEAFBFF, 0xFF428DFF, Theme.DARK, 1.35f, 1.4f, 0f), 1.2f, 6,
                new Motion(5f, 1.25f, 2.8f, 4f, 0.8f)),
        NEON(new Palette(0xFFFFD0EC, 0xFFFF429C, Theme.DARK, 1.2f, 1.7f, 0.35f), 2.1f, 2,
                new Motion(1.4f, 0.22f, 0.15f, 0f, 0.18f)),
        OUTLINE(new Palette(0xFFECFFF6, 0xFF39C7A1, Theme.DARK, 1.3f, 0.3f, 0.75f), 1.8f, 1,
                new Motion(0f, 0f, 0f, 0f, 0f));

        private final Palette palette;
        private final float thickness;
        private final int strands;
        private final Motion motion;
        private final ElectricLogoProfile profile;

        Preset(Palette palette, float thickness, int strands, Motion motion) {
            this.palette = palette;
            this.thickness = thickness;
            this.strands = strands;
            this.motion = motion;
            this.profile = apply(DEFAULT);
        }

        public ElectricLogoProfile apply(ElectricLogoProfile base) {
            return new ElectricLogoProfile(palette, new Lines(base.lines().scale(), thickness, strands),
                    motion, base.interaction());
        }

        public ElectricLogoProfile profile() {
            return profile;
        }
    }

    /** DARK 适合深色背景上的发光效果，LIGHT 适合浅色背景上的电光描边。 */
    public enum Theme { DARK, LIGHT }

    /** ARGB 配色及亮度、辉光、内部填充。亮度和辉光取 0..4，填充取 0..1。 */
    public record Palette(int coreArgb, int glowArgb, Theme theme, float intensity, float glow, float fill) {
        public Palette {
            Objects.requireNonNull(theme);
            range(intensity, 0f, 4f);
            range(glow, 0f, 4f);
            range(fill, 0f, 1f);
        }
    }

    /** scale 为贴合区域的比例 (0, 1]，thickness 为主电丝宽度 [0.25, 12]，strands 取 1..6。 */
    public record Lines(float scale, float thickness, int strands) {
        public Lines {
            if (!(scale > 0f) || strands < 1 || strands > 6) throw new IllegalArgumentException();
            range(scale, 0f, 1f);
            range(thickness, 0.25f, 12f);
        }
    }

    /** 速度取 0..10，弯曲和毛刺取 0..4，电弧频率取 0..4，闪烁取 0..1。速度为零时冻结动效。 */
    public record Motion(float speed, float bend, float crackle, float arcs, float flicker) {
        public Motion {
            range(speed, 0f, 10f);
            range(bend, 0f, 4f);
            range(crackle, 0f, 4f);
            range(arcs, 0f, 4f);
            range(flicker, 0f, 1f);
        }
    }

    /** 悬停增益取 0..2，半径为正数。悬停增益为零时仍可独立使用点击反馈。 */
    public record Interaction(boolean enabled, float cursorIntensity, float cursorRadius) {
        public static final Interaction NONE = new Interaction(false, 0f, 100f);

        public Interaction {
            range(cursorIntensity, 0f, 2f);
            if (!Float.isFinite(cursorRadius) || cursorRadius < 1f) throw new IllegalArgumentException();
        }
    }

    private static void range(float value, float min, float max) {
        if (!(value >= min && value <= max)) throw new IllegalArgumentException();
    }
}
