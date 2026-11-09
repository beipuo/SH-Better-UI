// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable registry snapshot. Use ThemeManager to validate and publish mutations. */
public final class ThemeRegistry {
    private static final int MAX_THEMES = 512;

    public enum DuplicatePolicy { REJECT, REPLACE_CODE }

    private final Map<String, Theme> code;
    private final Map<String, Theme> resources;
    private final Map<String, Theme> themes;

    public ThemeRegistry() { this(Map.of(), Map.of()); }

    private ThemeRegistry(Map<String, Theme> code, Map<String, Theme> resources) {
        this.code = Collections.unmodifiableMap(new LinkedHashMap<>(code));
        this.resources = Collections.unmodifiableMap(new LinkedHashMap<>(resources));
        var all = new LinkedHashMap<>(this.code);
        all.putAll(this.resources);
        if (all.size() > MAX_THEMES) throw new IllegalArgumentException("Too many themes: " + all.size());
        themes = Collections.unmodifiableMap(all);
    }

    public Map<String, Theme> themes() { return themes; }

    public Optional<Theme> find(String id) { return Optional.ofNullable(themes.get(id)); }

    public ThemeRegistry with(Theme theme) { return with(theme, DuplicatePolicy.REJECT); }

    /** Adds a code theme. Only explicit REPLACE_CODE can replace a prior code definition. */
    public ThemeRegistry with(Theme theme, DuplicatePolicy policy) {
        Objects.requireNonNull(theme);
        Objects.requireNonNull(policy);
        if (resources.containsKey(theme.id()) || (code.containsKey(theme.id()) && policy == DuplicatePolicy.REJECT)) {
            throw new IllegalArgumentException("Theme already registered: " + theme.id());
        }
        var next = new LinkedHashMap<>(code);
        next.put(theme.id(), theme);
        return new ThemeRegistry(next, resources);
    }

    /** Removes a code theme. Resource definitions are owned by the resource snapshot. */
    public ThemeRegistry without(String id) {
        if (!code.containsKey(id)) return this;
        var next = new LinkedHashMap<>(code);
        next.remove(id);
        return new ThemeRegistry(next, resources);
    }

    /** Replaces the whole resource layer, rejecting collisions with code registrations. */
    public ThemeRegistry withResources(Collection<Theme> definitions) {
        if (definitions.size() > MAX_THEMES) throw new IllegalArgumentException("Too many resource themes");
        var next = new LinkedHashMap<String, Theme>();
        for (var theme : definitions) {
            if (code.containsKey(theme.id()) || next.putIfAbsent(theme.id(), theme) != null) {
                throw new IllegalArgumentException("Conflicting resource theme ID: " + theme.id() + " in " + theme.source());
            }
        }
        return new ThemeRegistry(code, next);
    }
}
