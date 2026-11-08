package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import org.joml.Matrix4fc;

import java.util.Objects;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

/** 山海：将共享鼠标快照转换为区域内的指针、有效按压与取消状态，不消费宿主输入。 */
public final class UiPointerTracker {
    private boolean inside;
    private boolean down;
    private boolean armed;
    private float lastX;
    private float lastY;
    private long lastMovedMs;

    /** 清除状态；恢复采样后必须先见到区域内的松开状态，才接受下一次按压。 */
    public void reset() {
        inside = false;
        down = false;
        armed = false;
        lastX = lastY = 0;
        lastMovedMs = 0;
    }

    /**
     * GUI pose 支持平移、缩放和旋转。宿主用 enabled=false 或 mouse=null 屏蔽被模态层遮住的区域。
     * 离开、失焦、抓取和不可逆变换均取消已有按压，不产生释放事件。
     */
    public Frame update(Bounds bounds, Matrix4fc pose, UiMouseState mouse, boolean enabled, long nowMs) {
        Objects.requireNonNull(bounds);
        Objects.requireNonNull(pose);
        if (!enabled || mouse == null || !mouse.available()) return cancel();

        // GUI 平面的二维逆变换，与其他波面使用相同的坐标约定。
        double determinant = (double) pose.m00() * pose.m11() - (double) pose.m10() * pose.m01();
        if (!Double.isFinite(determinant) || Math.abs(determinant) < 1e-8) return cancel();
        double dx = mouse.x() - pose.m30();
        double dy = mouse.y() - pose.m31();
        double x = (dx * pose.m11() - dy * pose.m10()) / determinant;
        double y = (dy * pose.m00() - dx * pose.m01()) / determinant;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !bounds.contains(x, y)) return cancel();

        float localX = (float) (x - bounds.x());
        float localY = (float) (y - bounds.y());
        if (!inside || localX != lastX || localY != lastY) lastMovedMs = nowMs;
        lastX = localX;
        lastY = localY;
        boolean physicalDown = mouse.isDown(GLFW_MOUSE_BUTTON_LEFT);
        boolean pressed = false;
        boolean released = false;
        if (!inside) {
            // 不接管从其他区域拖入或失焦期间已经按住的按钮。
            armed = !physicalDown;
        } else if (physicalDown) {
            pressed = armed && !down;
            down |= pressed;
            armed = false;
        } else {
            released = down;
            down = false;
            armed = true;
        }
        inside = true;
        long idle = nowMs < lastMovedMs ? 0 : nowMs - lastMovedMs;
        if (idle < 0) idle = Long.MAX_VALUE;
        return new Frame(localX, localY, true, down, pressed, released, false, idle);
    }

    private Frame cancel() {
        boolean cancelled = down;
        reset();
        return new Frame(0, 0, false, false, false, false, cancelled, Long.MAX_VALUE);
    }

    /** x/y 为区域局部 GUI 单位；事件位仅在对应采样帧为真，idleMillis 从最后一次位置变化计时。 */
    public record Frame(float x, float y, boolean inside, boolean down, boolean pressed,
                        boolean released, boolean cancelled, long idleMillis) {}
}
