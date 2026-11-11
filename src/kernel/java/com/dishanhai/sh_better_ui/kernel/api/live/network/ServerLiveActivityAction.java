// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.live.network;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The permission check and handler for an offered action. Both run on the server game thread with
 * the actual sending player. The permission check should inspect current server state.
 */
public record ServerLiveActivityAction<P>(Predicate<? super P> permission, Consumer<? super P> execute) {
    public ServerLiveActivityAction {
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(execute, "execute");
    }
}
