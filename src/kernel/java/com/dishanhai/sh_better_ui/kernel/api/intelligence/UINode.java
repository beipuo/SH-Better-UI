// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.intelligence;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

/** Immutable UI intent. Binding revisions are allocated by the existing kernel owner thread. */
public interface UINode {
    UiId id();
    UiId parent();
    UIElement.State state();
    SpatialIntelligence.Constraints constraints();
    UIElement.VisualPolicy visualPolicy();
    UIElement.MotionPolicy motionPolicy();
    UICorrectnessContract contract();
    UIElement.InteractionPolicy interactionPolicy();
}
