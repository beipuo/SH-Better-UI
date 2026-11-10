// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.TaskHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIPhase;
import com.dishanhai.sh_better_ui.kernel.api.UIPriority;
import com.dishanhai.sh_better_ui.kernel.api.UITask;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.*;
import com.dishanhai.sh_better_ui.kernel.api.live.NotificationService;
import com.dishanhai.sh_better_ui.kernel.api.live.network.NetworkLiveActivity;
import com.dishanhai.sh_better_ui.kernel.internal.live.network.LiveActivityPackets;
import com.dishanhai.sh_better_ui.kernel.internal.live.network.LiveActivityReceiver;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Client mirror of the current native connection. The server owns the authoritative revision and action result. */
final class ClientLiveNetwork {
    private static final UiId SESSION = UiId.parse("sh_better_ui:live/remote_session");
    private static final UiId RESULT_KEY = UiId.parse("sh_better_ui:live/action_result");
    private final KernelHost host;
    private final ClientScreens screens;
    private final LiveSurfaceRuntime live;
    private final LiveActivityReceiver receiver = new LiveActivityReceiver();
    private final Map<UiId, Remote> activities = new LinkedHashMap<>();
    private final Map<Long, Pending> pending = new LinkedHashMap<>();
    private ModuleContext module;
    private ScopeHandle scope;
    private Consumer<LiveActivityPackets.Action> reply;
    private long nextRequest;

    ClientLiveNetwork(KernelHost host, ClientScreens screens, LiveSurfaceRuntime live) {
        this.host = host;
        this.screens = screens;
        this.live = live;
    }

    void initialize(ModuleContext module) { this.module = module; }

    void receive(Object connection, LiveActivityPackets.ServerMessage message, Consumer<LiveActivityPackets.Action> reply) {
        host.assertOwner();
        if (!screens.acceptsConnection(connection)) return;
        if (scope == null) {
            if (!(message instanceof LiveActivityPackets.Snapshot)) return;
            scope = screens.session(module, SESSION);
            scope.track((AutoCloseable) this::reset);
        }
        if (!receiver.accept(message)) return;
        this.reply = reply;
        if (message instanceof LiveActivityPackets.Snapshot snapshot) {
            var remaining = new HashSet<UiId>();
            for (var activity : snapshot.activities()) remaining.add(activity.data().id());
            for (var id : List.copyOf(activities.keySet())) if (!remaining.contains(id)) remove(id);
            for (var activity : snapshot.activities()) upsert(activity);
        } else if (message instanceof LiveActivityPackets.Create create) {
            if (!activities.containsKey(create.activity().data().id())) upsert(create.activity());
        } else if (message instanceof LiveActivityPackets.Update update) {
            upsert(update.activity());
        } else if (message instanceof LiveActivityPackets.Remove remove) {
            remove(remove.id());
        } else if (message instanceof LiveActivityPackets.ActionResult result) {
            result(result);
        }
    }

    private void upsert(LiveActivityPackets.VersionedActivity value) {
        var data = value.data();
        var existing = activities.get(data.id());
        if (existing != null && existing.version.revision() > value.revision()) return;
        boolean replace = existing == null || existing.handle.snapshot().lifecycle().terminal();
        if (existing == null && activities.size() >= LiveActivityPackets.MAX_ACTIVITIES) return;
        var content = content(value);
        try {
            var handle = replace ? live.publishRemote(module, scope, new Request(localId(data.id()), content,
                    Priority.NORMAL, Duration.ZERO, true, action -> send(data.id(), action.id()))) : existing.handle;
            if (!replace) handle.update(content);
            activities.put(data.id(), new Remote(value, handle));
        } catch (IllegalStateException | IllegalArgumentException failure) {
            host.report("Remote live activity admission " + data.id(), failure);
        }
    }

    private static Content content(LiveActivityPackets.VersionedActivity value) {
        var data = value.data();
        var label = Text.key("sh_better_ui.live.remote." + data.status().name().toLowerCase(java.util.Locale.ROOT));
        var detail = data.detail().isBlank() ? label : ClientLivePresentation.bounded(
                Component.translatable("sh_better_ui.live.remote.detail",
                        Component.translatable(label.value()), data.detail()).getString(), Text.literal(data.detail()));
        var controls = data.action() == null ? List.<Control>of()
                : List.of(new Control(localAction(value.revision()), Text.literal(data.action().label())));
        return new Content(Text.literal(data.title()), detail, null, data.progress(), false, controls, List.of());
    }

    private static UiId localId(UiId id) { return new UiId("sh_better_ui", "live/remote/" + id.namespace() + "/" + id.path()); }
    private static UiId localAction(long revision) { return new UiId("sh_better_ui", "live/remote_action/" + revision); }

    private void remove(UiId id) {
        var activity = activities.remove(id);
        if (activity != null) activity.handle.complete();
        pending.values().removeIf(item -> {
            if (!item.action.activityId().equals(id)) return false;
            item.timeout.cancel();
            return true;
        });
    }

    private void send(UiId id, UiId selected) {
        var remote = activities.get(id);
        if (scope == null || !scope.isOpen() || remote == null || remote.version.data().action() == null
                || !selected.equals(localAction(remote.version.revision())) || pending.size() >= 16) return;
        long sequence = ++nextRequest;
        var action = new LiveActivityPackets.Action(receiver.session(), sequence, id, remote.version.revision(),
                remote.version.data().action().id());
        var timeout = module.engine().scheduler().submit(UITask.ui(scope, UIPhase.STATE_MUTATION, UIPriority.NORMAL, () -> {
            if (pending.remove(sequence) != null) notice(sequence, Text.key("sh_better_ui.live.action.timeout"));
        }).delayed(Duration.ofSeconds(8).toNanos()));
        pending.put(sequence, new Pending(action, timeout));
        try {
            reply.accept(action);
        } catch (RuntimeException failure) {
            pending.remove(sequence);
            timeout.cancel();
            throw failure;
        }
    }

    private void result(LiveActivityPackets.ActionResult result) {
        var expected = pending.get(result.requestId());
        if (expected == null || !expected.action.activityId().equals(result.activityId())
                || !expected.action.actionId().equals(result.actionId())) return;
        pending.remove(result.requestId());
        expected.timeout.cancel();
        var outcome = Text.key("sh_better_ui.live.action." + result.outcome().name().toLowerCase(java.util.Locale.ROOT));
        notice(result.requestId(), outcome);
    }

    private void notice(long request, Text outcome) {
        try {
            live.publish(module, scope, new NotificationService.Request(
                    new UiId("sh_better_ui", "live/action_result/" + request), Content.label(outcome),
                    Priority.NORMAL, Duration.ofSeconds(3), RESULT_KEY, NotificationService.Merge.REPLACE, null));
        } catch (IllegalStateException limited) {
            // The user's quiet/disabled/capacity policy controls whether a result is presented.
        }
    }

    private void reset() {
        scope = null;
        reply = null;
        nextRequest = 0;
        activities.clear();
        pending.clear();
        receiver.reset();
    }

    private record Remote(LiveActivityPackets.VersionedActivity version, Handle handle) {}
    private record Pending(LiveActivityPackets.Action action, TaskHandle timeout) {}
}
