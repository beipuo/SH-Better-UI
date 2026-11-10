// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.Optional;

/** Scoped focus requests using the native Screen's focus and modal routing. */
public interface FocusService extends UIService {
    Optional<OwnershipLease> request(ModuleContext module, ScopeHandle node, UiId focus,
                                     GuiEventListener widget, int priority);

    /** Routes key and character events to this node when the native widget has focus. */
    Subscription target(ModuleContext module, ScopeHandle node, GuiEventListener widget, int layer);
}
