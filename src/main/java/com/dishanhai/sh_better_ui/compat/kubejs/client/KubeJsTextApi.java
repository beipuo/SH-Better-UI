package com.dishanhai.sh_better_ui.compat.kubejs.client;

import com.dishanhai.sh_better_ui.client.gui.text.UiTextEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/** 山海：客户端脚本的旧文本构造方法与按脚本世代清理的色板注册。 */
public final class KubeJsTextApi {
    private final KubeJsUiApi owner;

    KubeJsTextApi(KubeJsUiApi owner) {
        this.owner = owner;
    }

    public Component fcs(String effects, String text, String style) {
        String name = style == null || style.isBlank() ? "ultimate" : style;
        return UiTextEffects.applyNamed(Component.literal(text == null ? "" : text), name, effects == null ? "" : effects);
    }

    public Component styled(String text, String style) {
        return fcs("", text, style);
    }

    public Component body(String text, String style) {
        String name = style == null || style.isBlank() ? "silver" : style;
        return styled(text, name.startsWith("body_") ? name : "body_" + name);
    }

    public Component format(String input) {
        return UiTextEffects.parse(input == null ? "" : input);
    }

    public Component inline(String input) {
        return format(input);
    }

    public Component fromLegacy(String input) {
        return format(input);
    }

    public Component all(String text) { return styled(text, "all"); }
    public Component random(String text) { return styled(text, "random"); }
    public Component fullRandom(String text) { return styled(text, "fullrandom"); }
    public Component rainbow(String text) { return styled(text, "rainbow"); }
    public Component ultimateRainbow(String text) { return styled(text, "ultimate"); }

    public void kjsRegister(String name, int[] rgb, boolean body, String animation) {
        String key = name.toLowerCase(Locale.ROOT);
        ResourceLocation id = ResourceLocation.tryParse(key.indexOf(':') < 0 ? "kubejs:" + key : key);
        if (id == null) throw new IllegalArgumentException("Invalid text palette ID: " + name);
        owner.registerTextPalette(id, body, UiTextEffects.PaletteAnimation.valueOf(animation.toUpperCase(Locale.ROOT)), rgb);
    }

    public List<String> kjsGetAllNames() {
        return UiTextEffects.styleNames();
    }
}
