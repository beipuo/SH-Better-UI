// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.material;

import java.util.Map;

/** A validated snapshot that can replace a local draft in one operation. */
public record MaterialPreset(String schemaVersion, String catalogVersion, String materialId, String materialRevision,
                             Map<String, Object> parameters) {
    @SuppressWarnings("unchecked")
    public MaterialPreset {
        parameters = (Map<String, Object>) MaterialParameter.immutableJson(parameters, 0);
    }
}
