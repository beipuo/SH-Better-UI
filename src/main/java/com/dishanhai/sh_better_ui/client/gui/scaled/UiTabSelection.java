package com.dishanhai.sh_better_ui.client.gui.scaled;

import java.util.Objects;
import java.util.function.IntPredicate;

/** 山海：独立于渲染和键盘的单选状态，创建时固定各项是否可选。 */
public final class UiTabSelection {
    private final boolean[] enabled;
    private int selected;

    public UiTabSelection(int count, int selected, IntPredicate enabled) {
        Objects.checkIndex(selected, count);
        this.enabled = new boolean[count];
        for (int i = 0; i < count; i++) this.enabled[i] = enabled.test(i);
        if (!this.enabled[selected]) throw new IllegalArgumentException();
        this.selected = selected;
    }

    public int selectedIndex() { return selected; }

    public boolean isEnabled(int index) {
        return index >= 0 && index < enabled.length && enabled[index];
    }

    /** 仅在切换到另一可用项时返回 true。 */
    public boolean select(int index) {
        if (!isEnabled(index) || index == selected) return false;
        selected = index;
        return true;
    }

    /** 按方向寻找下一可用项，可绕回首尾。 */
    public boolean move(int direction) {
        int delta = Integer.signum(direction);
        if (delta == 0) return false;
        int index = selected;
        for (int i = 1; i < enabled.length; i++) {
            index = Math.floorMod(index + delta, enabled.length);
            if (select(index)) return true;
        }
        return false;
    }
}
