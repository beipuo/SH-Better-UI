// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal.live.network;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.live.network.NetworkLiveActivity;
import com.dishanhai.sh_better_ui.kernel.api.live.network.NetworkLiveActivity.ActionOffer;
import com.dishanhai.sh_better_ui.kernel.api.live.network.NetworkLiveActivity.Status;
import com.dishanhai.sh_better_ui.kernel.internal.live.network.LiveActivityPackets.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LiveActivityProtocolTest {
    private static final UUID SESSION = new UUID(1, 2);
    private static final UiId ID = UiId.parse("demo:queue/job");
    private static final UiId CANCEL = UiId.parse("demo:cancel");
    private static final NetworkLiveActivity ACTIVITY = new NetworkLiveActivity(ID, "合成任务", "已提交",
            Status.RUNNING, 0.25f, new ActionOffer(CANCEL, "取消"));

    @Test
    void roundTripsEveryMessageType() throws IOException {
        var activity = new VersionedActivity(1, ACTIVITY);
        for (ServerMessage message : List.of(new Snapshot(SESSION, 1, List.of(activity)),
                new Create(SESSION, 1, activity), new Update(SESSION, 1, activity),
                new Remove(SESSION, 2, ID), new ActionResult(SESSION, 3, 1, ID, CANCEL, Outcome.STALE))) {
            assertEquals(message, LiveActivityWire.decodeServer(LiveActivityWire.encode(message)));
        }
        var action = new Action(SESSION, 4, ID, 1, CANCEL);
        assertEquals(action, LiveActivityWire.decodeAction(LiveActivityWire.encode(action)));
    }

    @Test
    void rejectsUnknownVersionOversizeTruncationAndTrailingBytes() {
        byte[] valid = LiveActivityWire.encode(new Snapshot(SESSION, 1, List.of()));
        byte[] unknownVersion = valid.clone();
        unknownVersion[0]++;
        assertThrows(IOException.class, () -> LiveActivityWire.decodeServer(unknownVersion));
        assertThrows(IOException.class, () -> LiveActivityWire.decodeServer(Arrays.copyOf(valid, valid.length - 1)));
        assertThrows(IOException.class, () -> LiveActivityWire.decodeServer(Arrays.copyOf(valid, valid.length + 1)));
        assertThrows(IOException.class, () -> LiveActivityWire.decodeServer(new byte[LiveActivityWire.MAX_SERVER_BYTES + 1]));
        assertThrows(IOException.class, () -> LiveActivityWire.decodeAction(new byte[LiveActivityWire.MAX_ACTION_BYTES + 1]));
    }

    @Test
    void rejectsMalformedTextAndUnboundedSnapshots() {
        assertThrows(IllegalArgumentException.class, () ->
                new NetworkLiveActivity(ID, "标题".repeat(100), "", Status.RUNNING, 0, null));
        assertThrows(IllegalArgumentException.class, () ->
                new NetworkLiveActivity(ID, "§k格式", "", Status.RUNNING, 0, null));
        assertThrows(IllegalArgumentException.class, () ->
                new NetworkLiveActivity(ID, "标题", "", Status.RUNNING, Float.NaN, null));
        assertThrows(IllegalArgumentException.class, () ->
                new NetworkLiveActivity(ID, "标题", "", Status.RUNNING, 1.1f, null));
        assertThrows(IllegalArgumentException.class, () ->
                new NetworkLiveActivity(ID, "标题", "", Status.RUNNING, 0,
                        new ActionOffer(UiId.parse("other:cancel"), "取消")));
        var entry = new VersionedActivity(1, ACTIVITY);
        assertThrows(IllegalArgumentException.class, () -> new Snapshot(SESSION, 1, List.of(entry, entry)));
        assertThrows(IllegalArgumentException.class, () -> new Snapshot(SESSION, 1,
                java.util.Collections.nCopies(LiveActivityPackets.MAX_ACTIVITIES + 1, entry)));
    }

    @Test
    void receiverRequiresSnapshotAndRejectsDuplicatesGapsAndPreviousEpochs() {
        var receiver = new LiveActivityReceiver();
        var newSession = new UUID(1, 3);
        assertFalse(receiver.accept(new Remove(SESSION, 1, ID)));
        assertTrue(receiver.accept(new Snapshot(SESSION, 1, List.of())));
        assertFalse(receiver.accept(new Snapshot(SESSION, 1, List.of())));
        assertFalse(receiver.accept(new Remove(SESSION, 3, ID)));
        assertFalse(receiver.accept(new Snapshot(newSession, 4, List.of())));
        assertTrue(receiver.accept(new Snapshot(SESSION, 4, List.of())));
        assertTrue(receiver.accept(new Remove(SESSION, 5, ID)));
        assertFalse(receiver.accept(new Remove(SESSION, 5, ID)));

        receiver.reset();
        assertTrue(receiver.accept(new Snapshot(newSession, 1, List.of())));
        assertFalse(receiver.accept(new Snapshot(SESSION, 6, List.of())));
        assertEquals(newSession, receiver.session());
    }
}
