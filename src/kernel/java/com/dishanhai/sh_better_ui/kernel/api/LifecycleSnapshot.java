// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** A diagnostic snapshot without references to live modules or resources. */
public record LifecycleSnapshot(EngineState state, Map<UiId, ModuleLifecycle> modules, List<String> diagnostics) {
    public LifecycleSnapshot {
        Objects.requireNonNull(state, "state");
        modules = Collections.unmodifiableMap(new TreeMap<>(Map.copyOf(modules)));
        diagnostics = List.copyOf(diagnostics);
    }
}
