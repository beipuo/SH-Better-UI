// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialCatalog;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialEntry;

import java.util.Optional;

/** Binds a catalog registration to the matching generation of its native preview. */
record NativeMaterialRoute(MaterialCatalog catalog, MaterialEntry entry, PreviewSpec preview) {
    static Optional<MaterialCatalog> catalogService() {
        return UnifiedUI.isAvailable() ? UnifiedUI.get().find(UIApis.MATERIAL_CATALOG) : Optional.empty();
    }

    static NativeMaterialRoute require(String id, PreviewRegistry registry) {
        var catalog = catalogService().orElseThrow(() -> new IllegalStateException("Material catalog unavailable"));
        var entry = catalog.find(id).orElseThrow(() -> new IllegalArgumentException("Unknown material: " + id));
        return new NativeMaterialRoute(catalog, entry, preview(entry, registry)
                .orElseThrow(() -> new IllegalArgumentException("No registered native preview for " + entry.id())));
    }

    static Optional<PreviewSpec> preview(MaterialEntry entry, PreviewRegistry registry) {
        return Optional.ofNullable(entry.nativePreviewId()).flatMap(id -> registry.find(id).filter(spec -> spec.id().equals(id)));
    }

    boolean available(PreviewRegistry registry) {
        return catalogService().orElse(null) == catalog && catalog.find(entry.id()).orElse(null) == entry
                && registry.find(preview.id()).orElse(null) == preview;
    }
}
