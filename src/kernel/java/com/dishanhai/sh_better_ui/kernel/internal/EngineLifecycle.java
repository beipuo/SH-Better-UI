// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.EngineState;
import com.dishanhai.sh_better_ui.kernel.api.LifecycleSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.ModuleLifecycle;
import com.dishanhai.sh_better_ui.kernel.api.UILifecycle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The owner thread writes transitions. Diagnostic reads take a consistent snapshot. */
final class EngineLifecycle implements UILifecycle {
    private static final int MAX_DIAGNOSTICS = 256;
    private volatile EngineState state = EngineState.NEW;
    private final Map<UiId, ModuleLifecycle> modules = new TreeMap<>();
    private final ArrayDeque<String> diagnostics = new ArrayDeque<>();

    @Override
    public EngineState state() {
        return state;
    }

    @Override
    public synchronized LifecycleSnapshot snapshot() {
        return new LifecycleSnapshot(state, modules, List.copyOf(diagnostics));
    }

    synchronized void transition(EngineState next) {
        boolean valid = switch (state) {
            case NEW -> next == EngineState.DISCOVERING || next == EngineState.STOPPING;
            case DISCOVERING -> next == EngineState.REGISTERING || next == EngineState.FAILED;
            case REGISTERING -> next == EngineState.RESOLVING || next == EngineState.FAILED;
            case RESOLVING -> next == EngineState.INITIALIZING || next == EngineState.FAILED;
            case INITIALIZING -> next == EngineState.READY || next == EngineState.FAILED;
            case READY -> next == EngineState.RUNNING || next == EngineState.FAILED;
            case RUNNING -> next == EngineState.STOPPING || next == EngineState.FAILED;
            case FAILED -> next == EngineState.STOPPING;
            case STOPPING -> next == EngineState.TERMINATED;
            case TERMINATED -> false;
        };
        if (!valid) {
            throw new IllegalStateException("Invalid UI engine transition " + state + " -> " + next);
        }
        state = next;
    }

    synchronized void discover(UiId module) {
        if (modules.putIfAbsent(module, ModuleLifecycle.DISCOVERED) != null) {
            throw new IllegalStateException("UI module is already discovered: " + module);
        }
    }

    synchronized ModuleLifecycle module(UiId id) {
        return modules.get(id);
    }

    synchronized void transition(UiId id, ModuleLifecycle next) {
        ModuleLifecycle current = modules.get(id);
        boolean valid = switch (current) {
            case DISCOVERED -> next == ModuleLifecycle.REGISTERING || next == ModuleLifecycle.BLOCKED;
            case REGISTERING -> next == ModuleLifecycle.REGISTERED || next == ModuleLifecycle.FAILED;
            case REGISTERED -> next == ModuleLifecycle.STARTING || next == ModuleLifecycle.BLOCKED
                    || next == ModuleLifecycle.TERMINATED;
            case STARTING -> next == ModuleLifecycle.RUNNING || next == ModuleLifecycle.STOPPING;
            case RUNNING -> next == ModuleLifecycle.STOPPING;
            case STOPPING -> next == ModuleLifecycle.TERMINATED || next == ModuleLifecycle.FAILED;
            case TERMINATED, FAILED, BLOCKED -> false;
        };
        if (!valid) {
            throw new IllegalStateException("Invalid UI module transition for " + id + ": " + current + " -> " + next);
        }
        modules.put(id, next);
    }

    synchronized void diagnostic(String diagnostic) {
        if (diagnostics.size() == MAX_DIAGNOSTICS) {
            diagnostics.removeFirst();
        }
        diagnostics.addLast(diagnostic);
    }
}
