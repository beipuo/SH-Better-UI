package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** 山海：保存渐进展开状态，按钮沿用原生键盘操作和旁白。 */
public final class UiDisclosure {
    private boolean expanded;

    public UiDisclosure(boolean expanded) { this.expanded = expanded; }

    public boolean expanded() { return expanded; }

    /** 每次画面重排可重建按钮；保留实例可维持展开状态。回调只应控制可见性与布局。 */
    public Button button(int x, int y, int width, int height, Component showLabel, Component hideLabel,
                         Consumer<Boolean> onChanged) {
        return button(x, y, width, height, showLabel, hideLabel, UiPreferences.configured(), onChanged);
    }

    public Button button(int x, int y, int width, int height, Component showLabel, Component hideLabel,
                         UiPreferences preferences, Consumer<Boolean> onChanged) {
        return button(x, y, width, height, showLabel, hideLabel, preferences, UiStyle.DEFAULT,
                (button, value) -> onChanged.accept(value));
    }

    UiButton button(int x, int y, int width, int height, Component showLabel, Component hideLabel,
                    UiPreferences preferences, UiStyle style, BiConsumer<UiButton, Boolean> onChanged) {
        var control = new UiButton(x, y, width, height, expanded ? hideLabel : showLabel, button -> {
            expanded = !expanded;
            var disclosureButton = (UiButton) button;
            disclosureButton.setMessage(expanded ? hideLabel : showLabel);
            disclosureButton.setSelected(expanded);
            onChanged.accept(disclosureButton, expanded);
        }, preferences, false, style);
        control.setSelected(expanded);
        return control;
    }
}
