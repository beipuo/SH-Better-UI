package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.OwnershipLease;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.internal.ClientUiKernel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** 山海：把客户端控件已经处理的按键交给同一手势处理至放开。 */
public final class UiPointerCapture {
    private static Capture active;
    private static final List<WeakReference<PressRegistration>> pressHandlers = new ArrayList<>();
    private static final Set<Integer> pendingReleases = new HashSet<>();

    private UiPointerCapture() {}

    public interface Handler {
        void drag(double x, double y);
        void release(double x, double y);
        void cancel();
    }

    @FunctionalInterface
    public interface PressHandler {
        boolean press(double x, double y, int button);
    }

    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }

    /** 在原画面处理按下前接入有独立命中检测的控件。接入方保留返回值至控件移除。 */
    public static Registration registerPress(Screen owner, PressHandler handler) {
        var registration = new PressRegistration(Objects.requireNonNull(owner), Objects.requireNonNull(handler));
        pressHandlers.removeIf(reference -> reference.get() == null);
        pressHandlers.add(new WeakReference<>(registration));
        return registration;
    }

    /** 仅在控件处理按下时调用。坐标沿用 Screen 的 GUI 坐标。 */
    public static boolean start(Screen owner, int button, Handler handler) {
        return start(owner, button, handler, ClientUiKernel.hasNativeAuthority()
                ? revoke -> ClientUiKernel.claimNativePointer(owner, null, null, null, revoke) : null, null);
    }

    /** Internal bridge for a permission-checked kernel module capture. */
    public static boolean startScoped(Screen owner, int button, Handler handler, ModuleContext module,
                                      ScopeHandle node, UiId claim) {
        Objects.requireNonNull(module);
        return start(owner, button, handler,
                revoke -> ClientUiKernel.claimNativePointer(owner, module, node, claim, revoke), module);
    }

    private static boolean start(Screen owner, int button, Handler handler, Function<Runnable, OwnershipLease> acquire,
                                 ModuleContext module) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(handler);
        if (button < 0 || active != null || blocked(owner)) return false;
        var capture = new Capture(owner, button, handler, module);
        if (acquire != null) {
            capture.lease = acquire.apply(() -> {
                if (active == capture) abandonCapture(owner);
            });
            if (capture.lease == null) return false;
        }
        active = capture;
        return true;
    }

    /** 取消后继续接住同一按键的放开，防止它落入底层控件。 */
    public static boolean cancel(Screen owner) {
        if (active == null || active.owner != owner || active.cancelled) return false;
        active.cancel();
        return true;
    }

    public static void beforePress(Screen owner, int button) {
        validate(owner);
        if (matches(owner, button)) discard(owner);
        pendingReleases.remove(button);
    }

    public static boolean press(Screen owner, int button, double x, double y) {
        if (pressHandlers.isEmpty() || blocked(owner)) return false;
        pressHandlers.removeIf(reference -> reference.get() == null);
        var snapshot = List.copyOf(pressHandlers);
        for (int i = snapshot.size() - 1; i >= 0; i--) {
            var reference = snapshot.get(i);
            PressRegistration registration = reference.get();
            if (registration != null && !registration.closed && registration.owner == owner
                    && registration.handler.press(x, y, button)) return true;
        }
        return false;
    }

    public static boolean drag(Screen owner, int button, double x, double y) {
        validate(owner);
        if (pendingReleases.contains(button)) return true;
        if (!matches(owner, button)) return false;
        Capture capture = active;
        if (!capture.cancelled) capture.drag(x, y);
        return true;
    }

    public static boolean release(Screen owner, int button, double x, double y) {
        validate(owner);
        if (pendingReleases.remove(button)) return true;
        if (!matches(owner, button)) return false;
        Capture capture = active;
        active = null;
        capture.releaseLease();
        if (!capture.cancelled) capture.release(x, y);
        return true;
    }

    public static void validate(Screen current) {
        if (active == null) return;
        if (active.owner != current) abandonCapture(active.owner);
        else if (blocked(current)) active.cancel();
    }

    private static void discard(Screen owner) {
        if (active == null || active.owner != owner) return;
        Capture capture = active;
        active = null;
        try {
            capture.releaseLease();
        } finally {
            capture.cancel();
        }
    }

    public static void ownerClosed(Screen owner) {
        abandonCapture(owner);
        pressHandlers.removeIf(reference -> {
            PressRegistration registration = reference.get();
            if (registration == null) return true;
            if (registration.owner != owner) return false;
            registration.closed = true;
            return true;
        });
    }

    /** Drops a captured gesture immediately, while remembering the matching native button release. */
    public static void windowLost(Screen owner) {
        abandonCapture(owner);
    }

    private static void abandonCapture(Screen owner) {
        if (active == null || active.owner != owner) return;
        drainRelease(active.button);
        discard(owner);
    }

    // 放开可能由新画面收到。仅保留按键，不再持有旧画面或处理器。
    static void drainRelease(int button) {
        pendingReleases.add(button);
    }

    private static boolean matches(Screen owner, int button) {
        return active != null && active.owner == owner && active.button == button;
    }

    private static boolean blocked(Screen owner) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen != owner || !client.isWindowActive()) return true;
        UiOverlayHost overlay = UiOverlayHost.current(owner);
        return overlay != null && overlay.isModalOpen();
    }

    private static final class Capture {
        private final Screen owner;
        private final int button;
        private final Handler handler;
        private final ModuleContext module;
        private boolean cancelled;
        private OwnershipLease lease;

        private Capture(Screen owner, int button, Handler handler, ModuleContext module) {
            this.owner = owner;
            this.button = button;
            this.handler = handler;
            this.module = module;
        }

        private void drag(double x, double y) {
            try {
                handler.drag(x, y);
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                if (module == null) throw failure;
                ClientUiKernel.nativeCaptureFailed(module, "drag", failure);
                if (active == this) cancel();
            }
        }

        private void release(double x, double y) {
            try {
                handler.release(x, y);
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                if (module == null) throw failure;
                ClientUiKernel.nativeCaptureFailed(module, "release", failure);
            }
        }

        private void cancel() {
            if (cancelled) return;
            cancelled = true;
            try {
                handler.cancel();
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                if (module == null) throw failure;
                ClientUiKernel.nativeCaptureFailed(module, "cancel", failure);
            }
        }

        private void releaseLease() {
            if (lease == null) return;
            var previous = lease;
            lease = null;
            previous.close();
        }
    }

    private static final class PressRegistration implements Registration {
        private final Screen owner;
        private final PressHandler handler;
        private boolean closed;

        private PressRegistration(Screen owner, PressHandler handler) {
            this.owner = owner;
            this.handler = handler;
        }

        @Override
        public void close() {
            closed = true;
            pressHandlers.removeIf(reference -> reference.get() == null || reference.get() == this);
        }
    }
}
