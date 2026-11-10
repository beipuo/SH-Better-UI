// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.live;

import com.dishanhai.sh_better_ui.kernel.api.*;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Publishes scope-owned activities to the Unified UI Kernel. Obtain via {@code context.engine().require(KEY)}. */
public interface ActivityService extends UIService {
    /** Java 17 service contract and capability ID. */
    ServiceKey<ActivityService> KEY = new ServiceKey<>(UiId.parse("sh_better_ui:live_activities"), ActivityService.class, 1);
    CapabilityKey CAPABILITY = new CapabilityKey(KEY.id());
    /** Normalized seek gesture dispatched to an opted-in activity. */
    UiId SEEK = UiId.parse("sh_better_ui:live/seek");

    /**
     * Publishes on the UI thread. The ID must be unused and in the module's namespace.
     * @throws SecurityException for a foreign scope, namespace or ungranted critical priority
     * @throws IllegalArgumentException for a duplicate ID
     * @throws IllegalStateException for a closed scope or exhausted capacity
     */
    Handle publish(ModuleContext module, ScopeHandle scope, Request request);

    /**
     * Queues up to 128 complete snapshots as one UI-thread commit. Observers see the batch after all
     * members commit. Every handle must belong to the supplied scope, and a stale member fails the
     * task before publishing any revision. Independent handle updates are ordered by their scheduler jobs.
     * @throws SecurityException for a foreign handle or scope
     * @throws IllegalArgumentException for an empty or oversized batch
     */
    TaskHandle updateAll(ModuleContext module, ScopeHandle scope, Map<Handle, Content> contents);

    /** Arbitration tier. Critical publication requires a host grant. */
    enum Priority { BACKGROUND, LOW, NORMAL, HIGH, CRITICAL }

    /** Paused activities retain their data and may resume. The other non-running states are terminal. */
    enum Lifecycle {
        RUNNING, PAUSED, COMPLETED, CANCELLED, TIMED_OUT, DISMISSED;
        /** Whether the handle has ended. */
        public boolean terminal() { return this != RUNNING && this != PAUSED; }
    }

    /** Origin used by the plan and diagnostics. */
    enum Kind { ACTIVITY, NOTIFICATION, REMOTE }

    /** Display value or a resource-pack translation key. Empty detail text is allowed. */
    record Text(String value, boolean translated) {
        public Text {
            Objects.requireNonNull(value, "value");
            if (value.length() > 512 || translated && (value.isBlank() || value.codePoints().anyMatch(Character::isWhitespace))) {
                throw new IllegalArgumentException("Invalid activity text");
            }
        }

        /** Creates a user-provided literal, for example an artist name. */
        public static Text literal(String value) { return new Text(value, false); }
        /** Resolves a resource-pack key on the UI thread at draw time. */
        public static Text key(String value) { return new Text(value, true); }
    }

    /** An expanded-surface control. Its ID must be unique within the content. */
    record Control(UiId id, Text label) {
        public Control {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
            if (id.equals(SEEK) || label.value().isBlank()) throw new IllegalArgumentException("Invalid activity control");
        }
    }

    /**
     * Immutable display model. Icon is an optional texture resource path. Progress is -1 when absent or [0,1].
     * Seek requires progress. Up to four controls and sixteen normalized rhythm bands are supported.
     */
    record Content(Text title, Text detail, UiId icon, double progress, boolean seekable,
                   List<Control> controls, List<Float> rhythm, SurfaceTokens appearance) {
        public Content {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(detail, "detail");
            if (title.value().isBlank() || !Double.isFinite(progress) || progress > 1 || progress < 0 && progress != -1
                    || seekable && progress < 0 || controls.size() > 4 || rhythm.size() > 16) {
                throw new IllegalArgumentException("Invalid activity content");
            }
            controls = List.copyOf(controls);
            rhythm = List.copyOf(rhythm);
            var ids = new HashSet<UiId>();
            for (var control : controls) {
                if (!ids.add(control.id())) throw new IllegalArgumentException("Duplicate activity control " + control.id());
            }
            for (float band : rhythm) {
                if (!Float.isFinite(band) || band < 0 || band > 1) throw new IllegalArgumentException("Invalid rhythm band");
            }
        }

        /** Uses the user's current palette and sizing. */
        public Content(Text title, Text detail, UiId icon, double progress, boolean seekable,
                       List<Control> controls, List<Float> rhythm) {
            this(title, detail, icon, progress, seekable, controls, rhythm, null);
        }

        /** Replaces this publication's appearance. Null restores the user's theme. High contrast has precedence. */
        public Content withAppearance(SurfaceTokens tokens) {
            return new Content(title, detail, icon, progress, seekable, controls, rhythm, tokens);
        }

        /** Creates a title-only display without controls or progress. */
        public static Content label(Text title) { return new Content(title, Text.literal(""), null, -1, false, List.of(), List.of()); }
    }

    /** Owner callback input. The seek fraction is [0,1] for {@link #SEEK} and -1 for button actions. */
    record Action(UiId id, double fraction) {
        public Action {
            Objects.requireNonNull(id, "id");
            if (id.equals(SEEK) ? !Double.isFinite(fraction) || fraction < 0 || fraction > 1 : fraction != -1) {
                throw new IllegalArgumentException("Invalid activity action");
            }
        }
    }

    /**
     * Publication options. Zero timeout lasts until the owner or scope ends the activity. Nonzero timeout uses
     * the kernel's monotonic clock, including paused time. Null callback disables controls.
     */
    record Request(UiId id, Content initial, Priority priority, Duration timeout, boolean interruptible,
                   Consumer<Action> onAction) {
        public Request {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(initial, "initial");
            Objects.requireNonNull(priority, "priority");
            validateDuration(timeout, Duration.ZERO, Duration.ofDays(7));
        }
    }

    /** Immutable state at one revision. Instance distinguishes re-publications of the same ID. */
    record Snapshot(UiId id, UiId owner, long instance, long revision, Kind kind, Lifecycle lifecycle,
                    Priority priority, Content content, boolean presented, int occurrences, Set<UIDirtyFlag> invalidations) {
        public Snapshot { invalidations = Set.copyOf(invalidations); }
    }

    /** Owner capability for one publication. Reusing an ID never revives an earlier handle. */
    interface Handle {
        /** Latest immutable revision. Safe on any thread, including after scope close. */
        Snapshot snapshot();
        /**
         * Queues a complete content revision. Safe on background threads. Pending updates coalesce to the latest
         * accepted value, and equal contents are deduplicated. Throws if closed or the central queue is full.
         */
        void update(Content content);
        /** Subscribes on the UI thread. Observes committed changes, starting after this call. */
        Subscription observe(Consumer<Snapshot> listener);
        /** Stops presentation until resumed. UI thread only. */
        void pause();
        /** Resumes eligibility. UI thread only. */
        void resume();
        /** Ends normally and releases owned resources. UI thread only. */
        void complete();
        /** Ends without a successful result. UI thread only. */
        void cancel();
        /** Hides presentation while retaining the underlying activity. UI thread only. */
        void dismissPresentation();
        /** Restores presentation eligibility. UI thread only. */
        void showPresentation();
        /**
         * Requests an eligible shape for this activity. User input, viewport and policy may override it.
         * HIDDEN dismisses presentation. SPLIT and SUSPENDED are selected by the kernel.
         * @throws IllegalArgumentException for a kernel-only shape
         */
        void requestPresentation(SurfacePresentation presentation);
    }

    private static void validateDuration(Duration value, Duration min, Duration max) {
        Objects.requireNonNull(value, "duration");
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException("Activity duration out of range");
        }
    }
}
