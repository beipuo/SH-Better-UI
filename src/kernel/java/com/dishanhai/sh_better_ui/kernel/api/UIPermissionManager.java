// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

/** Framework cooperation checks for the authenticated module context. */
public interface UIPermissionManager {
    PermissionDecision check(PermissionRequest request);

    /** Throws SecurityException and records a denial when the operation is unauthorized. */
    void require(PermissionRequest request);
}
