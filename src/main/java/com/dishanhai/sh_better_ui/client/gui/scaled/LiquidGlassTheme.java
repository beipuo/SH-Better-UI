package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.glass.GlassBlur;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** 山海：原生界面的液态玻璃绘制。调用方在材质之后绘制内容并管理原生输入。 */
public final class LiquidGlassTheme {
    public static final int TEXT = 0xFFF5F7FA;
    public static final int TEXT_MUTED = 0xFFB2BBC7;
    public static final int ACCENT = 0xFF9ACDFF;
    public static final float DEFAULT_BUTTON_BLUR_RADIUS = 12f;

    private static final int EDGE = 0xA6CBD4E3;
    private static final int CONTRAST_EDGE = 0xFFF1F5F9;
    private static final int POPOVER_RADIUS = 5;

    private LiquidGlassTheme() {}

    /** 浮在内容上的工具列或导航底板。先绘制其后方的内容。 */
    public static void toolbar(GuiGraphics g, int x, int y, int width, int height, UiPreferences preferences) {
        surface(g, x, y, width, height, Math.min(12, height / 2), preferences, true,
                GlassStyle.LIQUID_CLEAR, 7f, 0xB8252B34, EDGE, false);
    }

    /** 独立浮层。较厚的底色用于保证正文与操作项的对比度。 */
    public static void popover(GuiGraphics g, int x, int y, int width, int height, UiPreferences preferences) {
        surface(g, x, y, width, height, POPOVER_RADIUS, preferences, true,
                GlassStyle.LIQUID_REGULAR, 10f, 0xE6252B34, EDGE, false);
    }

    /** 输入框底板。输入、光标、选取及旁白由原生控件管理。 */
    public static void input(GuiGraphics g, int x, int y, int width, int height,
                             UiPreferences preferences, boolean sampleBackground, boolean focused) {
        surface(g, x, y, width, height, 5, preferences, sampleBackground,
                GlassStyle.LIQUID_REGULAR, 8f, 0xE61D232C, focused ? ACCENT : EDGE, false);
        focus(g, x, y, width, height, 5, focused);
    }

    /** 候选项标记与文字分开绘制，保留键盘选择与鼠标悬停的区别。 */
    public static void suggestionRow(GuiGraphics g, int x, int y, int width, int height,
                                     boolean selected, boolean hovered, UiPreferences preferences) {
        if (width <= 2 || height <= 2 || (!selected && !hovered)) return;
        int fill = selected ? 0xFF335474 : 0xFF3D4653;
        int edge = preferences.highContrast() && selected ? ACCENT : 0;
        DarkModeTheme.rounded(g, x, y, width, height, 4, edge == 0 ? 0 : 1, edge, fill);
        if (selected && width >= 4 && height > 4) g.fill(x + 2, y + 2, x + 3, y + height - 2, ACCENT);
    }

    /**
     * 胶囊按钮。可复用现有的 Interaction 采样，已在玻璃底板上时可跳过重复取样。
     * content 在相同 pose 下绘制，按钮的原生命中范围保持固定。
     */
    public static void button(GuiGraphics g, int x, int y, int width, int height,
                              DarkModeTheme.State state, UiPreferences preferences, boolean sampleBackground,
                              boolean primary, boolean selected, float emphasis, Runnable content) {
        button(g, x, y, width, height, state, preferences, sampleBackground, DEFAULT_BUTTON_BLUR_RADIUS,
                primary, selected, emphasis, content);
    }

    /** 自选按钮背景模糊半径（GUI 单位），0 关闭，超过 32 按 32 处理。先绘制背景，再调用本方法。 */
    public static void button(GuiGraphics g, int x, int y, int width, int height,
                              DarkModeTheme.State state, UiPreferences preferences, boolean sampleBackground,
                              float blurRadius, boolean primary, boolean selected, float emphasis, Runnable content) {
        float radiusVirtual = normalizeBlurRadius(blurRadius);
        if (width <= 0 || height <= 0) return;
        int radius = (Math.min(width, height) + 1) / 2;
        int fill = buttonFill(state, primary, selected, emphasis);
        int edge = selected && state.enabled() ? ACCENT : EDGE;
        GuiRenderUtil.withButtonMotion(g, x, y, width, height, state.buttonScale(), 0f, () -> {
            surface(g, x, y, width, height, radius, preferences, sampleBackground,
                    GlassStyle.LIQUID_CLEAR, radiusVirtual, fill, edge, true);
            if (selected && width > 10 && height > 5) {
                int marker = Math.min(4, width / 4);
                g.fill(x + width / 2 - marker, y + height - 3, x + width / 2 + marker, y + height - 2,
                        state.enabled() ? ACCENT : TEXT_MUTED);
            }
            content.run();
        });
        if (state.focused()) drawCapsule(g, x - 3, y - 3, width + 6, height + 6, 2f, ACCENT, 0);
    }

    public static int controlText(boolean enabled) {
        return enabled ? TEXT : TEXT_MUTED;
    }

    static float normalizeBlurRadius(float radius) {
        if (!Float.isFinite(radius) || radius < 0f) throw new IllegalArgumentException();
        return Math.min(radius, 32f);
    }

    private static int buttonFill(DarkModeTheme.State state, boolean primary, boolean selected, float emphasis) {
        if (!state.enabled()) return 0x96303640;
        int rest = primary ? 0x86244B78 : 0x522B333E;
        int hover = primary ? 0x9C2B598A : 0x723B4A5B;
        int fill = GuiMotion.lerpColor(rest, hover, state.hover());
        if (selected) fill = GuiMotion.lerpColor(fill, 0xA0315E88, 0.45f + 0.20f * GuiMotion.clamp01(emphasis));
        return GuiMotion.lerpColor(fill, 0xAC182434, state.press() * 0.72f);
    }

    private static void surface(GuiGraphics g, int x, int y, int width, int height, int radius,
                                UiPreferences preferences, boolean sampleBackground, GlassStyle style,
                                float blurRadius, int fill, int edge, boolean capsule) {
        if (width <= 0 || height <= 0) return;
        int corner = capsule ? radius : Math.min(radius, Math.min(width, height) / 2);
        if (preferences.useBackdropBlur() && sampleBackground && blurRadius > 0f) {
            boolean sampled = GlassBlur.roundedBackdrop(g, new GuiRect(x, y, width, height), style, blurRadius, corner);
            if (!sampled) fill |= 0xFF000000;
        } else if (!preferences.useBackdropBlur() || blurRadius == 0f) {
            fill |= 0xFF000000;
        }
        if (preferences.highContrast()) {
            edge = CONTRAST_EDGE;
        }
        if (capsule) {
            drawCapsule(g, x, y, width, height, preferences.highContrast() ? 1f : 0.65f, edge, fill);
        } else {
            DarkModeTheme.rounded(g, x, y, width, height, corner, 1, edge, fill);
        }
        if (!capsule && !preferences.highContrast() && width > 2 * corner + 2 && height > 3) {
            g.fill(x + corner, y + 1, x + width - corner, y + 2, 0x45FFFFFF);
        }
    }

    private static void drawCapsule(GuiGraphics g, int x, int y, int width, int height,
                                    float stroke, int edge, int fill) {
        var pose = g.pose().last().pose();
        double scaleX = Math.hypot(pose.m00(), pose.m01());
        double scaleY = Math.hypot(pose.m10(), pose.m11());
        float pixelsPerUnit = (float) (Minecraft.getInstance().getWindow().getGuiScale() * Math.min(scaleX, scaleY));
        CapsuleRenderer.draw(g, x, y, width, height, stroke, pixelsPerUnit, edge, fill);
    }

    private static void focus(GuiGraphics g, int x, int y, int width, int height, int radius, boolean focused) {
        if (!focused || width <= 0 || height <= 0) return;
        int corner = Math.min(radius, Math.min(width, height) / 2);
        DarkModeTheme.rounded(g, x - 3, y - 3, width + 6, height + 6, corner + 3, 2, ACCENT, 0);
    }
}
