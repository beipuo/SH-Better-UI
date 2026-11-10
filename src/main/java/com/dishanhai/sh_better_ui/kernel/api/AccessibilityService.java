// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.theme.ThemeScope;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Accessibility preferences and semantics for native widgets and custom nodes. */
public interface AccessibilityService extends UIService {
    UiPreferences preferences(ThemeScope scope);

    Subscription describe(ModuleContext module, ScopeHandle node, Supplier<Semantics> description);

    Optional<Semantics> semantics(ModuleContext module, ScopeHandle node);

    /** Appends semantics in a widget's native narration callback. Returns false if none are bound. */
    boolean narrate(ModuleContext module, ScopeHandle node, NarrationElementOutput output);

    enum Role { BUTTON, TOGGLE, SLIDER, TEXT, TEXT_FIELD, LIST, LIST_ITEM, IMAGE, DIALOG, GROUP }

    record Semantics(Role role, Component name, Component hint) {
        public Semantics {
            Objects.requireNonNull(role);
            Objects.requireNonNull(name);
            Objects.requireNonNull(hint);
        }
    }
}
