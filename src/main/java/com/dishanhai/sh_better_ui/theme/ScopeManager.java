// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.accessibility.ThemePreferences;
import com.dishanhai.sh_better_ui.theme.style.StyleInvalidation;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;
import com.dishanhai.sh_better_ui.theme.token.TokenExpression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns the scope tree and its frame-batched property invalidations. */
final class ScopeManager {
    static final int MAX_DEPTH = 64;
    private final Thread owner = Thread.currentThread();
    private final ThemeResolver resolver;
    private final Supplier<Map<String, ThemeToken<?>>> catalog;
    private final Consumer<RuntimeException> onError;
    private final Consumer<ThemeScope> onScopeChanged;
    private final ThemeScope root;
    private ArrayList<ThemeScope.InvalidationOwner> pending = new ArrayList<>();
    private ArrayList<ThemeScope.InvalidationOwner> batch = new ArrayList<>();
    private int lastInvalidatedOwners;
    private long styleHits;
    private long styleMisses;
    private long resolutions;
    private long evaluatedTokens;
    private boolean committing;
    private boolean flushing;
    private ThemePreferences preferences = ThemePreferences.DEFAULT;

    ScopeManager(ThemeResolver resolver, Supplier<Map<String, ThemeToken<?>>> catalog, ThemeSnapshot initial,
                 Consumer<RuntimeException> onError) {
        this(resolver, catalog, initial, onError, ignored -> {});
    }

    ScopeManager(ThemeResolver resolver, Supplier<Map<String, ThemeToken<?>>> catalog, ThemeSnapshot initial,
                 Consumer<RuntimeException> onError, Consumer<ThemeScope> onScopeChanged) {
        this.resolver = resolver;
        this.catalog = catalog;
        this.onError = onError;
        this.onScopeChanged = onScopeChanged;
        root = new ThemeScope(this, "global", null, new State(Map.of(), initial, initial));
    }

    ThemeScope root() { return root; }

    ThemePreferences preferences() { return preferences; }

    void preferences(ThemePreferences next) {
        var before = preferences;
        preferences = next;
        if (before.highContrast() != next.highContrast() || before.reducedMotion() != next.reducedMotion()
                || before.lowPerformance() != next.lowPerformance()) {
            mutate(() -> refreshPreferences(root));
        }
    }

    private void refreshPreferences(ThemeScope scope) {
        scope.refreshBindings(Set.of(), true);
        scope.children.forEach(this::refreshPreferences);
    }

    void checkThread() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Theme scope requires its owner thread");
    }

    void checkMutation() {
        checkThread();
        if (committing) throw new IllegalStateException("Cannot mutate theme scopes during resolution");
    }

    void requireRegistered(ThemeToken<?> token) { ThemeResolver.requireRegistered(catalog.get(), token); }

    void edit(ThemeScope scope, ScopeOverride override) {
        if (scope == root) throw new IllegalStateException("Register a theme to change the global scope");
        if (override.equals(scope.overrides)) return;
        override.blocked().forEach(this::requireRegistered);
        mutate(() -> {
            var staged = prepare(scope, override, scope.parent.state, root.state, false);
            scope.overrides = override;
            commit(staged);
        });
        onScopeChanged.accept(scope);
    }

    void reparent(ThemeScope scope, ThemeScope parent) {
        if (scope == root || parent.manager != this) throw new IllegalArgumentException("Scopes belong to different trees");
        if (scope.parent == parent) return;
        int depth = 0;
        for (ThemeScope node = parent; node != null; node = node.parent) {
            if (node == scope) throw new IllegalArgumentException("Cannot inherit from a descendant: " + scope.name());
            depth++;
        }
        if (depth + scope.subtreeDepth() > MAX_DEPTH) throw new IllegalArgumentException("Theme scope depth limit");
        mutate(() -> {
            var staged = prepare(scope, scope.overrides, parent.state, root.state, false);
            scope.parent.children.remove(scope);
            scope.parent = parent;
            parent.children.add(scope);
            commit(staged);
        });
        onScopeChanged.accept(scope);
    }

    /** Checks the full tree before an incoming target is accepted. */
    void validate(ThemeSnapshot target) {
        if (root.state.evaluated == target) return;
        mutate(() -> prepareRoot(target, target, false));
    }

    void publish(ThemeSnapshot target, ThemeSnapshot displayed) {
        if (root.state.evaluated == target && root.state.displayed == displayed) return;
        mutate(() -> commit(prepareRoot(target, displayed, true)));
    }

    private List<Change> prepareRoot(ThemeSnapshot target, ThemeSnapshot displayed, boolean recoverAnimation) {
        var global = new State(Map.of(), target, displayed);
        var staged = new ArrayList<Change>();
        staged.add(new Change(root, global));
        for (ThemeScope child : root.children) {
            stage(staged, child, child.overrides, global, global, recoverAnimation);
        }
        return staged;
    }

    private List<Change> prepare(ThemeScope scope, ScopeOverride override, State parent, State global,
                                 boolean recoverAnimation) {
        var staged = new ArrayList<Change>();
        stage(staged, scope, override, parent, global, recoverAnimation);
        return staged;
    }

    private void stage(List<Change> staged, ThemeScope scope, ScopeOverride override, State parent,
                       State global, boolean recoverAnimation) {
        State next = resolve(scope, override, parent, global, recoverAnimation);
        staged.add(new Change(scope, next));
        for (ThemeScope child : scope.children) {
            stage(staged, child, child.overrides, next, global, recoverAnimation);
        }
    }

    private State resolve(ThemeScope scope, ScopeOverride local, State parent, State global, boolean recoverAnimation) {
        if (local.equals(ScopeOverride.INHERIT)) return parent;
        var effective = new LinkedHashMap<ThemeToken<?>, ThemeResolver.Definition>();
        if (local.inheritParent()) effective.putAll(parent.effective);
        local.blocked().forEach(effective::remove);
        local.values().forEach((token, expression) -> effective.put(token,
                new ThemeResolver.Definition(expression, "scope:" + scope.name())));
        if (effective.isEmpty()) return global;
        if (effective.equals(parent.effective)) return parent;

        var previous = scope.state;
        var evaluated = evaluate(global.evaluated.plan().overlay(effective), previous.evaluated);
        if (global.displayed == global.evaluated) return new State(Map.copyOf(effective), evaluated, evaluated);

        // Unmodified tokens use their animated global values. Local dependent expressions still
        // read those values while retaining their own scope overrides.
        var affected = evaluated.dependencies().affected(effective.keySet());
        var animated = new LinkedHashMap<>(effective);
        global.displayed.values().forEach((token, value) -> {
            if (!affected.contains(token) && !value.equals(global.evaluated.values().get(token))) {
                animated.put(token, new ThemeResolver.Definition(new TokenExpression.Literal<>(value),
                        global.displayed.source(token)));
            }
        });
        ThemeSnapshot displayed;
        try {
            displayed = evaluate(global.displayed.plan().overlay(animated), previous.displayed);
        } catch (ThemeResolutionException failure) {
            if (!recoverAnimation) throw failure;
            onError.accept(failure);
            displayed = evaluated;
        }
        return new State(Map.copyOf(effective), evaluated, displayed);
    }

    private ThemeSnapshot evaluate(ThemeResolver.Plan plan, ThemeSnapshot previous) {
        var next = resolver.evaluate(plan, catalog.get(), previous);
        if (next != previous) {
            resolutions++;
            evaluatedTokens += next.evaluatedTokens();
        }
        return next;
    }

    private void commit(List<Change> staged) {
        // Install the entire tree before any binding calculation observes a sibling.
        var previous = new ArrayList<ThemeSnapshot>(staged.size());
        for (var change : staged) {
            previous.add(change.scope.state.displayed);
            change.scope.state = change.state;
        }
        var deltas = new java.util.IdentityHashMap<ThemeSnapshot, Map<ThemeSnapshot, Set<ThemeToken<?>>>>();
        for (int i = 0; i < staged.size(); i++) {
            var change = staged.get(i);
            var before = previous.get(i);
            var after = change.state.displayed;
            if (before == after) continue;
            var tokens = deltas.computeIfAbsent(before, ignored -> new java.util.IdentityHashMap<>())
                    .computeIfAbsent(after, ignored -> after.changedFrom(before));
            change.scope.refreshBindings(tokens, !before.styles().equals(after.styles())
                    || preferences.highContrast() && tokens.contains(com.dishanhai.sh_better_ui.theme.token.ThemeTokens.LIGHT));
        }
    }

    private void mutate(Runnable action) {
        checkMutation();
        committing = true;
        try {
            action.run();
        } finally {
            committing = false;
        }
    }

    void enqueue(ThemeScope.InvalidationOwner owner, int mask) {
        if (owner.pendingMask == 0) pending.add(owner);
        owner.pendingMask |= mask;
    }

    void remove(ThemeScope.InvalidationOwner owner) {
        if (owner.pendingMask != 0) pending.remove(owner);
        owner.pendingMask = 0;
    }

    /** Deliver one coalesced callback per affected owner. Reentrant changes stay pending for the next frame. */
    void flush() {
        checkMutation();
        if (flushing) return;
        lastInvalidatedOwners = 0;
        if (pending.isEmpty()) return;
        var previousBatch = batch;
        batch = pending;
        pending = previousBatch;
        for (var owner : batch) {
            owner.dispatchMask = owner.pendingMask;
            owner.pendingMask = 0;
        }
        flushing = true;
        try {
            for (var owner : batch) {
                if (owner.isClosed()) continue;
                lastInvalidatedOwners++;
                try {
                    owner.deliver();
                } catch (RuntimeException failure) {
                    report(failure);
                }
            }
        } finally {
            batch.clear();
            flushing = false;
        }
    }

    void report(RuntimeException failure) { onError.accept(failure); }

    void styleRead(boolean hit) {
        if (hit) styleHits++;
        else styleMisses++;
    }

    int lastInvalidatedOwners() { return lastInvalidatedOwners; }
    long styleHits() { return styleHits; }
    long styleMisses() { return styleMisses; }
    long resolutions() { return resolutions; }
    long evaluatedTokens() { return evaluatedTokens; }

    record State(Map<ThemeToken<?>, ThemeResolver.Definition> effective,
                 ThemeSnapshot evaluated, ThemeSnapshot displayed) {}

    private record Change(ThemeScope scope, State state) {}
}
