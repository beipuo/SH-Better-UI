// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Objects;

/** The principal is determined by the engine view issuing this request. */
public record PermissionRequest(UIPermission permission, ScopeHandle target) {
    public PermissionRequest {
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(target, "target");
    }
}
