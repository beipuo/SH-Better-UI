// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.BlurApi;
import com.dishanhai.sh_better_ui.client.api.GraphicsApi;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.kernel.api.*;

import java.util.Objects;
import java.util.function.Consumer;

final class ClientRender implements RenderService {
    interface Backend {
        void push();
        void pop();
        void translate(double x, double y, double z);
        void scale(float factor);
        void clip(int x, int y, int right, int bottom);
        void unclip();
        void fill(int x, int y, int right, int bottom, int color);
    }

    private final KernelHost host;
    private final ClientScreens screens;
    private Frame frame;
    private Backend backend;

    ClientRender(KernelHost host, ClientScreens screens) {
        this.host = host;
        this.screens = screens;
    }

    @Override public GraphicsApi graphics() { return ShBetterUiApi.graphics(); }
    @Override public BlurApi blur() { return ShBetterUiApi.blur(); }

    @Override
    public TaskHandle submit(ModuleContext module, ScopeHandle scope, Surface surface, Layer layer, int zOffset,
                             Consumer<Context> draw) {
        var access = host.moduleAccess(module);
        var target = access.write(scope);
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(draw, "draw");
        if (zOffset < -99 || zOffset > 99) throw new IllegalArgumentException("Invalid render Z offset");
        switch (surface) {
            case SCREEN -> {
                if (screens.ownerOf(host.ownedNode(module, target)) == null) {
                    throw new IllegalStateException("Render pass requires a mounted Screen node");
                }
            }
            case HUD -> {
                if ((target.kind() != ScopeKind.MODULE && !screens.ownsSession(target))
                        || !module.scope().owner().equals(target.owner())) {
                    throw new SecurityException("HUD render pass requires an owned module or mounted native session");
                }
            }
        }
        int z = switch (layer) {
            case CONTENT -> zOffset;
            case OVERLAY -> 200 + zOffset;
            case TOOLTIP -> 400 + zOffset;
        };
        return module.engine().scheduler().submit(UITask.ui(target, UIPhase.RENDER, UIPriority.NORMAL,
                () -> paint(surface, z, draw)));
    }

    void render(Frame viewport, Backend canvas, Runnable runPhase) {
        host.assertOwner();
        if (backend != null) throw new IllegalStateException("Nested native render phase");
        frame = viewport;
        backend = canvas;
        try {
            runPhase.run();
        } finally {
            backend = null;
            frame = null;
        }
    }

    private void paint(Surface surface, int z, Consumer<Context> draw) {
        if (backend == null || frame.surface() != surface) {
            throw new IllegalStateException("No active render context for " + surface);
        }
        var context = new PassContext();
        backend.push();
        try {
            backend.translate(0, 0, z);
            draw.accept(context);
        } finally {
            context.live = false;
            backend.pop();
        }
    }

    private final class PassContext implements Context {
        private boolean live = true;

        @Override public Frame frame() {
            requireLive();
            return frame;
        }

        @Override public void fill(int x, int y, int right, int bottom, int argb) {
            requireLive();
            backend.fill(x, y, right, bottom, argb);
        }

        @Override public void clip(int x, int y, int right, int bottom, Runnable draw) {
            requireLive();
            backend.clip(x, y, right, bottom);
            try {
                draw.run();
            } finally {
                backend.unclip();
            }
        }

        @Override public void transform(double x, double y, float scale, Runnable draw) {
            requireLive();
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Float.isFinite(scale) || scale <= 0) {
                throw new IllegalArgumentException("Invalid render transform");
            }
            backend.push();
            try {
                backend.translate(x, y, 0);
                backend.scale(scale);
                draw.run();
            } finally {
                backend.pop();
            }
        }

        private void requireLive() {
            host.assertOwner();
            if (!live) throw new IllegalStateException("UI render context has expired");
        }
    }
}
