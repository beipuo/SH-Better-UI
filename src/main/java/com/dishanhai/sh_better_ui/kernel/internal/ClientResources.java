// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentAssets;
import com.dishanhai.sh_better_ui.kernel.api.*;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

final class ClientResources implements ResourcesService {
    private static final UiId RELOAD_ID = UiId.parse("sh_better_ui:native/reload");
    private final KernelHost host;
    private final AtomicLong preparing = new AtomicLong();
    private final Set<AlignmentAssets> caches = new LinkedHashSet<>();
    private final Set<Watch> watchers = new LinkedHashSet<>();
    private volatile long version;
    private boolean applying;

    ClientResources(KernelHost host) { this.host = host; }

    @Override public long version() { return version; }

    long begin() { return preparing.incrementAndGet(); }

    boolean commit(long ticket, Runnable install) {
        host.assertOwner();
        if (host.lifecycle().state() != EngineState.RUNNING || ticket != preparing.get() || ticket <= version) return false;
        if (applying) throw new IllegalStateException("Reentrant UI resource application");
        applying = true;
        try (var lifetime = host.rootScope().openChild(ScopeKind.RESOURCE_RELOAD, RELOAD_ID)) {
            install.run();
            if (!lifetime.isOpen()) return false;
            caches.forEach(AlignmentAssets::invalidate);
            version = ticket;
            for (var watch : List.copyOf(watchers)) {
                if (!watch.lifetime.isActive()) continue;
                try {
                    watch.observer.accept(ticket);
                } catch (RuntimeException | LinkageError | AssertionError failure) {
                    host.report("UI resource observer", failure);
                    watch.lifetime.close();
                }
            }
            return true;
        } finally {
            applying = false;
        }
    }

    @Override
    public Subscription observe(ModuleContext module, ScopeHandle scope, LongConsumer observer) {
        var target = host.ownedScope(module, scope);
        var watch = new Watch(Objects.requireNonNull(observer));
        watch.lifetime = host.onScopeClose(target, () -> watchers.remove(watch));
        watchers.add(watch);
        return watch.lifetime;
    }

    @Override
    public AlignmentAssets alignmentAssets(ModuleContext module, ScopeHandle scope, ResourceManager manager) {
        var target = host.ownedScope(module, scope);
        var assets = ShBetterUiApi.layout().assets(manager);
        host.onScopeClose(target, () -> {
            caches.remove(assets);
            assets.invalidate();
        });
        caches.add(assets);
        return assets;
    }

    private static final class Watch {
        final LongConsumer observer;
        Subscription lifetime;

        Watch(LongConsumer observer) { this.observer = observer; }
    }
}
