// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.live;

/** The content arrangement requested by the surface planner. */
public enum SurfacePresentation {
    HIDDEN, MINIMAL, COMPACT, EXPANDED, SPLIT, FLOATING, SUSPENDED;

    /** Whether this arrangement has drawable content after its transition settles. */
    public boolean visible() {
        return this != HIDDEN && this != SUSPENDED;
    }
}
