// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.client.api.LayoutApi;
import com.dishanhai.sh_better_ui.theme.ThemeScope;

/** Incremental measure and arrangement using the existing alignment and native widget helpers. */
public interface LayoutService extends UIService {
    LayoutApi layout();

    Binding bind(ModuleContext module, ScopeHandle node, UiId writer, Runnable measure, Runnable arrange);

    /** Use this owner when binding style tokens that change measurement, arrangement or paint. */
    ThemeScope.InvalidationOwner styles(ModuleContext module, ScopeHandle node, ThemeScope theme);

    interface Binding extends Subscription {
        void invalidate();
    }
}
