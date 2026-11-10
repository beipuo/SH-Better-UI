// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

/** Read-only information about the attached client platform. */
public interface UIPlatform extends UIService {
    ServiceKey<UIPlatform> KEY = new ServiceKey<>(UiId.parse("sh_better_ui:platform"), UIPlatform.class, 1);

    ModuleDescriptor.Platform info();

    Viewport viewport();

    boolean hasClientSession();

    boolean isOnUIThread();

    record Viewport(int width, int height, double guiScale, boolean focused) {}
}
