// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

/** Lifecycle status of one discovered module. */
public enum ModuleLifecycle {
    DISCOVERED,
    REGISTERING,
    REGISTERED,
    STARTING,
    RUNNING,
    STOPPING,
    TERMINATED,
    FAILED,
    BLOCKED
}
