// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.material;

import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ServiceKey;
import com.dishanhai.sh_better_ui.kernel.api.UIService;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Metadata queries are thread-safe and never instantiate a renderer or preview. */
public interface MaterialCatalog extends UIService {
    ServiceKey<MaterialCatalog> KEY = new ServiceKey<>(new UiId("sh_better_ui", "material_catalog"), MaterialCatalog.class, 1);

    MaterialCatalogSnapshot snapshot();

    default Optional<MaterialEntry> find(String id) { return snapshot().find(id); }
    default List<MaterialEntry> entries() { return snapshot().entries(); }
    default UiVersion version() { return snapshot().version(); }

    default List<MaterialEntry> byCategory(String category) {
        return entries().stream().filter(entry -> entry.category().equals(category)).toList();
    }

    default List<MaterialEntry> byCapability(String capability) {
        return entries().stream().filter(entry -> entry.requiredCapabilities().contains(capability)).toList();
    }

    String exportPreset(String id, Map<String, Object> values);
    MaterialPreset importPreset(String json);

    /** Validate availability and conflicts for an extension. Call from the owner thread. */
    void validate(ModuleContext context, MaterialEntry entry);

    /** Register for the lifetime of the UUK module. Call from the owner thread. */
    Registration register(ModuleContext context, MaterialEntry entry);

    interface Registration extends AutoCloseable {
        boolean isActive();
        void replace(MaterialEntry entry);
        @Override void close();
    }
}
