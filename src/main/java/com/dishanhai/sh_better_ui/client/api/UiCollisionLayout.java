package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.collision.CollisionBody;
import com.dishanhai.sh_better_ui.client.gui.collision.CollisionShape;
import com.dishanhai.sh_better_ui.client.gui.collision.CollisionWorld;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 山海：将动画边界应用到既有控件或绘制回调。宿主负责裁剪、焦点和事件分发。 */
public final class UiCollisionLayout implements AutoCloseable {
    private static final BooleanSupplier ALWAYS_HITTABLE = () -> true;
    private final CollisionWorld world;
    private final ArrayList<Binding> bindings = new ArrayList<>();
    private Bounds viewport;
    private boolean closed;
    private boolean updating;
    private boolean applying;

    public UiCollisionLayout(Bounds viewport) {
        this.viewport = Objects.requireNonNull(viewport);
        world = new CollisionWorld(shape(viewport));
    }

    public CollisionWorld world() {
        return world;
    }

    /** 一次绑定同时更新原生控件的绘制与命中几何，保留其原生焦点和输入行为。 */
    public UiCollisionLayout bind(CollisionBody body, AbstractWidget widget) {
        Objects.requireNonNull(widget);
        return bind(body, bounds -> {
            widget.setX(bounds.x());
            widget.setY(bounds.y());
            widget.setWidth(bounds.width());
            widget.setHeight(bounds.height());
        }, () -> widget.visible && widget.active);
    }

    /** 给自绘控件提供与 hitTest 同一份逻辑 GUI Bounds。重绑保留原来的图层顺序。 */
    public UiCollisionLayout bind(CollisionBody body, Consumer<Bounds> apply) {
        return bind(body, apply, ALWAYS_HITTABLE);
    }

    private UiCollisionLayout bind(CollisionBody body, Consumer<Bounds> apply, BooleanSupplier hittable) {
        requireMutableBindings();
        if (!world.contains(body)) throw new IllegalArgumentException("Body does not belong to this collision layout");
        var binding = new Binding(body, Objects.requireNonNull(apply), hittable);
        int index = bindingIndex(body);
        if (index < 0) bindings.add(binding);
        else bindings.set(index, binding);
        return this;
    }

    public boolean unbind(CollisionBody body) {
        requireMutableBindings();
        int index = bindingIndex(body);
        if (index < 0) return false;
        bindings.remove(index);
        return true;
    }

    /** 在宿主绘制前更新一次，使用 UiFrame 的时间、视口和减少动效设置。 */
    public int update(UiFrame frame) {
        return update(frame.viewport(), frame.nowMs(), frame.preferences().reducedMotion());
    }

    public int update(Bounds viewport, long nowMs, boolean reducedMotion) {
        requireOpen();
        if (updating) throw new IllegalStateException("Recursive UI collision layout update");
        updating = true;
        try {
            if (!matches(world.viewport(), viewport)) world.viewport(shape(viewport));
            this.viewport = viewport;
            int unresolved = world.update(nowMs, reducedMotion);
            applying = true;
            for (int index = 0; index < bindings.size();) {
                var binding = bindings.get(index);
                if (!world.contains(binding.body)) {
                    bindings.remove(index);
                } else {
                    binding.apply(viewport);
                    index++;
                }
            }
            return unresolved;
        } finally {
            applying = false;
            updating = false;
        }
    }

    /** 按反向绑定顺序命中最近应用的绘制矩形。适合宿主自行分发重叠控件的输入。 */
    public CollisionBody hitTest(double mouseX, double mouseY) {
        if (!viewport.contains(mouseX, mouseY)) return null;
        for (int index = bindings.size() - 1; index >= 0; index--) {
            var binding = bindings.get(index);
            if (binding.bounds != null && binding.bounds.contains(mouseX, mouseY)
                    && world.contains(binding.body) && binding.hittable.getAsBoolean()) return binding.body;
        }
        return null;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        bindings.clear();
        world.clear();
    }

    private int bindingIndex(CollisionBody body) {
        for (int index = 0; index < bindings.size(); index++) {
            if (bindings.get(index).body == body) return index;
        }
        return -1;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("UI collision layout is closed");
    }

    private void requireMutableBindings() {
        requireOpen();
        if (applying) throw new IllegalStateException("Cannot change collision bindings while applying geometry");
    }

    private static CollisionShape shape(Bounds viewport) {
        return new CollisionShape(viewport.x(), viewport.y(), viewport.width(), viewport.height());
    }

    private static boolean matches(CollisionShape shape, Bounds bounds) {
        return shape.x() == bounds.x() && shape.y() == bounds.y()
                && shape.width() == bounds.width() && shape.height() == bounds.height();
    }

    private static int clip(int value, int start, int end) {
        return Math.max(start, Math.min(value, end));
    }

    private static final class Binding {
        private final CollisionBody body;
        private final Consumer<Bounds> apply;
        private final BooleanSupplier hittable;
        private Bounds bounds;

        private Binding(CollisionBody body, Consumer<Bounds> apply, BooleanSupplier hittable) {
            this.body = body;
            this.apply = apply;
            this.hittable = hittable;
        }

        private void apply(Bounds viewport) {
            int x = clip(Math.round(body.visualX()), viewport.x(), viewport.right());
            int y = clip(Math.round(body.visualY()), viewport.y(), viewport.bottom());
            int right = clip(Math.round(body.visualRight()), x, viewport.right());
            int bottom = clip(Math.round(body.visualBottom()), y, viewport.bottom());
            int width = right - x;
            int height = bottom - y;
            if (bounds == null || bounds.x() != x || bounds.y() != y || bounds.width() != width
                    || bounds.height() != height) {
                var next = new Bounds(x, y, width, height);
                apply.accept(next);
                bounds = next;
            }
        }
    }
}
