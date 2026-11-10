// Author: dishanhai
package com.dishanhai.shbui_kernel_example;

import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.*;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.Anchor;

/** Shared Forge/NeoForge consumer. SH-Better-UI remains an external dependency. */
public final class CorrectnessExamples {
    private CorrectnessExamples() {}

    /** Use a mounted tree from Screen.init, or an owned module/session scope for HUD content. */
    public static UICorrectnessEngine.Binding mount(ModuleContext module, ScopeHandle scope) {
        return module.engine().correctness().register(module, scope,
                UIElement.builder("shbui_kernel_example:correctness_info")
                        .anchor(Anchor.TOP_CENTER).preferredSize(220, 48).minSize(120, 36).maxSize(360, 120)
                        .padding(8).priority(80).respectSafeArea(true).avoidOverlap(true)
                        .text("shbui_kernel_example.correctness.title")
                        .visualPolicy(UIElement.VisualPolicy.adaptiveGlass())
                        .motion(UIElement.MotionPolicy.animated()).build());
    }

    /** Carry the captured revision through asynchronous work; a late result is rejected by update. */
    public static TaskHandle updateOnUiThread(ModuleContext module, ScopeHandle scope,
            UICorrectnessEngine.Binding binding, long capturedRevision, UIElement next) {
        return module.engine().scheduler().submit(UITask.ui(scope, UIPhase.STATE_MUTATION, UIPriority.NORMAL,
                () -> binding.update(capturedRevision, next)).replacing(binding.declaration().id()));
    }

    public static void inspect(ModuleContext module, ScopeHandle scope) {
        module.engine().require(UIApis.RENDER).openInspector(module, scope);
    }
}
