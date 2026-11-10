// Author: dishanhai
package com.dishanhai.sh_better_ui.spatial;

import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ServiceKey;
import com.dishanhai.sh_better_ui.kernel.api.UIService;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;

import java.util.function.DoubleConsumer;

public interface SpatialUI extends UIService {
    ServiceKey<SpatialUI> KEY = new ServiceKey<>(new UiId("sh_better_ui", "spatial"), SpatialUI.class, 1);

    static SpatialUI get() { return UnifiedUI.get().require(KEY); }

    enum Capability { SDF_SHADOWS, INNER_SHADOWS, DYNAMIC_LIGHTING, BACKGROUND_BLUR,
        SPATIAL_ANIMATION, ARBITRARY_PATH_SHADOWS, GPU_TIMING }
    enum Support { SUPPORTED, UNSUPPORTED, DISABLED, DEGRADED }

    SpatialScene mount(ModuleContext module, ScopeHandle scope);
    Support capability(Capability capability);

    /** Binds one scalar property. A null node binds a scene property, such as a light channel. */
    Motion motion(SpatialScene scene, SpatialScene.Node node, UiId property, float initial,
                  SpringSpec spring, DoubleConsumer apply);

    enum MotionPolicy { REPLACE, RETARGET, QUEUE, BLEND, IGNORE }

    record SpringSpec(double response, double damping) {
        public static final SpringSpec INTERACTION = new SpringSpec(.16, 1);
        public SpringSpec {
            SpatialGeometry.range(response, .08, 2, "spring response");
            SpatialGeometry.range(damping, .3, 2, "spring damping");
        }
    }

    interface Motion extends AutoCloseable {
        long play(float target, MotionPolicy policy, int priority);
        long tween(float target, long durationMs, boolean eased, int priority);
        void pause();
        void resume();
        void cancel();
        boolean takeCompletion(long revision);
        float value();
        @Override void close();
    }
}
