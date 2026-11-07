package com.dishanhai.sh_better_ui;

import com.dishanhai.sh_better_ui.config.GlassBackdropSettings;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;

/** 山海：共用 UI 庫的共同入口，不載入任何客戶端或可選整合類別。 */
@Mod(ShBetterUi.MOD_ID)
public final class ShBetterUi {
    public static final String MOD_ID = "sh_better_ui";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ShBetterUi(ModContainer container) {
        var defaults = GlassBackdropSettings.DEFAULT;
        var builder = new ModConfigSpec.Builder().push("glassBackdrop");
        var enabled = builder.define("enabled", defaults.enabled());
        var radius = builder.defineInRange("blurRadiusScale", defaults.blurRadiusScale(), 0.0, 2.0);
        var strength = builder.defineInRange("strength", defaults.strength(), 0.0, 1.0);
        var tint = builder.defineInRange("tintScale", defaults.tintScale(), 0.0, 2.0);
        var grain = builder.defineInRange("grainScale", defaults.grainScale(), 0.0, 2.0);
        var spec = builder.pop().build();
        container.getEventBus().addListener((ModConfigEvent event) -> {
            if (event.getConfig().getSpec() != spec) return;
            GlassBackdropSettings.update(spec.isLoaded()
                    ? new GlassBackdropSettings(enabled.get(), radius.get().floatValue(), strength.get().floatValue(),
                            tint.get().floatValue(), grain.get().floatValue())
                    : defaults);
        });
        container.registerConfig(ModConfig.Type.CLIENT, spec);
    }
}
