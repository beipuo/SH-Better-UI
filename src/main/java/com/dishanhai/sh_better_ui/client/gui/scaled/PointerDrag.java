package com.dishanhai.sh_better_ui.client.gui.scaled;

/** 山海：可见拖曳把手的按键捕获。每个交互区域独立持有，使用同一套 GUI 坐标。 */
public final class PointerDrag {
    public record Delta(double x, double y) {}

    private int button = -1;
    private double startX, startY;
    private double pointerX, pointerY;

    /** 命中可用把手、子控件未处理事件后调用。 */
    public boolean begin(int button, double x, double y) {
        if (active() || button < 0 || !Double.isFinite(x) || !Double.isFinite(y)) return false;
        this.button = button;
        startX = pointerX = x;
        startY = pointerY = y;
        return true;
    }

    /** 继续捕获原按键，即使指针已经移出把手。返回相对上次采样的位移，未接收时返回 null。 */
    public Delta move(int button, double x, double y) {
        if (!owns(button) || !Double.isFinite(x) || !Double.isFinite(y)) return null;
        var delta = new Delta(x - pointerX, y - pointerY);
        pointerX = x;
        pointerY = y;
        return delta;
    }

    /** 返回最后一段位移并释放捕获。调用方用最终位置重新检查落点，再提交业务操作。 */
    public Delta release(int button, double x, double y) {
        if (!owns(button)) return null;
        Delta delta = move(button, x, y);
        if (delta == null) cancel();
        else this.button = -1;
        return delta;
    }

    /** 失焦、关闭或取消时清除捕获和位移。 */
    public void cancel() {
        button = -1;
        pointerX = startX;
        pointerY = startY;
    }

    public boolean active() { return button >= 0; }
    public double offsetX() { return pointerX - startX; }
    public double offsetY() { return pointerY - startY; }

    private boolean owns(int button) { return active() && this.button == button; }
}
