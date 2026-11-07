package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** 山海：保留原生输入、焦点、功能提示和旁白的共用按钮。 */
public final class UiButton extends Button {
    private final UiPreferences preferences;
    private final boolean primary;
    private final DarkModeTheme.Interaction motion = DarkModeTheme.Interaction.control();
    private boolean justPressed;
    private boolean selected;
    private long selectedAt;
    private Component preview;

    public UiButton(int x, int y, int width, int height, Component label, OnPress onPress) {
        this(x, y, width, height, label, onPress, UiPreferences.DEFAULT, false);
    }

    public UiButton(int x, int y, int width, int height, Component label, OnPress onPress,
                    UiPreferences preferences, boolean primary) {
        super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
        this.preferences = preferences;
        this.primary = primary;
        setPreview(null);
    }

    /** 本地化功能提示；null 时显示完整按钮名称，键盘焦点沿用原生 tooltip。 */
    public void setPreview(Component preview) {
        this.preview = preview;
        setTooltip(Tooltip.create(preview == null ? getMessage() : preview));
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
        float emphasis = selected && !preferences.reducedMotion()
                ? 1f - GuiMotion.clamp01((now - selectedAt) / 360f) : 0f;
        DarkModeTheme.button(graphics, x, y, width, height, state, primary, selected, emphasis, () -> {
            var font = Minecraft.getInstance().font;
            var lines = font.split(getMessage(), Math.max(1, width - 12));
            int count = Math.min(lines.size(), Math.max(1, (height - 4) / font.lineHeight));
            int textY = y + (height - count * font.lineHeight) / 2;
            for (int i = 0; i < count; i++) {
                var line = lines.get(i);
                graphics.drawString(font, line, x + (width - font.width(line)) / 2,
                        textY + i * font.lineHeight, state.controlText(), false);
            }
        });
    }
}
