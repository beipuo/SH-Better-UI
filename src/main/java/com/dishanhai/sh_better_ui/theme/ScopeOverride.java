// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.token.ThemeToken;
import com.dishanhai.sh_better_ui.theme.token.TokenExpression;
import com.dishanhai.sh_better_ui.theme.token.TokenReference;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable scope configuration. Blocking a token returns it to the active global definition. */
public record ScopeOverride(boolean inheritParent, Map<ThemeToken<?>, TokenExpression<?>> values,
                            Set<ThemeToken<?>> blocked) {
    public static final ScopeOverride INHERIT = new ScopeOverride(true, Map.of(), Set.of());

    public ScopeOverride {
        values = Map.copyOf(values);
        blocked = Set.copyOf(blocked);
    }

    /** Creates a mutable draft from this configuration. */
    public Builder edit() { return new Builder(this); }

    /** A local configuration draft that can be committed as a single change. */
    public static final class Builder {
        private boolean inheritParent;
        private final Map<ThemeToken<?>, TokenExpression<?>> values;
        private final Set<ThemeToken<?>> blocked;

        private Builder(ScopeOverride from) {
            inheritParent = from.inheritParent;
            values = new LinkedHashMap<>(from.values);
            blocked = new LinkedHashSet<>(from.blocked);
        }

        public Builder inheritParent(boolean inherit) { inheritParent = inherit; return this; }

        public <T> Builder override(ThemeToken<T> token, T value) {
            return derive(token, new TokenExpression.Literal<>(token.type().cast(value)));
        }

        public <T> Builder derive(ThemeToken<T> token, TokenExpression<T> expression) {
            values.put(Objects.requireNonNull(token), Objects.requireNonNull(expression));
            return this;
        }

        public <T> Builder reference(ThemeToken<T> token, ThemeToken<T> target) {
            if (token.type() != target.type()) throw new IllegalArgumentException("Incompatible scope alias: " + token);
            return derive(token, new TokenReference<>(target));
        }

        public Builder clear(ThemeToken<?> token) { values.remove(token); return this; }

        public Builder block(ThemeToken<?> token) { blocked.add(Objects.requireNonNull(token)); return this; }

        public Builder allow(ThemeToken<?> token) { blocked.remove(token); return this; }

        public ScopeOverride build() { return new ScopeOverride(inheritParent, values, blocked); }
    }
}
