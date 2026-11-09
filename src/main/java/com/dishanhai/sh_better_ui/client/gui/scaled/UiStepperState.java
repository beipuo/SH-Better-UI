package com.dishanhai.sh_better_ui.client.gui.scaled;

import java.util.Objects;

/** 山海：独立于控件的线性向导状态。返回早期步骤时保留已解锁的进度。 */
public final class UiStepperState {
    public enum Status { UPCOMING, CURRENT, COMPLETED }

    private final int count;
    private int current;
    private int lastReachable;

    /** 初始化时，当前步骤之前的步骤视为已完成。 */
    public UiStepperState(int count, int current) {
        this(count, current, current);
    }

    /** 恢复进度，lastReachable 之前的步骤视为已完成。 */
    public UiStepperState(int count, int current, int lastReachable) {
        Objects.checkIndex(lastReachable, count);
        Objects.checkIndex(current, lastReachable + 1);
        this.count = count;
        this.current = current;
        this.lastReachable = lastReachable;
    }

    public int count() { return count; }
    public int currentIndex() { return current; }
    public int lastReachableIndex() { return lastReachable; }

    public Status status(int index) {
        Objects.checkIndex(index, count);
        if (index == current) return Status.CURRENT;
        return index < lastReachable ? Status.COMPLETED : Status.UPCOMING;
    }

    public boolean isReachable(int index) {
        return index >= 0 && index <= lastReachable;
    }

    /** 导航到已解锁的步骤。 */
    public boolean select(int index) {
        if (!isReachable(index) || index == current) return false;
        current = index;
        return true;
    }

    /** 由宿主确认当前步骤有效后调用。到达末步时返回 false。 */
    public boolean advance() {
        if (current == count - 1) return false;
        current++;
        lastReachable = Math.max(lastReachable, current);
        return true;
    }
}
