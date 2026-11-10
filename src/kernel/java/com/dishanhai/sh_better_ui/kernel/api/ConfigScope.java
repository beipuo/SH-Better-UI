// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Objects;

/** A write layer at an engine-issued lifetime. */
public record ConfigScope(ScopeHandle lifetime, ConfigLayer layer) {
    public ConfigScope {
        Objects.requireNonNull(lifetime, "lifetime");
        Objects.requireNonNull(layer, "layer");
    }
}
