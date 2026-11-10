// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

/** Arbitration for compatible registrations during bootstrap. */
public enum RegistrationConflictPolicy {
    REJECT,
    KEEP_EXISTING,
    REPLACE_IF_ALLOWED,
    SELECT_BY_PRIORITY
}
