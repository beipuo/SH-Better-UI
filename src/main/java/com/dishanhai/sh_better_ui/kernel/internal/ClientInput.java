// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.InteractionApi;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;
import com.dishanhai.sh_better_ui.kernel.api.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

final class ClientInput implements InputService {
    private static final EventResult PASS = new EventResult(false, false, false, false, false, false);
    private static final int TARGET_LIMIT = 4096;
    private final KernelHost host;
    private final ClientScreens screens;
    private final Map<ScopeHandle, List<Target>> byScreen = new HashMap<>();
    private long sequence;

    ClientInput(KernelHost host, ClientScreens screens) {
        this.host = host;
        this.screens = screens;
    }

    @Override public InteractionApi interactions() { return ShBetterUiApi.interactions(); }

    @Override
    public boolean capture(ModuleContext module, ScopeHandle node, UiId claim, int button,
                           UiPointerCapture.Handler handler) {
        var owner = screens.ownerOf(host.ownedNode(module, node));
        if (owner == null) throw new IllegalStateException("Pointer capture requires a mounted native Screen");
        return UiPointerCapture.startScoped(owner, button, handler, module, node, claim);
    }

    boolean hasTargets(ScopeHandle screen) { return byScreen.containsKey(screen); }

    @Override
    public Subscription target(ModuleContext module, ScopeHandle node, int layer, Predicate<NativeInput> hit) {
        Objects.requireNonNull(hit);
        return addTarget(module, node, layer, selection -> hit.test(selection.input), ignored -> {});
    }

    <T> Subscription pickedTarget(ModuleContext module, ScopeHandle node, int layer, Function<NativeInput, T> picker,
                                  T expected, Consumer<Subscription> detached) {
        return addTarget(module, node, layer, selection -> selection.pick(picker) == expected, detached);
    }

    private Subscription addTarget(ModuleContext module, ScopeHandle node, int layer, Predicate<HitSelection> hit,
                                   Consumer<Subscription> detached) {
        var target = host.ownedNode(module, node);
        var screen = target.ancestor(ScopeKind.SCREEN);
        if (screen == null) throw new IllegalArgumentException("Native input targets require a Screen ancestor");
        var candidates = new ArrayList<>(byScreen.getOrDefault(screen, List.of()));
        if (candidates.size() >= TARGET_LIMIT) throw new IllegalStateException("UI input target limit reached");
        var entry = new Target(target, layer, ++sequence, hit);
        entry.lifetime = host.onScopeClose(target, () -> {
            remove(screen, entry);
            detached.accept(entry.lifetime);
        });
        candidates.add(entry);
        candidates.sort(Comparator.comparingInt((Target value) -> value.layer)
                .thenComparingLong(value -> value.order).reversed());
        byScreen.put(screen, List.copyOf(candidates));
        return entry.lifetime;
    }

    EventResult dispatch(ScopeHandle screen, NativeInput input) {
        var targets = byScreen.get(screen);
        if (targets == null) return PASS;
        return host.nativeInput(() -> {
            var selection = new HitSelection(input);
            for (var target : targets) {
                if (!target.lifetime.isActive()) continue;
                boolean match;
                try {
                    match = target.hit.test(selection);
                } catch (RuntimeException | LinkageError | AssertionError failure) {
                    host.report("UI input hit test for " + target.node.id(), failure);
                    target.lifetime.close();
                    continue;
                }
                if (match && target.lifetime.isActive()) {
                    var type = input instanceof NativeInput.Pointer pointer && pointer.action() == NativeInput.PointerAction.MOVE
                            ? POINTER_MOVED : EVENT;
                    return host.routeInput(type, screen, target.node, input);
                }
            }
            return PASS;
        });
    }

    private void remove(ScopeHandle screen, Target target) {
        var remaining = new ArrayList<>(byScreen.get(screen));
        remaining.remove(target);
        if (remaining.isEmpty()) byScreen.remove(screen);
        else byScreen.put(screen, List.copyOf(remaining));
    }

    /** One selection per shared picker, including misses, for this dispatch only. */
    private static final class HitSelection {
        final NativeInput input;
        private Map<Function<NativeInput, ?>, Object> choices;

        HitSelection(NativeInput input) { this.input = input; }

        Object pick(Function<NativeInput, ?> picker) {
            if (choices == null) choices = new IdentityHashMap<>();
            if (!choices.containsKey(picker)) {
                // Cache a miss if the picker fails and the dispatcher continues to another target.
                choices.put(picker, null);
                choices.put(picker, picker.apply(input));
            }
            return choices.get(picker);
        }
    }

    private static final class Target {
        final ScopeHandle node;
        final int layer;
        final long order;
        final Predicate<HitSelection> hit;
        Subscription lifetime;

        Target(ScopeHandle node, int layer, long order, Predicate<HitSelection> hit) {
            this.node = node;
            this.layer = layer;
            this.order = order;
            this.hit = hit;
        }
    }
}
