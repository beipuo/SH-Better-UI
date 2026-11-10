// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Optional;

public interface CommandHandle {
    TaskHandle.Status status();

    boolean cancel();

    /** Present after a successful commit. */
    Optional<CommandResult> result();

    Optional<Throwable> failure();
}
