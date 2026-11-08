package com.dishanhai.sh_better_ui.client.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.dishanhai.sh_better_ui.client.api.UiRequest.Status.*;
import static org.junit.jupiter.api.Assertions.*;

/** 山海：用可控时间和客户端队列验证异步状态机。 */
class UiRequestTest {
    private static final class Transport {
        private final ConcurrentLinkedQueue<Runnable> clientQueue = new ConcurrentLinkedQueue<>();
        private long time;
        private Object connection = new Object();

        UiRequest<String> request(UiRequest.Mode mode) {
            return new UiRequest<>(mode, clientQueue::add, () -> connection, () -> time);
        }

        void runClientTasks() {
            for (Runnable task; (task = clientQueue.poll()) != null;) task.run();
        }
    }

    @Test
    void latestQueryWinsAndReceivesOnClientThread() throws InterruptedException {
        var wire = new Transport();
        var query = wire.request(UiRequest.Mode.QUERY);
        var cancelled = new ArrayList<UUID>();
        var a = query.begin(1_500, cancelled::add).orElseThrow();
        wire.time = 50;
        var b = query.begin(1_500, cancelled::add).orElseThrow();
        wire.time = 200;
        var c = query.begin(1_500, cancelled::add).orElseThrow();
        assertEquals(List.of(a, b), cancelled);

        wire.time = 1_000;
        Thread network = new Thread(() -> {
            query.receive(c, true, "C");
            query.receive(a, true, "A");
            query.receive(b, true, "B");
        });
        network.start();
        network.join();
        assertEquals(PENDING, query.snapshot().status());
        wire.runClientTasks();
        assertEquals(new UiRequest.Snapshot<>(SUCCEEDED, c, "C"), query.snapshot());
    }

    @Test
    void closingAndReopeningCannotCommitQueuedReplyToOldOrNewPage() {
        var wire = new Transport();
        var oldPage = wire.request(UiRequest.Mode.QUERY);
        var cancelled = new ArrayList<UUID>();
        var oldId = oldPage.begin(200, cancelled::add).orElseThrow();
        oldPage.receive(oldId, true, "old");
        oldPage.close();
        oldPage.close();
        assertEquals(List.of(oldId), cancelled);

        var newPage = wire.request(UiRequest.Mode.QUERY);
        var newId = newPage.begin(200).orElseThrow();
        newPage.receive(oldId, true, "old");
        wire.runClientTasks();
        assertEquals(new UiRequest.Snapshot<>(CLOSED, null, null), oldPage.snapshot());
        assertEquals(new UiRequest.Snapshot<>(PENDING, newId, null), newPage.snapshot());
        assertTrue(oldPage.begin(200).isEmpty());
    }

    @Test
    void cancellingPanelQueryClearsResultWithoutClosingScope() {
        var wire = new Transport();
        var query = wire.request(UiRequest.Mode.QUERY);
        var id = query.begin(200).orElseThrow();
        query.receive(id, true, "first");
        wire.runClientTasks();
        query.cancelQuery();
        query.cancelQuery();
        assertEquals(new UiRequest.Snapshot<>(CANCELLED, id, null), query.snapshot());
        assertNotEquals(id, query.begin(200).orElseThrow());
    }

    @Test
    void lateQueryReplyExpiresAndCancelsEvenWithoutPollingBeforeDelivery() {
        var wire = new Transport();
        var query = wire.request(UiRequest.Mode.QUERY);
        var cancelled = new ArrayList<UUID>();
        var id = query.begin(1_000, cancelled::add).orElseThrow();
        wire.time = 1_000;
        query.receive(id, true, "late");
        wire.runClientTasks();
        var timeout = query.snapshot();
        assertEquals(new UiRequest.Snapshot<>(TIMED_OUT, id, null), timeout);
        assertSame(timeout, query.snapshot());
        query.close();
        assertEquals(List.of(id), cancelled);
    }

    @Test
    void replacedConnectionClearsPreviousQueryAndBlocksOldReplies() {
        var wire = new Transport();
        var query = wire.request(UiRequest.Mode.QUERY);
        var first = query.begin(1_000).orElseThrow();
        query.receive(first, true, "first");
        wire.runClientTasks();
        assertEquals(SUCCEEDED, query.snapshot().status());
        wire.connection = new Object();
        assertEquals(new UiRequest.Snapshot<>(DISCONNECTED, first, null), query.snapshot());

        var second = query.begin(1_000).orElseThrow();
        query.receive(first, true, "first");
        wire.runClientTasks();
        assertEquals(new UiRequest.Snapshot<>(PENDING, second, null), query.snapshot());
    }

    @Test
    void timeoutDoesNotPermitDuplicateOperationButSameSessionCanStillReject() {
        var wire = new Transport();
        var operation = wire.request(UiRequest.Mode.OPERATION);
        var id = operation.begin(200).orElseThrow();
        assertTrue(operation.begin(200).isEmpty());
        wire.time = 1_000;
        assertEquals(TIMED_OUT, operation.snapshot().status());
        assertTrue(operation.begin(200).isEmpty());

        operation.receive(id, false, "authority");
        operation.receive(id, true, "duplicate");
        wire.runClientTasks();
        var result = operation.snapshot();
        assertEquals(new UiRequest.Snapshot<>(REJECTED, id, "authority"), result);
        assertSame(result, operation.snapshot());
        assertTrue(operation.begin(200).isEmpty());
        assertSame(result, operation.snapshot());
        assertFalse(operation.acknowledgeReceipt(UUID.randomUUID()));
        assertTrue(operation.acknowledgeReceipt(id));
        assertFalse(operation.acknowledgeReceipt(id));
        assertNotEquals(id, operation.begin(200).orElseThrow());
    }

    @Test
    void disconnectedOperationRequiresAuthoritativeReconciliationBeforeNewSubmission() {
        var wire = new Transport();
        var operation = wire.request(UiRequest.Mode.OPERATION);
        var id = operation.begin(200).orElseThrow();
        operation.receive(id, true, "old reply");
        wire.connection = null;
        assertEquals(new UiRequest.Snapshot<>(DISCONNECTED, id, null), operation.snapshot());
        assertFalse(operation.reconcile(id, true, "offline"));
        assertTrue(operation.begin(200).isEmpty());
        wire.connection = new Object();
        wire.runClientTasks();
        assertEquals(DISCONNECTED, operation.snapshot().status());
        assertTrue(operation.begin(200).isEmpty());
        assertFalse(operation.reconcile(UUID.randomUUID(), true, "wrong id"));
        assertTrue(operation.reconcile(id, true, "verified"));
        assertFalse(operation.reconcile(id, true, "duplicate"));
        assertEquals(new UiRequest.Snapshot<>(SUCCEEDED, id, "verified"), operation.snapshot());
        assertTrue(operation.begin(200).isEmpty());
        assertTrue(operation.acknowledgeReceipt(id));
        assertNotEquals(id, operation.begin(200).orElseThrow());
    }

    @Test
    void unsuccessfulOfflineSubmissionDoesNotRelockACompletedOperation() {
        var wire = new Transport();
        var operation = wire.request(UiRequest.Mode.OPERATION);
        var id = operation.begin(200).orElseThrow();
        operation.receive(id, true, "committed");
        wire.runClientTasks();

        wire.connection = null;
        assertTrue(operation.begin(200).isEmpty());
        assertEquals(new UiRequest.Snapshot<>(SUCCEEDED, id, "committed"), operation.snapshot());
        assertTrue(operation.acknowledgeReceipt(id));
        assertTrue(operation.begin(200).isEmpty());
        assertEquals(new UiRequest.Snapshot<>(DISCONNECTED, null, null), operation.snapshot());
        wire.connection = new Object();
        assertTrue(operation.begin(200).isPresent());
    }

    @Test
    void reentrantCancellationCanReplaceRequestWithoutLeakingOuterSubmission() {
        var wire = new Transport();
        var query = wire.request(UiRequest.Mode.QUERY);
        var reentrant = new ArrayList<UUID>();
        var first = query.begin(200, id -> reentrant.add(query.begin(200).orElseThrow())).orElseThrow();
        assertTrue(query.begin(200).isEmpty());
        assertEquals(1, reentrant.size());
        query.receive(first, true, "cancelled");
        wire.runClientTasks();
        assertEquals(new UiRequest.Snapshot<>(PENDING, reentrant.getFirst(), null), query.snapshot());
    }

    @Test
    void cancellationHookClosingThePanelPreventsTheReplacementSubmission() {
        var wire = new Transport();
        var query = wire.request(UiRequest.Mode.QUERY);
        var first = query.begin(200, id -> query.cancelQuery()).orElseThrow();
        assertTrue(query.begin(200).isEmpty());
        assertEquals(new UiRequest.Snapshot<>(CANCELLED, first, null), query.snapshot());
    }

    @Test
    void failedCancellationIsSurfacedAfterInvalidatingOldRequest() {
        var wire = new Transport();
        var query = wire.request(UiRequest.Mode.QUERY);
        var failure = new IllegalStateException();
        var first = query.begin(200, id -> { throw failure; }).orElseThrow();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> query.begin(200)));
        assertEquals(new UiRequest.Snapshot<>(CANCELLED, first, null), query.snapshot());
        assertTrue(query.begin(200).isPresent());
    }
}
