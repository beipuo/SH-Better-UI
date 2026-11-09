// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.style.StyleRule;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;
import com.dishanhai.sh_better_ui.theme.token.TokenDependencyGraph;
import com.dishanhai.sh_better_ui.theme.token.TokenLookup;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable resolved snapshot. Reading a token never invokes a theme expression. */
public final class ThemeSnapshot implements TokenLookup {
    private final long version;
    private final ThemeResolver.Plan plan;
    private final Map<ThemeToken<?>, Object> values;
    private final TokenDependencyGraph dependencies;
    private final int evaluatedTokens;

    ThemeSnapshot(long version, ThemeResolver.Plan plan, Map<ThemeToken<?>, Object> values,
                  TokenDependencyGraph dependencies, int evaluatedTokens) {
        this.version = version;
        this.plan = plan;
        this.values = Map.copyOf(values);
        this.dependencies = dependencies;
        this.evaluatedTokens = evaluatedTokens;
    }

    @Override
    public <T> T resolve(ThemeToken<T> token) {
        return token.type().valueClass().cast(values.getOrDefault(token, token.defaultValue()));
    }

    public long version() { return version; }

    /** The selected theme IDs from base to strongest overlay. */
    public List<String> themeIds() { return plan.themeIds(); }

    /** Effective source of a token definition, or "default" for a system fallback. */
    public String source(ThemeToken<?> token) {
        var definition = plan.definitions().get(token);
        return definition == null ? "default" : definition.source();
    }

    public Map<ThemeToken<?>, Object> values() { return values; }

    public TokenDependencyGraph dependencies() { return dependencies; }

    public List<String> diagnostics() { return plan.diagnostics(); }

    public List<StyleRule> styles() { return plan.styles(); }

    /** Number of tokens actually evaluated while building this snapshot. */
    public int evaluatedTokens() { return evaluatedTokens; }

    /** Token-level visual differences, including registration of new keys. */
    public Set<ThemeToken<?>> changedFrom(ThemeSnapshot previous) {
        if (previous == this || values == previous.values) return Set.of();
        var changed = new java.util.LinkedHashSet<ThemeToken<?>>();
        values.forEach((key, value) -> {
            if (!value.equals(previous.values.get(key))) changed.add(key);
        });
        previous.values.keySet().forEach(key -> {
            if (!values.containsKey(key)) changed.add(key);
        });
        return Set.copyOf(changed);
    }

    ThemeResolver.Plan plan() { return plan; }
}
