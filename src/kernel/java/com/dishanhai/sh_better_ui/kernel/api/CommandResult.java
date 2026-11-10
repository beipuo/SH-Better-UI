// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.List;

/** State is committed when this receipt is returned. Each effect has its own outcome. */
public record CommandResult(long stateVersion, List<TaskHandle> effects) {
    public CommandResult {
        effects = List.copyOf(effects);
    }
}
