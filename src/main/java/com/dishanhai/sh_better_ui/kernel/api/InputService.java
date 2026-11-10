// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.client.api.InteractionApi;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;

import java.util.function.Predicate;

/** Opt-in native input targeting for a mounted tree. Listeners subscribe through the module's event bus. */
public interface InputService extends UIService {
    UIEventType<NativeInput> EVENT = new UIEventType<>(UiId.parse("sh_better_ui:native_input"), NativeInput.class, true);
    UIEventType<NativeInput> POINTER_MOVED = new UIEventType<>(UiId.parse("sh_better_ui:pointer_moved"),
            NativeInput.class, false);

    InteractionApi interactions();

    /** Higher layers are tested first. Within a layer the most recently mounted target is foremost. */
    Subscription target(ModuleContext module, ScopeHandle node, int layer, Predicate<NativeInput> hit);

    /** Requires capture and vanilla-blocking grants for the mounted node and the pressed native button. */
    boolean capture(ModuleContext module, ScopeHandle node, UiId claim, int button, UiPointerCapture.Handler handler);
}
