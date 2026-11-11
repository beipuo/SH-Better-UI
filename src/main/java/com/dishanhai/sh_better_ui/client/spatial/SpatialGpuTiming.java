// Author: dishanhai
package com.dishanhai.sh_better_ui.client.spatial;

import com.dishanhai.sh_better_ui.spatial.SpatialUI.Support;
import com.dishanhai.sh_better_ui.spatial.internal.Scene;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.lang.ref.WeakReference;

/** Shared, non-blocking timer pairs. Busy slots are skipped instead of stalling the renderer. */
final class SpatialGpuTiming {
    private static final int SLOT_COUNT = 4;
    private static final Slot[] slots = new Slot[SLOT_COUNT];
    private static volatile Support support = Support.DEGRADED;

    private SpatialGpuTiming() {}

    static Support support() { return support; }

    static Slot begin(Scene scene) {
        if (support == Support.DEGRADED) {
            support = GL.getCapabilities().OpenGL33
                    && GL15.glGetQueryi(GL33.GL_TIMESTAMP, GL15.GL_QUERY_COUNTER_BITS) >= 32
                    ? Support.SUPPORTED : Support.UNSUPPORTED;
        }
        if (support != Support.SUPPORTED) return null;
        int available = -1;
        for (int i = 0; i < slots.length; i++) {
            var slot = slots[i];
            if (slot != null && slot.scene != null && slot.ended
                    && GL15.glGetQueryObjecti(slot.end, GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
                long duration = GL33.glGetQueryObjectui64(slot.end, GL15.GL_QUERY_RESULT)
                        - GL33.glGetQueryObjectui64(slot.start, GL15.GL_QUERY_RESULT);
                var owner = slot.scene.get();
                if (owner != null) owner.recordGpuTime(duration >= 0 ? duration : -1);
                slot.scene = null;
            }
            if (available == -1 && (slot == null || slot.scene == null)) available = i;
        }
        if (available == -1) return null;
        if (slots[available] == null) slots[available] = new Slot();
        var slot = slots[available];
        slot.scene = new WeakReference<>(scene);
        slot.ended = false;
        GL33.glQueryCounter(slot.start, GL33.GL_TIMESTAMP);
        return slot;
    }

    static void release() {
        if (support != Support.SUPPORTED) {
            support = Support.DEGRADED;
            return;
        }
        RenderSystem.assertOnRenderThread();
        for (int i = 0; i < slots.length; i++) {
            var slot = slots[i];
            if (slot == null) continue;
            GL15.glDeleteQueries(slot.start);
            GL15.glDeleteQueries(slot.end);
            slots[i] = null;
        }
        support = Support.DEGRADED;
    }

    static final class Slot implements AutoCloseable {
        private final int start = GL15.glGenQueries();
        private final int end = GL15.glGenQueries();
        private WeakReference<Scene> scene;
        private boolean ended;

        @Override public void close() {
            GL33.glQueryCounter(end, GL33.GL_TIMESTAMP);
            ended = true;
        }
    }
}
