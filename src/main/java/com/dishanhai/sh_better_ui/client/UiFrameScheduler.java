package com.dishanhai.sh_better_ui.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Supplier;

/** 山海：顶层客户端帧的单调时钟快照与界面完成动作。 */
public final class UiFrameScheduler {
    private static final ThreadLocal<Frame> FRAME = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> ACTIONS_SUPPRESSED = new ThreadLocal<>();
    private static final ThreadLocal<OwnerLifetime> LIFETIME = new ThreadLocal<>();
    private static final OwnerLifetime INACTIVE = new OwnerLifetime(null);

    private UiFrameScheduler() {}

    /** 绘制期间共享帧首时间，其他时刻直接读取单调时钟。 */
    public static long nowMs() {
        Frame frame = FRAME.get();
        return frame != null && frame.rendering ? frame.nowMs : rawNowMs();
    }

    /** 读取实时单调毫秒，用于等待期限等必须独立于渲染帧的计时。 */
    public static long rawNowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    /** 仅活动界面能推进会触发界面动作的状态。 */
    public static boolean canCommitFor(Screen owner) {
        return ACTIONS_SUPPRESSED.get() == null && owner == activeScreen();
    }

    /** 在同一帧内准备或预绘父页，这段期间屏蔽会提交界面状态的帧尾动作。 */
    public static void renderPreview(Runnable draw) {
        withoutFrameActions(draw);
    }

    /** 捕获本次挂载的界面生命周期，退出界面时失效。没有界面时返回 null。 */
    public static OwnerLifetime captureOwnerLifetime() {
        if (ACTIONS_SUPPRESSED.get() != null) return INACTIVE;
        Screen owner = activeScreen();
        if (owner == null) return null;
        OwnerLifetime lifetime = LIFETIME.get();
        if (lifetime == null || lifetime.screen() != owner) {
            if (lifetime != null) lifetime.screen.clear();
            lifetime = new OwnerLifetime(owner);
            LIFETIME.set(lifetime);
        }
        return lifetime;
    }

    private static void withoutFrameActions(Runnable action) {
        Boolean previous = ACTIONS_SUPPRESSED.get();
        ACTIONS_SUPPRESSED.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            if (previous == null) ACTIONS_SUPPRESSED.remove();
        }
    }

    /**
     * 客户端线程在绘制完毕后提交动作。帧外立即执行，提交和执行时均检查活动 Screen。
     * owner 为 null 时仅在没有活动 Screen 的情况下运行。
     */
    public static void afterFrame(Screen owner, Runnable action) {
        Objects.requireNonNull(action);
        if (!canCommitFor(owner)) return;
        Frame frame = FRAME.get();
        if (frame == null || !frame.rendering) {
            action.run();
            return;
        }
        if (frame.actions == null) frame.actions = new ArrayDeque<>();
        frame.actions.addLast(new Deferred(owner, action));
    }

    static void beginFrame() {
        beginFrame(rawNowMs(), UiFrameScheduler::minecraftScreen);
    }

    static void beginFrame(long nowMs) {
        beginFrame(nowMs, UiFrameScheduler::minecraftScreen);
    }

    static void beginFrame(long nowMs, Supplier<Screen> activeScreen) {
        FRAME.set(new Frame(nowMs, activeScreen));
    }

    static void endFrame() {
        Frame frame = FRAME.get();
        if (frame == null || !frame.rendering) return;
        frame.rendering = false;
        try {
            if (frame.actions == null) return;
            Deferred next;
            while ((next = frame.actions.pollFirst()) != null) {
                if (next.owner() == frame.activeScreen.get()) next.action().run();
            }
        } finally {
            abortFrame();
        }
    }

    static void abortFrame() {
        FRAME.remove();
    }

    static void ownerClosing(Screen owner) {
        OwnerLifetime lifetime = LIFETIME.get();
        if (lifetime != null && lifetime.screen() == owner) {
            lifetime.screen.clear();
            LIFETIME.remove();
        }
        Frame frame = FRAME.get();
        if (frame != null && frame.actions != null) {
            frame.actions.removeIf(next -> next.owner() == owner);
        }
    }

    static void ownerClosing(Screen owner, Runnable cleanup) {
        ownerClosing(owner);
        withoutFrameActions(cleanup);
    }

    private static Screen activeScreen() {
        Frame frame = FRAME.get();
        return frame == null ? minecraftScreen() : frame.activeScreen.get();
    }

    private static Screen minecraftScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.screen;
    }

    private record Deferred(Screen owner, Runnable action) {}

    /** 退出或回收界面后失效，持有期间不会延长界面对象的寿命。 */
    public static final class OwnerLifetime {
        private final WeakReference<Screen> screen;

        private OwnerLifetime(Screen owner) {
            screen = new WeakReference<>(owner);
        }

        public Screen screen() {
            return screen.get();
        }

        public boolean valid() {
            return screen() != null;
        }
    }

    private static final class Frame {
        private final long nowMs;
        private final Supplier<Screen> activeScreen;
        private boolean rendering = true;
        private ArrayDeque<Deferred> actions;

        private Frame(long nowMs, Supplier<Screen> activeScreen) {
            this.nowMs = nowMs;
            this.activeScreen = activeScreen;
        }
    }
}
