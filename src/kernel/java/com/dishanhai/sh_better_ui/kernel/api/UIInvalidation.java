// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Set;

/** Marks affected UI tree and component nodes for incrementally scheduled work. */
public interface UIInvalidation {
    void invalidate(ScopeHandle node, Set<UIDirtyFlag> flags);
}
