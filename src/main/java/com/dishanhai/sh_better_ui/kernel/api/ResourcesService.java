// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentAssets;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.function.LongConsumer;

/** Published resource generations and caches using the client resource manager. */
public interface ResourcesService extends UIService {
    long version();

    /** Called on the UI thread after caches are invalidated for a successfully applied generation. */
    Subscription observe(ModuleContext module, ScopeHandle scope, LongConsumer observer);

    /** Cache is invalidated on reload and released with the caller's scope. */
    AlignmentAssets alignmentAssets(ModuleContext module, ScopeHandle scope, ResourceManager resources);
}
