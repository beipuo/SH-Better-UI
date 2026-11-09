// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.token;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Immutable direct dependencies and their reverse index for incremental resolution. */
public final class TokenDependencyGraph {
    private final Map<ThemeToken<?>, Set<ThemeToken<?>>> reads;
    private final Map<ThemeToken<?>, Set<ThemeToken<?>>> dependents;

    public TokenDependencyGraph(Map<ThemeToken<?>, Set<ThemeToken<?>>> reads) {
        this.reads = freeze(reads);
        var reverse = new HashMap<ThemeToken<?>, Set<ThemeToken<?>>>();
        this.reads.forEach((token, dependencies) -> dependencies.forEach(dependency ->
                reverse.computeIfAbsent(dependency, ignored -> new LinkedHashSet<>()).add(token)));
        dependents = freeze(reverse);
    }

    private static Map<ThemeToken<?>, Set<ThemeToken<?>>> freeze(Map<ThemeToken<?>, Set<ThemeToken<?>>> map) {
        var result = new HashMap<ThemeToken<?>, Set<ThemeToken<?>>>();
        map.forEach((token, dependencies) -> result.put(token, Set.copyOf(dependencies)));
        return Map.copyOf(result);
    }

    /** Returns the tokens read directly while computing this token. */
    public Set<ThemeToken<?>> dependencies(ThemeToken<?> token) {
        return reads.getOrDefault(token, Set.of());
    }

    /** Includes the supplied tokens and all their transitive dependents. */
    public Set<ThemeToken<?>> affected(Collection<ThemeToken<?>> changed) {
        var affected = new LinkedHashSet<>(changed);
        var queue = new ArrayDeque<>(affected);
        while (!queue.isEmpty()) {
            for (var dependent : dependents.getOrDefault(queue.removeFirst(), Set.of())) {
                if (affected.add(dependent)) queue.addLast(dependent);
            }
        }
        return affected;
    }
}
