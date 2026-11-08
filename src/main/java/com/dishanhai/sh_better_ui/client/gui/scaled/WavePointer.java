package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.Arrays;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LAST;

/** 山海：波面共用的鼠标命中、尾迹与点击涟漪。 */
final class WavePointer {
    private static final int WAKE_INTERVAL_MS = 55;
    static final int RIPPLE_MS = 1100;

    private final Ripple[] ripples = new Ripple[8];
    private final Matrix4f lastPose = new Matrix4f();
    private Bounds lastBounds;
    private UiMouseState previousMouse;
    private Point wakeFrom;
    private long lastWakeMs;
    private int nextRipple;
    private int forwardedPresses;
    private boolean enabled;

    Ripple[] ripples() { return ripples; }

    void reset() {
        lastBounds = null;
        enabled = false;
        clearRipples();
    }

    void update(Bounds bounds, Matrix4fc pose, UiMouseState mouse, boolean interactive, float radius, long now) {
        if (lastBounds == null || lastBounds.width() != bounds.width() || lastBounds.height() != bounds.height()) {
            clearRipples();
        }
        lastBounds = bounds;
        lastPose.set(pose);
        enabled = interactive && mouse != null && mouse.focused() && !mouse.grabbed();
        if (!enabled) {
            clearRipples();
        } else if (mouse.available()) {
            updatePointer(mouse, radius, now);
        } else {
            previousMouse = null;
            wakeFrom = null;
        }
        forwardedPresses = 0;
    }

    void mousePressed(double guiX, double guiY, int button, long now) {
        if (!enabled || button < 0 || button > GLFW_MOUSE_BUTTON_LAST) return;
        Point point = localPoint(guiX, guiY);
        if (point == null) return;
        emit(point, now, 1f);
        forwardedPresses |= 1 << button;
    }

    private void updatePointer(UiMouseState mouse, float radius, long now) {
        Point point = localPoint(mouse.x(), mouse.y());
        int pressed = mouse.pressedSince(previousMouse) & ~forwardedPresses;
        previousMouse = mouse;
        if (point == null) {
            wakeFrom = null;
            return;
        }
        if (pressed != 0) emit(point, now, 1f);
        if (wakeFrom == null || pressed != 0 || forwardedPresses != 0) {
            wakeFrom = point;
            lastWakeMs = now;
        } else if (now - lastWakeMs >= WAKE_INTERVAL_MS) {
            float distance = (float) Math.hypot(point.x - wakeFrom.x, point.y - wakeFrom.y);
            if (distance >= 1f) {
                emit(point, now, Math.min(.48f, .12f + distance / radius * .35f));
                wakeFrom = point;
                lastWakeMs = now;
            }
        }
    }

    private Point localPoint(double screenX, double screenY) {
        // GUI 平面上只需要反解 X/Y，可处理平移、缩放和旋转。
        double determinant = (double) lastPose.m00() * lastPose.m11() - (double) lastPose.m10() * lastPose.m01();
        if (Math.abs(determinant) < 1e-8) return null;
        double dx = screenX - lastPose.m30();
        double dy = screenY - lastPose.m31();
        double x = (dx * lastPose.m11() - dy * lastPose.m10()) / determinant;
        double y = (dy * lastPose.m00() - dx * lastPose.m01()) / determinant;
        return lastBounds.contains(x, y) ? new Point((float) (x - lastBounds.x()), (float) (y - lastBounds.y())) : null;
    }

    private void emit(Point point, long now, float strength) {
        ripples[nextRipple] = new Ripple(point.x, point.y, now, strength);
        nextRipple = (nextRipple + 1) % ripples.length;
    }

    private void clearRipples() {
        previousMouse = null;
        wakeFrom = null;
        forwardedPresses = 0;
        Arrays.fill(ripples, null);
        nextRipple = 0;
    }

    private record Point(float x, float y) {}
    record Ripple(float x, float y, long startedAt, float strength) {}
}
