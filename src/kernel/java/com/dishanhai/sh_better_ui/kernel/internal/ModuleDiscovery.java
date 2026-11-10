// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.ServiceOrigin;
import com.dishanhai.sh_better_ui.kernel.api.UIEngine;
import com.dishanhai.sh_better_ui.kernel.api.UIModule;
import com.dishanhai.sh_better_ui.kernel.api.UIPermission;
import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/** Validates explicit candidates supplied by the host with loader-verified provenance. */
final class ModuleDiscovery {
    private ModuleDiscovery() {
    }

    record Candidate(String modId, String source, UIModule module, Set<UIPermission> grants, boolean core,
                     boolean trusted) {
        Candidate(String modId, String source, UIModule module, Set<UIPermission> grants, boolean core) {
            this(modId, source, module, grants, core, core);
        }

        Candidate {
            Objects.requireNonNull(modId, "modId");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(module, "module");
            grants = Set.copyOf(grants);
        }
    }

    record Module(UIModule implementation, ModuleDescriptor descriptor, ServiceOrigin origin,
                  Set<UIPermission> permissions, boolean core, boolean trusted) {
        UiId id() {
            return descriptor.id();
        }
    }

    record Result(List<Module> modules, List<String> diagnostics) {}

    static Result discover(ModuleDescriptor.Platform platform, Collection<Candidate> candidates) {
        return discover(platform, candidates, null);
    }

    static Result discover(ModuleDescriptor.Platform platform, Collection<Candidate> candidates,
                           BiConsumer<String, Throwable> rejected) {
        Objects.requireNonNull(platform, "platform");
        var modules = new TreeMap<UiId, Module>();
        var diagnostics = new ArrayList<String>();
        for (Candidate candidate : candidates) {
            try {
                var module = validate(platform, candidate, diagnostics);
                if (module != null && modules.putIfAbsent(module.id(), module) != null) {
                    throw new IllegalArgumentException("Duplicate UI module " + module.id());
                }
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                if (rejected == null || candidate.trusted()) throw failure;
                rejected.accept("Rejected client UI extension from " + candidate.source(), failure);
            }
        }
        diagnostics.sort(String::compareTo);
        return new Result(List.copyOf(modules.values()), List.copyOf(diagnostics));
    }

    private static Module validate(ModuleDescriptor.Platform platform, Candidate candidate, List<String> diagnostics) {
        ModuleDescriptor descriptor = Objects.requireNonNull(candidate.module().descriptor(),
                "Missing UI module descriptor from " + candidate.source());
        UiId id = descriptor.id();
        if (!id.namespace().equals(candidate.modId())) {
            throw new SecurityException("UI module " + id + " belongs to a different mod than " + candidate.modId());
        }
        if (candidate.core() != (descriptor.initializationPhase() == ModuleDescriptor.InitializationPhase.CORE)) {
            throw new SecurityException("Core initialization phase does not match the host's trust for " + id);
        }
        if (!descriptor.platforms().contains(platform)) {
            String reason = "UI module " + id + " is incompatible with " + platform;
            if (candidate.trusted()) throw new IllegalArgumentException(reason);
            diagnostics.add(reason);
            return null;
        }
        if (descriptor.apiContractVersion() != UIEngine.CONTRACT_VERSION) {
            throw new IllegalArgumentException("Incompatible UI API contract for " + id + ": "
                    + descriptor.apiContractVersion() + ", expected " + UIEngine.CONTRACT_VERSION);
        }
        var denied = EnumSet.noneOf(UIPermission.class);
        denied.addAll(descriptor.requestedPermissions());
        denied.removeAll(candidate.grants());
        denied.remove(UIPermission.REGISTER_SERVICE);
        if (!denied.isEmpty()) throw new SecurityException("UI module " + id + " was denied " + denied);
        var permissions = EnumSet.of(UIPermission.REGISTER_SERVICE);
        permissions.addAll(descriptor.requestedPermissions());
        return new Module(candidate.module(), descriptor, new ServiceOrigin(id, candidate.source()),
                Set.copyOf(permissions), candidate.core(), candidate.trusted());
    }
}
