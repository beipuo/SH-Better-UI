// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.transition;

/** One ordered lifecycle notification from a track. */
public record TransitionNotice(Kind kind, long track, double progress) {
    public enum Kind { STARTED, UPDATED, INTERRUPTED, COMPLETED, CANCELLED }
}
