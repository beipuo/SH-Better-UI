// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.context.ContextSnapshot;
import com.dishanhai.sh_better_ui.theme.context.ThemeRuleResolver;
import com.dishanhai.sh_better_ui.theme.style.ComputedStyle;
import com.dishanhai.sh_better_ui.theme.style.StyleState;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;
import com.dishanhai.sh_better_ui.theme.transition.TransitionState;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Diagnostic snapshot. Inspecting a property does not change a rule or start an animation. */
public record ThemeInspector(String scope, String parent, ThemeSnapshot snapshot, ScopeOverride localOverrides,
                             Map<String, ThemeRuleResolver.DebugState> rules,
                             List<ThemeRuleResolver.Diagnostic> ruleDiagnostics, ContextSnapshot context,
                             TransitionState transitionState, double transitionProgress, int queuedTargets,
                             int invalidatedOwners, long resolutions, long evaluatedTokens,
                             long styleHits, long styleMisses, List<String> diagnostics) {
    public ThemeInspector {
        rules = Map.copyOf(rules);
        ruleDiagnostics = List.copyOf(ruleDiagnostics);
        diagnostics = List.copyOf(diagnostics);
    }

    public double styleHitRate() {
        long total = styleHits + styleMisses;
        return total == 0 ? 0 : styleHits / (double) total;
    }

    /** Explains the cascade for a specific typed property, including a provided explicit value. */
    public static <T> Property<T> property(ThemeScope scope, String role, Set<StyleState> state,
                                           ThemeToken<T> token, T explicit) {
        ComputedStyle style = scope.style(role, state);
        return new Property<>(style.resolve(token, explicit), style.source(token, explicit),
                style.scope().dependencies().dependencies(token));
    }

    public record Property<T>(T value, String source, Set<ThemeToken<?>> dependencies) {
        public Property { dependencies = Set.copyOf(dependencies); }
    }
}
