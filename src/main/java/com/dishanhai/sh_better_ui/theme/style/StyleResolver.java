// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.style;

import com.dishanhai.sh_better_ui.theme.ThemeSnapshot;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Selects applicable declarations, independently of scope inheritance or rule selection. */
public final class StyleResolver {
    private static final Comparator<StyleRule> ORDER = Comparator
            .comparingInt((StyleRule rule) -> rule.selector().states().size())
            .thenComparing(rule -> rule.selector().hasRole());

    private StyleResolver() {}

    /** More state constraints win, then an exact role, then the later declaration. */
    public static ComputedStyle resolve(ThemeSnapshot scope, String role, Set<StyleState> states,
                                        Map<ThemeToken<?>, Object> forced) {
        var matched = new ArrayList<StyleRule>();
        for (var rule : scope.styles()) {
            if (rule.selector().matches(role, states)) matched.add(rule);
        }
        matched.sort(ORDER);
        var values = new LinkedHashMap<ThemeToken<?>, Object>();
        var sources = new LinkedHashMap<ThemeToken<?>, StyleRule>();
        for (var rule : matched) {
            values.putAll(rule.values());
            rule.values().keySet().forEach(token -> sources.put(token, rule));
        }
        return new ComputedStyle(scope, values, sources, forced);
    }
}
