package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** 山海：显示调用方确认的开关状态，沿用共用按钮的输入、提示与旁白。 */
public final class UiToggleButton {
    private final UiButton button;
    private final Function<Boolean, Component> label;
    private boolean value;
    private boolean modeIndicatorsVisible;
    private GraphicIcon onIcon;
    private GraphicIcon offIcon;

    public UiToggleButton(Bounds area, boolean value, UiPreferences preferences, UiStyle style,
                          Function<Boolean, Component> label, Consumer<Boolean> onChange) {
        this.label = Objects.requireNonNull(label);
        Objects.requireNonNull(onChange);
        button = new UiButton(area.x(), area.y(), area.width(), area.height(), CommonComponents.EMPTY, pressed -> {
            if (pressed.active && pressed.visible) onChange.accept(!this.value);
        }, preferences, false, style);
        setValue(value);
    }

    /** 将此按钮注册到宿主 Screen。 */
    public UiButton button() {
        return button;
    }

    public boolean value() {
        return value;
    }

    /** 显示两根模式条，关为第一根，开为第二根。 */
    public void setModeIndicatorsVisible(boolean visible) {
        if (modeIndicatorsVisible == visible) return;
        modeIndicatorsVisible = visible;
        if (visible) button.setModeIndicators(2, value ? 1 : 0);
        else button.clearModeIndicators();
    }

    /** 显示已确认的状态，同步标签和图标。此操作不会再次触发回调。 */
    public void setValue(boolean value) {
        this.value = value;
        button.setMessage(label.apply(value));
        button.setSelected(value);
        if (modeIndicatorsVisible) button.setModeIndicators(2, value ? 1 : 0);
        if (onIcon != null || offIcon != null) button.setLeadingIcon(value ? onIcon : offIcon);
    }

    public void setIcons(GraphicIcon on, GraphicIcon off) {
        onIcon = on;
        offIcon = off;
        button.setLeadingIcon(value ? onIcon : offIcon);
    }
}
