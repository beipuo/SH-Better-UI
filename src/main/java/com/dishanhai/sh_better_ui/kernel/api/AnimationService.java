// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.client.api.MotionApi;
import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;

/** Frame-clock animation of a declared property. MotionApi supplies timelines and composition. */
public interface AnimationService extends UIService {
    MotionApi motion();

    /** Acquires the property until the track or its scope closes. Call on the UI thread. */
    Track bind(ModuleContext module, ScopeHandle node, UiId property, Spring.Preset spring, float initial,
               BooleanSupplier reducedMotion, DoubleConsumer apply);

    interface Track extends AutoCloseable {
        long play(float target, InterruptibleAnimation.Policy policy, int priority);
        long blend(float target, int priority, long durationMs, InterruptibleAnimation.BlendCurve curve);
        void pause();
        void resume();
        void cancel();
        boolean takeCompletion(long revision);
        boolean isActive();
        float value();
        @Override void close();
    }
}
