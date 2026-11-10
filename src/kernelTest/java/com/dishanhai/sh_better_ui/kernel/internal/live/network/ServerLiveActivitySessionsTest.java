// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.live.network;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.live.network.NetworkLiveActivity;
import com.dishanhai.sh_better_ui.kernel.api.live.network.NetworkLiveActivity.ActionOffer;
import com.dishanhai.sh_better_ui.kernel.api.live.network.NetworkLiveActivity.Status;
import com.dishanhai.sh_better_ui.kernel.api.live.network.ServerLiveActivityAction;
import com.dishanhai.sh_better_ui.kernel.internal.live.network.LiveActivityPackets.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ServerLiveActivitySessionsTest {
    private static final UiId JOB = UiId.parse("demo:job");
    private static final UiId CANCEL = UiId.parse("demo:cancel");

    @Test
    void publishesBoundedSnapshotsAndOrderedDeltas() {
        var sessions = new ServerLiveActivitySessions<String>(failure -> fail(failure));
        var packets = new ArrayList<ServerMessage>();
        var player = new Object();

        assertTrue(sessions.publish(player, activity(JOB, null), null, packets::add));
        var first = assertInstanceOf(Snapshot.class, packets.get(0));
        assertEquals(1, first.sequence());
        assertEquals(JOB, first.activities().get(0).data().id());
        assertTrue(sessions.publish(player, new NetworkLiveActivity(JOB, "Job", "", Status.RUNNING, 0.6f, null),
                null, packets::add));
        assertInstanceOf(Update.class, packets.get(1));
        for (int i = 1; i < LiveActivityPackets.MAX_ACTIVITIES; i++) {
            assertTrue(sessions.publish(player, activity(new UiId("demo", "job_" + i), null), null, packets::add));
            assertInstanceOf(Create.class, packets.get(i + 1));
        }
        assertFalse(sessions.publish(player, activity(UiId.parse("demo:overflow"), null), null, packets::add));
        assertTrue(sessions.remove(player, JOB, packets::add));
        assertInstanceOf(Remove.class, packets.get(packets.size() - 1));
        assertFalse(sessions.remove(player, JOB, packets::add));
        assertTrue(sessions.snapshot(player, packets::add));
        assertEquals(LiveActivityPackets.MAX_ACTIVITIES - 1,
                assertInstanceOf(Snapshot.class, packets.get(packets.size() - 1)).activities().size());
        for (int i = 0; i < packets.size(); i++) {
            assertEquals(i + 1L, packets.get(i).sequence());
            assertEquals(first.session(), packets.get(i).session());
        }
    }

    @Test
    void checksNegotiatedSessionLatestRevisionOfferedActionAndAuthenticatedSender() {
        var sessions = new ServerLiveActivitySessions<String>(failure -> fail(failure));
        var packets = new ArrayList<ServerMessage>();
        var connection = new Object();
        var invokedBy = new ArrayList<String>();
        var action = new ServerLiveActivityAction<String>("actual player"::equals, invokedBy::add);
        assertThrows(IllegalArgumentException.class, () ->
                sessions.publish(connection, activity(JOB, CANCEL), null, packets::add));
        sessions.publish(connection, activity(JOB, CANCEL), action, packets::add);
        var snapshot = (Snapshot) packets.get(0);
        UUID session = snapshot.session();
        long revision = snapshot.activities().get(0).revision();

        sessions.receive(new Object(), "actual player", new Action(session, 1, JOB, revision, CANCEL), packets::add);
        sessions.receive(connection, "actual player", new Action(UUID.randomUUID(), 1, JOB, revision, CANCEL), packets::add);
        assertEquals(1, packets.size());
        sessions.receive(connection, "other player", new Action(session, 1, JOB, revision, CANCEL), packets::add);
        assertEquals(Outcome.DENIED, lastResult(packets).outcome());
        sessions.receive(connection, "actual player",
                new Action(session, 2, JOB, revision, UiId.parse("demo:unknown")), packets::add);
        assertEquals(Outcome.DENIED, lastResult(packets).outcome());
        sessions.receive(connection, "actual player", new Action(session, 3, JOB, revision + 1, CANCEL), packets::add);
        assertEquals(Outcome.STALE, lastResult(packets).outcome());
        var valid = new Action(session, 4, JOB, revision, CANCEL);
        sessions.receive(connection, "actual player", valid, packets::add);
        assertEquals(Outcome.ACCEPTED, lastResult(packets).outcome());
        int afterSuccess = packets.size();
        sessions.receive(connection, "actual player", valid, packets::add);
        assertEquals(afterSuccess, packets.size());
        assertEquals(List.of("actual player"), invokedBy);
    }

    @Test
    void disconnectDropsBindingsAndReconnectUsesANewEpoch() {
        var sessions = new ServerLiveActivitySessions<String>(failure -> fail(failure));
        var packets = new ArrayList<ServerMessage>();
        var connection = new Object();
        var executions = new AtomicInteger();
        var action = new ServerLiveActivityAction<String>(sender -> true, sender -> executions.incrementAndGet());
        sessions.publish(connection, activity(JOB, CANCEL), action, packets::add);
        UUID oldSession = packets.get(0).session();
        sessions.disconnect(connection);
        assertFalse(sessions.snapshot(connection, packets::add));
        sessions.publish(connection, activity(JOB, CANCEL), action, packets::add);
        assertNotEquals(oldSession, packets.get(1).session());
        sessions.receive(connection, "player", new Action(oldSession, 1, JOB, 1, CANCEL), packets::add);
        assertEquals(0, executions.get());
        sessions.clear();
        assertFalse(sessions.snapshot(connection, packets::add));
    }

    @Test
    void handlerCanPublishAReplacementBeforeSendingItsResult() {
        var sessions = new ServerLiveActivitySessions<String>(failure -> fail(failure));
        var packets = new ArrayList<ServerMessage>();
        var connection = new Object();
        var action = new ServerLiveActivityAction<String>(sender -> true, sender ->
                sessions.publish(connection, activity(JOB, null), null, packets::add));
        sessions.publish(connection, activity(JOB, CANCEL), action, packets::add);
        UUID session = packets.get(0).session();
        sessions.receive(connection, "player", new Action(session, 1, JOB, 1, CANCEL), packets::add);
        assertInstanceOf(Update.class, packets.get(1));
        assertEquals(3L, lastResult(packets).sequence());
        assertEquals(Outcome.ACCEPTED, lastResult(packets).outcome());
        sessions.receive(connection, "player", new Action(session, 2, JOB, 1, CANCEL), packets::add);
        assertEquals(Outcome.STALE, lastResult(packets).outcome());
    }

    private static NetworkLiveActivity activity(UiId id, UiId action) {
        return new NetworkLiveActivity(id, "Job", "", Status.RUNNING, -1,
                action == null ? null : new ActionOffer(action, "Cancel"));
    }

    private static ActionResult lastResult(List<ServerMessage> packets) {
        return assertInstanceOf(ActionResult.class, packets.get(packets.size() - 1));
    }
}
