// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.live.network;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.live.network.NetworkLiveActivity;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class LiveActivityPackets {
    public static final int MAX_ACTIVITIES = 32;

    private LiveActivityPackets() {}

    public sealed interface ServerMessage permits Snapshot, Create, Update, Remove, ActionResult {
        UUID session();
        long sequence();
    }

    public record VersionedActivity(long revision, NetworkLiveActivity data) {
        public VersionedActivity {
            requirePositive(revision);
            Objects.requireNonNull(data, "data");
        }
    }

    public record Snapshot(UUID session, long sequence, List<VersionedActivity> activities) implements ServerMessage {
        public Snapshot {
            requireHeader(session, sequence);
            if (activities.size() > MAX_ACTIVITIES) throw new IllegalArgumentException("Too many activities");
            activities = List.copyOf(activities);
            var ids = new HashSet<UiId>();
            for (VersionedActivity activity : activities) {
                if (activity.revision() > sequence || !ids.add(activity.data().id())) {
                    throw new IllegalArgumentException("Invalid activity in snapshot");
                }
            }
        }
    }

    public record Create(UUID session, long sequence, VersionedActivity activity) implements ServerMessage {
        public Create {
            requireHeader(session, sequence);
            requireRevision(sequence, activity);
        }
    }

    public record Update(UUID session, long sequence, VersionedActivity activity) implements ServerMessage {
        public Update {
            requireHeader(session, sequence);
            requireRevision(sequence, activity);
        }
    }

    public record Remove(UUID session, long sequence, UiId id) implements ServerMessage {
        public Remove {
            requireHeader(session, sequence);
            Objects.requireNonNull(id, "id");
        }
    }

    public record Action(UUID session, long requestId, UiId activityId, long revision, UiId actionId) {
        public Action {
            requireHeader(session, requestId);
            requirePositive(revision);
            Objects.requireNonNull(activityId, "activityId");
            Objects.requireNonNull(actionId, "actionId");
        }
    }

    public enum Outcome { ACCEPTED, DENIED, STALE, FAILED }

    public record ActionResult(UUID session, long sequence, long requestId, UiId activityId, UiId actionId,
                               Outcome outcome) implements ServerMessage {
        public ActionResult {
            requireHeader(session, sequence);
            requirePositive(requestId);
            Objects.requireNonNull(activityId, "activityId");
            Objects.requireNonNull(actionId, "actionId");
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    private static void requireHeader(UUID session, long sequence) {
        Objects.requireNonNull(session, "session");
        requirePositive(sequence);
    }

    private static void requirePositive(long value) {
        if (value < 1) throw new IllegalArgumentException("Expected a positive sequence, revision or request ID");
    }

    private static void requireRevision(long sequence, VersionedActivity activity) {
        if (sequence != activity.revision()) throw new IllegalArgumentException("Delta revision must match sequence");
    }
}
