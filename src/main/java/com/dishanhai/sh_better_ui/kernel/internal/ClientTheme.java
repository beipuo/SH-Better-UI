// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.ThemeService;
import com.dishanhai.sh_better_ui.theme.ScopeOverride;
import com.dishanhai.sh_better_ui.theme.ThemeManager;
import com.dishanhai.sh_better_ui.theme.ThemeScope;
import com.dishanhai.sh_better_ui.theme.ThemeSnapshot;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class ClientTheme implements ThemeService {
    private final KernelHost host;
    private final ThemeManager manager;
    private final ScopeHandle lifetime;
    private final Map<ScopeLifecycle.Scope, Bound> scopes = new HashMap<>();

    ClientTheme(KernelHost host, ThemeManager manager, ScopeHandle lifetime) {
        this.host = host;
        this.manager = manager;
        this.lifetime = lifetime;
    }

    @Override public ThemeSnapshot snapshot() {
        requireOpen();
        return manager.snapshot();
    }

    @Override public Set<String> availableThemes() {
        requireOpen();
        return manager.registry().themes().keySet();
    }

    @Override
    public Scope scope(ModuleContext module, ScopeHandle node) {
        requireOpen();
        var target = host.ownedNode(module, node);
        var access = host.moduleAccess(module);
        access.assertSideEffectAllowed();
        var missing = new ArrayDeque<ScopeLifecycle.Scope>();
        ThemeScope parent = manager.global();
        for (var current = target; current != null; current = current.parent()) {
            if (current.kind() != ScopeKind.UI_TREE && current.kind() != ScopeKind.COMPONENT) break;
            var existing = scopes.get(current);
            if (existing != null) {
                parent = existing.theme;
                break;
            }
            missing.addFirst(current);
        }
        for (var child : missing) {
            var theme = parent.child(child.id().toString());
            var bound = new Bound(child, access, theme);
            host.onScopeClose(child, () -> {
                scopes.remove(child, bound);
                theme.close();
            });
            scopes.put(child, bound);
            parent = theme;
        }
        return scopes.get(target);
    }

    ThemeScope nativeScope(ModuleContext module, ScopeHandle node) {
        return ((Bound) scope(module, node)).theme();
    }

    private void requireOpen() {
        if (!lifetime.isOpen()) throw new IllegalStateException("The client theme service is closed");
    }

    private record Bound(ScopeLifecycle.Scope node, ModuleAccess access, ThemeScope theme) implements Scope {
        @Override public boolean isOpen() { return node.isOpen() && !theme.isClosed(); }

        @Override public <T> T resolve(ThemeToken<T> token) {
            node.assertOwner();
            if (!isOpen()) throw new IllegalStateException("The client theme scope is closed");
            return theme.resolve(token);
        }

        @Override public void configure(Consumer<ScopeOverride.Builder> edits) {
            node.assertOwner();
            access.assertSideEffectAllowed();
            access.write(node);
            theme.configure(edits);
        }
    }
}
