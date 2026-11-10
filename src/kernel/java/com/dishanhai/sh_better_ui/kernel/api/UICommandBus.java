// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.function.Consumer;

public interface UICommandBus {
    /** Registers a handler owned by the calling module. The permission may be null. */
    <T> Subscription register(CommandKey<T> key, UIPermission required, UICommandHandler<T> handler);

    default <T> Subscription register(CommandKey<T> key, UICommandHandler<T> handler) {
        return register(key, null, handler);
    }

    <T> CommandResult execute(UICommand<T> command);

    <T> CommandHandle submit(UICommand<T> command, long timeoutNanos);

    CommandResult transaction(Consumer<UITransaction> prepare);
}
