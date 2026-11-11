// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

/** A permission a module can request. Grants are issued by the host. */
public enum UIPermission {
    REGISTER_SERVICE,
    REPLACE_SERVICE,
    MODIFY_GLOBAL_CONFIG,
    MODIFY_FOREIGN_SCOPE,
    CAPTURE_INPUT,
    BLOCK_VANILLA_INPUT,
    REGISTER_RENDER_PASS,
    ACCESS_INTERNAL_DIAGNOSTICS,
    MODIFY_GLOBAL_THEME,
    /** Allows an explicitly trusted module to interrupt with a system-priority live publication. */
    PUBLISH_CRITICAL_ACTIVITY
}
