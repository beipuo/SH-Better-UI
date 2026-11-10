// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Serializes validation and publication, then drains notifications without nested delivery. */
final class MutationCoordinator {
    private static final int MAX_BATCHES = 64;

    enum Domain { CONFIG, STATE }

    record Change(Domain domain, UiId key, long generation) {}

    private record Batch(Set<Change> lineage, Runnable notifyListeners) {}

    private final CentralScheduler scheduler;
    private final Thread owner = Thread.currentThread();
    private final Consumer<String> diagnostics;
    private final ArrayDeque<Batch> pending = new ArrayDeque<>();
    private Set<Change> lineage = Set.of();
    private boolean writing;
    private boolean dispatching;
    private int batches;

    MutationCoordinator(CentralScheduler scheduler, Consumer<String> diagnostics) {
        this.scheduler = scheduler;
        this.diagnostics = diagnostics;
    }

    <T> T write(Supplier<T> body) {
        scheduler.assertMutationPhase();
        assertOutsideValidation();
        writing = true;
        T result;
        try {
            result = body.get();
        } finally {
            writing = false;
        }
        drain();
        return result;
    }

    void assertOutsideValidation() {
        if (Thread.currentThread() == owner && writing) {
            throw new IllegalStateException("Cannot perform UI side effects during transaction validation");
        }
    }

    /** Must be called before publishing a change, when no more validation can fail. */
    void stage(Set<Change> changes, Runnable notifyListeners) {
        if (changes.isEmpty()) {
            return;
        }
        var next = new LinkedHashSet<>(lineage);
        for (var change : changes) {
            if (!next.add(change)) {
                String message = "Cyclic UI value update " + lineage + " -> " + change;
                diagnostics.accept(message);
                throw new IllegalStateException(message);
            }
        }
        if (batches + pending.size() >= MAX_BATCHES) {
            String message = "UI value notification batch limit exceeded";
            diagnostics.accept(message);
            throw new IllegalStateException(message);
        }
        pending.addLast(new Batch(next, notifyListeners));
    }

    private void drain() {
        if (dispatching || pending.isEmpty()) {
            return;
        }
        dispatching = true;
        try {
            Batch batch;
            while ((batch = pending.pollFirst()) != null) {
                batches++;
                lineage = batch.lineage();
                batch.notifyListeners().run();
            }
        } finally {
            lineage = Set.of();
            pending.clear();
            batches = 0;
            dispatching = false;
        }
    }
}
