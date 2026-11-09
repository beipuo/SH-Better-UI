// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.style.StyleRule;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;
import com.dishanhai.sh_better_ui.theme.token.TokenExpression;
import com.dishanhai.sh_better_ui.theme.token.TokenReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable theme definition. A child only declares the tokens it wants to replace. */
public final class Theme {
    private final String id;
    private final String nameKey;
    private final String parent;
    private final String source;
    private final Map<String, String> metadata;
    private final Map<ThemeToken<?>, TokenExpression<?>> tokens;
    private final List<StyleRule> styles;

    private Theme(Builder builder) {
        id = builder.id;
        nameKey = builder.nameKey;
        parent = builder.parent;
        source = builder.source;
        metadata = Map.copyOf(builder.metadata);
        tokens = Collections.unmodifiableMap(new LinkedHashMap<>(builder.tokens));
        styles = List.copyOf(builder.styles);
    }

    /** Creates a definition with a resource-compatible namespaced identifier. */
    public static Builder builder(String id) { return new Builder(ThemeId.require(id)); }

    public String id() { return id; }

    /** Translation key for a client to display. Defaults to the theme ID. */
    public String nameKey() { return nameKey; }

    /** Parent ID, or null when the theme has no parent. */
    public String parent() { return parent; }

    /** Origin used in diagnostics, such as a mod or resource pack. */
    public String source() { return source; }

    /** Descriptive values including any consumer-supplied compatibility metadata. */
    public Map<String, String> metadata() { return metadata; }

    /** Immutable, local token definitions in declaration order. */
    public Map<ThemeToken<?>, TokenExpression<?>> tokens() { return tokens; }

    /** Local style rules in declaration order. */
    public List<StyleRule> styles() { return styles; }

    /** Builds a theme definition independently of the registry. */
    public static final class Builder {
        private final String id;
        private String nameKey;
        private String parent;
        private String source = "java";
        private final Map<String, String> metadata = new LinkedHashMap<>();
        private final Map<ThemeToken<?>, TokenExpression<?>> tokens = new LinkedHashMap<>();
        private final List<StyleRule> styles = new ArrayList<>();

        private Builder(String id) {
            this.id = id;
            nameKey = id;
        }

        public Builder parent(String id) {
            parent = ThemeId.require(id);
            return this;
        }

        public Builder nameKey(String key) {
            nameKey = Objects.requireNonNull(key);
            return this;
        }

        public Builder source(String source) {
            this.source = Objects.requireNonNull(source);
            return this;
        }

        public Builder metadata(String key, String value) {
            metadata.put(Objects.requireNonNull(key), Objects.requireNonNull(value));
            return this;
        }

        /** Assigns an immutable literal validated at construction. */
        public <T> Builder token(ThemeToken<T> token, T value) {
            return derive(token, new TokenExpression.Literal<>(token.type().cast(value)));
        }

        /** Aliases another registered token of the same semantic type. */
        public <T> Builder reference(ThemeToken<T> token, ThemeToken<T> target) {
            if (token.type() != target.type()) {
                throw new IllegalArgumentException("Token reference type mismatch: " + token + " -> " + target);
            }
            return derive(token, new TokenReference<>(target));
        }

        /** Sets a pure Java expression. Its actual reads are traced at resolution time. */
        public <T> Builder derive(ThemeToken<T> token, TokenExpression<T> expression) {
            tokens.put(Objects.requireNonNull(token), Objects.requireNonNull(expression));
            return this;
        }

        public Builder style(StyleRule rule) {
            styles.add(Objects.requireNonNull(rule));
            return this;
        }

        public Theme build() { return new Theme(this); }
    }
}
