package com.dishanhai.sh_better_ui.client.gui.collision;

/** 山海：逻辑 GUI 坐标中的轴对齐矩形。右边和下边不参与命中。 */
public record CollisionShape(float x, float y, float width, float height) {
    static final float LIMIT = 1_000_000;
    static final float SOLVER_EPSILON = 0.0001f;

    public CollisionShape {
        if (!(Math.abs(x) <= LIMIT && Math.abs(y) <= LIMIT
                && validSize(width) && validSize(height)
                && x + width <= LIMIT && y + height <= LIMIT)) {
            throw new IllegalArgumentException("Invalid collision rectangle");
        }
    }

    static boolean validSize(float size) {
        return size >= 0 && size <= LIMIT;
    }

    public float right() {
        return x + width;
    }

    public float bottom() {
        return y + height;
    }

    public boolean contains(double mouseX, double mouseY) {
        return width > 0 && height > 0 && mouseX >= x && mouseX < right()
                && mouseY >= y && mouseY < bottom();
    }

    public boolean intersects(CollisionShape other) {
        return width > 0 && height > 0 && other.width > 0 && other.height > 0
                && x < other.right() && other.x < right()
                && y < other.bottom() && other.y < bottom();
    }
}
