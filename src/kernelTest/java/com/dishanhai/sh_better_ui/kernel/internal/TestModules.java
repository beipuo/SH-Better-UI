// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.UIModule;
import com.dishanhai.sh_better_ui.kernel.api.UIPermission;
import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.Platform.FORGE_1_20_1;

final class TestModules {
    private TestModules() {
    }

    static ModuleDescriptor.Builder module(String path) {
        return ModuleDescriptor.builder().id(new UiId("example", path)).version("1.0.0");
    }

    static ModuleDiscovery.Candidate candidate(ModuleDescriptor descriptor) {
        return candidate(descriptor, Set.of(), false, ctx -> {}, ctx -> {}, ctx -> {});
    }

    static ModuleDiscovery.Candidate candidate(ModuleDescriptor descriptor, Set<UIPermission> grants, boolean core,
                                                Consumer<ModuleContext> register, Consumer<ModuleContext> start,
                                                Consumer<ModuleContext> stop) {
        UIModule module = new UIModule() {
            @Override
            public ModuleDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public void register(ModuleContext context) {
                register.accept(context);
            }

            @Override
            public void start(ModuleContext context) {
                start.accept(context);
            }

            @Override
            public void stop(ModuleContext context) {
                stop.accept(context);
            }
        };
        return new ModuleDiscovery.Candidate(descriptor.id().namespace(), "test-mod.jar", module, grants, core);
    }

    static List<ModuleDiscovery.Module> discover(ModuleDiscovery.Candidate... candidates) {
        return ModuleDiscovery.discover(FORGE_1_20_1, List.of(candidates)).modules();
    }
}
