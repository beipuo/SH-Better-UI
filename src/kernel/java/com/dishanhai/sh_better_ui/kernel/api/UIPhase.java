// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

/** The host enters each phase once per frame, in declaration order. */
public enum UIPhase {
    INPUT,
    EVENT_DISPATCH,
    STATE_MUTATION,
    STYLE_RESOLUTION,
    MEASURE,
    LAYOUT,
    ANIMATION,
    GEOMETRY_UPDATE,
    RENDER_PREPARATION,
    RENDER,
    CLEANUP
}
