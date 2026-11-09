// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.accessibility.AccessibilityTheme;
import com.dishanhai.sh_better_ui.theme.accessibility.ThemePreferences;
import com.dishanhai.sh_better_ui.theme.event.Subscription;
import com.dishanhai.sh_better_ui.theme.style.ComputedStyle;
import com.dishanhai.sh_better_ui.theme.style.StyleInvalidation;
import com.dishanhai.sh_better_ui.theme.style.StyleResolver;
import com.dishanhai.sh_better_ui.theme.style.StyleState;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;
import com.dishanhai.sh_better_ui.theme.token.TokenLookup;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** An owner-thread node in the theme tree. Closing it closes all descendants and their bindings. */
public final class ThemeScope implements TokenLookup, AutoCloseable {
    private static final int STYLE_CACHE_SIZE = 64;
    private static final List<Set<StyleInvalidation>> INVALIDATION_TYPES = invalidationTypes();
    final ScopeManager manager;
    final Set<ThemeScope> children = new LinkedHashSet<>();
    ThemeScope parent;
    ScopeManager.State state;
    ScopeOverride overrides = ScopeOverride.INHERIT;
    private final String name;
    private final Set<InvalidationOwner> owners = new LinkedHashSet<>();
    private final Map<ThemeToken<?>, Set<BoundValue<?>>> subscriptions = new LinkedHashMap<>();
    private final Map<StyleKey, ComputedStyle> styles = new LinkedHashMap<>(16, 0.75f, true);
    private final Set<Subscription> subscriptionsOnClose = new LinkedHashSet<>();
    private Map<ThemeToken<?>, Object> forced;
    private boolean closed;
    private long bindingRevision;

    ThemeScope(ScopeManager manager, String name, ThemeScope parent, ScopeManager.State state) {
        this.manager = manager;
        this.name = Objects.requireNonNull(name);
        this.parent = parent;
        this.state = state;
    }

    public String name() { return name; }
    public ThemeScope parent() { return parent; }
    public boolean isClosed() { return closed; }
    public ScopeOverride overrides() { return overrides; }

    /** User preferences shared across this scope tree. */
    public ThemePreferences preferences() { requireOpen(); return manager.preferences(); }

    /** Frozen values can outlive the scope and be inspected off the owner thread. */
    public ThemeSnapshot snapshot() {
        requireOpen();
        return state.displayed();
    }

    @Override
    public <T> T resolve(ThemeToken<T> token) {
        var snapshot = snapshot();
        var value = forced().get(token);
        return value == null ? snapshot.resolve(token) : token.type().valueClass().cast(value);
    }

    private Map<ThemeToken<?>, Object> forced() {
        if (forced == null) forced = AccessibilityTheme.forced(state.displayed(), manager.preferences());
        return forced;
    }

    Subscription manage(Subscription subscription) {
        requireOpen();
        subscriptionsOnClose.add(subscription);
        return () -> {
            subscription.close();
            subscriptionsOnClose.remove(subscription);
        };
    }

    /** Attaches a screen, container, component or widget scope to this node. */
    public ThemeScope child(String name) {
        requireOpen();
        manager.checkMutation();
        int depth = 0;
        for (ThemeScope node = this; node != null; node = node.parent) depth++;
        if (depth >= ScopeManager.MAX_DEPTH) throw new IllegalArgumentException("Theme scope depth limit");
        var child = new ThemeScope(manager, name, this, state);
        children.add(child);
        return child;
    }

    /** Applies local edits atomically to this branch. */
    public void configure(Consumer<ScopeOverride.Builder> edits) {
        requireOpen();
        manager.checkMutation();
        var builder = overrides.edit();
        edits.accept(builder);
        requireOpen();
        manager.edit(this, builder.build());
    }

    /** Moves this scope and its bindings without detaching siblings. */
    public void reparent(ThemeScope newParent) {
        requireOpen();
        newParent.requireOpen();
        manager.checkMutation();
        manager.reparent(this, newParent);
    }

    /** Resolves and caches the winning style for a role and state combination. */
    public ComputedStyle style(String role, Set<StyleState> states) {
        return style(new StyleKey(Objects.requireNonNull(role), Set.copyOf(states)));
    }

    private ComputedStyle style(StyleKey key) {
        requireOpen();
        var style = styles.get(key);
        manager.styleRead(style != null);
        if (style == null) {
            style = StyleResolver.resolve(state.displayed(), key.role(), key.states(), forced());
            styles.put(key, style);
            if (styles.size() > STYLE_CACHE_SIZE) styles.remove(styles.keySet().iterator().next());
        }
        return style;
    }

    /** One invalidation owner per widget, shared by all its bound properties. */
    public InvalidationOwner invalidationOwner(Consumer<Set<StyleInvalidation>> invalidation) {
        requireOpen();
        manager.checkMutation();
        var owner = new InvalidationOwner(Objects.requireNonNull(invalidation));
        owners.add(owner);
        return owner;
    }

    /** Subscribes a property to token and style changes within this scope. */
    public <T> BoundValue<T> bind(ThemeBinding<T> binding, InvalidationOwner owner, StyleInvalidation first,
                                  StyleInvalidation... more) {
        return bind("*", Set.of(), binding, owner, first, more);
    }

    /** Subscribes a property with an initial widget role and state. */
    public <T> BoundValue<T> bind(String role, Set<StyleState> states, ThemeBinding<T> binding,
                                  InvalidationOwner owner, StyleInvalidation first, StyleInvalidation... more) {
        requireOpen();
        manager.checkMutation();
        if (!owners.contains(owner) || owner.isClosed()) throw new IllegalArgumentException("Invalid binding owner");
        binding.dependencies().forEach(manager::requireRegistered);
        var value = new BoundValue<>(role, states, binding, owner, EnumSet.of(first, more));
        owner.values.add(value);
        binding.dependencies().forEach(token ->
                subscriptions.computeIfAbsent(token, ignored -> new LinkedHashSet<>()).add(value));
        return value;
    }

    void refreshBindings(Set<ThemeToken<?>> changed, boolean styleRulesChanged) {
        styles.clear();
        forced = null;
        if (styleRulesChanged) {
            for (var owner : owners) {
                for (var value : owner.values) value.refreshSafely();
            }
        } else if (!changed.isEmpty() && !subscriptions.isEmpty()) {
            long revision = ++bindingRevision;
            for (var token : changed) {
                var dependents = subscriptions.get(token);
                if (dependents == null) continue;
                for (var value : dependents) {
                    if (value.refreshRevision == revision) continue;
                    value.refreshRevision = revision;
                    value.refreshSafely();
                }
            }
        }
    }

    int subtreeDepth() {
        int deepestChild = 0;
        for (var child : children) deepestChild = Math.max(deepestChild, child.subtreeDepth());
        return 1 + deepestChild;
    }

    private void requireOpen() {
        manager.checkThread();
        if (closed) throw new IllegalStateException("Closed theme scope: " + name);
    }

    /** Releases the entire branch and discards its pending invalidations. */
    @Override
    public void close() {
        if (this == manager.root()) throw new IllegalStateException("Close the theme manager to release its root");
        closeBranch();
    }

    void closeBranch() {
        manager.checkMutation();
        if (closed) return;
        closed = true;
        for (var child : List.copyOf(children)) child.closeBranch();
        for (var owner : List.copyOf(owners)) owner.close();
        subscriptionsOnClose.forEach(Subscription::close);
        subscriptionsOnClose.clear();
        if (parent != null) parent.children.remove(this);
        parent = null;
        styles.clear();
    }

    /** A widget lifetime and its callback for frame-batched invalidations. */
    public final class InvalidationOwner implements AutoCloseable {
        private final Set<BoundValue<?>> values = new LinkedHashSet<>();
        private Consumer<Set<StyleInvalidation>> callback;
        int pendingMask;
        int dispatchMask;

        private InvalidationOwner(Consumer<Set<StyleInvalidation>> callback) { this.callback = callback; }

        public boolean isClosed() { return callback == null; }

        void deliver() { if (callback != null) callback.accept(INVALIDATION_TYPES.get(dispatchMask)); }

        @Override
        public void close() {
            manager.checkMutation();
            if (callback == null) return;
            callback = null;
            manager.remove(this);
            for (var value : List.copyOf(values)) value.close();
            owners.remove(this);
        }
    }

    /** Cached property value and its explicit, detachable subscription. */
    public final class BoundValue<T> implements AutoCloseable {
        private final ThemeBinding<T> binding;
        private final InvalidationOwner owner;
        private final int invalidationMask;
        private StyleKey styleKey;
        private T value;
        private boolean closed;
        private long refreshRevision;

        private BoundValue(String role, Set<StyleState> states, ThemeBinding<T> binding,
                           InvalidationOwner owner, EnumSet<StyleInvalidation> types) {
            styleKey = new StyleKey(Objects.requireNonNull(role), Set.copyOf(states));
            this.binding = binding;
            this.owner = owner;
            int mask = 0;
            for (var type : types) mask |= 1 << type.ordinal();
            invalidationMask = mask;
            value = binding.read(style(styleKey), styleKey.states());
        }

        public T value() {
            requireOpen();
            if (closed) throw new IllegalStateException("Closed theme binding");
            return value;
        }

        public boolean isClosed() { return closed; }

        /** Updates the widget state while preserving the property subscription. */
        public void state(Set<StyleState> next) {
            requireOpen();
            manager.checkMutation();
            if (closed || styleKey.states().equals(next)) return;
            styleKey = new StyleKey(styleKey.role(), Set.copyOf(next));
            refreshSafely();
        }

        private void refreshSafely() {
            try {
                T next = binding.read(style(styleKey), styleKey.states());
                if (!value.equals(next)) {
                    value = next;
                    manager.enqueue(owner, invalidationMask);
                }
            } catch (RuntimeException failure) {
                manager.report(failure);
            }
        }

        @Override
        public void close() {
            manager.checkMutation();
            if (closed) return;
            closed = true;
            owner.values.remove(this);
            for (var token : binding.dependencies()) {
                var values = subscriptions.get(token);
                if (values != null && values.remove(this) && values.isEmpty()) subscriptions.remove(token);
            }
        }
    }

    private record StyleKey(String role, Set<StyleState> states) {}

    private static List<Set<StyleInvalidation>> invalidationTypes() {
        var values = StyleInvalidation.values();
        var sets = new ArrayList<Set<StyleInvalidation>>(1 << values.length);
        for (int mask = 0; mask < (1 << values.length); mask++) {
            var set = EnumSet.noneOf(StyleInvalidation.class);
            for (var value : values) if ((mask & (1 << value.ordinal())) != 0) set.add(value);
            sets.add(Set.copyOf(set));
        }
        return List.copyOf(sets);
    }
}
