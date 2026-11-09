// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.style;

import com.dishanhai.sh_better_ui.theme.ThemeSnapshot;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;
import com.dishanhai.sh_better_ui.theme.token.TokenLookup;

import java.util.Map;

/** An immutable view of a scope and the declarations that won the style cascade. */
public record ComputedStyle(ThemeSnapshot scope, Map<ThemeToken<?>, Object> declarations,
                            Map<ThemeToken<?>, StyleRule> sources,
                            Map<ThemeToken<?>, Object> forced) implements TokenLookup {
    public ComputedStyle {
        declarations = Map.copyOf(declarations);
        sources = Map.copyOf(sources);
        forced = Map.copyOf(forced);
    }

    @Override
    public <T> T resolve(ThemeToken<T> token) {
        Object stateValue = forced.get(token);
        if (stateValue == null) stateValue = declarations.get(token);
        return stateValue == null ? scope.resolve(token) : token.type().valueClass().cast(stateValue);
    }

    /** Accessibility wins over an explicit widget property, which in turn wins over style rules. */
    public <T> T resolve(ThemeToken<T> token, T explicit) {
        return explicit == null || forced.containsKey(token) ? resolve(token) : token.type().cast(explicit);
    }

    /** Returns the winning cascade layer for inspection. */
    public String source(ThemeToken<?> token, Object explicit) {
        if (forced.containsKey(token)) return "accessibility";
        if (explicit != null) return "explicit";
        var rule = sources.get(token);
        return rule == null ? scope.source(token) : "style:" + rule.selector();
    }
}
