// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Tracks cleanup identity across scopes and releases each scope's resources in reverse order. */
final class CleanupManager {
    record Binding(ScopeLifecycle.Scope scope, AutoCloseable resource, AutoCloseable action) {}

    private final IdentityHashMap<AutoCloseable, Binding> owners = new IdentityHashMap<>();
    private final BiConsumer<ScopeLifecycle.Scope, Throwable> failures;

    CleanupManager(BiConsumer<ScopeLifecycle.Scope, Throwable> failures) {
        this.failures = Objects.requireNonNull(failures, "failures");
    }

    void track(ScopeLifecycle.Scope scope, AutoCloseable resource, AutoCloseable action) {
        var binding = new Binding(scope, Objects.requireNonNull(resource, "resource"), action);
        var previous = owners.putIfAbsent(resource, binding);
        if (previous == null) {
            scope.resources().add(binding);
        } else if (previous.scope() != scope || previous.action() != action) {
            throw new IllegalStateException("Cleanup resource already belongs to " + previous.scope().id());
        }
    }

    void forget(ScopeLifecycle.Scope scope, AutoCloseable resource) {
        Binding binding = owners.get(resource);
        if (scope.isOpen() && binding != null && binding.scope() == scope) {
            owners.remove(resource);
            scope.resources().removeIf(candidate -> candidate == binding);
        }
    }

    void release(ScopeLifecycle.Scope scope) {
        List<Binding> resources = scope.resources();
        for (int i = resources.size() - 1; i >= 0; i--) {
            Binding binding = resources.get(i);
            try {
                release(scope, binding.action());
            } finally {
                owners.remove(binding.resource());
            }
        }
        resources.clear();
    }

    void release(ScopeLifecycle.Scope scope, AutoCloseable resource) {
        try {
            resource.close();
        } catch (Exception | LinkageError | AssertionError failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            failures.accept(scope, failure);
        }
    }
}
