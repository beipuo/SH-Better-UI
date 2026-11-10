// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

/** Read-only view of the engine and module lifetimes. */
public interface UILifecycle {
    EngineState state();

    LifecycleSnapshot snapshot();
}
