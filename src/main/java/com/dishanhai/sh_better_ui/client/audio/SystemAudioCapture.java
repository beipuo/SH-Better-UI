package com.dishanhai.sh_better_ui.client.audio;

import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

/**
 * 山海：显式启动的 Windows 输出回环会话。创建实例不打开设备；没有麦克风后备路径。
 * 在所属画面 removed() 或宿主关闭时 close；重试须创建新实例。
 * 系统输出包含游戏及其他应用的声音；PCM 只留在分析工作区，不落盘或上传。
 */
public final class SystemAudioCapture implements AutoCloseable {
    public enum Status {
        READY("sh_better_ui.audio.ready"), STARTING("sh_better_ui.audio.starting"),
        RUNNING("sh_better_ui.audio.running"), STOPPING("sh_better_ui.audio.stopping"),
        CLOSED("sh_better_ui.audio.closed"), UNSUPPORTED("sh_better_ui.audio.unsupported"),
        FAILED("sh_better_ui.audio.failed");

        private final String translationKey;
        Status(String translationKey) { this.translationKey = translationKey; }
        public String translationKey() { return translationKey; }
    }

    private final LoopbackSource source;
    private final MusicAnalyzer analyzer = new MusicAnalyzer();
    private volatile Status status = Status.READY;
    private volatile Throwable failure;
    private volatile boolean closing;
    private Thread worker;

    public SystemAudioCapture() { this(new WindowsLoopbackSource()); }

    SystemAudioCapture(LoopbackSource source) { this.source = Objects.requireNonNull(source); }

    /** 一次性非阻塞启动；重复启动或关闭后返回 false。 */
    public synchronized boolean start() {
        if (status != Status.READY) return false;
        status = Status.STARTING;
        worker = new Thread(this::run, "sh-better-ui-system-output");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    public Status status() { return status; }

    /** 技术诊断供日志使用；用户可见状态用 status().translationKey()。 */
    public Optional<Throwable> failure() { return Optional.ofNullable(failure); }

    /** 渲染侧只读数值快照，不调用任何设备 API。 */
    public AudioFrame sample() {
        long nowMs = PageTransition.nowMs();
        return !closing && status == Status.RUNNING ? analyzer.sample(nowMs) : AudioFrame.silence(nowMs);
    }

    private void run() {
        try {
            if (closing) return;
            source.open();
            synchronized (this) {
                if (closing) return;
                status = Status.RUNNING;
            }
            while (!closing) {
                var block = source.poll();
                if (closing) break;
                if (block == null) LockSupport.parkNanos(5_000_000L);
                else {
                    if (block.discontinuity()) analyzer.reset();
                    analyzer.acceptInterleaved(block.samples(), block.channels(), block.sampleRate(), PageTransition.nowMs());
                }
            }
        } catch (Exception | LinkageError error) {
            failure = error;
            status = error instanceof UnsupportedOperationException ? Status.UNSUPPORTED : Status.FAILED;
        } finally {
            try {
                source.close();
            } catch (RuntimeException | LinkageError error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
                status = Status.FAILED;
            }
            analyzer.reset();
            if (closing) status = Status.CLOSED;
        }
    }

    /** 发出停止信号；最多等待一秒，由工作线程释放 COM，不从渲染线程跨线程释放。 */
    @Override public void close() {
        Thread active;
        synchronized (this) {
            closing = true;
            active = worker;
            if (active == null || !active.isAlive()) {
                status = Status.CLOSED;
                return;
            }
            status = Status.STOPPING;
        }
        LockSupport.unpark(active);
        if (active != Thread.currentThread()) {
            try { active.join(1000); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
        if (!active.isAlive()) status = Status.CLOSED;
    }
}
