// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.glass;

import com.dishanhai.sh_better_ui.kernel.api.ServiceKey;
import com.dishanhai.sh_better_ui.kernel.api.UIService;
import com.dishanhai.sh_better_ui.kernel.api.UiId;

/** Managed Liquid Glass materials and a read-only snapshot of the client's renderer. */
public interface GlassService extends UIService {
    ServiceKey<GlassService> KEY = new ServiceKey<>(new UiId("sh_better_ui", "glass"), GlassService.class, 1);

    default GlassMaterial material(GlassVariant variant) {
        return GlassMaterial.preset(variant);
    }

    /** Thread-safe snapshot. Counts are cumulative since installation and published after each native pass. */
    Diagnostics diagnostics();

    record Diagnostics(long passesWithCapture, long opticalDraws, long fallbackDraws,
                       long captureBytes, boolean shaderReady) {}
}
