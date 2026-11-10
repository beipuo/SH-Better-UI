// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

/** A module candidate discovered by a platform host. */
public interface UIModule {
    ModuleDescriptor descriptor();

    void register(ModuleContext context);

    void start(ModuleContext context);

    void stop(ModuleContext context);
}
