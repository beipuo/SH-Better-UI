// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.EngineState;
import com.dishanhai.sh_better_ui.kernel.api.UIEngine;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Publication boundary between client startup and the public facade. */
public final class EnginePublication {
    static final EnginePublication CLIENT = new EnginePublication();

    private final AtomicReference<Installation> slot = new AtomicReference<>();

    EnginePublication() {
    }

    /** Returns the current client engine, or null before startup. */
    public static UIEngine clientEngine() {
        return CLIENT.current();
    }

    UIEngine current() {
        Installation installation = slot.get();
        return installation == null ? null : installation.engine;
    }

    /** Called by the kernel host after successful initialization. */
    Installation install(UIEngine initialized) {
        Objects.requireNonNull(initialized, "initialized");
        if (initialized.lifecycle().state() != EngineState.READY) {
            throw new IllegalStateException("Only a ready client UI engine can be installed");
        }
        var installation = new Installation(initialized);
        if (!slot.compareAndSet(null, installation)) {
            throw new IllegalStateException("A client UI engine is already installed");
        }
        return installation;
    }

    final class Installation implements AutoCloseable {
        private final UIEngine engine;

        private Installation(UIEngine engine) {
            this.engine = engine;
        }

        @Override
        public void close() {
            slot.compareAndSet(this, null);
        }
    }
}
