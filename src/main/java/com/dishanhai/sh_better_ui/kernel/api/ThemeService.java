// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.theme.ScopeOverride;
import com.dishanhai.sh_better_ui.theme.ThemeSnapshot;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;

import java.util.Set;
import java.util.function.Consumer;

/** Shared client theme and module-owned token overrides. Snapshots can be read on a worker thread. */
public interface ThemeService extends UIService {
    ThemeSnapshot snapshot();

    Set<String> availableThemes();

    /** Returns the same theme branch for an owned node until its lifetime closes. */
    default Scope scope(ModuleContext module, ScopeHandle node) {
        throw new UnsupportedOperationException("This theme service does not support scoped overrides");
    }

    /** UI-thread operations on a single branch. Closing the node releases the branch. */
    interface Scope {
        boolean isOpen();
        <T> T resolve(ThemeToken<T> token);
        void configure(Consumer<ScopeOverride.Builder> edits);
    }
}
