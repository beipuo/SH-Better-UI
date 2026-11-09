// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.event;

import com.dishanhai.sh_better_ui.theme.ThemeScope;
import com.dishanhai.sh_better_ui.theme.ThemeSnapshot;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;
import com.dishanhai.sh_better_ui.theme.transition.TransitionNotice;

import java.util.List;
import java.util.Set;

/** Ordered theme lifecycle notification on the manager's owner thread. */
public record ThemeEvent(Kind kind, ThemeSnapshot snapshot, List<String> themeIds, ThemeScope scope,
                         Set<ThemeToken<?>> changedTokens, TransitionNotice transition, String detail) {
    public ThemeEvent {
        themeIds = List.copyOf(themeIds);
        changedTokens = Set.copyOf(changedTokens);
    }

    public enum Kind {
        REGISTERED, UNREGISTERED, REQUESTED, RULES_RESOLVED, WILL_CHANGE, CHANGED,
        TRANSITION_STARTED, TRANSITION_UPDATED, TRANSITION_INTERRUPTED, TRANSITION_COMPLETED, TRANSITION_CANCELLED,
        SCOPE_CHANGED, TOKEN_CHANGED, RESOURCES_RELOADED, RESOLUTION_FAILED
    }
}
