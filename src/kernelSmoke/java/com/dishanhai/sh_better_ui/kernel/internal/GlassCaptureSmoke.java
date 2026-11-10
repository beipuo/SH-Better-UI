// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

/** Runs only in the isolated development client's native Screen render pass. */
public final class GlassCaptureSmoke {
    private GlassCaptureSmoke() {}

    public static void verify(int background) {
        var viewport = GlassSceneCapture.Viewport.current();
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int unpack = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        require(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), "Fixture requires an active GUI clip");

        try (var capture = new GlassSceneCapture((message, failure) -> { throw new AssertionError(message, failure); })) {
            capture.begin();
            var first = capture.snapshot();
            require(first != null && first == capture.snapshot(), "Capture was not shared in the pass");
            require(viewport.equals(first.viewport()), "Snapshot dimensions do not match the native viewport");
            require(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == draw
                    && GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) == read
                    && GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == texture
                    && GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING) == unpack
                    && GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
                    && viewport.equals(GlassSceneCapture.Viewport.current()), "Capture leaked native GL state");
            require(readArgb(first, read) == background, "The background outside the current clip was not captured");
            capture.end();

            capture.begin();
            require(capture.snapshot().texture() == first.texture(), "Same-sized frame did not reuse its texture");
            capture.end();

            try {
                GL11.glViewport(viewport.x(), viewport.y(), viewport.width() / 2, viewport.height() / 2);
                capture.begin();
                var resized = capture.snapshot();
                require(resized != null && resized.size().width() == viewport.width() / 2
                        && resized.size().height() == viewport.height() / 2, "Capture size was stale after viewport change");
                capture.end();
                capture.reset();
                require(!GL11.glIsTexture(resized.texture()) && !GL30.glIsFramebuffer(resized.framebuffer()),
                        "Reset retained a scene capture target");
                capture.begin();
                var recreated = capture.snapshot();
                require(recreated != null && readArgb(recreated, read) == background, "Capture failed after reset");
                capture.end();
            } finally {
                GL11.glViewport(viewport.x(), viewport.y(), viewport.width(), viewport.height());
            }
        }
        System.out.println("SHB Liquid Glass capture verified on " + GL11.glGetString(GL11.GL_RENDERER)
                + " / GL " + GL11.glGetString(GL11.GL_VERSION));
    }

    private static int readArgb(GlassSceneCapture.Snapshot snapshot, int previousRead) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, snapshot.framebuffer());
        try (var stack = MemoryStack.stackPush()) {
            var rgba = stack.malloc(4);
            GL11.glReadPixels(1, 1, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
            return (rgba.get(3) & 255) << 24 | (rgba.get(0) & 255) << 16
                    | (rgba.get(1) & 255) << 8 | rgba.get(2) & 255;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
