// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.style.StyleState;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;
import com.dishanhai.sh_better_ui.theme.token.TokenLookup;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/** A typed property calculation with explicit token dependencies, evaluated against the current scope. */
public final class ThemeBinding<T> {
    private final Set<ThemeToken<?>> dependencies;
    private final ThemeToken<T> direct;
    private final BiFunction<TokenLookup, Set<StyleState>, T> function;

    private ThemeBinding(Set<ThemeToken<?>> dependencies, BiFunction<TokenLookup, Set<StyleState>, T> function) {
        this.dependencies = Set.copyOf(dependencies);
        this.function = Objects.requireNonNull(function);
        direct = null;
    }

    private ThemeBinding(ThemeToken<T> token) {
        direct = Objects.requireNonNull(token);
        dependencies = Set.of(token);
        function = null;
    }

    /** Binds a property to a single token, including its current theme transition value. */
    public static <T> ThemeBinding<T> of(ThemeToken<T> token) {
        return new ThemeBinding<>(token);
    }

    /** Combines a declared set of tokens and optionally the widget's state. */
    public static <T> ThemeBinding<T> derive(Set<ThemeToken<?>> dependencies,
                                             BiFunction<TokenLookup, Set<StyleState>, T> function) {
        return new ThemeBinding<>(dependencies, function);
    }

    /** Combines two bindings, retaining both dependency sets for invalidation. */
    public static <A, B, T> ThemeBinding<T> combine(ThemeBinding<A> a, ThemeBinding<B> b,
                                                   BiFunction<A, B, T> function) {
        Objects.requireNonNull(function);
        return derive(union(a, b), (values, state) -> function.apply(a.read(values, state), b.read(values, state)));
    }

    /** Selects between two bindings using the widget's current state. */
    public static <T> ThemeBinding<T> when(Predicate<Set<StyleState>> condition, ThemeBinding<T> yes,
                                           ThemeBinding<T> no) {
        Objects.requireNonNull(condition);
        return derive(union(yes, no), (values, state) -> (condition.test(state) ? yes : no).read(values, state));
    }

    private static Set<ThemeToken<?>> union(ThemeBinding<?> a, ThemeBinding<?> b) {
        var all = new LinkedHashSet<>(a.dependencies);
        all.addAll(b.dependencies);
        return all;
    }

    public Set<ThemeToken<?>> dependencies() { return dependencies; }

    /** Reads the binding without subscribing. An undeclared read fails with its token ID. */
    public T read(TokenLookup values, Set<StyleState> state) {
        if (direct != null) return values.resolve(direct);
        var checked = new TokenLookup() {
            @Override
            public <V> V resolve(ThemeToken<V> token) {
                if (!dependencies.contains(token)) throw new IllegalArgumentException("Undeclared binding token: " + token);
                return values.resolve(token);
            }
        };
        return Objects.requireNonNull(function.apply(checked, state), "Binding value");
    }
}
