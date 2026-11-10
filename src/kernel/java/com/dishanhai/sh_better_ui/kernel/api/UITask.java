// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Objects;
import java.util.function.Function;

/** An immutable submission. Delays and timeouts are relative to submission on the scheduler clock. */
public record UITask(ScopeHandle scope, UIPhase phase, UIPriority priority, long delayNanos, long timeoutNanos,
                     TaskHandle dependency, UiId replacementKey, Work work) {
    public UITask {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(work, "work");
        if (delayNanos < 0 || timeoutNanos < 0) {
            throw new IllegalArgumentException("UI task delay and timeout must be nonnegative");
        }
    }

    public sealed interface Work permits UIWork, BackgroundWork {}

    public record UIWork(Runnable action) implements Work {
        public UIWork {
            Objects.requireNonNull(action, "action");
        }
    }

    /**
     * Computes from immutable inputs. The continuation runs in STATE_MUTATION or
     * RENDER_PREPARATION, beginning with the frame after the worker is dispatched.
     * A null continuation completes on the worker. Long computations should check the task status.
     */
    public record BackgroundWork(Function<TaskHandle, Runnable> compute) implements Work {
        public BackgroundWork {
            Objects.requireNonNull(compute, "compute");
        }
    }

    public static UITask ui(ScopeHandle scope, UIPhase phase, UIPriority priority, Runnable action) {
        return new UITask(scope, phase, priority, 0, 0, null, null, new UIWork(action));
    }

    public static UITask background(ScopeHandle scope, UIPhase phase, UIPriority priority,
                                    Function<TaskHandle, Runnable> compute) {
        return new UITask(scope, phase, priority, 0, 0, null, null, new BackgroundWork(compute));
    }

    public UITask delayed(long nanos) {
        return new UITask(scope, phase, priority, nanos, timeoutNanos, dependency, replacementKey, work);
    }

    /** Zero disables expiry. A started UI callback runs to completion. */
    public UITask withTimeout(long nanos) {
        return new UITask(scope, phase, priority, delayNanos, nanos, dependency, replacementKey, work);
    }

    /** The predecessor must come from the same module and complete successfully. */
    public UITask after(TaskHandle predecessor) {
        return new UITask(scope, phase, priority, delayNanos, timeoutNanos,
                Objects.requireNonNull(predecessor, "predecessor"), replacementKey, work);
    }

    /** Replaces work with the same issuer, target scope generation and key. */
    public UITask replacing(UiId key) {
        return new UITask(scope, phase, priority, delayNanos, timeoutNanos, dependency,
                Objects.requireNonNull(key, "key"), work);
    }
}
