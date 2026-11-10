// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.live.network;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.live.network.NetworkLiveActivity;
import com.dishanhai.sh_better_ui.kernel.api.live.network.ServerLiveActivityAction;
import com.dishanhai.sh_better_ui.kernel.internal.live.network.LiveActivityPackets.*;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Server-thread-only authority for the currently negotiated play connections. */
public final class ServerLiveActivitySessions<P> {
    private final Map<Object, Session<P>> sessions = new IdentityHashMap<>();
    private final Consumer<RuntimeException> actionFailure;

    public ServerLiveActivitySessions(Consumer<RuntimeException> actionFailure) {
        this.actionFailure = Objects.requireNonNull(actionFailure, "actionFailure");
    }

    public boolean publish(Object connection, NetworkLiveActivity data, ServerLiveActivityAction<P> action,
                           Consumer<ServerMessage> send) {
        Objects.requireNonNull(connection, "connection");
        if ((data.action() == null) != (action == null)) {
            throw new IllegalArgumentException("An offered action requires a matching server binding");
        }
        var session = sessions.get(connection);
        boolean bootstrap = session == null;
        if (bootstrap) session = new Session<>();
        var old = session.activities.get(data.id());
        if (old == null && session.activities.size() >= LiveActivityPackets.MAX_ACTIVITIES) return false;
        if (old != null && old.activity().data().equals(data) && Objects.equals(old.action(), action)) return true;

        long sequence = ++session.sequence;
        var activity = new VersionedActivity(sequence, data);
        session.activities.put(data.id(), new Entry<>(activity, action));
        if (bootstrap) {
            sessions.put(connection, session);
            send.accept(snapshot(session));
        } else if (old == null) {
            send.accept(new Create(session.epoch, sequence, activity));
        } else {
            send.accept(new Update(session.epoch, sequence, activity));
        }
        return true;
    }

    public boolean remove(Object connection, UiId id, Consumer<ServerMessage> send) {
        var session = sessions.get(connection);
        if (session == null || session.activities.remove(id) == null) return false;
        send.accept(new Remove(session.epoch, ++session.sequence, id));
        return true;
    }

    public boolean snapshot(Object connection, Consumer<ServerMessage> send) {
        var session = sessions.get(connection);
        if (session == null) return false;
        session.sequence++;
        send.accept(snapshot(session));
        return true;
    }

    public void receive(Object connection, P sender, Action request, Consumer<ServerMessage> send) {
        var session = sessions.get(connection);
        if (session == null || !session.epoch.equals(request.session()) || request.requestId() <= session.lastRequest) return;
        session.lastRequest = request.requestId();
        Outcome outcome = execute(connection, session, Objects.requireNonNull(sender, "sender"), request);
        if (sessions.get(connection) == session) {
            send.accept(new ActionResult(session.epoch, ++session.sequence, request.requestId(), request.activityId(),
                    request.actionId(), outcome));
        }
    }

    public void disconnect(Object connection) {
        sessions.remove(connection);
    }

    public void clear() {
        sessions.clear();
    }

    private Outcome execute(Object connection, Session<P> session, P sender, Action request) {
        var entry = session.activities.get(request.activityId());
        if (entry == null || entry.activity().revision() != request.revision()) return Outcome.STALE;
        if (entry.action() == null || !entry.activity().data().action().id().equals(request.actionId())) {
            return Outcome.DENIED;
        }
        try {
            if (!entry.action().permission().test(sender)) return Outcome.DENIED;
            if (sessions.get(connection) != session || session.activities.get(request.activityId()) != entry) {
                return Outcome.STALE;
            }
            entry.action().execute().accept(sender);
            return Outcome.ACCEPTED;
        } catch (RuntimeException failure) {
            actionFailure.accept(failure);
            return Outcome.FAILED;
        }
    }

    private static Snapshot snapshot(Session<?> session) {
        return new Snapshot(session.epoch, session.sequence, session.activities.values().stream()
                .map(Entry::activity).toList());
    }

    private record Entry<P>(VersionedActivity activity, ServerLiveActivityAction<P> action) {}

    private static final class Session<P> {
        private final UUID epoch = UUID.randomUUID();
        private final Map<UiId, Entry<P>> activities = new LinkedHashMap<>();
        private long sequence;
        private long lastRequest;
    }
}
