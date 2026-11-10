// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ConflictPolicy;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceClaim;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.ResourceKind;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.UiId;

final class ConflictResolver {
    private ConflictResolver() {}

    record SlotKey(ResourceKind kind, UiId id, long generation) {}

    static void validate(ResourceKind kind, ConflictPolicy policy) {
        boolean legal = switch (policy) {
            case REJECT, REPLACE -> true;
            case QUEUE, RETARGET -> kind == ResourceKind.ANIMATION_PROPERTY;
            case COEXIST -> kind == ResourceKind.EVENT_SUBSCRIPTION || kind == ResourceKind.MODAL_LAYER;
            case MERGE -> false;
        };
        if (!legal) {
            throw new IllegalArgumentException("UI resource " + kind + " does not support " + policy);
        }
    }

    static SlotKey slot(ResourceClaim claim, ScopeLifecycle.Scope target, boolean legacy) {
        return switch (claim.kind()) {
            case KEYBOARD_FOCUS, MODAL_LAYER -> {
                ScopeLifecycle.Scope screen = target;
                while (screen != null && screen.kind() != ScopeKind.SCREEN) {
                    screen = screen.parent();
                }
                if (screen == null && !legacy) {
                    throw new IllegalArgumentException(claim.kind() + " requires a Screen scope");
                }
                yield screen == null ? new SlotKey(claim.kind(), claim.id(), 0)
                        : new SlotKey(claim.kind(), null, screen.generation());
            }
            case POINTER_CAPTURE -> new SlotKey(claim.kind(), null, 0);
            case RENDER_TARGET, TEXTURE_RESOURCE -> new SlotKey(claim.kind(), claim.id(), 0);
            case LAYOUT_MUTATION -> new SlotKey(claim.kind(), null, target.generation());
            case ANIMATION_PROPERTY, EVENT_SUBSCRIPTION -> new SlotKey(claim.kind(), claim.id(), target.generation());
        };
    }

    static boolean canReplace(int currentPriority, int nextPriority, boolean sameOwner) {
        return nextPriority > currentPriority || sameOwner && nextPriority == currentPriority;
    }
}
