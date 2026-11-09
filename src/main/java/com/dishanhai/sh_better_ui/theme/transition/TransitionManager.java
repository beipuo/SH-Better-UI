// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.transition;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Owner-thread transition engine. The clock is injectable for deterministic animations and tests. */
public final class TransitionManager<T> implements AutoCloseable {
    private static final int MAX_QUEUE = 32;
    private static final int MAX_REENTRANT = 64;
    private final Thread owner = Thread.currentThread();
    private final LongSupplier clock;
    private final PropertyInterpolator<T> interpolator;
    private final Consumer<TransitionNotice> observer;
    private final Consumer<RuntimeException> onError;
    private final ArrayDeque<Request<T>> waiting = new ArrayDeque<>();
    private final ArrayDeque<Runnable> commands = new ArrayDeque<>();
    private T value;
    private Track<T> active;
    private Track<T> blend;
    private TransitionState state = TransitionState.IDLE;
    private double progress;
    private long sequence;
    private long lastNow = Long.MIN_VALUE;
    private long sampledAt;
    private Long pausedAt;
    private boolean dispatching;
    private boolean closed;

    public TransitionManager(T initial, LongSupplier clockMillis, PropertyInterpolator<T> interpolator,
                             Consumer<TransitionNotice> observer) {
        this(initial, clockMillis, interpolator, observer, ignored -> {});
    }

    public TransitionManager(T initial, LongSupplier clockMillis, PropertyInterpolator<T> interpolator,
                             Consumer<TransitionNotice> observer, Consumer<RuntimeException> onError) {
        value = Objects.requireNonNull(initial);
        clock = Objects.requireNonNull(clockMillis);
        this.interpolator = Objects.requireNonNull(interpolator);
        this.observer = Objects.requireNonNull(observer);
        this.onError = Objects.requireNonNull(onError);
    }

    public T value() { checkThread(); return value; }
    public T target() { checkThread(); return active == null ? value : active.to; }
    public TransitionState state() { checkThread(); return state; }
    public double progress() { checkThread(); return progress; }
    public int queuedTargets() { checkThread(); return waiting.size(); }

    /** Requests an endpoint. RETARGET samples the exact value at the new track's start time. */
    public void request(T target, TransitionSpec spec) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(spec);
        execute(() -> accept(new Request<>(target, spec)));
    }

    private void accept(Request<T> request) {
        if (closed) throw new IllegalStateException("Closed theme transition");
        long now = time();
        advance(now);
        var policy = request.spec.policy();
        if (active != null) {
            if (policy == InterruptPolicy.QUEUE || policy == InterruptPolicy.FINISH_THEN_SWITCH) {
                if (policy == InterruptPolicy.FINISH_THEN_SWITCH) waiting.clear();
                T last = waiting.isEmpty() ? active.to : waiting.getLast().target;
                if (last.equals(request.target)) return;
                if (waiting.size() >= MAX_QUEUE) throw new IllegalStateException("Theme transition queue is full");
                waiting.addLast(request);
                return;
            }
            if (active.to.equals(request.target) && policy != InterruptPolicy.SNAP
                    && request.spec.durationMillis() > 0) return;
            // Keep one old trajectory when blending. Collapse older blends to the sampled value.
            Track<T> outgoing = active;
            if (blend != null) {
                long remaining = Math.max(1, active.spec.durationMillis() - (now - active.start));
                outgoing = new Track<>(active.id, value, active.to, now, TransitionSpec.of(remaining, active.spec.easing()));
            }
            terminate(TransitionNotice.Kind.INTERRUPTED, TransitionState.INTERRUPTED);
            blend = policy == InterruptPolicy.BLEND ? outgoing : null;
        }
        waiting.clear();
        begin(request, now);
    }

    private void begin(Request<T> request, long now) {
        if (value.equals(request.target)) {
            blend = null;
            return;
        }
        active = new Track<>(++sequence, value, request.target, now, request.spec);
        sampledAt = now;
        progress = 0;
        state = TransitionState.RUNNING;
        emit(TransitionNotice.Kind.STARTED, active.id);
        if (request.spec.durationMillis() == 0 || request.spec.policy() == InterruptPolicy.SNAP) finish();
    }

    /** Advances from the supplied clock, carrying excess elapsed time across queued tracks. */
    public T update() {
        execute(() -> advance(time()));
        return value;
    }

    private void advance(long now) {
        while (active != null) {
            long elapsed = Math.max(0, now - active.start);
            if (elapsed >= active.spec.durationMillis()) {
                long finishedAt = active.start + active.spec.durationMillis();
                finish();
                while (active == null && !waiting.isEmpty()) begin(waiting.removeFirst(), finishedAt);
                continue;
            }
            if (sampledAt == now) return;
            sampledAt = now;
            progress = elapsed / (double) active.spec.durationMillis();
            try {
                T start = blend == null ? active.from : sample(blend, now);
                value = interpolate(start, active.to, active.spec.eased(progress));
                emit(TransitionNotice.Kind.UPDATED, active.id);
            } catch (RuntimeException failure) {
                terminate(TransitionNotice.Kind.CANCELLED, TransitionState.CANCELLED);
                waiting.clear();
                report(failure);
            }
            return;
        }
    }

    private T sample(Track<T> track, long now) {
        double t = Math.max(0, Math.min(1, (now - track.start) / (double) track.spec.durationMillis()));
        return interpolate(track.from, track.to, track.spec.eased(t));
    }

    private T interpolate(T from, T to, double t) {
        if (t <= 0 || from.equals(to)) return from;
        if (t >= 1) return to;
        return Objects.requireNonNull(interpolator.interpolate(from, to, t), "Transition value");
    }

    private void finish() {
        progress = 1;
        value = active.to;
        terminate(TransitionNotice.Kind.COMPLETED, TransitionState.COMPLETED);
    }

    private void terminate(TransitionNotice.Kind kind, TransitionState terminal) {
        long id = active.id;
        active = null;
        blend = null;
        state = terminal;
        emit(kind, id);
    }

    /** Freezes sampling and shifts both trajectories when resumed. */
    public void pause() {
        execute(() -> {
            if (!closed && pausedAt == null) {
                long now = time();
                advance(now);
                pausedAt = now;
            }
        });
    }

    public void resume() {
        execute(() -> {
            if (pausedAt == null) return;
            long then = pausedAt;
            pausedAt = null;
            long delay = time() - then;
            if (active != null) active = active.shift(delay);
            if (blend != null) blend = blend.shift(delay);
            sampledAt += delay;
        });
    }

    /** Stops at the sampled value and clears pending requests. */
    public void cancel() {
        execute(this::cancelNow);
    }

    /** Abandons pending work and restores a caller-validated value after failed publication. */
    public void restore(T safeValue) {
        Objects.requireNonNull(safeValue);
        execute(() -> {
            if (closed) return;
            cancelNow();
            value = safeValue;
        });
    }

    private void cancelNow() {
        waiting.clear();
        if (active != null) terminate(TransitionNotice.Kind.CANCELLED, TransitionState.CANCELLED);
        pausedAt = null;
    }

    @Override
    public void close() {
        execute(() -> {
            if (closed) return;
            closed = true;
            cancelNow();
        });
    }

    private long time() {
        if (pausedAt != null) return pausedAt;
        lastNow = Math.max(lastNow, clock.getAsLong());
        return lastNow;
    }

    private void checkThread() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Theme transition requires its owner thread");
    }

    private void execute(Runnable action) {
        checkThread();
        if (dispatching) {
            if (commands.size() >= MAX_REENTRANT) throw new IllegalStateException("Too many pending theme transition commands");
            commands.addLast(action);
            return;
        }
        dispatching = true;
        try {
            action.run();
            for (int count = 0; !commands.isEmpty() && count < MAX_REENTRANT; count++) {
                try { commands.removeFirst().run(); }
                catch (RuntimeException failure) { report(failure); }
            }
            if (!commands.isEmpty()) report(new IllegalStateException("Theme transition event loop"));
        } finally {
            commands.clear();
            dispatching = false;
        }
    }

    private void emit(TransitionNotice.Kind kind, long id) {
        try { observer.accept(new TransitionNotice(kind, id, progress)); }
        catch (RuntimeException failure) { report(failure); }
    }

    private void report(RuntimeException failure) {
        try { onError.accept(failure); }
        catch (RuntimeException ignored) { /* Diagnostic failures do not change the track. */ }
    }

    private record Request<T>(T target, TransitionSpec spec) {}

    private record Track<T>(long id, T from, T to, long start, TransitionSpec spec) {
        Track<T> shift(long delay) { return new Track<>(id, from, to, start + delay, spec); }
    }
}
