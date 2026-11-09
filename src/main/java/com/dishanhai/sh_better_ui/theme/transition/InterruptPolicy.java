// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.transition;

/** Handling for a request that arrives while a track is running. */
public enum InterruptPolicy {
    /** Commit the newly requested endpoint immediately. */
    SNAP,
    /** Sample the running track and animate from that value. */
    RETARGET,
    /** Keep pending targets in a bounded FIFO. */
    QUEUE,
    /** Keep the current track and only the latest pending target. */
    FINISH_THEN_SWITCH,
    /** Fade the remaining old track into the new target. */
    BLEND
}
