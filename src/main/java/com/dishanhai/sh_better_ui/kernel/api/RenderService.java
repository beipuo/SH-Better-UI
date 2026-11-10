// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.client.api.BlurApi;
import com.dishanhai.sh_better_ui.client.api.GraphicsApi;

import java.util.Objects;
import java.util.function.Consumer;

/** Submits native GUI work for a single render phase with a lifetime-bound drawing context. */
public interface RenderService extends UIService {
    GraphicsApi graphics();
    BlurApi blur();

    /** SCREEN requires a mounted node. HUD uses a module scope or a session mounted through ScreenService. */
    TaskHandle submit(ModuleContext module, ScopeHandle scope, Surface surface, Layer layer, int zOffset,
                      Consumer<Context> draw);

    enum Surface { SCREEN, HUD }
    enum Layer { CONTENT, OVERLAY, TOOLTIP }

    record Frame(Surface surface, int width, int height, double guiScale, float partialTick) {
        public Frame {
            Objects.requireNonNull(surface, "surface");
            if (width < 0 || height < 0 || !Double.isFinite(guiScale) || guiScale <= 0) {
                throw new IllegalArgumentException("Invalid GUI viewport");
            }
        }
    }

    /** All operations require the current render thread and the active pass. Edges use GUI units. */
    interface Context {
        Frame frame();
        void fill(int x, int y, int right, int bottom, int argb);
        void clip(int x, int y, int right, int bottom, Runnable draw);
        void transform(double x, double y, float scale, Runnable draw);
    }
}
