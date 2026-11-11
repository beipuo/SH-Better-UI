// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.live;

import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.*;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

/** A bounded, owner-scoped notification queue on the same timeline as live activities. */
public interface NotificationService extends UIService {
    /** Lookup through the module's Unified UI engine. */
    ServiceKey<NotificationService> KEY =
            new ServiceKey<>(UiId.parse("sh_better_ui:live_notifications"), NotificationService.class, 1);
    CapabilityKey CAPABILITY = new CapabilityKey(KEY.id());

    /**
     * Publishes on the UI thread and returns the existing handle when merged in the same scope.
     * Throws for disabled notifications, exhausted capacity, a duplicate ID or unauthorized scope/priority.
     */
    Handle publish(ModuleContext module, ScopeHandle scope, Request request);

    /** Dismisses this module's notifications in the exact supplied scope. UI thread only. */
    void dismissAll(ModuleContext module, ScopeHandle scope);

    /** Behavior when an active publication shares the same owner, scope generation and deduplication key. */
    enum Merge { KEEP, REPLACE, COUNT }

    /**
     * A zero duration uses the user default. Explicit durations must be 600 ms to 30 s.
     * The display clock starts on first presentation. A queued notification has a separate bounded lifetime.
     */
    record Request(UiId id, Content content, Priority priority, Duration duration, UiId deduplicationKey,
                   Merge merge, Consumer<Action> onAction) {
        public Request {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(priority, "priority");
            Objects.requireNonNull(duration, "duration");
            Objects.requireNonNull(merge, "merge");
            if (!duration.isZero() && (duration.compareTo(Duration.ofMillis(600)) < 0
                    || duration.compareTo(Duration.ofSeconds(30)) > 0)) {
                throw new IllegalArgumentException("Notification duration out of range");
            }
        }
    }

    /** Capability for one queued or visible notification. */
    interface Handle {
        /** Thread-safe immutable publication revision and merge count. */
        Snapshot snapshot();
        /** Thread-safe content replacement. Throws after dismissal or when the central queue is full. */
        void update(Content content);
        /** Observes changes on the UI thread. Closing the scope ends delivery. */
        Subscription observe(Consumer<Snapshot> listener);
        /** Dismisses the notification. UI thread only. */
        void dismiss();
    }
}
