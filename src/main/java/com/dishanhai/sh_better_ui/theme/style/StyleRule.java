// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.style;

import com.dishanhai.sh_better_ui.theme.token.ThemeToken;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable declarations for a widget role and state. */
public record StyleRule(StyleSelector selector, Map<ThemeToken<?>, Object> values) {
    public StyleRule {
        Objects.requireNonNull(selector);
        values.forEach((token, value) -> token.type().cast(value));
        values = Map.copyOf(values);
    }

    public static Builder when(StyleSelector selector) { return new Builder(selector); }

    /** Adds typed declarations to this rule. */
    public static final class Builder {
        private final StyleSelector selector;
        private final Map<ThemeToken<?>, Object> values = new LinkedHashMap<>();

        private Builder(StyleSelector selector) { this.selector = Objects.requireNonNull(selector); }

        public <T> Builder set(ThemeToken<T> token, T value) {
            values.put(token, token.type().cast(value));
            return this;
        }

        public StyleRule build() { return new StyleRule(selector, values); }
    }
}
