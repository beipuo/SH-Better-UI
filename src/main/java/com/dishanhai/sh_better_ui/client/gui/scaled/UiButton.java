package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.text.UiFormattedText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/** 山海：保留原生输入、焦点、功能提示和旁白的共用按钮。 */
public final class UiButton extends Button {
    private static final int ICON_GAP = 4;
    private static final int LABEL_PADDING = 6;
    private final UiPreferences preferences;
    private final boolean primary;
    private final UiStyle style;
    private final DarkModeTheme.Interaction motion = DarkModeTheme.Interaction.control();
    private boolean justPressed;
    private boolean selected;
    private long selectedAt;
    private Component preview;
    private GraphicIcon icon;
    private boolean iconWithLabel;
    private boolean sampleBackground = true;
    private float backgroundBlurRadius = LiquidGlassTheme.DEFAULT_BUTTON_BLUR_RADIUS;

    public UiButton(int x, int y, int width, int height, Component label, OnPress onPress) {
        this(x, y, width, height, label, onPress, UiPreferences.DEFAULT, false);
    }

    public UiButton(int x, int y, int width, int height, Component label, OnPress onPress,
                    UiPreferences preferences, boolean primary) {
        this(x, y, width, height, label, onPress, preferences, primary, UiStyle.DEFAULT);
    }

    public UiButton(int x, int y, int width, int height, Component label, OnPress onPress,
                    UiPreferences preferences, boolean primary, UiStyle style) {
        super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
        this.preferences = preferences;
        this.primary = primary;
        this.style = Objects.requireNonNull(style);
        setPreview(null);
    }

    /** 位于已有玻璃工具列内时可跳过液态玻璃的重复背景取样。 */
    public void setSampleBackground(boolean sampleBackground) {
        this.sampleBackground = sampleBackground;
    }

    /** 液态玻璃背景模糊半径，单位为 GUI 单位。0 关闭，最大 32；负数与非有限值无效。 */
    public void setBackgroundBlurRadius(float radius) {
        backgroundBlurRadius = LiquidGlassTheme.normalizeBlurRadius(radius);
    }

    public float getBackgroundBlurRadius() {
        return backgroundBlurRadius;
    }

    /** 本地化功能提示；null 时显示完整按钮名称，键盘焦点沿用原生 tooltip。 */
    public void setPreview(Component preview) {
        this.preview = preview;
        setTooltip(Tooltip.create(preview == null ? getMessage() : preview));
    }

    /** 设置居中图标，原名称仍用于提示和旁白。传 null 恢复文字绘制。 */
    public void setIcon(GraphicIcon icon) {
        this.icon = icon;
        iconWithLabel = false;
    }

    /** 在可见文字左侧显示 16×16 图标。空间不足时保留文字。 */
    public void setLeadingIcon(GraphicIcon icon) {
        this.icon = icon;
        iconWithLabel = true;
    }

    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        if (preview == null) setTooltip(Tooltip.create(message));
    }

    /** 由消费画面的实际状态设置，点击不会自行宣告服务器状态已改变。 */
    public void setSelected(boolean selected) {
        if (this.selected == selected) return;
        this.selected = selected;
        selectedAt = PageTransition.nowMs();
    }

    public boolean isSelected() { return selected; }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        super.updateWidgetNarration(output);
        if (selected) output.add(NarratedElementType.HINT, Component.translatable("sh_better_ui.control.selected"));
    }

    @Override
    public void onPress() {
        justPressed = true;
        super.onPress();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = PageTransition.nowMs();
        var state = motion.sample(isHovered(), justPressed, isFocused(), active, preferences, now);
        justPressed = false;
        int x = getX(), y = getY(), width = getWidth(), height = getHeight();
        if (style == UiStyle.NETWORK) {
            if (width <= 2 || height <= 2) return;
            NetworkTheme.drawTab(graphics, x, y, width, height, state, selected);
            graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
            try {
                drawContent(graphics, x, y, width, height, NetworkTheme.controlText(state, selected));
            } finally {
                graphics.disableScissor();
            }
            return;
        }
        float emphasis = selected && !preferences.reducedMotion()
                ? 1f - GuiMotion.clamp01((now - selectedAt) / 360f) : 0f;
        boolean liquidGlass = style == UiStyle.LIQUID_GLASS;
        int textColor = liquidGlass ? LiquidGlassTheme.controlText(state.enabled()) : state.controlText();
        Runnable content = () -> drawContent(graphics, x, y, width, height, textColor);
        if (liquidGlass) {
            LiquidGlassTheme.button(graphics, x, y, width, height, state, preferences, sampleBackground,
                    backgroundBlurRadius, primary, selected, emphasis, content);
        } else {
            DarkModeTheme.button(graphics, x, y, width, height, state, primary, selected, emphasis, content);
        }
    }

    private void drawContent(GuiGraphics graphics, int x, int y, int width, int height, int textColor) {
        int iconSize = GraphicIcon.PIXELS;
        if (!iconWithLabel && icon != null && width >= iconSize && height >= iconSize) {
            icon.draw(graphics, x + (width - iconSize) / 2, y + (height - iconSize) / 2, 1, textColor, preferences);
            return;
        }
        var font = Minecraft.getInstance().font;
        int inset = leadingInset(width, height);
        var lines = font.split(getMessage(), labelWidth(width, height));
        int count = Math.min(lines.size(), Math.max(1, (height - 4) / font.lineHeight));
        int contentX = x;
        if (inset > 0) {
            int textWidth = 0;
            for (int i = 0; i < count; i++) textWidth = Math.max(textWidth, font.width(lines.get(i)));
            contentX += (width - inset - textWidth) / 2;
            icon.draw(graphics, contentX, y + (height - iconSize) / 2, 1, textColor, preferences);
        }
        int textY = y + (height - count * font.lineHeight) / 2;
        for (int i = 0; i < count; i++) {
            var line = lines.get(i);
            int textX = inset > 0 ? contentX + inset : x + (width - font.width(line)) / 2;
            UiFormattedText.draw(graphics, font, line, textX, textY + i * font.lineHeight,
                    textColor, false, preferences);
        }
    }

    int labelWidth(int width, int height) {
        return Math.max(1, width - 2 * LABEL_PADDING - leadingInset(width, height));
    }

    private int leadingInset(int width, int height) {
        int inset = GraphicIcon.PIXELS + ICON_GAP;
        return iconWithLabel && icon != null && width >= 2 * LABEL_PADDING + inset + 24
                && height >= GraphicIcon.PIXELS + 2 ? inset : 0;
    }
}
