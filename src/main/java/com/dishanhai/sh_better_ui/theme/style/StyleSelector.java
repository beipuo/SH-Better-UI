// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.style;

import java.util.Objects;
import java.util.Set;

/** Matches a widget role and a conjunction of states. The role "*" matches any widget. */
public record StyleSelector(String role, Set<StyleState> states) {
    public StyleSelector {
        Objects.requireNonNull(role);
        if (role.isBlank() || role.length() > 128) throw new IllegalArgumentException("Invalid style role: " + role);
        states = Set.copyOf(states);
    }

    public static StyleSelector of(String role, StyleState... states) {
        return new StyleSelector(role, Set.of(states));
    }

    public boolean matches(String widgetRole, Set<StyleState> current) {
        return (role.equals("*") || role.equals(widgetRole)) && current.containsAll(states);
    }

    public boolean hasRole() { return !role.equals("*"); }
}
