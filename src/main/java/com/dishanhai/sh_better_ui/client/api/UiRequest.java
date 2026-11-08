package com.dishanhai.sh_better_ui.client.api;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 山海：单个查询槽或业务操作槽。查询随页面释放，业务操作由跨页面的业务模型持有。
 * 只有 {@link #receive} 可从网络线程调用，其他方法在客户端线程调用。
 */
public final class UiRequest<T> implements AutoCloseable {
    public enum Mode { QUERY, OPERATION }

    public enum Status { IDLE, PENDING, SUCCEEDED, REJECTED, TIMED_OUT, DISCONNECTED, CANCELLED, CLOSED }

    /** 状态不变时复用同一个快照。终态数据由调用方提供，可以为空。 */
    public record Snapshot<T>(Status status, UUID id, T value) {}

    private final Mode mode;
    private final Executor clientExecutor;
    private final Supplier<?> connection;
    private final LongSupplier nowMs;
    private Snapshot<T> state = new Snapshot<>(Status.IDLE, null, null);
    private WeakReference<Object> originConnection;
    private Consumer<UUID> queryCancellation;
    private boolean receiptPending;
    private long queryCommand;
    private long startedMs;
    private long timeoutMs;

    UiRequest(Mode mode, Executor clientExecutor, Supplier<?> connection, LongSupplier nowMs) {
        this.mode = Objects.requireNonNull(mode);
        this.clientExecutor = Objects.requireNonNull(clientExecutor);
        this.connection = Objects.requireNonNull(connection);
        this.nowMs = Objects.requireNonNull(nowMs);
    }

    public Optional<UUID> begin(long timeoutMs) {
        return begin(timeoutMs, null);
    }

    /**
     * 在发包前登记请求并取得线上的操作 ID。空结果表示不可提交。
     * 查询替换旧查询，可以登记同步的协议取消动作，且只能在原连接上执行。
     * 业务操作直到确认或权威对账且模型已消费回执后，才能再次提交。
     */
    public Optional<UUID> begin(long timeoutMs, Consumer<UUID> cancel) {
        if (timeoutMs <= 0 || (cancel != null && mode == Mode.OPERATION)) throw new IllegalArgumentException();
        if (state.status() == Status.CLOSED) return Optional.empty();
        if (mode == Mode.QUERY) {
            long command = ++queryCommand;
            if (state.status() == Status.PENDING) {
                release(Status.CANCELLED, true);
                if (command != queryCommand || state.status() == Status.CLOSED) return Optional.empty();
            }
        }

        Object current = connection.get();
        refresh(current);
        if (mode == Mode.OPERATION && (unresolvedOperation() || receiptPending)) return Optional.empty();
        if (current == null) {
            if (state.status() != Status.DISCONNECTED) state = new Snapshot<>(Status.DISCONNECTED, null, null);
            return Optional.empty();
        }

        var id = UUID.randomUUID();
        startedMs = nowMs.getAsLong();
        this.timeoutMs = timeoutMs;
        originConnection = new WeakReference<>(current);
        queryCancellation = cancel;
        state = new Snapshot<>(Status.PENDING, id, null);
        return Optional.of(id);
    }

    /** 将服务端回执交给客户端执行器，提交时检查 ID、连接、超时和槽状态。 */
    public void receive(UUID id, boolean accepted, T value) {
        Objects.requireNonNull(id);
        clientExecutor.execute(() -> {
            if (!id.equals(state.id()) || originConnection == null) return;
            refresh(connection.get());
            if (originConnection == null || !id.equals(state.id())) return;
            if (state.status() == Status.PENDING || (mode == Mode.OPERATION && state.status() == Status.TIMED_OUT)) {
                resolve(id, accepted, value);
            }
        });
    }

    /** 在 Screen tick 或读取状态时检查连接及真实时间；不注册任务或持有页面回调。 */
    public Snapshot<T> snapshot() {
        if (originConnection != null) refresh(connection.get());
        return state;
    }

    /** 面板关闭意图发出时使查询失效，即使关闭动画尚在播放。 */
    public void cancelQuery() {
        if (mode != Mode.QUERY) throw new IllegalStateException();
        queryCommand++;
        switch (state.status()) {
            case PENDING, SUCCEEDED, REJECTED -> release(Status.CANCELLED, true);
            default -> {}
        }
    }

    /**
     * 使用当前连接上已核实的权威结果对账。调用方必须确认指定 ID 的真实业务结局，
     * 例如使用当前会话的查询取得服务端幂等记录，超时或断线只表示结果未知。
     */
    public boolean reconcile(UUID id, boolean accepted, T authority) {
        if (mode != Mode.OPERATION) throw new IllegalStateException();
        Objects.requireNonNull(id);
        if (!id.equals(state.id()) || !unresolvedOperation()) return false;
        Object current = connection.get();
        refresh(current);
        if (current == null) return false;
        resolve(id, accepted, authority);
        return true;
    }

    /** 业务模型处理终态后确认回执，允许后续操作覆盖该快照。同一回执只能确认一次。 */
    public boolean acknowledgeReceipt(UUID id) {
        if (mode != Mode.OPERATION) throw new IllegalStateException();
        Objects.requireNonNull(id);
        if (!receiptPending || !id.equals(state.id())) return false;
        receiptPending = false;
        return true;
    }

    /**
     * 释放查询槽时使旧回执失效，并尝试协议取消。
     * 关闭业务操作槽只释放本地记录，业务模型应先处理回执，或持久保存未决 ID。
     */
    @Override
    public void close() {
        if (state.status() != Status.CLOSED) release(Status.CLOSED, true);
    }

    private boolean unresolvedOperation() {
        return state.id() != null && switch (state.status()) {
            case PENDING, TIMED_OUT, DISCONNECTED -> true;
            default -> false;
        };
    }

    private void refresh(Object current) {
        if (originConnection == null) return;
        if (current == null || originConnection.get() != current) {
            release(Status.DISCONNECTED, false);
        } else if (state.status() == Status.PENDING && nowMs.getAsLong() - startedMs >= timeoutMs) {
            if (mode == Mode.QUERY) release(Status.TIMED_OUT, true);
            else state = new Snapshot<>(Status.TIMED_OUT, state.id(), null);
        }
    }

    private void resolve(UUID id, boolean accepted, T value) {
        state = new Snapshot<>(accepted ? Status.SUCCEEDED : Status.REJECTED, id, value);
        queryCancellation = null;
        if (mode == Mode.OPERATION) {
            originConnection = null;
            receiptPending = true;
        }
    }

    private void release(Status status, boolean sendCancellation) {
        UUID id = state.id();
        var previousConnection = originConnection == null ? null : originConnection.get();
        var cancel = queryCancellation;
        state = new Snapshot<>(status, status == Status.CLOSED ? null : id, null);
        originConnection = null;
        queryCancellation = null;
        receiptPending = false;
        if (sendCancellation && cancel != null && previousConnection != null
                && previousConnection == connection.get()) cancel.accept(id);
    }
}
