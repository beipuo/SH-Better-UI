package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.gui.GuiGraphics;

/** 山海：独立的 Slate 暗色主题。由消费端控件提供交互状态并绘制内容。 */
public final class DarkModeTheme {
    public static final int BACKGROUND = 0xFF0F172A;
    public static final int SURFACE = 0xFF1E293B;
    public static final int BORDER = 0xFF334155;
    public static final int BORDER_HOVER = 0xFF64748B;
    public static final int TEXT = 0xFFF1F5F9;
    public static final int TEXT_SECONDARY = 0xFFCBD5E1;
    public static final int TEXT_MUTED = 0xFF94A3B8;
    public static final int ACCENT = 0xFF3B82F6;
    public static final int SUCCESS = 0xFF22C55E;
    private static final int CONTRAST_EDGE = 0xFFCBD5E1;
    private static final int CONTRAST_FOCUS = 0xFF93C5FD;

    private static final int CARD_REST = 0x801E293B;
    private static final int PRIMARY = 0xFF1D4ED8;
    private static final int PRIMARY_HOVER = 0xFF2563EB;
    private static final int SHADOW = 0xFF020617;
    private static final int CONTROL_RADIUS = 4;
    private static final int CARD_RADIUS = 6;
    private static final int FOCUS_WIDTH = 2;
    private static final int FOCUS_OFFSET = 2;
    private static final long CONTROL_MS = 200L;
    private static final long CARD_MS = 300L;
    private static final long CARD_ROTATION_MS = 8_000L;

    private DarkModeTheme() {}

    public static int backgroundColor() { return UiTheme.current().background(BACKGROUND); }
    public static int surfaceColor() { return ThemeEffects.material("slate").fill(UiTheme.current().surface(SURFACE)); }
    public static int borderColor() { return ThemeEffects.material("slate").edge(UiTheme.current().border(BORDER)); }
    public static int hoverBorderColor() { return UiTheme.current().accent(BORDER_HOVER); }
    public static int textColor() { return ThemeEffects.material("slate").text(UiTheme.current().text(TEXT)); }
    public static int secondaryTextColor() { return UiTheme.current().secondaryText(TEXT_SECONDARY, 0.2f); }
    public static int mutedTextColor() { return UiTheme.current().secondaryText(TEXT_MUTED, 0.38f); }
    public static int accentColor() { return ThemeEffects.material("slate").accent(UiTheme.current().accent(ACCENT)); }

    private static int textContrastEdge() { return UiTheme.current().text(CONTRAST_EDGE); }

    public static void background(GuiGraphics g, int x, int y, int width, int height) {
        if (width > 0 && height > 0) g.fill(x, y, x + width, y + height, backgroundColor());
    }

    static void modalPanel(GuiGraphics g, int x, int y, int width, int height, boolean highContrast) {
        rounded(g, x, y, width, height, CARD_RADIUS, 1,
                highContrast ? textContrastEdge() : borderColor(), highContrast ? backgroundColor() : UiTheme.current().surface(0xF21E293B));
    }

    /**
     * 卡片底板、描边和背光随内容移动。底部预留最多 13 个 GUI 单位可显示完整背光。
     * 消费端可用 {@link State#cardOffset()} 对齐卡片内控件的命中位置。
     */
    public static void card(GuiGraphics g, int x, int y, int width, int height, State state, Runnable content) {
        if (width <= 0 || height <= 0) return;
        g.pose().pushPose();
        try {
            g.pose().translate(0f, state.cardOffset(), 0f);
            if (state.hover() > 0f) {
                for (int spread = 3; spread >= 1; spread--) {
                    rounded(g, x - spread, y - spread + 3, width + 2 * spread, height + 2 * spread,
                            RoundedCorners.outsetRadius(width, height, CARD_RADIUS, spread),
                            0, 0, opacity(SHADOW, state.hover() * (4 - spread) / 12f));
                }
            }
            int fill = state.highContrast() ? surfaceColor() : GuiMotion.lerpColor(UiTheme.current().surface(CARD_REST), surfaceColor(), state.hover());
            if (state.enabled() && !state.highContrast()) {
                float turn = state.reducedMotion() ? 0.12f
                        : Math.floorMod(PageTransition.nowMs(), CARD_ROTATION_MS) / (float) CARD_ROTATION_MS;
                // 每圈包含两个呼吸周期，相位回绕时亮度和扩散范围连续。
                float breath = state.reducedMotion() ? 0.5f : (float) (0.5 - 0.5 * Math.cos(4.0 * Math.PI * turn));
                RoundedCorners.drawCard(g, x, y, width, height, CARD_RADIUS, edge(state), fill, turn, breath);
            } else {
                rounded(g, x, y, width, height, CARD_RADIUS, 1, edge(state), fill);
            }
            focus(g, x, y, width, height, CARD_RADIUS, state);
            content.run();
        } finally {
            g.pose().popPose();
        }
    }

    /** 按钮内容、底板和焦点环一起缩放。 */
    public static void button(GuiGraphics g, int x, int y, int width, int height,
                              State state, boolean primary, Runnable content) {
        button(g, x, y, width, height, state, primary, false, 0f, content);
    }

    /** 选中项持续保留底边标记，emphasis 是新选择的短暂强调强度。 */
    public static void button(GuiGraphics g, int x, int y, int width, int height,
                              State state, boolean primary, boolean selected, float emphasis, Runnable content) {
        button(g, x, y, width, height, state, primary, selected, emphasis, true, content);
    }

    static void button(GuiGraphics g, int x, int y, int width, int height, State state,
                       boolean primary, boolean selected, float emphasis, boolean selectionMarker, Runnable content) {
        button(g, x, y, width, height, state, primary, selected, emphasis, selectionMarker, null, content);
    }

    static void button(GuiGraphics g, int x, int y, int width, int height, State state,
                       boolean primary, boolean selected, float emphasis, boolean selectionMarker,
                       ThemeButtonStyle.Paint paint, Runnable content) {
        if (width <= 0 || height <= 0) return;
        float radius = paint == null ? CONTROL_RADIUS : RoundedCorners.fit(width, height, paint.radius());
        int focusColor = paint == null ? state.focusColor() : paint.focus();
        int accent = paint == null ? accentColor() : paint.accent();
        GuiRenderUtil.withButtonMotion(g, x, y, width, height, state.buttonScale(), 0f, () -> {
            if (state.enabled() && state.hover() > 0f && (paint == null || !state.highContrast())) {
                for (int spread = 3; spread >= 1; spread--) {
                    rounded(g, x - spread, y - spread, width + 2 * spread, height + 2 * spread,
                            RoundedCorners.outsetRadius(width, height, radius, spread), 1,
                            opacity(accent, state.hover() * (1f - state.press()) * (4 - spread) / 10f), 0);
                }
            }
            int border = paint == null ? (selected ? focusColor : edge(state)) : paint.border();
            int fill = paint == null ? state.buttonFill(primary, selected, emphasis) : paint.fill();
            rounded(g, x, y, width, height, radius, paint == null ? 1 : paint.borderWidth(), border, fill);
            int inset = Math.round(radius);
            if (width > 2 * inset && height > 3 && (paint == null || !state.highContrast())) {
                int highlight = opacity(0xFFFFFFFF, (0.15f + 0.05f * state.hover()) * (1f - state.press()));
                g.fill(x + inset, y + 1, x + width - inset, y + 2, highlight);
                if (state.press() > 0f) {
                    rounded(g, x + 1, y + 1, width - 2, height - 2,
                            RoundedCorners.insetRadius(width, height, radius, 1), 1,
                            opacity(SHADOW, 0.3f * state.press()), 0);
                }
            }
            if (selected && selectionMarker && width > 4 && height > 3) {
                g.fill(x + 2, y + height - 3, x + width - 2, y + height - 1, focusColor);
            }
            content.run();
            if (paint == null) focus(g, x, y, width, height, radius, state);
            else if (state.focused()) focus(g, x, y, width, height, radius, focusColor, paint.background());
        });
    }

    /** 页签底部的移动指示条，可用于自绘或原生控件。 */
    public static void selectionIndicator(GuiGraphics g, UiAttention.Frame frame, UiPreferences preferences) {
        if (!frame.visible() || frame.width() <= 0 || frame.height() <= 0) return;
        int x = Math.round(frame.x());
        int bottom = Math.round(frame.y() + frame.height());
        g.fill(x, bottom - 2, Math.round(frame.x() + frame.width()), bottom,
                preferences.highContrast() ? UiTheme.current().accent(CONTRAST_FOCUS) : accentColor());
    }

    /** 内容目标区域的短暂内侧亮边，在减少动效时仅保留选中控件的静态标记。 */
    public static void attentionTarget(GuiGraphics g, int x, int y, int width, int height,
                                       UiAttention.Frame frame, UiPreferences preferences) {
        if (!frame.visible() || preferences.reducedMotion() || frame.emphasis() <= 0f
                || width <= 0 || height <= 0) return;
        rounded(g, x, y, width, height, CONTROL_RADIUS, 1,
                opacity(preferences.highContrast() ? UiTheme.current().accent(CONTRAST_FOCUS) : accentColor(), frame.emphasis()), 0);
    }

    /** 输入框底板。由原生输入控件继续处理光标、选择区、输入及旁白。 */
    public static void input(GuiGraphics g, int x, int y, int width, int height, State state) {
        if (width <= 0 || height <= 0) return;
        focus(g, x, y, width, height, CONTROL_RADIUS, state);
        rounded(g, x, y, width, height, CONTROL_RADIUS, 1,
                state.focused() ? state.focusColor() : edge(state), surfaceColor());
    }

    /** 绘制可识别的拖曳把手。以同一矩形命中，并用 PointerDrag.active() 表示按住。 */
    public static void dragHandle(GuiGraphics g, int x, int y, int width, int height, State state) {
        button(g, x, y, width, height, state, false, () -> {
            int cx = x + width / 2;
            int cy = y + height / 2;
            int ink = state.secondaryText();
            if (width < 8 || height < 10) {
                if (width >= 4 && height >= 5) {
                    g.fill(x + 1, cy - 1, x + width - 1, cy, ink);
                    g.fill(x + 1, cy + 1, x + width - 1, cy + 2, ink);
                }
                return;
            }
            for (int dy = -3; dy <= 3; dy += 3) {
                g.fill(cx - 2, cy + dy, cx - 1, cy + dy + 1, ink);
                g.fill(cx + 1, cy + dy, cx + 2, cy + dy + 1, ink);
            }
        });
    }

    /** 对已验证可接收的落点画内侧预览边，不改变布局和焦点。 */
    public static void dropTarget(GuiGraphics g, int x, int y, int width, int height) {
        if (width > 0 && height > 0) rounded(g, x, y, width, height, CARD_RADIUS, 2, accentColor(), 0);
    }

    private static int edge(State state) {
        if (state.highContrast()) return textContrastEdge();
        return GuiMotion.lerpColor(borderColor(), accentColor(), Math.max(state.hover(), state.press()));
    }

    private static void focus(GuiGraphics g, int x, int y, int width, int height, float radius, State state) {
        if (!state.focused()) return;
        focus(g, x, y, width, height, radius, state.focusColor(), backgroundColor());
    }

    private static void focus(GuiGraphics g, int x, int y, int width, int height, float radius, int color, int background) {
        int outer = FOCUS_OFFSET + FOCUS_WIDTH;
        rounded(g, x - outer, y - outer, width + 2 * outer, height + 2 * outer,
                RoundedCorners.outsetRadius(width, height, radius, outer), FOCUS_WIDTH, color, 0);
        rounded(g, x - FOCUS_OFFSET, y - FOCUS_OFFSET, width + 2 * FOCUS_OFFSET, height + 2 * FOCUS_OFFSET,
                RoundedCorners.outsetRadius(width, height, radius, FOCUS_OFFSET), FOCUS_OFFSET, background, 0);
    }

    private static int opacity(int argb, float alpha) {
        return (argb & 0x00FFFFFF) | (Math.round(255f * alpha) << 24);
    }

    static void rounded(GuiGraphics g, int x, int y, int width, int height,
                        float radius, int stroke, int edge, int fill) {
        RoundedCorners.draw(g, x, y, width, height, radius, stroke, edge, fill);
    }

    /** 每个控件保留一份。使用 {@link PageTransition#nowMs()} 提供单调时间。 */
    public static final class Interaction {
        private final long hoverMs;
        private final Tween hover = new Tween();
        private final Tween textHover = new Tween();
        private final Tween press = new Tween();

        private Interaction(long hoverMs) { this.hoverMs = hoverMs; }

        public static Interaction card() { return new Interaction(CARD_MS); }

        public static Interaction control() { return new Interaction(CONTROL_MS); }

        public State sample(boolean hovered, boolean pressed, boolean focused, boolean enabled,
                            boolean reducedMotion, long nowMs) {
            return sample(hovered, pressed, focused, enabled, reducedMotion, false, nowMs);
        }

        public State sample(boolean hovered, boolean pressed, boolean focused, boolean enabled,
                            UiPreferences preferences, long nowMs) {
            var feedback = preferences.feedback();
            boolean immediate = preferences.reducedMotion() || !enabled;
            boolean hoverTarget = enabled && hovered;
            boolean pressTarget = enabled && pressed;
            return new State(hover.update(hoverTarget, immediate || !feedback.hoverAnimation(), nowMs, hoverMs),
                    textHover.update(hoverTarget, immediate || !feedback.hoverAnimation(), nowMs, CONTROL_MS),
                    press.update(pressTarget, immediate || pressTarget || !feedback.pressAnimation(), nowMs, CONTROL_MS),
                    enabled && focused, enabled, preferences.reducedMotion(), preferences.highContrast(),
                    feedback.hoverAnimation(), feedback.pressAnimation());
        }

        private State sample(boolean hovered, boolean pressed, boolean focused, boolean enabled,
                             boolean reducedMotion, boolean highContrast, long nowMs) {
            boolean immediate = reducedMotion || !enabled;
            boolean hoverTarget = enabled && hovered;
            boolean pressTarget = enabled && pressed;
            return new State(hover.update(hoverTarget, immediate, nowMs, hoverMs),
                    textHover.update(hoverTarget, immediate, nowMs, CONTROL_MS),
                    press.update(pressTarget, immediate || pressTarget, nowMs, CONTROL_MS),
                    enabled && focused, enabled, reducedMotion, highContrast);
        }

        /** 取消按压时立即清除反馈。 */
        public void cancelPress(long nowMs) {
            press.update(false, true, nowMs, CONTROL_MS);
        }
    }

    public record State(float hover, float textHover, float press, boolean focused, boolean enabled,
                        boolean reducedMotion, boolean highContrast, boolean hoverAnimation, boolean pressAnimation) {
        public State(float hover, float textHover, float press, boolean focused, boolean enabled,
                     boolean reducedMotion, boolean highContrast) {
            this(hover, textHover, press, focused, enabled, reducedMotion, highContrast, true, true);
        }
        public State(float hover, float textHover, float press, boolean focused, boolean enabled,
                     boolean reducedMotion) {
            this(hover, textHover, press, focused, enabled, reducedMotion, false);
        }

        public int titleText() { return controlText(); }
        public int secondaryText() {
            return highContrast ? secondaryTextColor() : GuiMotion.lerpColor(mutedTextColor(), secondaryTextColor(), textHover);
        }
        public int controlText() {
            if (!enabled) return mutedTextColor();
            int rest = UiTheme.current().secondaryText(0xFFE2E8F0, 0.08f);
            return GuiMotion.lerpColor(rest, textColor(), textHover);
        }
        int focusColor() { return highContrast ? UiTheme.current().accent(CONTRAST_FOCUS) : accentColor(); }

        /** 卡片当前的绘制 Y 偏移，子控件命中应使用同一偏移。 */
        public float cardOffset() { return reducedMotion || !hoverAnimation ? 0f : -2f * hover; }

        float buttonScale() { return reducedMotion || !pressAnimation ? 1f : GuiMotion.lerp(1f, 0.98f, press); }

        int buttonFill(boolean primary) {
            var theme = UiTheme.current();
            if (primary && enabled) {
                return GuiMotion.lerpColor(theme.controlFill(PRIMARY, 0.18f),
                        theme.controlFill(PRIMARY_HOVER, 0.28f), hover);
            }
            return GuiMotion.lerpColor(surfaceColor(), ThemeEffects.material("slate").fill(theme.controlFill(0xFF334B6B, 0.12f)), hover);
        }

        int buttonFill(boolean primary, boolean selected, float emphasis) {
            float strength = selected && !primary ? 0.22f + 0.18f * GuiMotion.clamp01(emphasis) : 0f;
            return GuiMotion.lerpColor(buttonFill(primary), accentColor(), strength);
        }
    }

    private static final class Tween {
        private float start;
        private float target;
        private long startedAt;

        private float update(boolean on, boolean immediate, long nowMs, long duration) {
            float next = on ? 1f : 0f;
            if (immediate) {
                start = target = next;
                startedAt = nowMs;
                return next;
            }
            float progress = GuiMotion.clamp01((nowMs - startedAt) / (float) duration);
            float remaining = 1f - progress;
            float value = GuiMotion.lerp(start, target, 1f - remaining * remaining * remaining);
            if (next != target) {
                start = value;
                target = next;
                startedAt = nowMs;
            }
            return value;
        }
    }
}
