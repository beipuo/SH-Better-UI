// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.theme.ThemeManager;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

final class ClientServiceFixture implements AutoCloseable {
    final AtomicLong time = new AtomicLong();
    final EnginePublication publication = new EnginePublication();
    final ThemeManager themes = new ThemeManager();
    final UiId id = new UiId("example", "extension");
    final ClientUiKernel client;
    ModuleContext module;

    ClientServiceFixture(Set<ModuleDescriptor.ResourceClaim> claims, Set<UIPermission> permissions) {
        var extension = new UIModule() {
            @Override public ModuleDescriptor descriptor() {
                var builder = ModuleDescriptor.builder().id(id).version("1.0.0");
                claims.forEach(builder::claimResource);
                permissions.forEach(builder::requestPermission);
                return builder.build();
            }
            @Override public void register(ModuleContext context) {}
            @Override public void start(ModuleContext context) { module = context; }
            @Override public void stop(ModuleContext context) {}
        };
        client = ClientUiKernel.install(ModuleDescriptor.Platform.FORGE_1_20_1, publication, themes, themes::close,
                time::get, List.of(new ModuleDiscovery.Candidate(id.namespace(), "fixture", extension, permissions, false)));
    }

    ScopeHandle tree() {
        return module.scope().openChild(ScopeKind.SCREEN, new UiId("example", "screen"))
                .openChild(ScopeKind.UI_TREE, new UiId("example", "tree"));
    }

    @Override public void close() { client.close(); }
}
