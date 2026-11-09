package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;

/** 山海：将内置示例登记到客户端开发者图库。 */
public final class BuiltinPreviews {
    private BuiltinPreviews() {}

    public static void registerInto(PreviewRegistry registry) {
        for (var sample : DeveloperSamples.values()) {
            registry.register(sample.spec);
        }
        DeveloperBottomSheetPreview.registerInto(registry);
        DeveloperAnimationPreview.registerInto(registry);
    }
}
