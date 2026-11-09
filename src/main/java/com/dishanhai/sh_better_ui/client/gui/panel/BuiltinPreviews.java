package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.theme.DynamicThemePreview;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** 山海：将内置示例登记到客户端开发者图库。 */
public final class BuiltinPreviews {
    private BuiltinPreviews() {}

    public static void registerInto(PreviewRegistry registry) {
        for (var sample : DeveloperSamples.values()) {
            registry.register(sample.spec);
        }
        DeveloperBottomSheetPreview.registerInto(registry);
        DeveloperAnimationPreview.registerInto(registry);
        VisualAlignmentPreview.registerInto(registry);
        registry.register(new PreviewSpec("sh_better_ui:dynamic_theme", PreviewSpec.Category.MATERIALS, 260,
                "sh_better_ui.theme.dynamic.title", () -> host -> {
                    var area = host.viewport();
                    if (area.width() < 40 || area.height() < 22) return;
                    var button = new UiButton(area.x(), area.y(), Math.min(160, area.width()), 22,
                            Component.translatable("sh_better_ui.theme.dynamic.open"), ignored ->
                                    DynamicThemePreview.screen(host.screen(), () -> host.screen().getFocused()).open(),
                            host.preferences(), false);
                    button.active = host.interactive();
                    host.add(button);
                }));
    }
}
