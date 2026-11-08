package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.api.PlatformUiApi;
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
    private static final int MODE_INDICATOR_WIDTH = 8;
    private static final int MODE_INDICATOR_GAP = 3;
    private static final int MODE_INDICATOR_RESERVE = 4;
    private UiPreferences preferences;
    private final boolean primary;
    private final UiStyle style;
    private final DarkModeTheme.Interaction motion = DarkModeTheme.Interaction.control();
    private boolean justPressed;
    private boolean held;
    private boolean selected;
    private long selectedAt;
    private int modeIndicatorCount;
    private int selectedModeIndex = -1;
    private Component preview;
    private GraphicIcon icon;
    private boolean iconWithLabel;
    private boolean sampleBackground = true;
    private float backgroundBlurRadius = LiquidGlassTheme.DEFAULT_BUTTON_BLUR_RADIUS;

    public UiButton(int x, int y, int width, int height, Component label, OnPress onPress) {
        this(x, y, width, height, label, onPress, UiPreferences.configured(), false);
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
        PlatformUiApi.setTooltipDelay(this, preferences.feedback().tooltipDelayMs());
        setPreview(null);
    }

    /** 更新正在显示的控件，不重建按钮或移动焦点。 */
    public void setPreferences(UiPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
        PlatformUiApi.setTooltipDelay(this, preferences.feedback().tooltipDelayMs());
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

    /**
     * 在按钮内容下方显示每个模式的短条，并高亮已确认的零基索引。
     * 每条及条间至少需要一个 GUI 单位。空间不足时保留按钮的常规绘制。
     */
    public void setModeIndicators(int count, int selectedIndex) {
        if (count < 1 || selectedIndex < 0 || selectedIndex >= count) throw new IllegalArgumentException();
        modeIndicatorCount = count;
        selectedModeIndex = selectedIndex;
    }

    /** 恢复常规按钮的底边绘制。 */
    public void clearModeIndicators() {
        modeIndicatorCount = 0;
        selectedModeIndex = -1;
    }

    public int getModeIndicatorCount() {
        return modeIndicatorCount;
    }

    /** 关闭模式条时返回 -1。 */
    public int getSelectedModeIndex() {
        return selectedModeIndex;
    }

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
        if (isCapsule() && UiOverlayHost.hidesSource(this)) return;
        long now = PageTransition.nowMs();
        var state = sampleMotion(now);
        int x = getX(), y = getY(), width = getWidth(), height = getHeight();
        boolean network = style == UiStyle.NETWORK;
        if (network && (width <= 2 || height <= 2)) return;
        var modeLayout = modeIndicatorLayout();
        int textColor = switch (style) {
            case DEFAULT -> state.controlText();
            case LIQUID_GLASS -> LiquidGlassTheme.controlText(state.enabled());
            case NETWORK -> NetworkTheme.controlText(state, selected);
        };
        Runnable content = () -> drawContent(graphics, x, y, width, height, textColor, state, modeLayout);
        if (network) {
            NetworkTheme.drawTab(graphics, x, y, width, height, state, selected);
            graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
            try {
                content.run();
            } finally {
                graphics.disableScissor();
            }
            return;
        }
        float emphasis = selectionEmphasis(now);
        boolean showSelectionMarker = modeLayout == null;
        if (style == UiStyle.LIQUID_GLASS) {
            LiquidGlassTheme.button(graphics, x, y, width, height, state, preferences, sampleBackground,
                    backgroundBlurRadius, primary, selected, emphasis, showSelectionMarker, content);
        } else {
            DarkModeTheme.button(graphics, x, y, width, height, state, primary, selected, emphasis,
                    showSelectionMarker, content);
        }
    }

    boolean isCapsule() {
        return style == UiStyle.LIQUID_GLASS;
    }

    FluidMorph renderMorph(GuiGraphics graphics, GuiRect target, UiPreferences panelPreferences,
                           float progress, long now) {
        var state = sampleMotion(now);
        int x = getX(), y = getY(), width = getWidth(), height = getHeight();
        float scale = state.buttonScale();
        if (Math.abs(1f - scale) < 0.001f) scale = 1f;
        float centerX = x + width * 0.5f, centerY = y + height * 0.5f;
        var capsule = new GuiRect(centerX - width * scale * 0.5f, centerY - height * scale * 0.5f,
                width * scale, height * scale);
        var frame = FluidMorph.between(capsule, target, LiquidGlassTheme.POPOVER_RADIUS, progress);
        LiquidGlassTheme.morphPopover(graphics, frame, panelPreferences, state, primary, selected,
                selectionEmphasis(now), sampleBackground, backgroundBlurRadius, progress);
        float labelOpacity = 1f - GuiMotion.clamp01(progress / 0.36f);
        if (labelOpacity > 0f) {
            GuiRect rect = frame.rect();
            float labelScale = Math.min(scale, Math.min(rect.getW() / width, rect.getH() / height));
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(rect.getX() + rect.getW() * 0.5f - centerX,
                        rect.getY() + rect.getH() * 0.5f - centerY, 0f);
                var modeLayout = modeIndicatorLayout();
                GuiRenderUtil.withButtonMotion(graphics, x, y, width, height, labelScale, 0f,
                        () -> GuiRenderUtil.withAttentionContent(graphics, labelOpacity, 0f, () -> {
                            if (selected && modeLayout == null) {
                                LiquidGlassTheme.drawSelectionMarker(graphics, x, y, width, height, state.enabled());
                            }
                            drawContent(graphics, x, y, width, height,
                                    LiquidGlassTheme.controlText(state.enabled()), state, modeLayout);
                        }));
            } finally {
                graphics.pose().popPose();
            }
        }
        return frame;
    }

    private DarkModeTheme.State sampleMotion(long now) {
        var state = motion.sample(isHovered(), justPressed || held, isFocused(), active, preferences, now);
        justPressed = false;
        return state;
    }

    /** 山海：聚合控件在提交点击前同步按住状态，不触发操作回调。 */
    void setHeld(boolean held) {
        this.held = held;
    }

    private float selectionEmphasis(long now) {
        return selected && !preferences.reducedMotion() && preferences.feedback().selectionPulse()
                ? 1f - GuiMotion.clamp01((now - selectedAt) / 360f) : 0f;
    }

    private void drawContent(GuiGraphics graphics, int x, int y, int width, int height, int textColor,
                             DarkModeTheme.State state, ModeIndicatorLayout modeLayout) {
        int contentHeight = modeLayout == null ? height : height - MODE_INDICATOR_RESERVE;
        drawContent(graphics, x, y, width, contentHeight, textColor);
        if (modeLayout != null) drawModeIndicators(graphics, modeLayout, state, textColor);
    }

    ModeIndicatorLayout modeIndicatorLayout() {
        int width = getWidth(), height = getHeight();
        if (modeIndicatorCount == 0 || width < 3 || height < 14) return null;
        int gapCount = modeIndicatorCount - 1;
        long minimumWidth = (long) modeIndicatorCount + gapCount;
        if (minimumWidth > width - 2) return null;

        int padding = Math.min(LABEL_PADDING, (width - (int) minimumWidth) / 2);
        int available = width - 2 * padding;
        int gap = gapCount == 0 ? 0 : Math.min(MODE_INDICATOR_GAP, (available - modeIndicatorCount) / gapCount);
        int barWidth = Math.min(MODE_INDICATOR_WIDTH, (available - gapCount * gap) / modeIndicatorCount);
        int stripWidth = modeIndicatorCount * barWidth + gapCount * gap;
        return new ModeIndicatorLayout(getX() + (width - stripWidth) / 2, getY() + height - 2,
                barWidth, barWidth + gap);
    }

    private void drawModeIndicators(GuiGraphics graphics, ModeIndicatorLayout layout,
                                    DarkModeTheme.State state, int textColor) {
        int accent = switch (style) {
            case DEFAULT -> state.focusColor();
            case LIQUID_GLASS -> LiquidGlassTheme.ACCENT;
            case NETWORK -> NetworkTheme.outline(true);
        };
        int selectedColor = state.enabled() && !state.highContrast() ? accent : textColor;
        float opacity = state.highContrast() ? 0.75f : state.enabled() ? 0.55f : 0.4f;
        int idleColor = GuiMotion.lerpColor(textColor & 0x00FFFFFF, textColor, opacity);
        for (int i = 0; i < modeIndicatorCount; i++) {
            boolean current = i == selectedModeIndex;
            int barX = layout.firstX() + i * layout.step();
            int bottom = layout.bottomY();
            graphics.fill(barX, bottom - (current ? 2 : 1), barX + layout.barWidth(), bottom,
                    current ? selectedColor : idleColor);
        }
    }

    record ModeIndicatorLayout(int firstX, int bottomY, int barWidth, int step) {}

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
