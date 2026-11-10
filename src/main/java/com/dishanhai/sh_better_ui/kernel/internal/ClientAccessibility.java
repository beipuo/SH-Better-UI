// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.theme.ThemeScope;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

final class ClientAccessibility implements AccessibilityService {
    private final KernelHost host;
    private final Map<ScopeLifecycle.Scope, Supplier<Semantics>> descriptions = new HashMap<>();

    ClientAccessibility(KernelHost host) { this.host = host; }

    @Override public UiPreferences preferences(ThemeScope scope) {
        return UiPreferences.configured().withTheme(scope);
    }

    @Override
    public Subscription describe(ModuleContext module, ScopeHandle node, Supplier<Semantics> description) {
        var target = host.ownedNode(module, node);
        Objects.requireNonNull(description);
        if (descriptions.containsKey(target)) throw new IllegalStateException("UI semantics already bound");
        var lifetime = host.onScopeClose(target, () -> descriptions.remove(target, description));
        descriptions.put(target, description);
        return lifetime;
    }

    @Override
    public Optional<Semantics> semantics(ModuleContext module, ScopeHandle node) {
        var target = host.ownedNode(module, node);
        var description = descriptions.get(target);
        if (description == null) return Optional.empty();
        var value = Objects.requireNonNull(description.get(), "semantics");
        return target.isOpen() ? Optional.of(value) : Optional.empty();
    }

    @Override
    public boolean narrate(ModuleContext module, ScopeHandle node, NarrationElementOutput output) {
        var semantics = semantics(module, node);
        if (semantics.isEmpty()) return false;
        var value = semantics.orElseThrow();
        output.add(NarratedElementType.TITLE, value.name());
        if (!value.hint().getString().isEmpty()) output.add(NarratedElementType.HINT, value.hint());
        return true;
    }
}
