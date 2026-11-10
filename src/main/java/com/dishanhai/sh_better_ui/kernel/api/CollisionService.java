// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.client.api.UiCollisionLayout;
import com.dishanhai.sh_better_ui.client.gui.collision.CollisionBody;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;

import java.util.function.BooleanSupplier;

/** Schedules the existing collision solver and routes input against its published presentation. */
public interface CollisionService extends UIService {
    Geometry mount(ModuleContext module, ScopeHandle node, UiId writer, Bounds viewport, BooleanSupplier reducedMotion);

    /**
     * The body hit area uses GUI units and the same clipping and stacking as the drawn bounds.
     * Concurrent registrations for one body share the same hit slop.
     */
    Subscription target(ModuleContext module, ScopeHandle node, Geometry geometry, CollisionBody body,
                        int layer, int hitSlop);

    interface Geometry extends Subscription {
        UiCollisionLayout layout();
        void invalidate(Bounds viewport);
    }
}
