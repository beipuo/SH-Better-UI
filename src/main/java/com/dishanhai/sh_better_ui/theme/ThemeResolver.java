// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.style.StyleRule;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;
import com.dishanhai.sh_better_ui.theme.token.TokenDependencyGraph;
import com.dishanhai.sh_better_ui.theme.token.TokenExpression;
import com.dishanhai.sh_better_ui.theme.token.TokenLookup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Flattens inheritance and evaluates affected token expressions on the owner thread. */
public final class ThemeResolver {
    private static final int MAX_INHERITANCE = 32;
    private static final int MAX_TOKEN_DEPTH = 128;
    private long version;

    /** Resolves a base and ordered overlays. A previous snapshot can reuse unaffected expression results. */
    public ThemeSnapshot resolve(ThemeRegistry registry, Map<String, ThemeToken<?>> catalog,
                                 List<String> themeIds, ThemeSnapshot previous) {
        return evaluate(plan(registry, themeIds), catalog, previous);
    }

    Plan plan(ThemeRegistry registry, List<String> themeIds) {
        var definitions = new LinkedHashMap<ThemeToken<?>, Definition>();
        var diagnostics = new ArrayList<String>();
        var applied = new LinkedHashSet<String>();
        var styles = new ArrayList<StyleRule>();
        for (String id : themeIds) {
            var chain = new ArrayList<Theme>();
            var path = new LinkedHashSet<String>();
            String cursor = id;
            while (cursor != null) {
                if (!path.add(cursor)) throw new ThemeResolutionException("Theme inheritance cycle: "
                        + String.join(" -> ", path) + " -> " + cursor);
                if (path.size() > MAX_INHERITANCE) {
                    throw new ThemeResolutionException("Theme inheritance exceeds " + MAX_INHERITANCE + ": " + path);
                }
                Theme theme = registry.themes().get(cursor);
                if (theme == null) {
                    diagnostics.add("Missing theme " + cursor + " in " + String.join(" -> ", path));
                    break;
                }
                chain.add(theme);
                cursor = theme.parent();
            }
            Collections.reverse(chain);
            for (Theme theme : chain) {
                if (applied.add(theme.id())) {
                    theme.tokens().forEach((token, expr) ->
                            definitions.put(token, new Definition(expr, theme.id())));
                    styles.addAll(theme.styles());
                }
            }
        }
        return new Plan(List.copyOf(themeIds), definitions, List.copyOf(diagnostics), List.copyOf(styles));
    }

    ThemeSnapshot evaluate(Plan plan, Map<String, ThemeToken<?>> catalog, ThemeSnapshot previous) {
        if (previous != null && previous.plan().equals(plan)
                && previous.values().keySet().equals(new LinkedHashSet<>(catalog.values()))) {
            return previous;
        }
        for (var token : plan.definitions.keySet()) requireRegistered(catalog, token);
        for (var style : plan.styles) {
            for (var token : style.values().keySet()) requireRegistered(catalog, token);
        }
        Set<ThemeToken<?>> changed = new LinkedHashSet<>();
        if (previous != null) {
            var old = previous.plan().definitions;
            old.forEach((key, definition) -> {
                if (!definition.equals(plan.definitions.get(key))) changed.add(key);
            });
            plan.definitions.forEach((key, definition) -> {
                if (!definition.equals(old.get(key))) changed.add(key);
            });
        }
        var dirty = previous == null ? changed : previous.dependencies().affected(changed);
        var evaluation = new Evaluation(plan, catalog, previous, dirty);
        catalog.values().forEach(evaluation::resolve);
        return snapshot(plan, evaluation.values, new TokenDependencyGraph(evaluation.reads), evaluation.evaluated);
    }

    ThemeSnapshot snapshot(Plan plan, Map<ThemeToken<?>, Object> values, TokenDependencyGraph graph, int evaluated) {
        return new ThemeSnapshot(++version, plan, values, graph, evaluated);
    }

    static void requireRegistered(Map<String, ThemeToken<?>> catalog, ThemeToken<?> token) {
        if (!token.equals(catalog.get(token.id()))) {
            throw new ThemeResolutionException("Unknown or conflicting token definition: " + token.id());
        }
    }

    record Definition(TokenExpression<?> expression, String source) {}

    record Plan(List<String> themeIds, Map<ThemeToken<?>, Definition> definitions, List<String> diagnostics,
                List<StyleRule> styles) {
        Plan {
            definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
        }

        Plan overlay(Map<ThemeToken<?>, Definition> overrides) {
            if (overrides.isEmpty()) return this;
            var merged = new LinkedHashMap<>(definitions);
            merged.putAll(overrides);
            return new Plan(themeIds, merged, diagnostics, styles);
        }
    }

    private static final class Evaluation implements TokenLookup {
        private final Plan plan;
        private final Map<String, ThemeToken<?>> catalog;
        private final ThemeSnapshot previous;
        private final Set<ThemeToken<?>> dirty;
        private final Map<ThemeToken<?>, Object> values = new LinkedHashMap<>();
        private final Map<ThemeToken<?>, Set<ThemeToken<?>>> reads = new HashMap<>();
        private final LinkedHashSet<ThemeToken<?>> stack = new LinkedHashSet<>();
        private ThemeToken<?> evaluating;
        private int evaluated;

        Evaluation(Plan plan, Map<String, ThemeToken<?>> catalog, ThemeSnapshot previous, Set<ThemeToken<?>> dirty) {
            this.plan = plan;
            this.catalog = catalog;
            this.previous = previous;
            this.dirty = dirty;
        }

        @Override
        public <T> T resolve(ThemeToken<T> token) {
            requireRegistered(catalog, token);
            if (evaluating != null) reads.computeIfAbsent(evaluating, ignored -> new LinkedHashSet<>()).add(token);
            Object cached = values.get(token);
            if (cached != null) return token.type().valueClass().cast(cached);
            if (previous != null && !dirty.contains(token) && previous.values().containsKey(token)) {
                T reused = previous.resolve(token);
                values.put(token, reused);
                var dependencies = previous.dependencies().dependencies(token);
                if (!dependencies.isEmpty()) reads.put(token, dependencies);
                return reused;
            }
            if (stack.contains(token) || stack.size() >= MAX_TOKEN_DEPTH) {
                String path = String.join(" -> ", stack.stream().map(ThemeToken::id).toList()) + " -> " + token.id();
                throw new ThemeResolutionException((stack.contains(token) ? "Token dependency cycle: "
                        : "Token dependency depth exceeds " + MAX_TOKEN_DEPTH + ": ") + path);
            }
            stack.add(token);
            var caller = evaluating;
            evaluating = token;
            try {
                var definition = plan.definitions.get(token);
                Object raw = definition == null ? token.defaultValue() : definition.expression.evaluate(this);
                T value = token.type().cast(raw);
                values.put(token, value);
                evaluated++;
                return value;
            } catch (ThemeResolutionException failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw new ThemeResolutionException("Token " + token + " from " + plan.definitions.get(token)
                        + " failed along " + stack + ": " + failure.getMessage(), failure);
            } finally {
                stack.remove(token);
                evaluating = caller;
            }
        }
    }
}
