package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.collision.CollisionBody;
import com.dishanhai.sh_better_ui.client.gui.collision.CollisionShape;
import com.dishanhai.sh_better_ui.client.gui.collision.CollisionWorld;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/** 山海：将动画边界应用到既有控件或绘制回调。宿主负责裁剪、焦点和事件分发。 */
public final class UiCollisionLayout implements AutoCloseable {
    public static final int MAX_HIT_SLOP = 64;
    private static final int HIT_ROW_HEIGHT = 32;
    private static final int MAX_INDEXED_ROWS = 64;
    private static final BooleanSupplier ALWAYS_HITTABLE = () -> true;
    private final CollisionWorld world;
    private final ArrayList<Binding> bindings = new ArrayList<>();
    private Bounds viewport;
    private boolean closed;
    private boolean updating;
    private boolean applying;
    private HitRows hitRows;

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
        hitRows = null;
        return this;
    }

    public boolean unbind(CollisionBody body) {
        requireMutableBindings();
        int index = bindingIndex(body);
        if (index < 0) return false;
        bindings.remove(index);
        hitRows = null;
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
                    hitRows = null;
                } else {
                    if (binding.apply(viewport)) hitRows = null;
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

    /** Indexed input hit test. Hit slop never expands drawing or the viewport. */
    public CollisionBody hitTestExpanded(double x, double y, int hitSlop) {
        validateHitSlop(hitSlop);
        return hitTestExpanded(x, y, hitSlop, null);
    }

    /** Picks the foremost body using its own hit slop. The resolver must leave the layout unchanged. */
    public CollisionBody hitTestExpanded(double x, double y, ToIntFunction<? super CollisionBody> hitSlop) {
        return hitTestExpanded(x, y, MAX_HIT_SLOP, Objects.requireNonNull(hitSlop));
    }

    private CollisionBody hitTestExpanded(double x, double y, int maxSlop,
                                          ToIntFunction<? super CollisionBody> hitSlop) {
        if (!viewport.contains(x, y)) return null;
        if (hitRows == null) hitRows = indexHits();
        Binding match = foremost(hitRows.large(), x, y, maxSlop, hitSlop, null);
        for (int row = row(y - maxSlop); row <= row(y + maxSlop); row++) {
            var candidates = hitRows.rows().get(row);
            if (candidates != null) match = foremost(candidates, x, y, maxSlop, hitSlop, match);
        }
        return match == null ? null : match.body;
    }

    private HitRows indexHits() {
        var rows = new HashMap<Integer, List<Binding>>();
        var large = new ArrayList<Binding>();
        for (int i = 0; i < bindings.size(); i++) {
            var binding = bindings.get(i);
            var bounds = binding.bounds;
            if (bounds == null || bounds.width() == 0 || bounds.height() == 0) continue;
            binding.hitOrder = i;
            int first = row(bounds.y()), last = row(bounds.bottom() - 1);
            if (last - first >= MAX_INDEXED_ROWS) {
                large.add(binding);
            } else {
                for (int row = first; row <= last; row++) {
                    rows.computeIfAbsent(row, ignored -> new ArrayList<>()).add(binding);
                }
            }
        }
        return new HitRows(rows, large);
    }

    private Binding foremost(List<Binding> candidates, double x, double y, int maxSlop,
                              ToIntFunction<? super CollisionBody> hitSlop, Binding match) {
        for (int i = candidates.size() - 1; i >= 0; i--) {
            var candidate = candidates.get(i);
            if (match != null && candidate.hitOrder <= match.hitOrder) break;
            var bounds = candidate.bounds;
            int slop = maxSlop;
            if (hitSlop != null) {
                slop = hitSlop.applyAsInt(candidate.body);
                validateHitSlop(slop);
            }
            if (x >= (double) bounds.x() - slop && x < (double) bounds.right() + slop
                    && y >= (double) bounds.y() - slop && y < (double) bounds.bottom() + slop
                    && world.contains(candidate.body) && candidate.hittable.getAsBoolean()) return candidate;
        }
        return match;
    }

    private static void validateHitSlop(int hitSlop) {
        if (hitSlop < 0 || hitSlop > MAX_HIT_SLOP) throw new IllegalArgumentException("Invalid hit slop");
    }

    private static int row(double y) { return (int) Math.floor(y / HIT_ROW_HEIGHT); }

    private record HitRows(Map<Integer, List<Binding>> rows, List<Binding> large) {}

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        bindings.clear();
        hitRows = null;
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
        private int hitOrder;

        private Binding(CollisionBody body, Consumer<Bounds> apply, BooleanSupplier hittable) {
            this.body = body;
            this.apply = apply;
            this.hittable = hittable;
        }

        private boolean apply(Bounds viewport) {
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
                return true;
            }
            return false;
        }
    }
}
