// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

public enum PermissionDecision {
    GRANTED, NO_MODULE_CONTEXT, INVALID_SCOPE, CLOSED_SCOPE, MISSING_GRANT, OUTSIDE_GRANT;

    public boolean allowed() {
        return this == GRANTED;
    }
}
