package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.glass.GlassBlur;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import net.minecraft.client.gui.GuiGraphics;

/** 山海：原生界面的液态玻璃绘制。调用方在材质之后绘制内容并管理原生输入。 */
public final class LiquidGlassTheme {
    public static final int TEXT = 0xFFF5F7FA;
    public static final int TEXT_MUTED = 0xFFB2BBC7;
    public static final int ACCENT = 0xFF9ACDFF;
    public static final float DEFAULT_BUTTON_BLUR_RADIUS = 12f;

    private static final int EDGE = 0xA6CBD4E3;
    private static final int CONTRAST_EDGE = 0xFFF1F5F9;
    static final int POPOVER_RADIUS = 5;
    private static final float POPOVER_BLUR_RADIUS = 10f;
    static final int POPOVER_FILL = 0xE6252B34;
    private static final int LAYERED_POPOVER_FILL = 0xA6252B34;

    private LiquidGlassTheme() {}

    /** 浮在内容上的工具列或导航底板。先绘制其后方的内容。 */
    public static void toolbar(GuiGraphics g, int x, int y, int width, int height, UiPreferences preferences) {
        surface(g, x, y, width, height, 12f, preferences, true,
                GlassStyle.LIQUID_CLEAR, 7f, 1f, 0xB8252B34, EDGE, false);
    }

    /** 独立浮层。较厚的底色用于保证正文与操作项的对比度。 */
    public static void popover(GuiGraphics g, int x, int y, int width, int height, UiPreferences preferences) {
        surface(g, x, y, width, height, POPOVER_RADIUS, preferences, true,
                GlassStyle.LIQUID_REGULAR, 10f, 1f, POPOVER_FILL, EDGE, false);
    }

    static void popover(GuiGraphics g, FluidMorph frame, UiPreferences preferences, float progress) {
        var rect = frame.rect();
        int edge = GuiMotion.lerpColor(ACCENT, EDGE, progress);
        surface(g, rect.getX(), rect.getY(), rect.getW(), rect.getH(), frame.radius(), preferences,
                true, GlassStyle.LIQUID_REGULAR, POPOVER_BLUR_RADIUS, progress, POPOVER_FILL, edge, false);
    }

    /** 山海：在当前图层位置绘制带柔影的浮层底板。调用方随后在同一位置绘制前景。 */
    public static void layeredPopover(GuiGraphics g, int x, int y, int width, int height,
                                      UiPreferences preferences, float blurStrength) {
        if (width <= 0 || height <= 0) return;
        popoverShadow(g, x, y, width, height, POPOVER_RADIUS, preferences, 1f);
        surface(g, x, y, width, height, POPOVER_RADIUS, preferences, true,
                GlassStyle.LIQUID_REGULAR, POPOVER_BLUR_RADIUS, blurStrength, LAYERED_POPOVER_FILL, EDGE, false);
    }

    static void morphPopover(GuiGraphics g, FluidMorph frame, UiPreferences preferences, DarkModeTheme.State state,
                             boolean primary, boolean selected, float emphasis, boolean sampleBackground,
                             float buttonBlurRadius, float progress) {
        GuiRect rect = frame.rect();
        float x = rect.getX(), y = rect.getY(), width = rect.getW(), height = rect.getH();
        float corner = frame.radius();
        popoverShadow(g, x, y, width, height, corner, preferences, progress);

        int fromFill = buttonFill(state, primary, selected, emphasis);
        if (buttonBlurRadius == 0f) fromFill |= 0xFF000000;
        int fill = GuiMotion.lerpColor(fromFill, LAYERED_POPOVER_FILL, progress);
        boolean buttonSamples = sampleBackground && buttonBlurRadius > 0f;
        float radius = GuiMotion.lerp(buttonSamples ? buttonBlurRadius : 0f, POPOVER_BLUR_RADIUS, progress);
        float strength = buttonSamples ? 1f : progress;
        if (!preferences.useBackdropBlur()) {
            fill |= 0xFF000000;
        } else if (radius > 0f && !GlassBlur.tryBackdrop(g, rect, strength,
                progress == 0f ? GlassStyle.LIQUID_CLEAR : GlassStyle.LIQUID_REGULAR, radius, corner)) {
            fill = GuiMotion.lerpColor(fill, fill | 0xFF000000, buttonSamples ? 1f : progress);
        }
        int edge = preferences.highContrast() ? CONTRAST_EDGE
                : GuiMotion.lerpColor(buttonEdge(state, selected), EDGE, progress);
        float stroke = preferences.highContrast() ? 1f : GuiMotion.lerp(0.65f, 1f, progress);
        RoundedCorners.draw(g, x, y, width, height, corner, stroke, edge, fill, progress > 0f);
        popoverHighlight(g, x, y, width, height, corner, preferences, progress);
    }

    private static void popoverShadow(GuiGraphics g, float x, float y, float width, float height, float radius,
                                      UiPreferences preferences, float opacity) {
        if (preferences.highContrast() || opacity <= 0f) return;
        int shadow = GuiMotion.lerpColor(0x00060912, 0x18060912, opacity);
        for (int spread = 6; spread >= 2; spread -= 2) {
            float corner = RoundedCorners.outsetRadius(width, height, radius, spread);
            RoundedCorners.draw(g, x - spread, y + 2 - spread, width + 2 * spread, height + 2 * spread,
                    corner, 0f, 0, shadow, false);
        }
    }

    /** 输入框底板。输入、光标、选取及旁白由原生控件管理。 */
    public static void input(GuiGraphics g, int x, int y, int width, int height,
                             UiPreferences preferences, boolean sampleBackground, boolean focused) {
        surface(g, x, y, width, height, 5, preferences, sampleBackground,
                GlassStyle.LIQUID_REGULAR, 8f, 1f, 0xE61D232C, focused ? ACCENT : EDGE, false);
        focus(g, x, y, width, height, 5, focused, true);
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
        button(g, x, y, width, height, state, preferences, sampleBackground, blurRadius,
                primary, selected, emphasis, true, content);
    }

    static void button(GuiGraphics g, int x, int y, int width, int height, DarkModeTheme.State state,
                       UiPreferences preferences, boolean sampleBackground, float blurRadius, boolean primary,
                       boolean selected, float emphasis, boolean selectionMarker, Runnable content) {
        float radiusVirtual = normalizeBlurRadius(blurRadius);
        if (width <= 0 || height <= 0) return;
        float radius = Math.min(width, height) * 0.5f;
        int fill = buttonFill(state, primary, selected, emphasis);
        int edge = buttonEdge(state, selected);
        GuiRenderUtil.withButtonMotion(g, x, y, width, height, state.buttonScale(), 0f, () -> {
            surface(g, x, y, width, height, radius, preferences, sampleBackground,
                    GlassStyle.LIQUID_CLEAR, radiusVirtual, 1f, fill, edge, true);
            if (selected && selectionMarker) drawSelectionMarker(g, x, y, width, height, state.enabled());
            content.run();
            focus(g, x, y, width, height, radius, state.focused(), false);
        });
    }

    public static int controlText(boolean enabled) {
        return enabled ? TEXT : TEXT_MUTED;
    }

    static void drawSelectionMarker(GuiGraphics g, int x, int y, int width, int height, boolean enabled) {
        if (width <= 10 || height <= 5) return;
        int marker = Math.min(4, width / 4);
        g.fill(x + width / 2 - marker, y + height - 3, x + width / 2 + marker, y + height - 2,
                enabled ? ACCENT : TEXT_MUTED);
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

    private static int buttonEdge(DarkModeTheme.State state, boolean selected) {
        return selected && state.enabled() ? ACCENT : EDGE;
    }

    private static void surface(GuiGraphics g, float x, float y, float width, float height, float radius,
                                UiPreferences preferences, boolean sampleBackground, GlassStyle style,
                                float blurRadius, float blurStrength, int fill, int edge, boolean capsule) {
        if (width <= 0 || height <= 0) return;
        float corner = RoundedCorners.fit(width, height, radius);
        if (preferences.useBackdropBlur() && sampleBackground && blurRadius > 0f) {
            boolean sampled = GlassBlur.tryBackdrop(g, new GuiRect(x, y, width, height), blurStrength,
                    style, blurRadius, corner);
            if (!sampled) fill |= 0xFF000000;
        } else if (!preferences.useBackdropBlur() || blurRadius == 0f) {
            fill |= 0xFF000000;
        }
        if (preferences.highContrast()) {
            edge = CONTRAST_EDGE;
        }
        float stroke = capsule && !preferences.highContrast() ? 0.65f : 1f;
        RoundedCorners.draw(g, x, y, width, height, corner, stroke, edge, fill, !capsule);
        if (!capsule) popoverHighlight(g, x, y, width, height, corner, preferences, 1f);
    }

    private static void popoverHighlight(GuiGraphics g, float x, float y, float width, float height, float corner,
                                         UiPreferences preferences, float opacity) {
        if (preferences.highContrast() || opacity <= 0f || width <= 2 * corner + 2 || height <= 3) return;
        int left = (int) Math.ceil(x + corner);
        int right = (int) Math.floor(x + width - corner);
        int top = Math.round(y) + 1;
        if (right > left) g.fill(left, top, right, top + 1, GuiMotion.lerpColor(0x00FFFFFF, 0x45FFFFFF, opacity));
    }

    private static void focus(GuiGraphics g, int x, int y, int width, int height,
                              float radius, boolean focused, boolean writeDepth) {
        if (!focused || width <= 0 || height <= 0) return;
        float corner = RoundedCorners.outsetRadius(width, height, radius, 3f);
        RoundedCorners.draw(g, x - 3, y - 3, width + 6, height + 6, corner, 2f, ACCENT, 0, writeDepth);
    }
}
