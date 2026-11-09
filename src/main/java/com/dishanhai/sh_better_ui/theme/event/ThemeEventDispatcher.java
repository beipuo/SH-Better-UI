// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.event;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Dispatches in registration order, excluding removals and deferring additions to the next event. */
public final class ThemeEventDispatcher {
    private final Thread owner = Thread.currentThread();
    private final LinkedHashSet<Listener> listeners = new LinkedHashSet<>();
    private final Consumer<RuntimeException> errors;

    public ThemeEventDispatcher(Consumer<RuntimeException> errors) {
        this.errors = Objects.requireNonNull(errors);
    }

    public Subscription observe(Predicate<ThemeEvent> filter, ThemeObserver observer) {
        checkThread();
        var listener = new Listener(Objects.requireNonNull(filter), Objects.requireNonNull(observer));
        listeners.add(listener);
        return listener;
    }

    /** The manager serializes nested operations before calling this dispatcher. */
    public void dispatch(ThemeEvent event) {
        checkThread();
        for (var listener : List.copyOf(listeners)) {
            if (listener.observer == null) continue;
            try {
                if (listener.filter.test(event)) listener.observer.onThemeEvent(event);
            } catch (RuntimeException failure) {
                errors.accept(failure);
            }
        }
    }

    public void clear() {
        checkThread();
        for (var listener : List.copyOf(listeners)) listener.close();
    }

    private void checkThread() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Theme events require their owner thread");
    }

    private final class Listener implements Subscription {
        private Predicate<ThemeEvent> filter;
        private ThemeObserver observer;

        private Listener(Predicate<ThemeEvent> filter, ThemeObserver observer) {
            this.filter = filter;
            this.observer = observer;
        }

        @Override
        public void close() {
            checkThread();
            listeners.remove(this);
            observer = null;
            filter = null;
        }
    }
}
