package com.dishanhai.sh_better_ui;

import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.GlassBackdropSettings;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.WritingException;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.Map;

/** 山海：共用 UI 庫的共同入口，不載入任何客戶端或可選整合類別。 */
@Mod(ShBetterUi.MOD_ID)
public final class ShBetterUi {
    public static final String MOD_ID = "sh_better_ui";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final String BACKDROP_SECTION = "glassBackdrop";
    private static final String UI_SECTION = "ui";
    private static ModConfig clientConfig;

    public ShBetterUi(FMLJavaModLoadingContext context) {
        var defaults = ClientUiSettings.DEFAULT;
        var backdrop = defaults.backdrop();
        var builder = new ForgeConfigSpec.Builder().push(BACKDROP_SECTION);
        var enabled = builder.define("enabled", backdrop.enabled());
        var radius = builder.defineInRange("blurRadiusScale", backdrop.blurRadiusScale(), 0.0, 2.0);
        var strength = builder.defineInRange("strength", backdrop.strength(), 0.0, 1.0);
        var tint = builder.defineInRange("tintScale", backdrop.tintScale(), 0.0, 2.0);
        var grain = builder.defineInRange("grainScale", backdrop.grainScale(), 0.0, 2.0);
        var saturation = builder.defineInRange("saturationScale", backdrop.saturationScale(), 0.0, 2.0);
        var lift = builder.defineInRange("lift", backdrop.lift(), 0.0, 0.15);
        var rim = builder.defineInRange("rimScale", backdrop.rimScale(), 0.0, 2.0);
        var refraction = builder.defineInRange("refractionScale", backdrop.refractionScale(), 0.0, 2.0);
        var denseSampling = builder.define("denseSampling", backdrop.denseSampling());
        builder.pop().push(UI_SECTION);
        var highContrast = builder.define("highContrast", defaults.highContrast());
        var reducedMotion = builder.define("reducedMotion", defaults.reducedMotion());
        var widgetBackdropBlur = builder.define("widgetBackdropBlur", defaults.widgetBackdropBlur());
        var spec = builder.pop().build();
        context.getModEventBus().addListener((ModConfigEvent event) -> {
            if (event.getConfig().getSpec() != spec) return;
            synchronized (ShBetterUi.class) {
                boolean loaded = spec.isLoaded();
                clientConfig = loaded ? event.getConfig() : null;
                ClientUiSettings.update(loaded
                        ? new ClientUiSettings(new GlassBackdropSettings(enabled.get(), radius.get().floatValue(),
                                strength.get().floatValue(), tint.get().floatValue(), grain.get().floatValue(),
                                saturation.get().floatValue(), lift.get().floatValue(), rim.get().floatValue(),
                                refraction.get().floatValue(), denseSampling.get()),
                                highContrast.get(), reducedMotion.get(), widgetBackdropBlur.get())
                        : defaults);
            }
        });
        context.registerConfig(ModConfig.Type.CLIENT, spec);
    }

    public static synchronized boolean saveGlassBackdrop(GlassBackdropSettings settings) {
        return saveClientUiSettings(ClientUiSettings.current().withBackdrop(settings));
    }

    public static synchronized boolean saveClientUiSettings(ClientUiSettings settings) {
        ModConfig config = clientConfig;
        if (config == null) return false;
        CommentedConfig data = config.getConfigData();
        if (data == null) return false;
        CommentedConfig previousBackdrop = data.get(BACKDROP_SECTION);
        CommentedConfig previousUi = data.get(UI_SECTION);
        if (previousBackdrop == null || previousUi == null) return false;

        var backdrop = settings.backdrop();
        CommentedConfig updatedBackdrop = copySection(data, previousBackdrop);
        updatedBackdrop.set("enabled", backdrop.enabled());
        replaceScale(updatedBackdrop, "blurRadiusScale", backdrop.blurRadiusScale());
        replaceScale(updatedBackdrop, "strength", backdrop.strength());
        replaceScale(updatedBackdrop, "tintScale", backdrop.tintScale());
        replaceScale(updatedBackdrop, "grainScale", backdrop.grainScale());
        replaceScale(updatedBackdrop, "saturationScale", backdrop.saturationScale());
        replaceScale(updatedBackdrop, "lift", backdrop.lift());
        replaceScale(updatedBackdrop, "rimScale", backdrop.rimScale());
        replaceScale(updatedBackdrop, "refractionScale", backdrop.refractionScale());
        updatedBackdrop.set("denseSampling", backdrop.denseSampling());
        CommentedConfig updatedUi = copySection(data, previousUi);
        updatedUi.set("highContrast", settings.highContrast());
        updatedUi.set("reducedMotion", settings.reducedMotion());
        updatedUi.set("widgetBackdropBlur", settings.widgetBackdropBlur());

        // Forge 的根配置会自动保存。一次 putAll 同时替换两段并只触发一次保存。
        try {
            data.valueMap().putAll(Map.of(BACKDROP_SECTION, updatedBackdrop, UI_SECTION, updatedUi));
        } catch (WritingException error) {
            try {
                data.valueMap().putAll(Map.of(BACKDROP_SECTION, previousBackdrop, UI_SECTION, previousUi));
            } catch (WritingException rollbackError) {
                error.addSuppressed(rollbackError);
            }
            LOGGER.warn("Could not save {}", config.getFileName(), error);
            return false;
        } finally {
            config.getSpec().afterReload();
        }
        ClientUiSettings.update(settings);
        return true;
    }

    private static CommentedConfig copySection(CommentedConfig data, CommentedConfig previous) {
        CommentedConfig copy = data.createSubConfig();
        copy.putAll(previous);
        copy.putAllComments(previous);
        return copy;
    }

    private static void replaceScale(CommentedConfig section, String key, float value) {
        Number previous = section.get(key);
        if (previous.floatValue() != value) section.set(key, Double.valueOf(Float.toString(value)));
    }
}
