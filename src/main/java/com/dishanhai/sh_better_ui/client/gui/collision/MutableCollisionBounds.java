package com.dishanhai.sh_better_ui.client.gui.collision;

/** 山海：求解与呈现各自持有的可复用矩形缓冲。 */
final class MutableCollisionBounds {
    float x;
    float y;
    float width;
    float height;

    void set(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    void setAxis(boolean horizontal, float start, float size) {
        if (horizontal) {
            x = start;
            width = size;
        } else {
            y = start;
            height = size;
        }
    }

    float size(boolean horizontal) { return horizontal ? width : height; }
    float start(boolean horizontal) { return horizontal ? x : y; }
    float end(boolean horizontal) { return start(horizontal) + size(horizontal); }
    float center(boolean horizontal) { return start(horizontal) + size(horizontal) * 0.5f; }

    boolean fit(CollisionShape viewport) {
        float w = clamp(width, 0, viewport.width());
        float h = clamp(height, 0, viewport.height());
        float left = clamp(x, viewport.x(), viewport.right() - w);
        float top = clamp(y, viewport.y(), viewport.bottom() - h);
        boolean changed = x != left || y != top || width != w || height != h;
        set(left, top, w, h);
        return changed;
    }

    boolean overlaps(MutableCollisionBounds other) {
        float epsilon = CollisionShape.SOLVER_EPSILON;
        return width > epsilon && height > epsilon && other.width > epsilon && other.height > epsilon
                && Math.min(x + width, other.x + other.width) - Math.max(x, other.x) > epsilon
                && Math.min(y + height, other.y + other.height) - Math.max(y, other.y) > epsilon;
    }

    CollisionShape snapshot() {
        return new CollisionShape(x, y, width, height);
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }
}
