// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassQuality;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassRegion;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassService;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.List;

/** Native optical pass. The kernel owns its only capture and program for the current client lifetime. */
final class ClientGlassRender implements GlassService, AutoCloseable {
    private final KernelHost host;
    private final GlassSceneCapture capture;
    private GlassProgram program;
    private boolean programFailed;
    private boolean passCaptured;
    private boolean updated;
    private long capturedPasses, opticalDraws, fallbackDraws;
    private volatile Diagnostics published = new Diagnostics(0, 0, 0, 0, false);

    ClientGlassRender(KernelHost host) {
        this.host = host;
        capture = new GlassSceneCapture(host::report);
    }

    @Override public Diagnostics diagnostics() { return published; }

    void begin() {
        capture.begin();
        passCaptured = false;
    }

    void end() {
        capture.end();
        if (updated) publish();
    }

    void paint(GuiGraphics graphics, List<GlassDraw> draws) {
        RenderSystem.assertOnRenderThread();
        if (draws.isEmpty()) return;
        // Both Minecraft versions enable depth testing when GuiGraphics.flush() returns.
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        try {
            paintBatch(graphics, draws);
        } finally {
            if (depthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
        }
    }

    private void paintBatch(GuiGraphics graphics, List<GlassDraw> draws) {
        var settings = ClientUiSettings.current();
        boolean sampling = settings.widgetBackdropBlur() && settings.backdrop().enabled()
                && draws.stream().anyMatch(draw -> draw.material().quality() != GlassQuality.FALLBACK);
        GlassProgram shader = sampling ? shader() : null;
        GlassSceneCapture.Snapshot scene = null;
        if (shader != null) {
            graphics.flush();
            scene = capture.snapshot();
        }
        updated = true;
        if (scene == null) {
            for (var draw : draws) fallback(graphics, draw);
            fallbackDraws += draws.size();
            return;
        }

        if (!passCaptured) {
            passCaptured = true;
            capturedPasses++;
        }
        var view = scene.viewport();
        var pose = graphics.pose().last().pose();
        var projection = RenderSystem.getProjectionMatrix();
        float sx = projection.m00() * pose.m00() * (view.width() * .5f);
        float sy = -projection.m11() * pose.m11() * (view.height() * .5f);
        float ox = view.x() + (projection.m00() * pose.m30() + projection.m30() + 1f) * (view.width() * .5f);
        float oy = view.y() + (projection.m11() * pose.m31() + projection.m31() + 1f) * (view.height() * .5f);
        float unit = Math.min(sx, sy);
        if (!Float.isFinite(unit) || unit <= 0) return;

        try (var state = new GlassDrawState()) {
            shader.bind(state, scene);
            for (var draw : draws) {
                var region = draw.region();
                float halfWidth = region.width() * .5f;
                float halfHeight = region.height() * .5f;
                boolean drawn = shader.draw(draw, view, ox + (region.x() + halfWidth) * sx,
                        oy - (region.y() + halfHeight) * sy, halfWidth * sx, halfHeight * sy, unit, settings.highContrast());
                if (drawn) {
                    if (draw.material().quality() == GlassQuality.FALLBACK) fallbackDraws++;
                    else opticalDraws++;
                }
            }
        }
    }

    private GlassProgram shader() {
        if (program == null && !programFailed) {
            try {
                program = GlassProgram.load(Minecraft.getInstance().getResourceManager());
            } catch (IOException | RuntimeException | LinkageError failure) {
                programFailed = true;
                host.report("Liquid Glass program unavailable; using solid materials", failure);
            }
        }
        return program;
    }

    private static void fallback(GuiGraphics graphics, GlassDraw draw) {
        var region = draw.region();
        int fill = draw.material().foreground().fallbackArgb();
        if (region.shape() == GlassRegion.Shape.ROUNDED || region.shape() == GlassRegion.Shape.CAPSULE) {
            RoundedCorners.draw(graphics, region.x(), region.y(), region.width(), region.height(),
                    region.radius(), 0f, fill, fill);
            return;
        }
        long top = (int) Math.ceil(region.y() - .5);
        long bottom = (int) Math.ceil((double) region.y() + region.height() - .5);
        long stride = Math.max(1L, (bottom - top + 63L) / 64L);
        double center = region.x() + region.width() * .5;
        for (long row = top; row < bottom; row += stride) {
            long end = Math.min(bottom, row + stride);
            double half = region.halfWidthAt(row + (end - row) * .5);
            int left = (int) Math.ceil(center - half - .5);
            int right = (int) Math.ceil(center + half - .5);
            if (left < right) graphics.fill(left, (int) row, right, (int) end, fill);
        }
    }

    void reload() {
        capture.reset();
        releaseProgram();
        publish();
    }

    private void releaseProgram() {
        if (program != null) {
            program.close();
            program = null;
        }
        programFailed = false;
    }

    private void publish() {
        published = new Diagnostics(capturedPasses, opticalDraws, fallbackDraws, capture.allocatedBytes(), program != null);
        updated = false;
    }

    @Override public void close() {
        try {
            capture.close();
        } finally {
            releaseProgram();
            publish();
        }
    }
}
