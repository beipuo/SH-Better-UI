// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

/** Host-issued access bound to one module and its current lifetime. */
public interface ModuleContext {
    ModuleDescriptor descriptor();

    /** Available during register. The returned registrar expires when the callback ends. */
    ServiceRegistrar services();

    /** Available during start and while the module is running. */
    UIEngine engine();

    /** Module lifetime for attaching cleanup or creating child scopes. */
    ScopeHandle scope();
}
