// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.UIDirtyFlag;
import com.dishanhai.sh_better_ui.kernel.api.UIInvalidation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Coalesces work by live node generation. Each flag is processed at most once per node per frame. */
final class DirtyWork {
    private static final int MAX_PER_FLAG = 4096;
    private final ScopeLifecycle scopes;
    private final Consumer<String> diagnostics;
    private final Map<UIDirtyFlag, Lane> lanes = new EnumMap<>(UIDirtyFlag.class);
    private final Map<ScopeLifecycle.Scope, Set<UIDirtyFlag>> boundaries = new HashMap<>();

    DirtyWork(ScopeLifecycle scopes, Consumer<String> diagnostics) {
        this.scopes = scopes;
        this.diagnostics = diagnostics;
        for (UIDirtyFlag flag : UIDirtyFlag.values()) {
            lanes.put(flag, new Lane());
        }
    }

    UIInvalidation view(ModuleAccess access) {
        return (node, flags) -> {
            access.assertSideEffectAllowed();
            invalidate(validate(access, node), Set.copyOf(flags));
        };
    }

    ScopeLifecycle.Scope validate(ModuleAccess access, ScopeHandle node) {
        var target = access.write(node);
        target.assertOwner();
        requireNode(target);
        return target;
    }

    void invalidate(ScopeLifecycle.Scope node, Set<UIDirtyFlag> flags) {
        node.assertOwner();
        requireNode(node);
        if (!node.isOpen()) {
            throw new IllegalStateException("Dirty node is closed: " + node.id());
        }
        if (flags.isEmpty()) {
            return;
        }
        var work = EnumSet.copyOf(flags);
        if (work.contains(UIDirtyFlag.LAYOUT)) {
            work.add(UIDirtyFlag.TRANSFORM);
        }
        if (work.contains(UIDirtyFlag.TRANSFORM)) {
            work.add(UIDirtyFlag.HIT_TEST);
            paintSubtree(node);
            work.add(UIDirtyFlag.PAINT);
        }
        for (UIDirtyFlag flag : work) {
            switch (flag) {
                case MEASURE, PAINT, ACCESSIBILITY -> propagateUp(node, flag);
                case STYLE, LAYOUT, TRANSFORM, HIT_TEST -> propagateDown(node, flag);
            }
        }
    }

    void boundaries(ScopeHandle handle, Set<UIDirtyFlag> flags) {
        var node = scopes.checkedScope(handle);
        node.assertOwner();
        requireNode(node);
        if (!node.isOpen()) {
            throw new IllegalStateException("Dirty node is closed: " + node.id());
        }
        boundaries.put(node, Set.copyOf(flags));
    }

    void beginFrame() {
        for (Lane lane : lanes.values()) {
            lane.drained.clear();
            lane.used = 0;
            lane.reported = false;
        }
    }

    List<ScopeLifecycle.Scope> drain(UIDirtyFlag flag) {
        scopes.root().assertOwner();
        var lane = lanes.get(flag);
        if (lane.pending.isEmpty()) {
            return List.of();
        }
        var available = new ArrayList<ScopeLifecycle.Scope>();
        for (var node : lane.pending) {
            if (!lane.drained.contains(node)) {
                available.add(node);
            }
        }
        var depth = Comparator.comparingInt(ScopeLifecycle.Scope::depth);
        if (flag == UIDirtyFlag.MEASURE || flag == UIDirtyFlag.ACCESSIBILITY) {
            depth = depth.reversed();
        }
        available.sort(depth.thenComparingLong(ScopeLifecycle.Scope::generation));
        int count = Math.min(available.size(), MAX_PER_FLAG - lane.used);
        var result = List.copyOf(available.subList(0, count));
        lane.pending.removeAll(result);
        lane.drained.addAll(result);
        lane.used += count;
        if (!lane.pending.isEmpty() && !lane.reported) {
            lane.reported = true;
            diagnostics.accept("Deferred " + flag + " work for " + lane.pending.iterator().next().id()
                    + " after reaching its frame limit");
        }
        return result;
    }

    void remove(ScopeLifecycle.Scope node) {
        boundaries.remove(node);
        for (Lane lane : lanes.values()) {
            lane.pending.remove(node);
            lane.drained.remove(node);
        }
    }

    private void propagateUp(ScopeLifecycle.Scope node, UIDirtyFlag flag) {
        var pending = lanes.get(flag).pending;
        for (var current = node; isNode(current); current = current.parent()) {
            pending.add(current);
            if (isBoundary(current, flag)) {
                return;
            }
        }
    }

    private void propagateDown(ScopeLifecycle.Scope node, UIDirtyFlag flag) {
        walk(node, flag, lanes.get(flag).pending);
    }

    private void paintSubtree(ScopeLifecycle.Scope node) {
        walk(node, UIDirtyFlag.TRANSFORM, lanes.get(UIDirtyFlag.PAINT).pending);
    }

    private void walk(ScopeLifecycle.Scope node, UIDirtyFlag boundary, Set<ScopeLifecycle.Scope> pending) {
        var queue = new ArrayDeque<ScopeLifecycle.Scope>();
        queue.add(node);
        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            if (!current.isOpen()) {
                continue;
            }
            pending.add(current);
            if (!isBoundary(current, boundary)) {
                queue.addAll(current.children());
            }
        }
    }

    private boolean isBoundary(ScopeLifecycle.Scope node, UIDirtyFlag flag) {
        return boundaries.getOrDefault(node, Set.of()).contains(flag);
    }

    private static void requireNode(ScopeLifecycle.Scope node) {
        if (!isNode(node)) {
            throw new IllegalArgumentException("Dirty work requires a UI tree or component scope");
        }
    }

    private static boolean isNode(ScopeLifecycle.Scope node) {
        return node != null && (node.kind() == ScopeKind.UI_TREE || node.kind() == ScopeKind.COMPONENT);
    }

    private static final class Lane {
        final Set<ScopeLifecycle.Scope> pending = new LinkedHashSet<>();
        final Set<ScopeLifecycle.Scope> drained = new LinkedHashSet<>();
        int used;
        boolean reported;
    }
}
